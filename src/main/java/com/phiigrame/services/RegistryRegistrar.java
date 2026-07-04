package com.phiigrame.services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Registers the IDE with Windows so it shows up in the Start Menu and
 * is searchable from the taskbar.
 *
 * <p>Two registry entries are written (per-user, no admin needed):
 * <ol>
 *   <li><b>App Paths</b> - {@code HKCU\Software\Microsoft\Windows\
 *       CurrentVersion\App Paths\PhiigrameIDE.exe} so Windows can find
 *       the executable by name (used by Search / "Run" dialog).</li>
 *   <li><b>Start Menu shortcut</b> - a {@code .lnk} is created in
 *       {@code %APPDATA%\Microsoft\Windows\Start Menu\Programs\} so the
 *       app appears in the Start menu and is indexed by Search.</li>
 * </ol>
 *
 * The class never touches {@code HKLM} so no elevation is required.
 * It also exposes {@link #unregister()} to remove everything we wrote.
 */
public class RegistryRegistrar {

    public static final String EXE_NAME = "PhiigrameIDE.exe";
    public static final String FRIENDLY_NAME = "Phiigrame IDE";

    private final Path exePath;
    private final String startMenuDir;
    private final String startMenuShortcut;
    private final String appPathsKey;

    public RegistryRegistrar(Path exePath) {
        this.exePath = exePath;
        this.startMenuDir = System.getenv("APPDATA") + "\\Microsoft\\Windows\\Start Menu\\Programs";
        this.startMenuShortcut = startMenuDir + "\\" + FRIENDLY_NAME + ".lnk";
        this.appPathsKey = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\App Paths\\" + EXE_NAME;
    }

    /** Result of a register/unregister attempt - shown to the user. */
    public static class Result {
        public final boolean ok;
        public final String message;
        public Result(boolean ok, String message) { this.ok = ok; this.message = message; }
    }

    public Result register() {
        if (exePath == null || !Files.isRegularFile(exePath)) {
            return new Result(false, "Cannot find " + EXE_NAME + " at " + exePath +
                    " - build the app-image with jpackage first.");
        }
        try {
            // 1) App Paths registry entry (makes the .exe discoverable
            //    by name from "Run" / "Start" search).
            String exe = exePath.toString();
            runReg(new String[]{
                    "reg", "add", appPathsKey, "/ve", "/d", exe, "/f"
            });
            runReg(new String[]{
                    "reg", "add", appPathsKey, "/v", "Path", "/d",
                    exePath.getParent().toString(), "/f"
            });
            runReg(new String[]{
                    "reg", "add", appPathsKey, "/v", "FriendlyAppName",
                    "/d", FRIENDLY_NAME, "/f"
            });

            // 2) Start Menu shortcut via PowerShell + WScript.Shell COM.
            runShortcut("create", startMenuShortcut, exe);

            return new Result(true, "Registered " + FRIENDLY_NAME + " to:\n" +
                    "  • " + startMenuShortcut + "\n" +
                    "  • " + appPathsKey);
        } catch (Exception e) {
            return new Result(false, "Registration failed: " + e.getMessage());
        }
    }

    public Result unregister() {
        try {
            runReg(new String[]{"reg", "delete", appPathsKey, "/f"});
            Files.deleteIfExists(Paths.get(startMenuShortcut));
            return new Result(true, "Removed " + FRIENDLY_NAME + " from Start Menu.");
        } catch (Exception e) {
            return new Result(false, "Unregister failed: " + e.getMessage());
        }
    }

    public boolean isRegistered() {
        try {
            String out = runReg(new String[]{"reg", "query", appPathsKey});
            return out != null && out.contains(EXE_NAME);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Run a {@code reg} command.  Output (stdout+stderr) is captured and
     * returned.  Throws when {@code reg} exits with a non-zero code.
     */
    private String runReg(String[] cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        String out;
        try (InputStream in = p.getInputStream()) {
            out = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        int code = p.waitFor();
        if (code != 0) {
            throw new IOException("reg failed (exit " + code + "): " + out.trim());
        }
        return out;
    }

    /**
     * Use PowerShell + WScript.Shell to create or delete a Windows .lnk
     * shortcut.  Going through PowerShell avoids pulling in a JNI
     * library just for this.
     */
    private void runShortcut(String action, String shortcut, String target)
            throws IOException, InterruptedException {
        Path ps1 = Files.createTempFile("phiigrame_shim_", ".ps1");
        String body;
        if ("create".equals(action)) {
            // $ws->CreateShortcut(shortcut).TargetPath = target
            body  = "$ws = New-Object -ComObject WScript.Shell;\n";
            body += "$s  = $ws.CreateShortcut('" + ps1Escape(shortcut) + "');\n";
            body += "$s.TargetPath = '" + ps1Escape(target) + "';\n";
            body += "$s.WorkingDirectory = '" + ps1Escape(parentOf(target)) + "';\n";
            body += "$s.WindowStyle = 1;\n";
            body += "$s.Description = '" + ps1Escape(FRIENDLY_NAME) + "';\n";
            body += "$s.Save();\n";
        } else {
            // delete handled by Files.delete in caller
            return;
        }
        Files.writeString(ps1, body, StandardCharsets.UTF_8);
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-File", ps1.toString())
                    .redirectErrorStream(true);
            Process p = pb.start();
            String out;
            try (InputStream in = p.getInputStream()) {
                out = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            int code = p.waitFor();
            if (code != 0) {
                throw new IOException("shortcut " + action + " failed (exit " + code + "): " + out.trim());
            }
        } finally {
            Files.deleteIfExists(ps1);
        }
    }

    private static String ps1Escape(String s) {
        return s.replace("'", "''");
    }

    private static String parentOf(String p) {
        int i = Math.max(p.lastIndexOf('\\'), p.lastIndexOf('/'));
        return i < 0 ? p : p.substring(0, i);
    }
}
