package com.phiigrame.ai;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single callable function that the AI can invoke during a chat session.
 *
 * <p>Tools are described in JSON-style metadata and executed by the IDE in
 * the JavaFX application thread (so they can show confirmation dialogs and
 * touch the workspace safely).  Each tool has a stable {@link #name()} which
 * is what the model writes in its tool-call block.
 */
public interface AiTool {

    /** Stable identifier used in the model's tool-call JSON, e.g. {@code "read_file"}. */
    String name();

    /** One-line description shown to the model. */
    String description();

    /**
     * Run the tool.
     *
     * @param args  The argument map parsed from the model's tool-call JSON
     *              (keys are always Strings; values are Strings/booleans/numbers
     *              or maps/lists for nested args).
     * @return      Human-readable result text that will be fed back to the
     *              model in the next prompt so it can continue.
     */
    String execute(Map<String, Object> args) throws Exception;

    // ---- helpers ----------------------------------------------------------

    /** Get a string arg, returning {@code defaultValue} if missing. */
    static String str(Map<String, Object> args, String key, String defaultValue) {
        Object v = args == null ? null : args.get(key);
        return v == null ? defaultValue : String.valueOf(v);
    }

    /** Get an int arg, returning {@code defaultValue} if missing/invalid. */
    static int integer(Map<String, Object> args, String key, int defaultValue) {
        Object v = args == null ? null : args.get(key);
        if (v == null) return defaultValue;
        try { return Integer.parseInt(String.valueOf(v)); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    static Map<String, Object> emptyArgs() { return new LinkedHashMap<>(); }
}
