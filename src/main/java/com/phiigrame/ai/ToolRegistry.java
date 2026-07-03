package com.phiigrame.ai;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds the set of {@link AiTool}s the AI is allowed to call.  Lookup by
 * name is the only operation the chat loop needs; everything else is just
 * registration helpers.
 */
public class ToolRegistry {

    private final Map<String, AiTool> tools = new LinkedHashMap<>();

    public ToolRegistry register(AiTool tool) {
        if (tool == null) throw new IllegalArgumentException("tool == null");
        tools.put(tool.name(), tool);
        return this;
    }

    public AiTool get(String name) {
        return name == null ? null : tools.get(name);
    }

    public Collection<AiTool> all() { return tools.values(); }

    /**
     * Format the registry as a prompt block that lists every tool to the
     * model.  This is injected into the system message so the model knows
     * which tools exist and what JSON schema each one expects.
     */
    public String describeForPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("You can call the following tools by emitting a single JSON block:\n");
        sb.append("```tool\n");
        sb.append("{\"name\": \"<tool_name>\", \"args\": { ... }}\n");
        sb.append("```\n\n");
        for (AiTool t : tools.values()) {
            sb.append("- ").append(t.name()).append(": ").append(t.description()).append('\n');
        }
        return sb.toString();
    }
}
