package com.phiigrame.ai;

import com.phiigrame.services.WorkspaceService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Delete a file. Always requires user confirmation (handled in the callback). */
public class DeleteFileTool implements AiTool {

    private final WorkspaceService workspace;

    public DeleteFileTool(WorkspaceService workspace) {
        this.workspace = workspace;
    }

    @Override public String name() { return "delete_file"; }

    @Override public AiTool.Risk risk() { return AiTool.Risk.DESTRUCTIVE; }

    @Override
    public String description() {
        return "Delete a file. Args: {path}.";
    }

    @Override
    public String execute(Map<String, Object> args) throws Exception {
        String raw = AiTool.str(args, "path", null);
        Path p = ToolCallParser.resolveAgainstProject(raw, workspace.getProjectDir());
        if (workspace.getProjectDir() != null
                && !ToolCallParser.isInside(workspace.getProjectDir().toPath(), p)) {
            return "Error: path escapes the project root: " + p;
        }
        if (!Files.exists(p)) return "Error: file does not exist: " + p;
        if (!Files.isRegularFile(p)) return "Error: not a regular file: " + p;
        Files.delete(p);
        workspace.refreshFileTree();
        return "Deleted " + p;
    }
}
