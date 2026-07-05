package com.phiigrame.ai;

import com.phiigrame.services.WorkspaceService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Create a new file with the given content. */
public class CreateFileTool implements AiTool {

    private final WorkspaceService workspace;

    public CreateFileTool(WorkspaceService workspace) {
        this.workspace = workspace;
    }

    @Override public String name() { return "create_file"; }

    @Override public AiTool.Risk risk() { return AiTool.Risk.WRITE; }

    @Override
    public String description() {
        return "Create a new file. Args: {path, content}.";
    }

    @Override
    public String execute(Map<String, Object> args) throws Exception {
        String raw = AiTool.str(args, "path", null);
        String content = AiTool.str(args, "content", "");
        Path p = ToolCallParser.resolveAgainstProject(raw, workspace.getProjectDir());
        if (!ToolCallParser.isInside(workspace.getProjectDir().toPath(), p)) {
            return "Error: path escapes the project root: " + p;
        }
        if (Files.exists(p)) {
            return "Error: file already exists at " + p +
                    " - use edit_file to modify it instead.";
        }
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        Files.writeString(p, content);
        workspace.refreshFileTree();
        return "Created " + p + " (" + content.length() + " chars)";
    }
}
