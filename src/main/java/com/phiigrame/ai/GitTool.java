package com.phiigrame.ai;

import com.phiigrame.services.GitService;
import com.phiigrame.services.WorkspaceService;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Thin wrapper around the system {@code git} binary that the AI can
 * call.  Read-only status / log / diff are auto-approved; commits and
 * pushes always require user approval via the {@link com.phiigrame.ai.ToolCallParser.ToolCallback}.
 */
public class GitTool implements AiTool {

    /** Which sub-command this instance implements, e.g. {@code "status"}. */
    private final String subcommand;
    private final WorkspaceService workspace;

    public GitTool(WorkspaceService workspace, String subcommand) {
        this.workspace = workspace;
        this.subcommand = subcommand;
    }

    @Override public String name() { return "git_" + subcommand; }

    @Override public AiTool.Risk risk() {
        switch (subcommand) {
            case "status":
            case "log":
            case "diff":
                return AiTool.Risk.READ;
            case "push":
            case "reset":
            case "clean":
                return AiTool.Risk.DESTRUCTIVE;
            default:
                return AiTool.Risk.WRITE;
        }
    }

    @Override
    public String description() {
        switch (subcommand) {
            case "status":
                return "Show `git status` for the project. Read-only.";
            case "log":
                return "Show recent `git log` (one line per commit). Args: {n?: int (default 10)}. Read-only.";
            case "diff":
                return "Show `git diff` for the working tree. Args: {path?: string}. Read-only.";
            case "add":
                return "Run `git add` on one or more paths. Args: {paths: [string]}. Write - always requires approval.";
            case "commit":
                return "Run `git commit -m <message>`. Args: {message: string, add_all?: bool (default true)}. " +
                        "Write - always requires approval.";
            case "push":
                return "Run `git push` to the configured remote. Args: {remote?: string (default 'origin'), " +
                        "branch?: string (default current branch), set_upstream?: bool (default true)}. " +
                        "Write - always requires approval.";
            default:
                return "Git sub-command: " + subcommand;
        }
    }

    @Override
    public String execute(Map<String, Object> args) throws Exception {
        File cwd = workspace.getProjectDir();
        if (cwd == null) return "Error: no project is open";
        switch (subcommand) {
            case "status": return run(cwd, "status", "--short", "--branch");
            case "log": {
                int n = Math.max(1, Math.min(200, AiTool.integer(args, "n", 10)));
                return run(cwd, "log", "--pretty=format:%h %an %ar: %s", "-n", String.valueOf(n));
            }
            case "diff": {
                String p = AiTool.str(args, "path", null);
                List<String> cmd = new ArrayList<>(Arrays.asList("diff"));
                if (p != null && !p.isBlank()) cmd.add(p);
                return run(cwd, cmd.toArray(new String[0]));
            }
            case "add": {
                Object pathsObj = args == null ? null : args.get("paths");
                List<String> paths = new ArrayList<>();
                if (pathsObj instanceof List) {
                    for (Object o : (List<?>) pathsObj) paths.add(String.valueOf(o));
                } else if (pathsObj instanceof String) {
                    paths.add((String) pathsObj);
                } else {
                    return "Error: 'paths' (array of strings) is required";
                }
                if (paths.isEmpty()) return "Error: 'paths' is empty";
                List<String> cmd = new ArrayList<>();
                cmd.add("add");
                cmd.addAll(paths);
                return run(cwd, cmd.toArray(new String[0]));
            }
            case "commit": {
                String msg = AiTool.str(args, "message", null);
                if (msg == null || msg.isBlank()) return "Error: 'message' is required";
                boolean addAll = AiTool.str(args, "add_all", "true").equalsIgnoreCase("true");
                List<String> cmd = new ArrayList<>();
                cmd.add("commit");
                if (addAll) cmd.add("-a");
                cmd.add("-m");
                cmd.add(msg);
                return run(cwd, cmd.toArray(new String[0]));
            }
            case "push": {
                String remote = AiTool.str(args, "remote", "origin");
                String branch = AiTool.str(args, "branch", null);
                boolean upstream = AiTool.str(args, "set_upstream", "true").equalsIgnoreCase("true");
                List<String> cmd = new ArrayList<>();
                cmd.add("push");
                if (upstream) cmd.add("-u");
                cmd.add(remote);
                if (branch != null && !branch.isBlank()) cmd.add(branch);
                return run(cwd, cmd.toArray(new String[0]));
            }
            default:
                return "Error: unsupported sub-command: " + subcommand;
        }
    }

    private String run(File cwd, String... cmd) throws Exception {
        List<String> full = new ArrayList<>();
        full.add("git");
        full.addAll(Arrays.asList(cmd));
        ProcessBuilder pb = new ProcessBuilder(full).directory(cwd).redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                out.append(line).append('\n');
                if (out.length() > 8000) break;
            }
        }
        if (!p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return "Error: git timed out\n" + out;
        }
        int code = p.exitValue();
        String header = "[git " + String.join(" ", cmd) + " -> exit " + code + "]\n";
        return ToolCallParser.truncate(header + out, 8000);
    }
}
