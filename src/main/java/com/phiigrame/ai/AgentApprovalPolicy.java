package com.phiigrame.ai;

import java.util.HashSet;
import java.util.Set;

/**
 * User-configurable approval policy for AI tool calls.
 *
 * <p>Two layers:
 * <ol>
 *   <li><b>Per-tool memory</b> - the user can tick "always allow this tool"
 *       in the confirmation dialog.  That decision sticks for the rest of
 *       the IDE session.</li>
 *   <li><b>Auto-approve by risk</b> - read-only tools can be auto-approved
 *       in agent mode so the model can read freely; write/destructive tools
 *       always need consent unless explicitly remembered.</li>
 * </ol>
 *
 * The policy is intentionally in-memory: it does not survive an IDE
 * restart, which is the safer default.  The model is told in the system
 * prompt that all destructive actions are gated.
 */
public class AgentApprovalPolicy {

    /** Mode toggled from the chat panel - "agent" lets the AI chain tools. */
    public enum Mode { OFF, AGENT }

    private Mode mode = Mode.OFF;
    private final Set<String> alwaysAllow = new HashSet<>();
    private boolean autoApproveReads = true;

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode == null ? Mode.OFF : mode; }

    public boolean isAutoApproveReads() { return autoApproveReads; }
    public void setAutoApproveReads(boolean v) { this.autoApproveReads = v; }

    public void rememberAlwaysAllow(String toolName) {
        if (toolName != null) alwaysAllow.add(toolName);
    }

    public void clearMemory() { alwaysAllow.clear(); }

    public boolean isAlwaysAllowed(String toolName) {
        return toolName != null && alwaysAllow.contains(toolName);
    }

    /**
     * Decide whether the given tool call needs a confirmation prompt.
     * Returns {@code true} when the call may proceed without bothering
     * the user.
     */
    public boolean canAutoApprove(AiTool tool) {
        if (tool == null) return false;
        if (isAlwaysAllowed(tool.name())) return true;
        return autoApproveReads && tool.risk() == AiTool.Risk.READ;
    }

    /** Human-readable summary of the current policy for the UI. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(mode == Mode.AGENT ? "Agent" : "Chat");
        sb.append(" - auto-approve read: ").append(autoApproveReads ? "on" : "off");
        sb.append(", remembered: ").append(alwaysAllow.size());
        return sb.toString();
    }
}
