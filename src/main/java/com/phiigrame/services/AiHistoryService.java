package com.phiigrame.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI conversation history persistence service.
 * Stores all AI chats in a JSON file under the user's Phiigrame data dir.
 */
public class AiHistoryService {
    
    private static final String DATA_DIR = System.getProperty("user.home") + "/.phiigrame";
    private static final String HISTORY_FILE = DATA_DIR + "/ai_history.json";
    private static final int MAX_SESSIONS = 200;
    
    private final List<AiSession> sessions = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ai-history");
        t.setDaemon(true);
        return t;
    });
    private final Gson gson = new Gson();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    
    public AiHistoryService() {
        File dir = new File(DATA_DIR);
        if (!dir.exists()) dir.mkdirs();
        load();
    }
    
    private void load() {
        File f = new File(HISTORY_FILE);
        if (!f.exists()) return;
        try (FileReader reader = new FileReader(f, StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<AiSession>>(){}.getType();
            List<AiSession> loaded = gson.fromJson(reader, listType);
            if (loaded != null) {
                sessions.addAll(loaded);
                sessions.sort((a, b) -> Long.compare(b.lastModified, a.lastModified));
            }
        } catch (Exception e) {
            System.err.println("Failed to load AI history: " + e.getMessage());
        }
    }
    
    private void save() {
        executor.submit(() -> {
            try (FileWriter writer = new FileWriter(HISTORY_FILE, StandardCharsets.UTF_8)) {
                List<AiSession> toSave = new ArrayList<>(sessions);
                if (toSave.size() > MAX_SESSIONS) {
                    toSave = toSave.subList(0, MAX_SESSIONS);
                }
                gson.toJson(toSave, writer);
            } catch (Exception e) {
                System.err.println("Failed to save AI history: " + e.getMessage());
            }
        });
    }
    
    public AiSession createSession(String title) {
        AiSession s = new AiSession();
        s.id = UUID.randomUUID().toString();
        s.title = title != null ? title : "New Chat";
        s.createdAt = System.currentTimeMillis();
        s.lastModified = s.createdAt;
        s.messages = new ArrayList<>();
        sessions.add(0, s);
        save();
        return s;
    }
    
    public void addMessage(String sessionId, String role, String content) {
        AiSession s = findById(sessionId);
        if (s == null) return;
        Message m = new Message();
        m.role = role;
        m.content = content;
        m.timestamp = System.currentTimeMillis();
        s.messages.add(m);
        s.lastModified = m.timestamp;
        if (s.title == null || s.title.equals("New Chat")) {
            String trimmed = content.trim();
            if (trimmed.length() > 40) {
                s.title = trimmed.substring(0, 40) + "...";
            } else {
                s.title = trimmed;
            }
        }
        // Move to top
        sessions.remove(s);
        sessions.add(0, s);
        save();
    }
    
    public void deleteSession(String sessionId) {
        sessions.removeIf(s -> s.id.equals(sessionId));
        save();
    }
    
    public void clearAll() {
        sessions.clear();
        save();
    }
    
    public List<AiSession> getAllSessions() {
        return new ArrayList<>(sessions);
    }
    
    public AiSession findById(String id) {
        for (AiSession s : sessions) {
            if (s.id.equals(id)) return s;
        }
        return null;
    }
    
    public String formatTimestamp(long ts) {
        return dateFormat.format(new Date(ts));
    }
    
    public static class AiSession {
        public String id;
        public String title;
        public long createdAt;
        public long lastModified;
        public List<Message> messages;
    }
    
    public static class Message {
        public String role;
        public String content;
        public long timestamp;
    }
}
