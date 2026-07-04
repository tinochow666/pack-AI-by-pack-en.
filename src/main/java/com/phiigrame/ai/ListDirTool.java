package com.phiigrame.ai;

import com.phiigrame.services.WorkspaceService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** List the entries in a directory. */
public class ListDirTool implements AiTool {

    private final WorkspaceService workspace;

    public ListDirTool(WorkspaceService workspace) {
        this.workspace = workspace;
    }

    @Override public String name() { return "list_dir"; }

    @Override public AiTool.Risk risk() { return AiTool.Risk.READ; }

    @Override
    public String description() {
        return "List files and sub-directories. Args: {path?: string (default '.')}. " +
                "Returns a directory listing.";
    }

    @Override
    public String execute(Map<String, Object> args) throws Exception {
        String raw = AiTool.str(args, "path", ".");
        Path p = ToolCallParser.resolveAgainstProject(raw, workspace.getProjectDir());
        if (!Files.exists(p)) return "Error: directory does not exist: " + p;
        if (!Files.isDirectory(p)) return "Error: not a directory: " + p;
        return ToolCallParser.formatListing(p);
    }
}
