package com.phiigrame.services;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Local SQLite-backed store for everything that should survive IDE restarts:
 *   - users          (account credentials, salted + hashed)
 *   - sessions       (single-row "who is currently signed in")
 *   - recents        (recently opened projects, ordered by last opened)
 *   - model_configs  (AI model presets - local GGUF or remote OpenAI-style)
 *
 * On first launch the DB is created in {@code ~/.phiigrame/phiigrame.db}.
 * If a pre-existing {@code users.json} or {@code recents.json} is found from
 * an older build, it is migrated into the DB automatically.
 *
 * This is the single source of truth now - the older per-file JSON stores
 * are kept only for the one-shot migration and then left alone.
 */
public class UserDatabase {

    private static final String DATA_DIR = System.getProperty("user.home") + "/.phiigrame";
    private static final String DB_PATH = DATA_DIR + "/phiigrame.db";
    private static final String LEGACY_USERS = DATA_DIR + "/users.json";
    private static final String LEGACY_SESSION = DATA_DIR + "/session.json";
    private static final String LEGACY_RECENT = DATA_DIR + "/recents.json";

    /** Cap on how many recent projects we keep. */
    public static final int MAX_RECENTS = 20;

    private final Gson gson = new Gson();
    private final Connection conn;

    public UserDatabase() {
        ensureDataDir();
        try {
            // Make sure the driver is loaded (jpackage bundles the jar but
            // Class.forName makes the dependency explicit).
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
            // Fall through - DriverManager will try to find it anyway.
        }
        try {
            this.conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
            try (Statement s = conn.createStatement()) {
                s.executeUpdate("PRAGMA journal_mode = WAL;");
                s.executeUpdate("PRAGMA foreign_keys = ON;");
            }
            createSchema();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to open user database at " + DB_PATH, e);
        }
        migrateLegacy();
    }

    private void ensureDataDir() {
        File dir = new File(DATA_DIR);
        if (!dir.exists()) dir.mkdirs();
    }

    private void createSchema() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.executeUpdate(
                "CREATE TABLE IF NOT EXISTS users (" +
                "  username     TEXT PRIMARY KEY COLLATE NOCASE," +
                "  id           TEXT NOT NULL," +
                "  email        TEXT," +
                "  salt         TEXT NOT NULL," +
                "  password_hash TEXT NOT NULL," +
                "  created_at   INTEGER NOT NULL" +
                ");");
            s.executeUpdate(
                "CREATE TABLE IF NOT EXISTS sessions (" +
                "  id          INTEGER PRIMARY KEY CHECK (id = 1)," +
                "  username    TEXT NOT NULL," +
                "  token       TEXT NOT NULL," +
                "  created_at  INTEGER NOT NULL," +
                "  FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE" +
                ");");
            s.executeUpdate(
                "CREATE TABLE IF NOT EXISTS recents (" +
                "  path         TEXT PRIMARY KEY," +
                "  name         TEXT NOT NULL," +
                "  last_opened  INTEGER NOT NULL," +
                "  open_count   INTEGER NOT NULL DEFAULT 1," +
                "  branch       TEXT" +
                ");");
            s.executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_recents_last_opened " +
                "ON recents(last_opened DESC);");
        }
    }

    /**
     * One-shot import from the old per-file JSON stores. Safe to call on
     * every startup - it only writes rows that aren't already present.
     */
    private void migrateLegacy() {
        // 1. users.json -> users table
        File usersFile = new File(LEGACY_USERS);
        if (usersFile.exists()) {
            try (FileReader r = new FileReader(usersFile, StandardCharsets.UTF_8)) {
                Type t = new TypeToken<List<LegacyUser>>() {}.getType();
                List<LegacyUser> old = gson.fromJson(r, t);
                if (old != null) {
                    for (LegacyUser u : old) {
                        if (u.username == null) continue;
                        try (PreparedStatement ps = conn.prepareStatement(
                                "INSERT OR IGNORE INTO users " +
                                "(username, id, email, salt, password_hash, created_at) " +
                                "VALUES (?, ?, ?, ?, ?, ?)")) {
                            ps.setString(1, u.username);
                            ps.setString(2, u.id != null ? u.id : UUID.randomUUID().toString());
                            ps.setString(3, u.email != null ? u.email : "");
                            ps.setString(4, u.salt != null ? u.salt : "");
                            ps.setString(5, u.passwordHash != null ? u.passwordHash : "");
                            ps.setLong(6, u.createdAt);
                            ps.executeUpdate();
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 2. session.json -> sessions table
        File sessionFile = new File(LEGACY_SESSION);
        if (sessionFile.exists()) {
            try (FileReader r = new FileReader(sessionFile, StandardCharsets.UTF_8)) {
                LegacySession s = gson.fromJson(r, LegacySession.class);
                if (s != null && s.username != null) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO sessions (id, username, token, created_at) " +
                            "VALUES (1, ?, ?, ?)")) {
                        ps.setString(1, s.username);
                        ps.setString(2, s.token != null ? s.token : UUID.randomUUID().toString());
                        ps.setLong(3, System.currentTimeMillis());
                        ps.executeUpdate();
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 3. recents.json -> recents table
        File recentsFile = new File(LEGACY_RECENT);
        if (recentsFile.exists()) {
            try (FileReader r = new FileReader(recentsFile, StandardCharsets.UTF_8)) {
                Type t = new TypeToken<List<LegacyRecent>>() {}.getType();
                List<LegacyRecent> old = gson.fromJson(r, t);
                if (old != null) {
                    for (LegacyRecent e : old) {
                        if (e.path == null) continue;
                        try (PreparedStatement ps = conn.prepareStatement(
                                "INSERT OR REPLACE INTO recents " +
                                "(path, name, last_opened, open_count, branch) " +
                                "VALUES (?, ?, ?, ?, ?)")) {
                            ps.setString(1, e.path);
                            ps.setString(2, e.name != null ? e.name : new File(e.path).getName());
                            ps.setLong(3, e.lastOpened);
                            ps.setInt(4, e.openCount);
                            ps.setString(5, e.branch);
                            ps.executeUpdate();
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    // ------------------------------------------------------------------ users

    public boolean register(String username, String email, String password) {
        if (username == null || username.trim().isEmpty() || username.trim().length() < 3) return false;
        if (password == null || password.length() < 4) return false;
        if (userExists(username)) return false;

        String salt = generateSalt();
        String hash = hashPassword(password, salt);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (username, id, email, salt, password_hash, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, username.trim());
            ps.setString(2, UUID.randomUUID().toString());
            ps.setString(3, email != null ? email.trim() : "");
            ps.setString(4, salt);
            ps.setString(5, hash);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
            // Auto-login on successful registration.
            login(username.trim(), password);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean login(String username, String password) {
        if (username == null || password == null) return false;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT salt, password_hash FROM users WHERE username = ? COLLATE NOCASE")) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                String salt = rs.getString("salt");
                String expected = rs.getString("password_hash");
                if (!hashPassword(password, salt).equals(expected)) return false;
            }
        } catch (SQLException e) {
            return false;
        }
        // Persist the session.
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO sessions (id, username, token, created_at) VALUES (1, ?, ?, ?)")) {
            ps.setString(1, username.trim());
            ps.setString(2, UUID.randomUUID().toString());
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
        return true;
    }

    public void logout() {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM sessions WHERE id = 1")) {
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public boolean isLoggedIn() {
        try (PreparedStatement ps = conn.prepareStatement("SELECT username FROM sessions WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    public String currentUsername() {
        try (PreparedStatement ps = conn.prepareStatement("SELECT username FROM sessions WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            return null;
        }
    }

    public boolean userExists(String username) {
        if (username == null) return false;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM users WHERE username = ? COLLATE NOCASE")) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /** Returns all usernames - useful for "switch account" pickers. */
    public List<String> allUsernames() {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT username FROM users ORDER BY created_at DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(rs.getString(1));
        } catch (SQLException ignored) {
        }
        return out;
    }

    // -------------------------------------------------------------- recents

    /** Insert or refresh a recent project entry. */
    public void recordRecent(String path, String branch) {
        if (path == null) return;
        String abs = new File(path).toPath().toAbsolutePath().normalize().toString();
        String name = new File(abs).getName();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO recents (path, name, last_opened, open_count, branch) " +
                "VALUES (?, ?, ?, 1, ?) " +
                "ON CONFLICT(path) DO UPDATE SET " +
                "  name = excluded.name, " +
                "  last_opened = excluded.last_opened, " +
                "  open_count = open_count + 1, " +
                "  branch = excluded.branch")) {
            ps.setString(1, abs);
            ps.setString(2, name);
            ps.setLong(3, System.currentTimeMillis());
            ps.setString(4, branch);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
        trimRecents();
    }

    /** Pull the recents list, freshest first. */
    public List<Recent> listRecents() {
        List<Recent> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT path, name, last_opened, open_count, branch FROM recents " +
                "ORDER BY last_opened DESC LIMIT ?")) {
            ps.setInt(1, MAX_RECENTS);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Recent r = new Recent();
                    r.path = rs.getString("path");
                    r.name = rs.getString("name");
                    r.lastOpened = rs.getLong("last_opened");
                    r.openCount = rs.getInt("open_count");
                    r.branch = rs.getString("branch");
                    out.add(r);
                }
            }
        } catch (SQLException ignored) {
        }
        return out;
    }

    public void removeRecent(String path) {
        if (path == null) return;
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM recents WHERE path = ?")) {
            ps.setString(1, new File(path).toPath().toAbsolutePath().normalize().toString());
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public void clearRecents() {
        try (Statement s = conn.createStatement()) {
            s.executeUpdate("DELETE FROM recents");
        } catch (SQLException ignored) {
        }
    }

    private void trimRecents() {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM recents WHERE path NOT IN (" +
                "  SELECT path FROM recents ORDER BY last_opened DESC LIMIT ?)")) {
            ps.setInt(1, MAX_RECENTS);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    // ---------------------------------------------------------------- helpers

    private static String generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    private static String hashPassword(String password, String salt) {
        if (password == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Base64.getDecoder().decode(salt));
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            for (int i = 0; i < 1000; i++) {
                digest.reset();
                hash = digest.digest(hash);
            }
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return "";
        }
    }

    // ----------------------------------------------------------------- types

    public static class Recent {
        public String path;
        public String name;
        public long lastOpened;
        public int openCount;
        public String branch;
    }

    // Used only during the one-shot migration from the old JSON files.
    private static class LegacyUser {
        String id;
        String username;
        String email;
        String salt;
        String passwordHash;
        long createdAt;
    }
    private static class LegacySession {
        String username;
        String token;
    }
    private static class LegacyRecent {
        String path;
        String name;
        long lastOpened;
        int openCount;
        String branch;
    }
}
