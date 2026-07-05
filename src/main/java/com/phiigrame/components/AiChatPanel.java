package com.phiigrame.components;

import com.phiigrame.ai.AgentApprovalPolicy;
import com.phiigrame.ai.AiTool;
import com.phiigrame.ai.ProjectContextBuilder;
import com.phiigrame.ai.ToolCallParser;
import com.phiigrame.ai.ToolRegistry;
import com.phiigrame.dialogs.LoginDialog;
import com.phiigrame.services.AiHistoryService;
import com.phiigrame.services.AiService;
import com.phiigrame.services.AuthService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI chat panel - right sidebar.
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>Chat</b> - simple Q&A, no tools.</li>
 *   <li><b>Agent</b> - the AI can read, edit and run commands.  Destructive
 *       actions are gated by an approval dialog.</li>
 * </ul>
 *
 * <p>The Agent toggle is in the header as a pill.  Ctrl+Shift+A flips it.
 * Streaming replies show token-by-token; tool calls show as a tiny
 * one-liner bubble (color-coded by outcome).
 */
public class AiChatPanel extends VBox {

    private final AiService aiService;
    private final AiHistoryService historyService;
    private final AuthService authService;
    private final ToolRegistry toolRegistry;
    private final com.phiigrame.services.WorkspaceService workspaceService;
    private final ProjectContextBuilder projectContext = new ProjectContextBuilder();
    private final AgentApprovalPolicy policy = new AgentApprovalPolicy();

    private ScrollPane messagesScroll;
    private VBox messagesBox;
    private TextArea inputArea;
    private Button sendButton;
    private Button stopButton;
    private Button loginButton;
    private Label statusLabel;
    private Label modePill;
    private ComboBox<String> sessionCombo;

    private String currentSessionId;
    private boolean busy = false;

    // Streaming state
    private VBox streamingBubble;
    private StringBuilder streamingText = new StringBuilder();
    private boolean streaming = false;

    public AiChatPanel(AiService aiService, AiHistoryService historyService,
                       AuthService authService, ToolRegistry toolRegistry,
                       com.phiigrame.services.WorkspaceService workspaceService) {
        this.aiService = aiService;
        this.historyService = historyService;
        this.authService = authService;
        this.toolRegistry = toolRegistry;
        this.workspaceService = workspaceService;

        getStyleClass().add("ai-panel");
        setPadding(new Insets(0));
        setSpacing(0);
        setFillWidth(true);

        buildUi();
        refreshSessions();
        ensureSession();
        applyLoginState();
        aiService.checkAvailability(available ->
                Platform.runLater(() -> updateStatus(available)));
    }

    // ------------------------------------------------------------------
    // UI construction
    // ------------------------------------------------------------------

    private void buildUi() {
        // ---- Header: title + mode pill + login + history + new ----
        HBox header = new HBox(8);
        header.getStyleClass().add("ai-header");

        Label title = new Label("Phiigrame AI");
        title.getStyleClass().add("ai-title");
        HBox.setHgrow(title, Priority.ALWAYS);

        modePill = new Label();
        modePill.getStyleClass().addAll("ai-mode-pill");
        refreshModePill();
        modePill.setOnMouseClicked(e -> toggleMode());

        loginButton = new Button();
        loginButton.getStyleClass().add("ai-header-btn");
        loginButton.setOnAction(e -> handleLoginClick());

        Button historyBtn = new Button("History");
        historyBtn.getStyleClass().add("ai-header-btn");
        historyBtn.setOnAction(e -> showHistoryDialog());

        Button newChatBtn = new Button("New");
        newChatBtn.getStyleClass().add("ai-header-btn");
        newChatBtn.setOnAction(e -> startNewSession());

        header.getChildren().addAll(title, modePill, loginButton, historyBtn, newChatBtn);

        // Ctrl+Shift+A toggles Agent mode globally
        KeyCodeCombination ks = new KeyCodeCombination(KeyCode.A, KeyCombination.SHIFT_DOWN, KeyCombination.CONTROL_DOWN);
        Platform.runLater(() -> {
            if (getScene() != null) {
                getScene().getAccelerators().put(ks, this::toggleMode);
            }
        });

        // ---- Session selector row ----
        HBox sessionRow = new HBox(8);
        sessionRow.getStyleClass().add("ai-session-row");
        Label sessionLabel = new Label("SESSION");
        sessionLabel.getStyleClass().add("ai-session-label");

        sessionCombo = new ComboBox<>();
        sessionCombo.getStyleClass().add("ai-combo");
        HBox.setHgrow(sessionCombo, Priority.ALWAYS);
        sessionCombo.setOnAction(e -> {
            String id = sessionCombo.getValue();
            if (id != null) switchSession(id);
        });
        sessionRow.getChildren().addAll(sessionLabel, sessionCombo);

        // ---- Messages area ----
        messagesBox = new VBox(10);
        messagesBox.getStyleClass().add("ai-messages");
        messagesScroll = new ScrollPane(messagesBox);
        messagesScroll.setFitToWidth(true);
        messagesScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        messagesScroll.getStyleClass().add("ai-scroll");
        VBox.setVgrow(messagesScroll, Priority.ALWAYS);

        // ---- Quick actions (chip style) ----
        HBox quickActions = new HBox(6);
        quickActions.getStyleClass().add("ai-quick-actions");
        for (String[] action : new String[][] {
                {"Explain",  "Explain the selected code briefly."},
                {"Refactor", "Refactor the selected code."},
                {"Document", "Add comments to the selected code."},
                {"Tests",    "Write unit tests for the selected code."},
                {"Fix",      "Find and fix bugs in the selected code."}
        }) {
            Button btn = new Button(action[0]);
            btn.getStyleClass().add("ai-quick-btn");
            btn.setTooltip(new Tooltip(action[1]));
            btn.setOnAction(e -> runQuickAction(action[0]));
            quickActions.getChildren().add(btn);
        }

        // ---- Input row ----
        HBox inputRow = new HBox(8);
        inputRow.getStyleClass().add("ai-input-row");
        VBox.setVgrow(inputRow, Priority.NEVER);

        inputArea = new TextArea();
        inputArea.setPromptText("Ask Phiigrame AI...  (Ctrl+Shift+A for Agent)");
        inputArea.setPrefRowCount(2);
        inputArea.setWrapText(true);
        inputArea.getStyleClass().add("ai-input");
        HBox.setHgrow(inputArea, Priority.ALWAYS);

        VBox buttonCol = new VBox(6);
        sendButton = new Button("Send");
        sendButton.getStyleClass().add("ai-send");
        sendButton.setOnAction(e -> sendMessage());

        stopButton = new Button("Stop");
        stopButton.getStyleClass().add("ai-stop");
        stopButton.setOnAction(e -> {
            aiService.shutdown();
            finishStreaming("Stopped");
        });
        stopButton.setDisable(true);

        buttonCol.getChildren().addAll(sendButton, stopButton);
        inputRow.getChildren().addAll(inputArea, buttonCol);

        inputArea.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && !e.isShiftDown()) {
                e.consume();
                sendMessage();
            }
        });

        // ---- Status bar ----
        statusLabel = new Label("Checking...");
        statusLabel.getStyleClass().add("ai-status");
        statusLabel.setMaxWidth(Double.MAX_VALUE);

        getChildren().addAll(header, sessionRow, messagesScroll, quickActions, inputRow, statusLabel);
    }

    private void refreshModePill() {
        boolean on = policy.getMode() == AgentApprovalPolicy.Mode.AGENT;
        modePill.setText(on ? "Agent" : "Chat");
        modePill.getStyleClass().removeAll("active");
        if (on) modePill.getStyleClass().add("active");
        modePill.setTooltip(new Tooltip(on
                ? "Agent mode: AI can read, edit and run commands.  Ctrl+Shift+A to toggle."
                : "Chat only.  Click or Ctrl+Shift+A to enable Agent."));
    }

    private void toggleMode() {
        policy.setMode(policy.getMode() == AgentApprovalPolicy.Mode.AGENT
                ? AgentApprovalPolicy.Mode.OFF
                : AgentApprovalPolicy.Mode.AGENT);
        refreshModePill();
    }

    // ------------------------------------------------------------------
    // Login state
    // ------------------------------------------------------------------

    public void applyLoginState() {
        boolean loggedIn = authService != null && authService.isLoggedIn();
        if (loggedIn) {
            String name = authService.getCurrentUser() != null
                    ? authService.getCurrentUser().username : "user";
            loginButton.setText(name);
        } else {
            loginButton.setText("Sign in");
        }
        inputArea.setDisable(!loggedIn || busy);
        sendButton.setDisable(!loggedIn || busy);
    }

    private void handleLoginClick() {
        if (authService == null) return;
        if (authService.isLoggedIn()) {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                    "Sign out of " + authService.getCurrentUser().username + "?",
                    ButtonType.CANCEL, ButtonType.OK);
            a.setHeaderText("Log out");
            a.showAndWait().ifPresent(bt -> {
                if (bt == ButtonType.OK) {
                    authService.logout();
                    applyLoginState();
                }
            });
        } else {
            LoginDialog dlg = new LoginDialog(authService);
            if (dlg.showAndWait()) {
                appendSystem("Signed in as " + authService.getCurrentUser().username);
            }
            applyLoginState();
        }
    }

    // ------------------------------------------------------------------
    // Status / bubbles
    // ------------------------------------------------------------------

    private void updateStatus(boolean available) {
        boolean loggedIn = authService != null && authService.isLoggedIn();
        if (!loggedIn) {
            statusLabel.setText("Sign in to start  -  " +
                    (available ? aiService.getModelName() : "AI offline"));
            statusLabel.getStyleClass().removeAll("ready", "error", "warn", "busy");
            statusLabel.getStyleClass().add("warn");
            return;
        }
        if (available) {
            String suffix = policy.getMode() == AgentApprovalPolicy.Mode.AGENT
                    ? "  -  Agent  -  " + toolRegistry.all().size() + " tools"
                    : "";
            statusLabel.setText("Ready  -  " + aiService.getModelName() + suffix);
            statusLabel.getStyleClass().removeAll("ready", "error", "warn", "busy");
            statusLabel.getStyleClass().add("ready");
        } else {
            statusLabel.setText("Offline  -  " + aiService.getLastError());
            statusLabel.getStyleClass().removeAll("ready", "error", "warn", "busy");
            statusLabel.getStyleClass().add("error");
        }
    }

    private void appendSystem(String text) {
        appendBubble("system", text);
    }

    /** A single message bubble.  User messages are tinted indigo. */
    private VBox appendBubble(String role, String content) {
        if (content == null) content = "";
        VBox bubble = new VBox(4);
        bubble.getStyleClass().add("ai-bubble");
        if ("user".equals(role)) bubble.getStyleClass().add("ai-bubble-user");
        else if ("system".equals(role)) bubble.getStyleClass().add("ai-bubble-system");
        else bubble.getStyleClass().add("ai-bubble-assistant");
        bubble.setFillWidth(true);

        String label;
        switch (role) {
            case "user": label = "YOU"; break;
            case "system": label = "SYSTEM"; break;
            default: label = "AI";
        }
        Label header = new Label(label);
        header.getStyleClass().add("ai-bubble-header");
        bubble.getChildren().add(header);

        Node body;
        if ("user".equals(role) || "system".equals(role)) {
            Label l = new Label(content);
            l.setWrapText(true);
            l.getStyleClass().add("ai-bubble-body");
            body = l;
        } else {
            body = MarkdownRenderer.render(content);
        }
        bubble.getChildren().add(body);

        messagesBox.getChildren().add(bubble);
        Platform.runLater(() -> messagesScroll.setVvalue(1.0));
        return bubble;
    }

    /** Empty assistant bubble that fills with streaming tokens. */
    private VBox startStreamingBubble() {
        VBox bubble = new VBox(4);
        bubble.getStyleClass().addAll("ai-bubble", "ai-bubble-assistant");
        bubble.setFillWidth(true);

        Label header = new Label("AI");
        header.getStyleClass().add("ai-bubble-header");
        bubble.getChildren().add(header);

        Label body = new Label("");
        body.setWrapText(true);
        body.getStyleClass().add("ai-bubble-body");
        bubble.getChildren().add(body);

        messagesBox.getChildren().add(bubble);
        Platform.runLater(() -> messagesScroll.setVvalue(1.0));
        return bubble;
    }

    // ------------------------------------------------------------------
    // Session management
    // ------------------------------------------------------------------

    private void ensureSession() {
        if (currentSessionId == null) {
            AiHistoryService.AiSession s = historyService.createSession("New Chat");
            currentSessionId = s.id;
            refreshSessions();
        }
        loadCurrentSession();
    }

    private void refreshSessions() {
        if (sessionCombo == null) return;
        List<AiHistoryService.AiSession> all = historyService.getAllSessions();
        sessionCombo.getItems().clear();
        for (AiHistoryService.AiSession s : all) {
            sessionCombo.getItems().add(s.id);
        }
        if (currentSessionId != null) sessionCombo.setValue(currentSessionId);
    }

    private void switchSession(String sessionId) {
        if (sessionId == null) return;
        currentSessionId = sessionId;
        loadCurrentSession();
    }

    private void loadCurrentSession() {
        AiHistoryService.AiSession s = historyService.findById(currentSessionId);
        messagesBox.getChildren().clear();
        if (s == null) return;
        for (AiHistoryService.Message m : s.messages) {
            appendBubble(m.role, m.content);
        }
    }

    private void startNewSession() {
        AiHistoryService.AiSession s = historyService.createSession("New Chat");
        currentSessionId = s.id;
        refreshSessions();
        messagesBox.getChildren().clear();
        appendSystem("New chat started.");
    }

    private void showHistoryDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Chat History");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(10);
        content.setPadding(new Insets(16));
        content.setPrefSize(560, 480);

        ListView<String> list = new ListView<>();
        for (AiHistoryService.AiSession s : historyService.getAllSessions()) {
            String label = s.title + "  (" + s.messages.size() + " msgs)";
            list.getItems().add(s.id + "::" + label);
        }

        Button openBtn = new Button("Open");
        Button deleteBtn = new Button("Delete");
        Button clearBtn = new Button("Clear All");
        openBtn.getStyleClass().add("ai-header-btn");
        deleteBtn.getStyleClass().add("ai-header-btn");
        clearBtn.getStyleClass().add("ai-header-btn");

        openBtn.setOnAction(e -> {
            String sel = list.getSelectionModel().getSelectedItem();
            if (sel != null) {
                currentSessionId = sel.split("::")[0];
                refreshSessions();
                loadCurrentSession();
                dialog.close();
            }
        });
        deleteBtn.setOnAction(e -> {
            String sel = list.getSelectionModel().getSelectedItem();
            if (sel != null) {
                String id = sel.split("::")[0];
                historyService.deleteSession(id);
                if (id.equals(currentSessionId)) startNewSession();
                dialog.close();
                showHistoryDialog();
            }
        });
        clearBtn.setOnAction(e -> {
            historyService.clearAll();
            startNewSession();
            dialog.close();
        });

        HBox buttons = new HBox(8);
        buttons.getChildren().addAll(openBtn, deleteBtn, clearBtn);
        content.getChildren().addAll(new Label("Sessions"), list, buttons);
        list.setPrefHeight(380);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    // ------------------------------------------------------------------
    // Sending & streaming
    // ------------------------------------------------------------------

    private void sendMessage() {
        if (busy) return;

        if (authService == null || !authService.isLoggedIn()) {
            Alert a = new Alert(Alert.AlertType.INFORMATION,
                    "Sign in to chat with the AI.  The editor still works without signing in.",
                    ButtonType.OK);
            a.setHeaderText("Login required");
            a.showAndWait();
            handleLoginClick();
            return;
        }

        String text = inputArea.getText().trim();
        if (text.isEmpty()) return;

        ensureSession();

        // Build a short project snapshot for the model only.
        String ctx = projectContext.build(
                workspaceService != null ? workspaceService.getProjectDir() : null,
                null);
        final String effectiveText = ctx.isEmpty() ? text
                : "<project_context>\n" + ctx + "</project_context>\n\n" + text;

        appendBubble("user", text);
        inputArea.clear();

        AiHistoryService.AiSession session = historyService.findById(currentSessionId);
        if (session != null) historyService.addMessage(currentSessionId, "user", text);

        List<Map<String, String>> history = new ArrayList<>();
        if (session != null) {
            int start = Math.max(0, session.messages.size() - 10);
            for (int i = start; i < session.messages.size() - 1; i++) {
                Map<String, String> m = new HashMap<>();
                m.put("role", session.messages.get(i).role);
                m.put("content", session.messages.get(i).content);
                history.add(m);
            }
        }

        setBusy(true);
        statusLabel.setText("Thinking...");
        statusLabel.getStyleClass().removeAll("ready", "error", "warn", "busy");
        statusLabel.getStyleClass().add("busy");

        streamingBubble = startStreamingBubble();
        streamingText = new StringBuilder();
        streaming = true;

        if (policy.getMode() == AgentApprovalPolicy.Mode.AGENT) {
            ToolCallParser.ToolCallback callback = new ToolCallParser.ToolCallback() {
                @Override public boolean confirm(AiTool tool, ToolCallParser.ToolCall call) {
                    if (policy.canAutoApprove(tool)) {
                        appendToolEvent(tool, call, "auto");
                        return true;
                    }
                    return askPermissionForTool(tool, call);
                }
                @Override public void onResult(AiTool tool, ToolCallParser.ToolCall call, String result) {
                    appendToolEvent(tool, call, "ok");
                }
            };
            aiService.chatWithTools(effectiveText, history, toolRegistry, callback, 8,
                    response -> Platform.runLater(() -> onAssistantResponse(response, true)),
                    () -> Platform.runLater(() -> finishStreaming(null)));
        } else {
            aiService.chatStream(effectiveText, history,
                    token -> onStreamToken(token),
                    response -> Platform.runLater(() -> onAssistantResponse(response, true)),
                    () -> Platform.runLater(() -> finishStreaming(null)));
        }
    }

    private void onStreamToken(String token) {
        if (!streaming || streamingBubble == null) return;
        streamingText.append(token);
        String current = streamingText.toString();
        Label body = (Label) streamingBubble.getChildren().get(1);
        body.setText(current);
        Platform.runLater(() -> messagesScroll.setVvalue(1.0));
    }

    private void onAssistantResponse(String response, boolean save) {
        String display = stripToolBlocks(response == null ? "" : response);
        if (streamingBubble != null) {
            streamingBubble.getChildren().set(1, MarkdownRenderer.render(display));
            streamingBubble = null;
        } else {
            appendBubble("assistant", display);
        }
        if (save && currentSessionId != null) {
            historyService.addMessage(currentSessionId, "assistant", response);
        }
        refreshSessions();
        streaming = false;
    }

    private void finishStreaming(String replacement) {
        if (!streaming) {
            setBusy(false);
            updateStatus(aiService.getLastError() == null);
            return;
        }
        if (replacement != null) {
            String current = streamingText.toString();
            onAssistantResponse(current + "\n\n_[" + replacement + "]_", false);
        } else {
            String current = streamingText.toString();
            if (streamingBubble != null) {
                streamingBubble.getChildren().set(1,
                        MarkdownRenderer.render(stripToolBlocks(current)));
                streamingBubble = null;
            }
            if (currentSessionId != null && !current.isEmpty()) {
                historyService.addMessage(currentSessionId, "assistant", current);
            }
            refreshSessions();
            streaming = false;
        }
        setBusy(false);
        updateStatus(aiService.getLastError() == null);
    }

    private static String stripToolBlocks(String text) {
        if (text == null) return "";
        return text.replaceAll("```(?:tool|json)?\\s*\\n?\\{[\\s\\S]*?\\}\\s*\\n?```", "")
                .replaceAll("<tool\\s+[^/>]*/?>", "")
                .trim();
    }

    // ------------------------------------------------------------------
    // Approval dialog
    // ------------------------------------------------------------------

    private boolean askPermissionForTool(AiTool tool, ToolCallParser.ToolCall call) {
        if (policy.canAutoApprove(tool)) return true;

        final boolean[] result = {false};
        Runnable prompt = () -> {
            String args = call.args == null || call.args.isEmpty()
                    ? "(no args)"
                    : call.args.toString();
            Dialog<ButtonType> dlg = new Dialog<>();
            dlg.setTitle("Allow " + tool.name() + "?");
            dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            dlg.getDialogPane().getStyleClass().add("ai-approval");

            VBox content = new VBox(8);
            content.setPadding(new Insets(16));

            Label title = new Label("AGENT WANTS TO RUN");
            title.getStyleClass().add("ai-approval-title");

            Label toolLbl = new Label(tool.name());
            toolLbl.getStyleClass().add("ai-approval-tool");

            Label argsLbl = new Label(args);
            argsLbl.setWrapText(true);
            argsLbl.getStyleClass().add("ai-approval-args");
            argsLbl.setMaxWidth(420);

            CheckBox remember = new CheckBox("Always allow this tool");
            remember.getStyleClass().add("ai-approval-remember");

            content.getChildren().addAll(title, toolLbl, argsLbl, remember);
            dlg.getDialogPane().setContent(content);
            dlg.showAndWait().ifPresent(bt -> {
                if (bt == ButtonType.OK) {
                    result[0] = true;
                    if (remember.isSelected()) policy.rememberAlwaysAllow(tool.name());
                } else {
                    appendToolEvent(tool, call, "denied");
                }
            });
        };
        if (Platform.isFxApplicationThread()) prompt.run();
        else Platform.runLater(prompt);

        long deadline = System.currentTimeMillis() + 60_000L * 5;
        while (System.currentTimeMillis() < deadline) {
            try { Thread.sleep(50); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
            if (result[0]) break;
        }
        return result[0];
    }

    /** Tiny one-line row showing a tool call and its outcome. */
    private void appendToolEvent(AiTool tool, ToolCallParser.ToolCall call, String status) {
        if (tool == null || call == null) return;
        HBox row = new HBox(6);
        row.getStyleClass().add("ai-tool-event");

        Circle dot = new Circle(3);
        dot.getStyleClass().add("ai-tool-dot");
        switch (status) {
            case "ok":      dot.getStyleClass().add("ok"); break;
            case "auto":    dot.getStyleClass().add("auto"); break;
            case "denied":  dot.getStyleClass().add("deny"); break;
            default:        dot.getStyleClass().add("run");
        }

        String args = call.args == null || call.args.isEmpty()
                ? ""
                : "  " + truncate(call.args.toString(), 50);
        Label l = new Label(tool.name() + args);
        l.getStyleClass().add("ai-tool-text");
        if ("ok".equals(status)) l.getStyleClass().add("ok");
        else if ("denied".equals(status)) l.getStyleClass().add("deny");

        row.getChildren().addAll(dot, l);
        messagesBox.getChildren().add(row);
        Platform.runLater(() -> messagesScroll.setVvalue(1.0));
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        String oneLine = s.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "...";
    }

    // ------------------------------------------------------------------
    // Quick actions
    // ------------------------------------------------------------------

    private void runQuickAction(String action) {
        if (busy) return;
        String prompt;
        switch (action) {
            case "Explain":  prompt = "Explain the selected code in 2-3 sentences."; break;
            case "Refactor": prompt = "Refactor the selected code. Return only the new code."; break;
            case "Document": prompt = "Add comments to the selected code."; break;
            case "Tests":    prompt = "Generate unit tests for the selected code."; break;
            case "Fix":      prompt = "Find and fix bugs in the selected code."; break;
            default: prompt = action;
        }
        inputArea.setText(prompt);
        sendMessage();
    }

    // ------------------------------------------------------------------
    // Misc
    // ------------------------------------------------------------------

    private void setBusy(boolean b) {
        busy = b;
        boolean loggedIn = authService != null && authService.isLoggedIn();
        inputArea.setDisable(b || !loggedIn);
        sendButton.setDisable(b || !loggedIn);
        stopButton.setDisable(!b);
    }
}
