package com.phiigrame.ai;

import com.phiigrame.services.WorkspaceService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Find files by glob pattern.  Read-only.  Returns a newline-separated
 * list of paths relative to the project root when one is open.
 */
public class GlobTool implements AiTool {

    private final WorkspaceService workspace;

    public GlobTool(WorkspaceService workspace) {
        this.workspace = workspace;
    }

    @Override public String name() { return "glob"; }

    @Override public AiTool.Risk risk() { return AiTool.Risk.READ; }

    @Override
    public String description() {
        return "Find files by glob. Args: {pattern, path?, max_results?}.";
    }

    @Override
    public String execute(Map<String, Object> args) throws Exception {
        String pattern = AiTool.str(args, "pattern", null);
        if (pattern == null || pattern.isBlank()) return "Error: 'pattern' is required";
        String baseRaw = AiTool.str(args, "path", ".");
        int max = Math.max(1, Math.min(2000, AiTool.integer(args, "max_results", 200)));

        Path base = ToolCallParser.resolveAgainstProject(baseRaw, workspace.getProjectDir());
        if (!Files.exists(base)) return "Error: path does not exist: " + base;

        // Translate glob to a regex; only **, * and ? are special here.
        String[] segments = pattern.split("/");
        StringBuilder rx = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            String seg = segments[i];
            if ("**".equals(seg)) {
                rx.append(".*");
            } else {
                for (int j = 0; j < seg.length(); j++) {
                    char c = seg.charAt(j);
                    if (c == '*') rx.append("[^/]*");
                    else if (c == '?') rx.append("[^/]");
                    else if ("\\.[]{}()+-^$|".indexOf(c) >= 0) rx.append('\\').append(c);
                    else rx.append(c);
                }
            }
            if (i < segments.length - 1) rx.append('/');
        }
        java.util.regex.Pattern compiled = java.util.regex.Pattern.compile("^" + rx + "$");

        final List<String> out = new ArrayList<>();
        final Path baseAbs = base;
        final java.util.regex.Pattern finalCompiled = compiled;
        try (Stream<Path> stream = Files.walk(baseAbs)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                String rel = baseAbs.relativize(p).toString().replace('\\', '/');
                if (finalCompiled.matcher(rel).matches()) {
                    out.add(rel);
                }
            });
        }
        if (out.isEmpty()) return "(no files match " + pattern + " under " + base + ")";
        List<String> sorted = new ArrayList<>(out);
        sorted.sort(String::compareTo);
        if (sorted.size() > max) sorted = sorted.subList(0, max);
        return String.join("\n", sorted) + (sorted.size() >= max ? "\n... (truncated to " + max + ")" : "");
    }
}
