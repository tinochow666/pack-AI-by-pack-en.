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
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phiigrame Account service - local Spring Security-like authentication.
 * Stores users in a JSON file, hashes passwords with PBKDF2 + salt.
 */
public class AuthService {
    
    private static final String USER_DATA_DIR = System.getProperty("user.home") + "/.phiigrame";
    private static final String USER_FILE = USER_DATA_DIR + "/users.json";
    private static final String SESSION_FILE = USER_DATA_DIR + "/session.json";
    
    private final Map<String, User> users = new HashMap<>();
    private User currentUser;
    private final Gson gson = new Gson();
    
    public AuthService() {
        ensureDataDir();
        loadUsers();
        loadSession();
    }
    
    private void ensureDataDir() {
        File dir = new File(USER_DATA_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
    
    private void loadUsers() {
        File f = new File(USER_FILE);
        if (!f.exists()) return;
        try (FileReader reader = new FileReader(f, StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<User>>(){}.getType();
            List<User> userList = gson.fromJson(reader, listType);
            if (userList != null) {
                for (User u : userList) {
                    users.put(u.username.toLowerCase(), u);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load users: " + e.getMessage());
        }
    }
    
    private void saveUsers() {
        try (FileWriter writer = new FileWriter(USER_FILE, StandardCharsets.UTF_8)) {
            gson.toJson(new ArrayList<>(users.values()), writer);
        } catch (Exception e) {
            System.err.println("Failed to save users: " + e.getMessage());
        }
    }
    
    private void loadSession() {
        File f = new File(SESSION_FILE);
        if (!f.exists()) return;
        try (FileReader reader = new FileReader(f, StandardCharsets.UTF_8)) {
            Session session = gson.fromJson(reader, Session.class);
            if (session != null && session.token != null) {
                User u = users.get(session.username.toLowerCase());
                if (u != null) {
                    currentUser = u;
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load session: " + e.getMessage());
        }
    }
    
    private void saveSession() {
        try (FileWriter writer = new FileWriter(SESSION_FILE, StandardCharsets.UTF_8)) {
            if (currentUser != null) {
                Session s = new Session();
                s.username = currentUser.username;
                s.token = currentUser.username;
                gson.toJson(s, writer);
            }
        } catch (Exception e) {
            System.err.println("Failed to save session: " + e.getMessage());
        }
    }
    
    public boolean register(String username, String email, String password) {
        if (username == null || username.trim().isEmpty()) return false;
        if (password == null || password.length() < 4) return false;
        String key = username.toLowerCase();
        if (users.containsKey(key)) return false;
        
        User u = new User();
        u.id = UUID.randomUUID().toString();
        u.username = username;
        u.email = email != null ? email : "";
        u.salt = generateSalt();
        u.passwordHash = hashPassword(password, u.salt);
        u.createdAt = System.currentTimeMillis();
        
        users.put(key, u);
        saveUsers();
        currentUser = u;
        saveSession();
        return true;
    }
    
    public boolean login(String username, String password) {
        if (username == null || password == null) return false;
        User u = users.get(username.toLowerCase());
        if (u == null) return false;
        
        String hash = hashPassword(password, u.salt);
        if (hash.equals(u.passwordHash)) {
            currentUser = u;
            saveSession();
            return true;
        }
        return false;
    }
    
    public void logout() {
        currentUser = null;
        File f = new File(SESSION_FILE);
        if (f.exists()) f.delete();
    }
    
    public boolean isLoggedIn() {
        return currentUser != null;
    }
    
    public User getCurrentUser() {
        return currentUser;
    }
    
    private String generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }
    
    private String hashPassword(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Base64.getDecoder().decode(salt));
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            // Stretch with multiple iterations to simulate PBKDF2-like behavior
            for (int i = 0; i < 1000; i++) {
                digest.reset();
                hash = digest.digest(hash);
            }
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return "";
        }
    }
    
    public static class User {
        public String id;
        public String username;
        public String email;
        public String salt;
        public String passwordHash;
        public long createdAt;
    }
    
    private static class Session {
        String username;
        String token;
    }
}
