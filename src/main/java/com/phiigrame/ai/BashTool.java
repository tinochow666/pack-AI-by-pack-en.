package com.phiigrame.ai;

import com.phiigrame.services.WorkspaceService;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Run an arbitrary shell command on the host.  Always requires explicit
 * user approval, which the {@link com.phiigrame.ai.ToolCallParser.ToolCallback}
 * is responsible for.
 *
 * <p>The command runs in the project directory if one is open, otherwise in
 * the user's working directory.  Output is captured (stdout + stderr merged)
 * and returned to the model.  Commands time out after {@link #DEFAULT_TIMEOUT_MS}
 * to prevent runaway processes from locking the UI thread.
 */
public class BashTool implements AiTool {

    /** Default timeout for a single bash invocation. */
    public static final long DEFAULT_TIMEOUT_MS = 30_000L;

    private final WorkspaceService workspace;

    public BashTool(WorkspaceService workspace) {
        this.workspace = workspace;
    }

    @Override public String name() { return "bash"; }

    @Override public AiTool.Risk risk() { return AiTool.Risk.DESTRUCTIVE; }

    @Override
    public String description() {
        return "Run a shell command in the project directory. " +
                "Args: {command: string, timeout_ms?: int}. " +
                "Returns combined stdout+stderr (truncated to 8000 chars). " +
                "ALWAYS requires explicit user approval.";
    }

    @Override
    public String execute(Map<String, Object> args) throws Exception {
        String command = AiTool.str(args, "command", null);
        if (command == null || command.isBlank()) {
            return "Error: 'command' is required";
        }
        long timeoutMs = Math.max(1000L, AiTool.integer(args, "timeout_ms", 0) * 1L);
        if (timeoutMs == 0) timeoutMs = DEFAULT_TIMEOUT_MS;

        File cwd = workspace.getProjectDir();
        if (cwd == null) cwd = new File(System.getProperty("user.dir"));

        ProcessBuilder pb = new ProcessBuilder();
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            pb.command("cmd.exe", "/c", command);
        } else {
            pb.command("bash", "-c", command);
        }
        pb.directory(cwd);
        pb.redirectErrorStream(true);

        Process proc = pb.start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            char[] buf = new char[4096];
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                if (!proc.isAlive()) break;
                if (proc.getInputStream().available() > 0) {
                    int n = r.read(buf);
                    if (n > 0) out.append(buf, 0, n);
                } else {
                    try { Thread.sleep(20); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
            // drain remaining
            try {
                if (proc.isAlive()) proc.destroyForcibly();
            } catch (Exception ignored) {}
        }

        int exitCode;
        try {
            if (!proc.waitFor(2, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                exitCode = -1;
            } else {
                exitCode = proc.exitValue();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            proc.destroyForcibly();
            exitCode = -1;
        }

        String header = "[exit " + exitCode + " in " + cwd.getAbsolutePath() + "]\n";
        return ToolCallParser.truncate(header + out.toString(), 8000);
    }
}
