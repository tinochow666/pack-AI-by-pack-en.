package com.phiigrame.services;

import de.kherud.llama.InferenceParameters;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.ModelParameters;
import de.kherud.llama.args.Sampler;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI service backed by llama.cpp via the {@code de.kherud:llama} JNI bindings.
 *
 * <p><b>No Python, no Ollama, no external service</b> - everything runs in-process.
 * The model file (Qwen2.5-Coder-1.5B-Instruct in Q4_K_M GGUF) is downloaded
 * from Hugging Face the first time AI is used, then cached locally at
 * {@code ~/.cache/phiigrame/models/}.
 *
 * <p>Default model: https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF
 */
public class AiService {

    /** HF repo holding the GGUF file. */
    private static final String DEFAULT_MODEL_REPO =
            "Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF";
    /** Quantised GGUF file to fetch (~1.1 GB, fits comfortably in RAM). */
    private static final String DEFAULT_GGUF_FILE =
            "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf";
    private static final String HF_BASE = "https://huggingface.co";
    /**
     * Default mirror that is fast in mainland China.  Override with
     * {@code -Dphiigrame.hf.mirror=https://hf-mirror.com} or set
     * {@link #mirrorBase} from the AI Settings dialog.
     */
    private static final String DEFAULT_MIRROR_BASE = "https://hf-mirror.com";

    private final Path modelCacheDir;
    private final ExecutorService executor;
    private final AtomicInteger loadGen = new AtomicInteger();
    private String modelRepo = DEFAULT_MODEL_REPO;
    private String ggufFile = DEFAULT_GGUF_FILE;
    /**
     * Mirror base URL. If non-null, downloads are fetched from
     * {@code <mirror>/<repo>/resolve/main/<filename>} instead of
     * huggingface.co.  Configurable via the AI Settings dialog or
     * the system property {@code phiigrame.hf.mirror}.
     */
    private String mirrorBase = resolveDefaultMirror();
    private int contextSize = 4096;
    private int gpuLayers = 0;
    private int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

    // Remote (OpenAI-compatible) mode.  When all three of these are
    // set and the active model is a remote preset, generate() routes
    // requests through the HTTP client instead of the local model.
    private volatile String remoteBaseUrl;
    private volatile String remoteApiKey;
    private volatile String remoteModelName;

    /** Loaded model - {@code null} until {@link #checkAvailability(Consumer)} succeeds. */
    private volatile LlamaModel model;
    private volatile boolean loading;
    private volatile boolean available;
    private volatile String lastError = "";

    /** Listener for model-load progress (0-100). Optional, used by UI. */
    private volatile Consumer<Integer> downloadProgressListener;

    public AiService() {
        this.modelCacheDir = Paths.get(
                System.getProperty("user.home"),
                ".cache", "phiigrame", "models");
        ThreadFactory tf = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "phiigrame-ai");
                t.setDaemon(true);
                return true ? t : null;
            }
        };
        this.executor = Executors.newSingleThreadExecutor(tf);
    }

    // ---- configuration -------------------------------------------------------

    public void setModelRepo(String repo) {
        this.modelRepo = repo;
        unloadModel();
    }

    public void setGgufFile(String file) {
        this.ggufFile = file;
        unloadModel();
    }

    public void setMirrorBase(String base) {
        this.mirrorBase = (base == null || base.isBlank()) ? null : base.trim();
    }

    public String getMirrorBase() { return mirrorBase; }

    private static String resolveDefaultMirror() {
        // Honour the explicit system property first
        String fromProp = System.getProperty("phiigrame.hf.mirror");
        if (fromProp != null && !fromProp.isBlank()) {
            return fromProp.trim();
        }
        // The user can also force the official HF endpoint by setting it to "off" or empty
        String disable = System.getProperty("phiigrame.hf.official");
        if (disable != null && !disable.isBlank()) {
            return null;
        }
        // Default to the China-friendly mirror
        return DEFAULT_MIRROR_BASE;
    }

    public void setContextSize(int n) {
        this.contextSize = Math.max(512, n);
    }
    public void setGpuLayers(int n) { this.gpuLayers = Math.max(0, n); }

    /**
     * Switch to a local preset, replacing the current local model
     * configuration.  Has no effect on already-loaded state; the
     * model will be reloaded the next time it is needed.
     */
    public void setModelConfig(String repo, String ggufFile, String mirror) {
        this.modelRepo = (repo == null || repo.isBlank()) ? DEFAULT_MODEL_REPO : repo;
        this.ggufFile = (ggufFile == null || ggufFile.isBlank()) ? DEFAULT_GGUF_FILE : ggufFile;
        this.mirrorBase = mirror;
        // Drop the remote-mode fields so subsequent calls go back to local.
        this.remoteBaseUrl = null;
        this.remoteApiKey = null;
        this.remoteModelName = null;
    }

    /**
     * Switch to a remote OpenAI-compatible preset.  While the preset is
     * active the service will not try to load a local model file.
     */
    public void setRemoteModel(String baseUrl, String apiKey, String modelName) {
        this.remoteBaseUrl = baseUrl;
        this.remoteApiKey = apiKey;
        this.remoteModelName = modelName;
    }

    public boolean isRemote() {
        return remoteBaseUrl != null && !remoteBaseUrl.isBlank()
                && remoteModelName != null && !remoteModelName.isBlank();
    }

    public String getModelRepo() { return modelRepo; }
    public String getGgufFile() { return ggufFile; }
    public int getContextSize() { return contextSize; }
    public int getGpuLayers() { return gpuLayers; }
    public Path getModelCacheDir() { return modelCacheDir; }
    public Path getModelPath() { return modelCacheDir.resolve(ggufFile); }
    public String getModelName() { return ggufFile; }

    public boolean isModelFilePresent() {
        try {
            return Files.isRegularFile(getModelPath())
                    && Files.size(getModelPath()) > 100_000_000L;
        } catch (Exception e) {
            return false;
        }
    }

    public void setDownloadProgressListener(Consumer<Integer> l) {
        this.downloadProgressListener = l;
    }

    public boolean isAvailable() { return available; }
    public boolean isLoading() { return loading; }
    public String getLastError() { return lastError; }

    // ---- lifecycle -----------------------------------------------------------

    /**
     * Check (or start) the AI backend. Asynchronous because the first
     * invocation will download the GGUF model (~1GB) and load it into memory.
     */
    public void checkAvailability(Consumer<Boolean> callback) {
        executor.submit(() -> {
            if (loading) {
                if (callback != null) deliverBool(callback, available);
                return;
            }
            if (model != null && available) {
                if (callback != null) deliverBool(callback, true);
                return;
            }
            loading = true;
            lastError = "";
            try {
                Files.createDirectories(modelCacheDir);
                Path modelPath = modelCacheDir.resolve(ggufFile);
                if (!Files.isRegularFile(modelPath) || Files.size(modelPath) < 100_000_000L) {
                    reportProgress(0);
                    downloadWithFallback(modelRepo, ggufFile, modelPath, this::reportProgress);
                }
                if (model != null) {
                    try { model.close(); } catch (Exception ignored) {}
                    model = null;
                }
                ModelParameters params = new ModelParameters()
                        .setModelFilePath(modelPath.toString())
                        .setNCtx(contextSize)
                        .setNGpuLayers(gpuLayers)
                        .setNThreads(threads);
                model = new LlamaModel(params);
                available = true;
                loading = false;
                if (callback != null) deliverBool(callback, true);
            } catch (Throwable t) {
                lastError = (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
                available = false;
                loading = false;
                if (callback != null) deliverBool(callback, false);
            }
        });
    }

    public void shutdown() {
        unloadModel();
    }

    private void unloadModel() {
        LlamaModel m = this.model;
        this.model = null;
        this.available = false;
        if (m != null) {
            try { m.close(); } catch (Exception ignored) {}
        }
    }

    private void reportProgress(int pct) {
        Consumer<Integer> l = downloadProgressListener;
        if (l != null) {
            if (javafx.application.Platform.isFxApplicationThread()) {
                l.accept(pct);
            } else {
                javafx.application.Platform.runLater(() -> l.accept(pct));
            }
        }
    }

    private void deliverBool(Consumer<Boolean> c, boolean value) {
        if (javafx.application.Platform.isFxApplicationThread()) {
            c.accept(value);
        } else {
            javafx.application.Platform.runLater(() -> c.accept(value));
        }
    }

    // ---- high level API ------------------------------------------------------

    public void completeCode(String prefix, String suffix, String language,
                             String fileName, Consumer<String> onResult) {
        ensureModelThen(() -> {
            try {
                String prompt = buildCompletionPrompt(prefix, suffix, language, fileName);
                String result = generate(prompt, 256, 0.2f, false);
                String cleaned = cleanCompletion(result, prefix, suffix);
                deliver(onResult, cleaned);
            } catch (Throwable t) {
                lastError = t.getMessage();
                deliver(onResult, "");
            }
        });
    }

    public void chat(String userMessage, List<Map<String, String>> history,
                     Consumer<String> onResult, Runnable onComplete) {
        ensureModelThen(() -> {
            try {
                String prompt = buildChatPrompt(userMessage, history);
                String result = generate(prompt, 1024, 0.7f, true);
                deliver(onResult, result);
                if (onComplete != null) {
                    if (javafx.application.Platform.isFxApplicationThread()) onComplete.run();
                    else javafx.application.Platform.runLater(onComplete);
                }
            } catch (Throwable t) {
                lastError = t.getMessage();
                deliver(onResult, "Error: " + lastError);
                if (onComplete != null) {
                    if (javafx.application.Platform.isFxApplicationThread()) onComplete.run();
                    else javafx.application.Platform.runLater(onComplete);
                }
            }
        });
    }

    /**
     * Streaming chat.  Tokens are pushed one at a time to {@code onToken}
     * on the JavaFX thread; the final, full text is delivered once to
     * {@code onResult} when generation finishes.  {@code onComplete} is
     * invoked after that.  This is the method the UI uses to render
     * tokens live into the chat bubble.
     */
    public void chatStream(String userMessage, List<Map<String, String>> history,
                           Consumer<String> onToken, Consumer<String> onResult,
                           Runnable onComplete) {
        ensureModelThen(() -> {
            String prompt = buildChatPrompt(userMessage, history);
            executor.submit(() -> {
                try {
                    if (isRemote()) {
                        // For the HTTP path we still buffer the response -
                        // most OpenAI-compatible endpoints don't support
                        // streaming, and even when they do the UX is
                        // good enough with one update.
                        String text = generateRemote(prompt, 1024, 0.7f);
                        if (onToken != null && text != null && !text.isEmpty()) {
                            String finalText = text;
                            javafx.application.Platform.runLater(() -> onToken.accept(finalText));
                        }
                        deliverResult(onResult, text);
                        runCallback(onComplete);
                        return;
                    }
                    LlamaModel m = model;
                    if (m == null) throw new IllegalStateException("Model not loaded");
                    InferenceParameters inf = new InferenceParameters(prompt)
                            .setNPredict(1024)
                            .setTemperature(0.7f)
                            .setTopP(0.95f)
                            .setTopK(40)
                            .setSamplers(Sampler.TOP_K, Sampler.TOP_P, Sampler.TEMPERATURE);
                    StringBuilder out = new StringBuilder();
                    for (LlamaModel.Output o : m.generate(inf)) {
                        if (o == null || o.text == null) continue;
                        out.append(o.text);
                        if (onToken != null) {
                            String token = o.text;
                            javafx.application.Platform.runLater(() -> onToken.accept(token));
                        }
                        String s = out.toString();
                        if (s.contains("<|im_end|>") || s.contains("<|endoftext|>")) {
                            break;
                        }
                    }
                    String cleaned = out.toString()
                            .replace("<|im_end|>", "")
                            .replace("<|endoftext|>", "")
                            .replace("<|im_start|>", "")
                            .trim();
                    deliverResult(onResult, cleaned);
                    runCallback(onComplete);
                } catch (Throwable t) {
                    lastError = t.getMessage();
                    String err = "Error: " + lastError;
                    if (onToken != null) {
                        javafx.application.Platform.runLater(() -> onToken.accept(err));
                    }
                    deliverResult(onResult, err);
                    runCallback(onComplete);
                }
            });
        });
    }

    private void deliverResult(Consumer<String> c, String value) {
        if (c == null) return;
        if (javafx.application.Platform.isFxApplicationThread()) {
            c.accept(value);
        } else {
            javafx.application.Platform.runLater(() -> c.accept(value));
        }
    }

    private void runCallback(Runnable r) {
        if (r == null) return;
        if (javafx.application.Platform.isFxApplicationThread()) r.run();
        else javafx.application.Platform.runLater(r);
    }

    /**
     * Chat variant that knows about a {@link com.phiigrame.ai.ToolRegistry}.
     *
     * <p>The flow is:
     * <ol>
     *   <li>Build a prompt that lists the tools and asks the model to use them.</li>
     *   <li>Run the model, parse out any {@code ```tool ... ```} blocks.</li>
     *   <li>For each call, ask the {@link com.phiigrame.ai.ToolCallParser.ToolCallback}
     *       for permission, then execute and append a tool-result turn to the
     *       conversation.</li>
     *   <li>Run the model again so it can use the results; repeat up to
     *       {@code maxToolRounds} times.</li>
     *   <li>Return the final assistant text in {@code onResult}.</li>
     * </ol>
     */
    public void chatWithTools(String userMessage,
                              List<Map<String, String>> history,
                              com.phiigrame.ai.ToolRegistry registry,
                              com.phiigrame.ai.ToolCallParser.ToolCallback callback,
                              int maxToolRounds,
                              Consumer<String> onResult,
                              Runnable onComplete) {
        ensureModelThen(() -> {
            String currentHistory = "";
            if (history != null) {
                int start = Math.max(0, history.size() - 10);
                StringBuilder hist = new StringBuilder();
                for (int i = start; i < history.size(); i++) {
                    Map<String, String> msg = history.get(i);
                    String role = msg.getOrDefault("role", "user");
                    String content = msg.getOrDefault("content", "");
                    hist.append("<|im_start|>").append(role).append("\n")
                        .append(content).append("\n<|im_end|>\n");
                }
                currentHistory = hist.toString();
            }

            String result = "";
            try {
                StringBuilder conversation = new StringBuilder(currentHistory);
                conversation.append("<|im_start|>user\n").append(userMessage).append("\n<|im_end|>\n");

                for (int round = 0; round < Math.max(1, maxToolRounds); round++) {
                    String prompt = buildToolChatPrompt(
                            conversation.toString(), registry.describeForPrompt());
                    String turn = generate(prompt, 1024, 0.6f, true);

                    var calls = com.phiigrame.ai.ToolCallParser.extract(turn);
                    if (calls.isEmpty()) {
                        // Final answer - no tool calls left.
                        result = turn;
                        break;
                    }

                    // Append the assistant turn (with the tool calls) verbatim.
                    conversation.append("<|im_start|>assistant\n")
                                .append(turn).append("\n<|im_end|>\n");

                    // Execute every tool call, then append a tool message with the results.
                    var results = com.phiigrame.ai.ToolCallParser.executeAll(turn, registry, callback);
                    if (results.isEmpty()) { result = turn; break; }
                    StringBuilder toolOut = new StringBuilder();
                    toolOut.append("Tool results (in order):\n");
                    for (var r : results) {
                        toolOut.append("- ").append(r.call.name)
                               .append("(").append(r.call.args).append(")\n")
                               .append(r.result).append("\n");
                    }
                    conversation.append("<|im_start|>user\n")
                                .append(toolOut).append("\n<|im_end|>\n");

                    if (round == maxToolRounds - 1) {
                        // Out of rounds - return the last assistant turn as the answer.
                        result = turn;
                    }
                }
                deliver(onResult, result);
            } catch (Throwable t) {
                lastError = t.getMessage();
                deliver(onResult, "Error: " + lastError);
            }
            if (onComplete != null) {
                if (javafx.application.Platform.isFxApplicationThread()) onComplete.run();
                else javafx.application.Platform.runLater(onComplete);
            }
        });
    }

    private String buildToolChatPrompt(String conversationSoFar, String toolBlock) {
        StringBuilder sb = new StringBuilder();
        sb.append("<|im_start|>system\n");
        sb.append("You are Phiigrame AI - a local coding assistant with tools.\n");
        sb.append("To call a tool, emit one fenced JSON block:\n");
        sb.append("```tool\n");
        sb.append("{\"name\": \"<tool_name>\", \"args\": { ... }}\n");
        sb.append("```\n");
        sb.append("Wait for the tool result, then continue. When done, answer without a tool block.\n\n");
        sb.append(toolBlock);
        sb.append("<|im_end|>\n");
        sb.append(conversationSoFar);
        sb.append("<|im_start|>assistant\n");
        return sb.toString();
    }

    public void explainCode(String code, String language, Consumer<String> onResult) {
        ensureModelThen(() -> {
            try {
                String prompt = "Explain the following " + (language == null ? "" : language)
                        + " code in plain English. Be concise.\n```\n"
                        + code + "\n```\nExplanation:";
                String result = generate(prompt, 512, 0.3f, true);
                deliver(onResult, result);
            } catch (Throwable t) {
                lastError = t.getMessage();
                deliver(onResult, "Error: " + lastError);
            }
        });
    }

    public void refactorCode(String code, String language, Consumer<String> onResult) {
        ensureModelThen(() -> {
            try {
                String prompt = "Refactor the following " + (language == null ? "" : language)
                        + " code to be cleaner and more idiomatic. Reply with ONLY the refactored code in a fenced block.\n```\n"
                        + code + "\n```\nRefactored:";
                String result = generate(prompt, 1024, 0.2f, true);
                deliver(onResult, extractCode(result));
            } catch (Throwable t) {
                lastError = t.getMessage();
                deliver(onResult, "Error: " + lastError);
            }
        });
    }

    // ---- core generation -----------------------------------------------------

    private String generate(String prompt, int maxTokens, float temperature, boolean stopOnEos) {
        if (isRemote()) {
            return generateRemote(prompt, maxTokens, temperature);
        }
        LlamaModel m = model;
        if (m == null) throw new IllegalStateException("Model not loaded");
        InferenceParameters inf = new InferenceParameters(prompt)
                .setNPredict(maxTokens)
                .setTemperature(temperature)
                .setTopP(0.95f)
                .setTopK(40)
                .setSamplers(Sampler.TOP_K, Sampler.TOP_P, Sampler.TEMPERATURE);
        StringBuilder out = new StringBuilder();
        for (LlamaModel.Output o : m.generate(inf)) {
            if (o == null || o.text == null) continue;
            out.append(o.text);
            if (stopOnEos) {
                String s = out.toString();
                if (s.contains("<|im_end|>") || s.contains("<|endoftext|>")) {
                    break;
                }
            }
        }
        String result = out.toString()
                .replace("<|im_end|>", "")
                .replace("<|endoftext|>", "")
                .replace("<|im_start|>", "");
        return result.trim();
    }

    private String generateRemote(String prompt, int maxTokens, float temperature) {
        try {
            com.phiigrame.ai.OpenAICompatClient client =
                    new com.phiigrame.ai.OpenAICompatClient();
            List<Map<String, String>> msgs = new ArrayList<>();
            // Many remote endpoints reject a prompt that looks like a
            // raw Qwen template.  Re-parse the prompt: every "<|im_start|>role\n...
            // <|im_end|>\n" segment becomes a proper chat message.
            int cursor = 0;
            while (cursor < prompt.length()) {
                int start = prompt.indexOf("<|im_start|>", cursor);
                if (start < 0) break;
                int eol = prompt.indexOf("\n", start);
                if (eol < 0) break;
                String role = prompt.substring(start + "<|im_start|>".length(), eol).trim();
                int end = prompt.indexOf("<|im_end|>", eol);
                if (end < 0) break;
                String content = prompt.substring(eol + 1, end);
                cursor = end + "<|im_end|>".length();
                // Drop the synthetic system message: the chat client
                // is told its role via the "system" field, but the
                // Qwen template already encodes the system line as
                // "system" - we keep that as a normal system message.
                if (role.isEmpty() || role.equals("system")
                        || role.equals("user") || role.equals("assistant")) {
                    msgs.add(java.util.Map.of(
                            "role", role.isEmpty() ? "user" : role,
                            "content", content.trim()));
                }
            }
            if (msgs.isEmpty()) {
                msgs.add(java.util.Map.of("role", "user", "content", prompt));
            }
            String text = client.chat(remoteBaseUrl, remoteApiKey, remoteModelName,
                    msgs, maxTokens, temperature);
            if (text == null) return "";
            // Some servers add a leftover "<|im_start|>assistant" - strip
            // it so it doesn't show up in the chat bubble.
            text = text.replace("<|im_start|>assistant\n", "")
                    .replace("<|im_end|>", "")
                    .replace("<|im_start|>", "");
            return text.trim();
        } catch (Exception e) {
            lastError = "remote: " + e.getMessage();
            return "Error: " + lastError;
        }
    }

    private void ensureModelThen(Runnable action) {
        executor.submit(() -> {
            if (isRemote()) {
                // No local model to load - the request will go via HTTP.
                available = true;
                lastError = null;
                action.run();
                return;
            }
            if (model == null) {
                checkAvailability(ok -> {
                    if (ok) action.run();
                });
            } else {
                action.run();
            }
        });
    }

    private void deliver(Consumer<String> c, String value) {
        if (c == null) return;
        if (javafx.application.Platform.isFxApplicationThread()) {
            c.accept(value);
        } else {
            javafx.application.Platform.runLater(() -> c.accept(value));
        }
    }

    // ---- prompt builders -----------------------------------------------------

    private String buildCompletionPrompt(String prefix, String suffix, String language, String fileName) {
        // FIM-style completion for the Qwen2.5-Coder model.
        StringBuilder sb = new StringBuilder();
        sb.append("<|im_start|>system\n");
        sb.append("You are a code completion engine. Output ONLY the missing code that fits between the prefix and the suffix. ");
        sb.append("Do not repeat the prefix or suffix. Do not add explanations.\n<|im_end|>\n");
        sb.append("<|im_start|>user\nFile: ").append(fileName == null ? "" : fileName);
        if (language != null && !language.isEmpty()) sb.append(" (").append(language).append(")");
        sb.append("\n<|fim_prefix|>").append(prefix == null ? "" : prefix);
        sb.append("<|fim_suffix|>").append(suffix == null ? "" : suffix);
        sb.append("<|fim_middle|>\n<|im_end|>\n");
        sb.append("<|im_start|>assistant\n");
        return sb.toString();
    }

    private String buildChatPrompt(String userMessage, List<Map<String, String>> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("<|im_start|>system\n");
        sb.append("You are Phiigrame AI, a coding assistant that runs locally. ");
        sb.append("Be concise, accurate, and use code blocks where helpful.\n<|im_end|>\n");
        if (history != null) {
            int start = Math.max(0, history.size() - 10);
            for (int i = start; i < history.size(); i++) {
                Map<String, String> msg = history.get(i);
                String role = msg.getOrDefault("role", "user");
                String content = msg.getOrDefault("content", "");
                sb.append("<|im_start|>").append(role).append("\n")
                  .append(content).append("\n<|im_end|>\n");
            }
        }
        sb.append("<|im_start|>user\n").append(userMessage).append("\n<|im_end|>\n");
        sb.append("<|im_start|>assistant\n");
        return sb.toString();
    }

    private String cleanCompletion(String raw, String prefix, String suffix) {
        if (raw == null) return "";
        String s = raw;
        // Cut at any code fence - the model sometimes starts explaining
        int fence = s.indexOf("```");
        if (fence >= 0) s = s.substring(0, fence);
        // Cut at any new explanation line
        s = s.split("\nExplanation:", 2)[0];
        s = s.split("\nNote:", 2)[0];
        return s;
    }

    private String extractCode(String raw) {
        if (raw == null) return "";
        Pattern p = Pattern.compile("```[a-zA-Z]*\\n([\\s\\S]*?)```");
        Matcher m = p.matcher(raw);
        if (m.find()) return m.group(1).trim();
        return raw.trim();
    }

    // ---- download ------------------------------------------------------------

    /**
     * Try the configured mirror first, then fall back to huggingface.co.
     * Each attempt is retried once on a connection timeout / 5xx.
     */
    private void downloadWithFallback(String repo, String file, Path dest,
                                      Consumer<Integer> progress) throws IOException {
        String[] bases;
        if (mirrorBase != null && !mirrorBase.isBlank()) {
            bases = new String[]{mirrorBase, HF_BASE};
        } else {
            bases = new String[]{HF_BASE};
        }
        IOException last = null;
        for (String base : bases) {
            String url = base + "/" + repo + "/resolve/main/" + file;
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    if (attempt > 1 || !base.equals(bases[0])) {
                        // small back-off before the second attempt
                        try { Thread.sleep(1500L * attempt); } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new IOException("interrupted");
                        }
                    }
                    downloadFile(url, dest, progress);
                    return;
                } catch (IOException ioe) {
                    last = ioe;
                    // wipe the partial file so the next attempt starts fresh
                    try {
                        Path tmp = dest.resolveSibling(dest.getFileName() + ".part");
                        Files.deleteIfExists(tmp);
                    } catch (Exception ignored) {}
                    if (attempt == 2) break;
                }
            }
        }
        throw new IOException(
                "Failed to download model from " + bases.length + " endpoint(s).\n" +
                "Last error: " + (last == null ? "?" : last.getMessage()) + "\n\n" +
                "Try changing the mirror in AI Settings (or run with -Dphiigrame.hf.mirror=https://hf-mirror.com)");
    }

    private void downloadFile(String url, Path dest, Consumer<Integer> progress) throws IOException {
        Files.createDirectories(dest.getParent());
        Path tmp = dest.resolveSibling(dest.getFileName() + ".part");
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestProperty("User-Agent", "Phiigrame-IDE/1.0");
        // 30s to connect, 5 minutes of silence before we abort a stalled stream
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(300_000);
        conn.setInstanceFollowRedirects(true);
        conn.connect();
        int code = conn.getResponseCode();
        if (code != 200) {
            throw new IOException("HTTP " + code + " while downloading model from " + url);
        }
        long total = conn.getContentLengthLong();
        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             OutputStream out = new FileOutputStream(tmp.toFile())) {
            byte[] buf = new byte[128 * 1024];
            long done = 0;
            int n;
            int lastPct = -1;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                if (total > 0 && progress != null) {
                    int pct = (int) (done * 100 / total);
                    if (pct != lastPct && pct % 2 == 0) {
                        progress.accept(pct);
                        lastPct = pct;
                    }
                }
            }
        }
        Files.move(tmp, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
