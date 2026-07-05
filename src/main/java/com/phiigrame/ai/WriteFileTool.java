package com.phiigrame.ai;

import com.phiigrame.services.WorkspaceService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Replace the entire contents of a file with new content.  Use this when
 * the user (or the AI) wants to rewrite a file from scratch, e.g. to
 * generate a brand-new module or scaffold a class.
 *
 * <p>For surgical changes prefer {@link EditFileTool}, which can fail-fast
 * if the snippet is ambiguous.
 */
public class WriteFileTool implements AiTool {

    private final WorkspaceService workspace;

    public WriteFileTool(WorkspaceService workspace) {
        this.workspace = workspace;
    }

    @Override public String name() { return "write_file"; }

    @Override public AiTool.Risk risk() { return AiTool.Risk.WRITE; }

    @Override
    public String description() {
        return "Replace a file's contents. Args: {path, content}.";
    }

    @Override
    public String execute(Map<String, Object> args) throws Exception {
        String raw = AiTool.str(args, "path", null);
        String content = AiTool.str(args, "content", "");
        Path p = ToolCallParser.resolveAgainstProject(raw, workspace.getProjectDir());
        if (workspace.getProjectDir() != null
                && !ToolCallParser.isInside(workspace.getProjectDir().toPath(), p)) {
            return "Error: path escapes the project root: " + p;
        }
        if (!Files.exists(p)) return "Error: file does not exist: " + p +
                " - use create_file for new files.";
        if (!Files.isRegularFile(p)) return "Error: not a regular file: " + p;
        Files.writeString(p, content);
        workspace.refreshFileTree();
        return "Wrote " + p + " (" + content.length() + " chars)";
    }
}
