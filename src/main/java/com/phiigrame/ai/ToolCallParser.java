package com.phiigrame.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses tool calls out of raw model output and dispatches them to the
 * registered {@link AiTool} implementations.
 *
 * <p>Two parser strategies are supported:
 * <ol>
 *   <li><b>JSON block</b> - the model writes
 *       <pre>{@code
 *       ```tool
 *       {"name": "read_file", "args": {"path": "src/main.java"}}
 *       ```}
 *       </pre>
 *       This is the recommended format and is what the chat prompt instructs
 *       the model to emit.
 *   <li><b>Inline tag</b> - the model writes
 *       <pre>{@code
 *       <tool name="read_file" path="src/main.java"/>}
 *       </pre>
 *       Used as a fallback if the model ignores the block format.
 * </ol>
 *
 * Parsing is intentionally lenient: the goal is to be forgiving enough that
 * a 1.5B-parameter model can drive it reliably.
 */
public class ToolCallParser {

    /** Marker the model is told to use; matches a {@code ```tool ... ```} block. */
    private static final Pattern TOOL_BLOCK = Pattern.compile(
            "```(?:tool|json)?\\s*\\n?(\\{[\\s\\S]*?\"name\"\\s*:[\\s\\S]*?\\})\\s*\\n?```",
            Pattern.CASE_INSENSITIVE);

    /** Fallback: {@code <tool name="..." path="..." .../>} */
    private static final Pattern TOOL_TAG = Pattern.compile(
            "<tool\\s+([^/>]*)/?>", Pattern.CASE_INSENSITIVE);

    private static final Gson GSON = new Gson();

    /** A single tool call as extracted from the model output. */
    public static final class ToolCall {
        public final String name;
        public final Map<String, Object> args;
        /** Length of the matched text in the original response, so the caller can strip it. */
        public final int matchStart;
        public final int matchEnd;

        public ToolCall(String name, Map<String, Object> args, int matchStart, int matchEnd) {
            this.name = name;
            this.args = args == null ? new LinkedHashMap<>() : args;
            this.matchStart = matchStart;
            this.matchEnd = matchEnd;
        }
    }

    private ToolCallParser() {}

    /** Pull every tool call out of the model output, preserving order. */
    public static List<ToolCall> extract(String response) {
        List<ToolCall> out = new ArrayList<>();
        if (response == null || response.isEmpty()) return out;

        // 1) ```tool ... ``` blocks
        Matcher m = TOOL_BLOCK.matcher(response);
        while (m.find()) {
            ToolCall tc = parseJson(m.group(1), m.start(), m.end());
            if (tc != null) out.add(tc);
        }
        if (!out.isEmpty()) return out;

        // 2) <tool ... /> tags
        m = TOOL_TAG.matcher(response);
        while (m.find()) {
            ToolCall tc = parseTag(m.group(1), m.start(), m.end());
            if (tc != null) out.add(tc);
        }
        return out;
    }

    /**
     * Run every tool call found in {@code response} and produce a list of
     * (tool-call, result) pairs in the order they were issued.  The callback
     * is invoked once per call, on the calling thread, so UI code can
     * prompt the user before executing.
     */
    public static List<ToolResult> executeAll(String response,
                                              ToolRegistry registry,
                                              ToolCallback callback) {
        List<ToolResult> results = new ArrayList<>();
        for (ToolCall call : extract(response)) {
            AiTool tool = registry.get(call.name);
            String result;
            try {
                if (tool == null) {
                    result = "Error: unknown tool '" + call.name + "'";
                } else {
                    boolean proceed = callback == null || callback.confirm(tool, call);
                    if (!proceed) {
                        result = "User denied permission to call " + call.name + ".";
                    } else {
                        result = tool.execute(call.args);
                        if (result == null) result = "(no result)";
                    }
                }
            } catch (Exception ex) {
                result = "Error: " + ex.getMessage();
            }
            if (callback != null) callback.onResult(tool, call, result);
            results.add(new ToolResult(call, result));
        }
        return results;
    }

    public static final class ToolResult {
        public final ToolCall call;
        public final String result;
        public ToolResult(ToolCall call, String result) {
            this.call = call;
            this.result = result == null ? "" : result;
        }
    }

    /** Hook the IDE installs to prompt the user before destructive ops. */
    public interface ToolCallback {
        /** Return true to allow execution, false to abort with a "denied" result. */
        boolean confirm(AiTool tool, ToolCall call);
        /** Called after the tool runs (always, even on deny / error). */
        void onResult(AiTool tool, ToolCall call, String result);
    }

    // ---- internals --------------------------------------------------------

    private static ToolCall parseJson(String json, int start, int end) {
        try {
            JsonElement el = JsonParser.parseString(json);
            if (!el.isJsonObject()) return null;
            JsonObject obj = el.getAsJsonObject();
            String name = obj.has("name") ? obj.get("name").getAsString() : null;
            if (name == null || name.isBlank()) return null;
            Map<String, Object> args = new LinkedHashMap<>();
            if (obj.has("args") && obj.get("args").isJsonObject()) {
                args = jsonToMap(obj.get("args").getAsJsonObject());
            } else {
                // allow flat {"name":"read_file","path":"..."}
                for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                    if ("name".equals(e.getKey())) continue;
                    args.put(e.getKey(), unwrap(e.getValue()));
                }
            }
            return new ToolCall(name.trim(), args, start, end);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ToolCall parseTag(String attrs, int start, int end) {
        try {
            Map<String, Object> args = new LinkedHashMap<>();
            String name = null;
            // very small attribute parser: name="..." or name='...' or name=value
            Matcher m = Pattern.compile("(\\w+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|(\\S+))").matcher(attrs);
            while (m.find()) {
                String key = m.group(1);
                String val = m.group(3) != null ? m.group(3)
                        : m.group(4) != null ? m.group(4)
                        : m.group(5);
                if ("name".equals(key)) name = val;
                else args.put(key, val);
            }
            if (name == null) return null;
            return new ToolCall(name.trim(), args, start, end);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Map<String, Object> jsonToMap(JsonObject obj) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            out.put(e.getKey(), unwrap(e.getValue()));
        }
        return out;
    }

    private static Object unwrap(JsonElement el) {
        if (el == null || el.isJsonNull()) return null;
        if (el.isJsonPrimitive()) {
            var p = el.getAsJsonPrimitive();
            if (p.isString())  return p.getAsString();
            if (p.isNumber())  return p.getAsNumber();
            if (p.isBoolean()) return p.getAsBoolean();
        }
        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            List<Object> list = new ArrayList<>(arr.size());
            for (JsonElement e : arr) list.add(unwrap(e));
            return list;
        }
        if (el.isJsonObject()) return jsonToMap(el.getAsJsonObject());
        return el.toString();
    }

    // ---- small I/O helpers shared by tools --------------------------------

    /** Resolve a relative path against the current project root (or cwd if no project). */
    public static Path resolveAgainstProject(String raw, java.io.File projectDir) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        Path p = Paths.get(raw);
        if (p.isAbsolute()) return p.normalize();
        if (projectDir != null) return projectDir.toPath().resolve(p).normalize();
        return Paths.get(System.getProperty("user.dir")).resolve(p).normalize();
    }

    /**
     * Return true when {@code candidate} lives inside {@code root} (or
     * equals it).  Used to keep the AI tools from escaping the
     * workspace by passing {@code ../../etc/passwd} or an absolute
     * path.
     */
    public static boolean isInside(Path root, Path candidate) {
        if (root == null || candidate == null) return false;
        Path r = root.toAbsolutePath().normalize();
        Path c = candidate.toAbsolutePath().normalize();
        return c.equals(r) || c.startsWith(r);
    }

    /** Pretty-format a small file listing. */
    public static String formatListing(Path dir) {
        StringBuilder sb = new StringBuilder();
        sb.append("Directory: ").append(dir).append('\n');
        try (var stream = Files.list(dir)) {
            String[] entries = stream
                    .map(p -> {
                        String name = p.getFileName().toString();
                        return Files.isDirectory(p) ? name + "/" : name;
                    })
                    .sorted()
                    .toArray(String[]::new);
            for (String e : entries) sb.append("  ").append(e).append('\n');
        } catch (IOException e) {
            sb.append("  (error: ").append(e.getMessage()).append(")\n");
        }
        return sb.toString();
    }

    /** Truncate very long output to keep the next prompt small. */
    public static String truncate(String s, int maxChars) {
        if (s == null) return "";
        if (s.length() <= maxChars) return s;
        return s.substring(0, maxChars) + "\n... (truncated, " +
                (s.length() - maxChars) + " more chars)";
    }

    /** Quote a one-line summary for the confirmation dialog. */
    public static String previewFor(Path path) {
        try {
            if (!Files.isRegularFile(path)) return "(binary or non-regular file)";
            long size = Files.size(path);
            if (size > 64 * 1024) return size + " bytes (large file)";
            return Arrays.toString(Files.readAllBytes(path)).length() + " bytes";
        } catch (IOException e) {
            return "(unreadable: " + e.getMessage() + ")";
        }
    }
}
