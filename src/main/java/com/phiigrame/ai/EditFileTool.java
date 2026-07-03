package com.phiigrame.ai;

import com.phiigrame.services.WorkspaceService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Edit an existing file by replacing an exact snippet.  Either {@code old}
 * and {@code new} are supplied directly, or the model writes
 * {@code match_text} and {@code replacement} (a friendlier alias pair).
 */
public class EditFileTool implements AiTool {

    private final WorkspaceService workspace;

    public EditFileTool(WorkspaceService workspace) {
        this.workspace = workspace;
    }

    @Override public String name() { return "edit_file"; }

    @Override
    public String description() {
        return "Edit an existing file by replacing a snippet. Args: {path: string, " +
                "old: string, new: string} (or aliases match_text / replacement). " +
                "Fails if 'old' is not found exactly once in the file.";
    }

    @Override
    public String execute(Map<String, Object> args) throws Exception {
        String raw = AiTool.str(args, "path", null);
        String oldText = firstNonBlank(args, "old", "match_text", "find");
        String newText = firstNonBlank(args, "new", "replacement", "replace");
        if (oldText == null) return "Error: 'old' / 'match_text' is required";
        if (newText == null) newText = "";

        Path p = ToolCallParser.resolveAgainstProject(raw, workspace.getProjectDir());
        if (workspace.getProjectDir() != null
                && !ToolCallParser.isInside(workspace.getProjectDir().toPath(), p)) {
            return "Error: path escapes the project root: " + p;
        }
        if (!Files.exists(p)) return "Error: file does not exist: " + p;
        if (!Files.isRegularFile(p)) return "Error: not a regular file: " + p;

        String content = Files.readString(p);
        int first = content.indexOf(oldText);
        int last = content.lastIndexOf(oldText);
        if (first < 0) {
            return "Error: 'old' snippet not found in " + p;
        }
        if (first != last) {
            return "Error: 'old' snippet matches " + ((last - first > 0) ?
                    "multiple" : "exactly") + " places in the file - provide more context.";
        }
        String updated = content.substring(0, first) + newText + content.substring(first + oldText.length());
        Files.writeString(p, updated);
        workspace.refreshFileTree();
        // Report a small unified-diff-ish summary so the model can describe what it did
        return "Edited " + p + " (replaced " + oldText.length() +
                " chars with " + newText.length() + " chars)";
    }

    private static String firstNonBlank(Map<String, Object> args, String... keys) {
        if (args == null) return null;
        for (String k : keys) {
            Object v = args.get(k);
            if (v != null) {
                String s = String.valueOf(v);
                if (!s.isEmpty()) return s;
            }
        }
        return null;
    }
}
