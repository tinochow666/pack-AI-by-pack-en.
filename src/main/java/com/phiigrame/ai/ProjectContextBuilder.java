package com.phiigrame.ai;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Builds a short text summary of the user's project that can be
 * prepended to the AI's system prompt.  Goal: make the model aware of
 * the project structure without having to ask the user, while staying
 * well below the model's context budget.
 *
 * <p>The summary contains:
 * <ul>
 *   <li>The project root path</li>
 *   <li>A short file tree (2 levels deep, capped at ~80 entries)</li>
 *   <li>The contents of any "key" file the project has
 *       (README, build.gradle, package.json, pom.xml, etc.) truncated
 *       to ~1 KB each</li>
 *   <li>An optional "current file" section when the caller supplies
 *       the file the user is currently editing</li>
 * </ul>
 *
 * Designed to be cheap: caps total output at ~5 KB and skips hidden
 * directories and {@code node_modules}/{@code build}/{@code .gradle}
 * by default.
 */
public class ProjectContextBuilder {

    /** Hard cap on the size of the generated context string. */
    public static final int MAX_TOTAL_CHARS = 5_000;

    /** Max depth for the file tree (root = 0). */
    public static final int MAX_TREE_DEPTH = 2;

    /** Max number of entries in the file tree. */
    public static final int MAX_TREE_ENTRIES = 80;

    /** Max chars per key file snippet. */
    public static final int MAX_SNIPPET_CHARS = 1_000;

    /** Directories we always skip - they are noise. */
    private static final Set<String> SKIP_DIRS = new HashSet<>(Arrays.asList(
            "node_modules", ".gradle", "build", "dist", "out", "target",
            ".git", ".idea", ".vscode", "__pycache__", ".cache", "venv",
            "releases"
    ));

    /** File names that count as "key" - their contents are prepended. */
    private static final Set<String> KEY_FILE_NAMES = new HashSet<>(Arrays.asList(
            "readme.md", "readme", "readme.txt",
            "package.json", "tsconfig.json", "vite.config.ts", "vite.config.js",
            "build.gradle", "build.gradle.kts", "settings.gradle", "pom.xml",
            "cargo.toml", "go.mod", "requirements.txt", "pyproject.toml",
            "index.html", "main.py", "app.py", "main.go", "main.rs",
            "itch_readme.md"
    ));

    /** File extensions we will never dump (binaries). */
    private static final Set<String> SKIP_EXT = new HashSet<>(Arrays.asList(
            "jar", "class", "exe", "dll", "so", "dylib", "png", "jpg",
            "jpeg", "gif", "ico", "pdf", "zip", "tar", "gz", "rar",
            "7z", "mp3", "mp4", "wav", "ogg", "webm", "mov", "avi",
            "ttf", "otf", "woff", "woff2", "o", "obj", "lib", "a"
    ));

    public static class CurrentFile {
        public final String path;
        public final String language; // e.g. "java", "python" or null
        public final String content;
        public final int cursorLine;  // 1-based, 0 if unknown
        public CurrentFile(String path, String language, String content, int cursorLine) {
            this.path = path;
            this.language = language;
            this.content = content;
            this.cursorLine = cursorLine;
        }
    }

    /**
     * Build a project context string.  Returns an empty string when no
     * project is open.  The string never exceeds {@link #MAX_TOTAL_CHARS}.
     */
    public String build(File projectDir, CurrentFile current) {
        if (projectDir == null || !projectDir.isDirectory()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("Project: ").append(projectDir.getAbsolutePath()).append("\n\n");

        // 1) file tree
        List<String> tree = new ArrayList<>();
        walkTree(projectDir.toPath(), 0, MAX_TREE_DEPTH, tree);
        if (!tree.isEmpty()) {
            sb.append("File tree (depth ").append(MAX_TREE_DEPTH).append(", capped at ")
              .append(MAX_TREE_ENTRIES).append("):\n");
            int n = 0;
            for (String line : tree) {
                if (n++ >= MAX_TREE_ENTRIES) {
                    sb.append("  ... (more files)\n");
                    break;
                }
                sb.append("  ").append(line).append("\n");
            }
            sb.append("\n");
        }

        // 2) key files - dump short snippets
        List<File> keyFiles = findKeyFiles(projectDir);
        for (File f : keyFiles) {
            if (sb.length() > MAX_TOTAL_CHARS - 200) break;
            String snippet = readSnippet(f, MAX_SNIPPET_CHARS);
            if (snippet == null) continue;
            sb.append("--- ").append(relPath(projectDir, f)).append(" ---\n");
            sb.append(snippet).append("\n\n");
        }

        // 3) current file - always include, even if it pushes us over
        if (current != null && current.path != null) {
            sb.append("--- currently open: ").append(current.path);
            if (current.language != null) sb.append(" (").append(current.language).append(")");
            if (current.cursorLine > 0) sb.append(", cursor line ").append(current.cursorLine);
            sb.append(" ---\n");
            String c = current.content == null ? "" : current.content;
            if (c.length() > MAX_SNIPPET_CHARS * 2) {
                c = c.substring(0, MAX_SNIPPET_CHARS * 2) + "\n... (truncated)";
            }
            sb.append(c).append("\n");
        }

        if (sb.length() > MAX_TOTAL_CHARS) {
            sb.setLength(MAX_TOTAL_CHARS);
            sb.append("\n... (context truncated)");
        }
        return sb.toString();
    }

    private void walkTree(Path dir, int depth, int max, List<String> out) {
        if (depth > max) return;
        File[] files = dir.toFile().listFiles();
        if (files == null) return;
        Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        for (File f : files) {
            if (f.isHidden()) continue;
            if (f.isDirectory()) {
                if (SKIP_DIRS.contains(f.getName().toLowerCase())) continue;
                out.add(indent(depth) + f.getName() + "/");
                if (depth < max) walkTree(f.toPath(), depth + 1, max, out);
            } else {
                if (SKIP_EXT.contains(ext(f.getName()).toLowerCase())) continue;
                out.add(indent(depth) + f.getName());
            }
        }
    }

    private static String indent(int depth) {
        char[] cs = new char[depth * 2];
        Arrays.fill(cs, ' ');
        return new String(cs);
    }

    private static String ext(String name) {
        int d = name.lastIndexOf('.');
        return d < 0 ? "" : name.substring(d + 1);
    }

    private List<File> findKeyFiles(File root) {
        List<File> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root.toPath(), 2)) {
            s.filter(Files::isRegularFile).forEach(p -> {
                File f = p.toFile();
                if (f.isHidden()) return;
                String name = f.getName().toLowerCase();
                if (KEY_FILE_NAMES.contains(name)) {
                    if (f.length() < 64 * 1024) out.add(f);
                }
            });
        } catch (IOException ignored) {}
        // Sort: README first, then build files, then rest.
        out.sort((a, b) -> {
            int ra = rank(a.getName().toLowerCase());
            int rb = rank(b.getName().toLowerCase());
            if (ra != rb) return Integer.compare(ra, rb);
            return a.getName().compareToIgnoreCase(b.getName());
        });
        return out;
    }

    private static int rank(String name) {
        if (name.startsWith("readme")) return 0;
        if (name.equals("package.json") || name.equals("pyproject.toml")
                || name.equals("cargo.toml") || name.equals("go.mod")) return 1;
        if (name.equals("build.gradle") || name.equals("build.gradle.kts")
                || name.equals("pom.xml")) return 2;
        if (name.equals("index.html")) return 3;
        return 4;
    }

    private static String readSnippet(File f, int max) {
        try {
            byte[] bytes = Files.readAllBytes(f.toPath());
            if (bytes.length == 0) return null;
            // Cheap binary check
            int check = Math.min(bytes.length, 512);
            for (int i = 0; i < check; i++) {
                byte b = bytes[i];
                if (b == 0) return null; // NUL byte = binary
            }
            String s = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            if (s.length() > max) s = s.substring(0, max) + "\n... (truncated)";
            return s;
        } catch (IOException e) {
            return null;
        }
    }

    private static String relPath(File root, File f) {
        String r = root.toPath().relativize(f.toPath()).toString();
        return r.replace('\\', '/');
    }
}
