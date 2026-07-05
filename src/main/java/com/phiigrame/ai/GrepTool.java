package com.phiigrame.ai;

import com.phiigrame.services.WorkspaceService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Search inside files for a regex or literal substring.  Read-only.
 * Supports a glob filter and a max-results cap so the model doesn't get
 * flooded with output.
 */
public class GrepTool implements AiTool {

    private final WorkspaceService workspace;

    public GrepTool(WorkspaceService workspace) {
        this.workspace = workspace;
    }

    @Override public String name() { return "grep"; }

    @Override public AiTool.Risk risk() { return AiTool.Risk.READ; }

    @Override
    public String description() {
        return "Search files for a regex. Args: {pattern, path?, glob?, max_results?}.";
    }

    @Override
    public String execute(Map<String, Object> args) throws Exception {
        String patternStr = AiTool.str(args, "pattern", null);
        if (patternStr == null || patternStr.isBlank()) {
            return "Error: 'pattern' is required";
        }
        String baseRaw = AiTool.str(args, "path", ".");
        String glob = AiTool.str(args, "glob", "*");
        int max = Math.max(1, Math.min(500, AiTool.integer(args, "max_results", 50)));

        Path base = ToolCallParser.resolveAgainstProject(baseRaw, workspace.getProjectDir());
        if (!Files.exists(base)) return "Error: path does not exist: " + base;

        Pattern pattern;
        try {
            pattern = Pattern.compile(patternStr, Pattern.MULTILINE);
        } catch (Exception ex) {
            return "Error: invalid regex: " + ex.getMessage();
        }

        List<String> out = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(base)) {
            List<Path> files = new ArrayList<>();
            stream.filter(Files::isRegularFile).forEach(p -> {
                String name = p.getFileName().toString();
                if (matchGlob(name, glob)) files.add(p);
            });
            for (Path f : files) {
                if (out.size() >= max) break;
                if (isProbablyBinary(f)) continue;
                List<String> lines;
                try {
                    lines = Files.readAllLines(f);
                } catch (IOException e) {
                    continue;
                }
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (pattern.matcher(line).find()) {
                        out.add(f + ":" + (i + 1) + ":" + line);
                        if (out.size() >= max) break;
                    }
                }
            }
        }

        if (out.isEmpty()) return "(no matches for /" + patternStr + "/ under " + base + ")";
        return String.join("\n", out) + (out.size() >= max ? "\n... (truncated to " + max + ")" : "");
    }

    /** Tiny case-sensitive glob matcher (supports * and ? wildcards). */
    private static boolean matchGlob(String name, String glob) {
        if (glob == null || glob.isEmpty() || "*".equals(glob)) return true;
        StringBuilder rx = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') rx.append(".*");
            else if (c == '?') rx.append('.');
            else if (Character.isLetterOrDigit(c)) rx.append(c);
            else rx.append("\\").append(c);
        }
        rx.append('$');
        return name.matches(rx.toString());
    }

    private static boolean isProbablyBinary(Path p) {
        try {
            if (Files.size(p) > 1_000_000L) return true;
            byte[] head = new byte[Math.min(2048, (int) Files.size(p))];
            try (var in = Files.newInputStream(p)) {
                int n = in.read(head);
                for (int i = 0; i < n; i++) {
                    if (head[i] == 0) return true;
                }
            }
        } catch (IOException ignored) {}
        return false;
    }
}
