package com.phiigrame.ai;

import com.phiigrame.services.WorkspaceService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Read a file from disk and return its contents (or a short error). */
public class ReadFileTool implements AiTool {

    private final WorkspaceService workspace;

    public ReadFileTool(WorkspaceService workspace) {
        this.workspace = workspace;
    }

    @Override public String name() { return "read_file"; }

    @Override
    public String description() {
        return "Read a UTF-8 text file. Args: {path: string, max_chars?: int}. " +
                "Returns the file content. Paths are relative to the project root if one is open.";
    }

    @Override
    public String execute(Map<String, Object> args) throws Exception {
        String raw = AiTool.str(args, "path", null);
        int max = Math.max(0, AiTool.integer(args, "max_chars", 8000));
        Path p = ToolCallParser.resolveAgainstProject(raw, workspace.getProjectDir());
        if (!Files.exists(p)) return "Error: file does not exist: " + p;
        if (!Files.isRegularFile(p)) return "Error: not a regular file: " + p;
        String text = Files.readString(p);
        return ToolCallParser.truncate(text, max <= 0 ? 8000 : max);
    }
}
