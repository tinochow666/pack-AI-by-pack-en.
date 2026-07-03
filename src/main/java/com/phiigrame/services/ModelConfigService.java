package com.phiigrame.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persistent registry of AI model presets the user can chat with.
 *
 * <p>Two kinds of presets are supported:
 * <ul>
 *   <li><b>local</b> - a model served by the in-process llama.cpp
 *       runtime.  Has a {@code repo} (Hugging Face repo) and
 *       {@code file} (single-file GGUF inside that repo) and is run
 *       completely on the user's machine.</li>
 *   <li><b>remote</b> - an OpenAI-compatible HTTP API (e.g. an
 *       OpenAI-compatible proxy, a self-hosted vLLM, OpenRouter, etc.).
 *       Has a {@code baseUrl} and {@code apiKey}.</li>
 * </ul>
 *
 * <p>The active model id is stored in the same JSON file so the IDE
 * remembers it across sessions.
 */
public class ModelConfigService {

    public enum Kind { LOCAL, REMOTE }

    public static final class Model {
        public String id;
        public Kind kind;
        public String name;
        public String description;

        // local-only
        public String repo;
        public String file;
        public String mirror;

        // remote-only
        public String baseUrl;
        public String apiKey;
        public String remoteModel;

        public Model() {}

        public Model(String id, Kind kind, String name, String description) {
            this.id = id;
            this.kind = kind;
            this.name = name;
            this.description = description;
        }
    }

    private static final Path CONFIG_PATH = Paths.get(
            System.getProperty("user.home"), ".phiigrame", "models.json");

    private final List<Model> models = new ArrayList<>();
    private String activeId;
    private final Gson gson = new Gson();

    public ModelConfigService() {
        load();
        if (models.isEmpty()) installDefaults();
    }

    // ---- defaults ----------------------------------------------------------

    private void installDefaults() {
        // Default local model: the one we already support.
        Model qwen = new Model("qwen2.5-coder-1.5b", Kind.LOCAL,
                "Qwen2.5-Coder-1.5B-Instruct (local)",
                "Small coding model from Alibaba; runs offline on your machine via llama.cpp.");
        qwen.repo = "Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF";
        qwen.file = "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf";
        qwen.mirror = "";
        models.add(qwen);

        // DeepSeek coder preset.
        Model deepseek = new Model("deepseek-coder-6.7b", Kind.LOCAL,
                "DeepSeek-Coder-6.7B-Instruct (local)",
                "DeepSeek's 6.7B coder. Stronger reasoning than Qwen 1.5B, larger download (~4 GB Q4).");
        deepseek.repo = "TheBloke/deepseek-coder-6.7B-instruct-GGUF";
        deepseek.file = "deepseek-coder-6.7b-instruct.Q4_K_M.gguf";
        deepseek.mirror = "";
        models.add(deepseek);

        // Custom API placeholder - empty fields, the user fills them in.
        Model custom = new Model("custom-openai", Kind.REMOTE,
                "Custom OpenAI-compatible API",
                "Point Phiigrame at any OpenAI-compatible endpoint (OpenAI, Azure, OpenRouter, vLLM, etc.).");
        custom.baseUrl = "https://api.openai.com/v1";
        custom.apiKey = "";
        custom.remoteModel = "gpt-4o-mini";
        models.add(custom);

        activeId = qwen.id;
        save();
    }

    // ---- queries -----------------------------------------------------------

    public List<Model> all() { return new ArrayList<>(models); }

    public Model get(String id) {
        if (id == null) return null;
        for (Model m : models) if (m.id.equals(id)) return m;
        return null;
    }

    public Model active() { return get(activeId); }

    public String activeId() { return activeId; }

    public void setActive(String id) {
        if (get(id) != null) {
            activeId = id;
            save();
        }
    }

    public void upsert(Model m) {
        if (m.id == null || m.id.isEmpty()) m.id = "model-" + UUID.randomUUID();
        for (int i = 0; i < models.size(); i++) {
            if (models.get(i).id.equals(m.id)) { models.set(i, m); save(); return; }
        }
        models.add(m);
        save();
    }

    public void delete(String id) {
        models.removeIf(m -> m.id.equals(id));
        if (id.equals(activeId)) {
            activeId = models.isEmpty() ? null : models.get(0).id;
        }
        save();
    }

    // ---- persistence -------------------------------------------------------

    private void load() {
        if (!Files.exists(CONFIG_PATH)) return;
        try (Reader r = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(r);
            if (!el.isJsonObject()) return;
            JsonObject obj = el.getAsJsonObject();
            activeId = obj.has("activeId") ? obj.get("activeId").getAsString() : null;
            if (obj.has("models") && obj.get("models").isJsonArray()) {
                JsonArray arr = obj.get("models").getAsJsonArray();
                models.clear();
                for (JsonElement e : arr) models.add(gson.fromJson(e, Model.class));
            }
        } catch (IOException e) {
            System.err.println("Failed to load model config: " + e.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("activeId", activeId);
            JsonArray arr = new JsonArray();
            for (Model m : models) arr.add(gson.toJsonTree(m));
            obj.add("models", arr);
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                gson.toJson(obj, w);
            }
        } catch (IOException e) {
            System.err.println("Failed to save model config: " + e.getMessage());
        }
    }
}
