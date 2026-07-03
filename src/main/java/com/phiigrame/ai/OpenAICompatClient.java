package com.phiigrame.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Minimal OpenAI-compatible {@code /v1/chat/completions} client.
 *
 * <p>This is used by the IDE for "remote" model presets (any OpenAI-shaped
 * API: OpenAI, Azure OpenAI, OpenRouter, vLLM, llama.cpp's
 * {@code --server}, etc.).  It intentionally implements only the fields we
 * actually need so the dependency surface stays small.
 */
public class OpenAICompatClient {

    private static final Gson GSON = new Gson();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public String chat(String baseUrl, String apiKey, String model,
                      List<Map<String, String>> messages,
                      int maxTokens, float temperature) throws Exception {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl is required");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model is required");
        }
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages is required");
        }
        String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (!url.endsWith("/chat/completions")) url = url + "/chat/completions";

        JsonObject payload = new JsonObject();
        payload.addProperty("model", model);
        payload.addProperty("max_tokens", maxTokens);
        payload.addProperty("temperature", temperature);
        payload.addProperty("stream", false);
        JsonArray arr = new JsonArray();
        for (Map<String, String> m : messages) {
            JsonObject o = new JsonObject();
            o.addProperty("role", m.getOrDefault("role", "user"));
            o.addProperty("content", m.getOrDefault("content", ""));
            arr.add(o);
        }
        payload.add("messages", arr);

        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        GSON.toJson(payload), StandardCharsets.UTF_8));
        if (apiKey != null && !apiKey.isBlank()) {
            b.header("Authorization", "Bearer " + apiKey);
        }
        HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException("HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 400));
        }
        return parseContent(resp.body());
    }

    private static String parseContent(String body) {
        JsonObject obj = GSON.fromJson(body, JsonObject.class);
        if (obj == null) return "";
        JsonArray choices = obj.has("choices") ? obj.getAsJsonArray("choices") : null;
        if (choices == null || choices.isEmpty()) return "";
        JsonObject first = choices.get(0).getAsJsonObject();
        JsonObject message = first.has("message") ? first.getAsJsonObject("message") : null;
        if (message == null) return "";
        JsonElement content = message.get("content");
        return content == null || content.isJsonNull() ? "" : content.getAsString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
