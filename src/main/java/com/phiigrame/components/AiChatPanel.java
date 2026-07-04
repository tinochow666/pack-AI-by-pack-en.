package com.phiigrame.components;

import com.phiigrame.ai.AgentApprovalPolicy;
import com.phiigrame.ai.AiTool;
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
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI chat panel - right sidebar that allows the user to chat with the
 * configured AI model.  Stores conversation history in
 * {@link AiHistoryService}.
 *
 * <p>The editor works without a login, but starting a chat requires one
 * (the {@link AuthService} is consulted on every send).  The model can
 * also call a {@link ToolRegistry} of file tools while answering.
 *
 * <p>Assistant replies are streamed: tokens are pushed one at a time
 * into the live bubble so the user can see the answer forming.
 */
public class AiChatPanel extends VBox {

    private final AiService aiService;
    private final AiHistoryService historyService;
    private final AuthService authService;
    private final ToolRegistry toolRegistry;
    private final AgentApprovalPolicy policy = new AgentApprovalPolicy();

    private ScrollPane messagesScroll;
    private VBox messagesBox;
    private TextArea inputArea;
    private Button sendButton;
    private Button stopButton;
    private Button loginButton;
    private Label statusLabel;
    private Label headerLabel;
    private ComboBox<String> sessionCombo;
    private String currentSessionId;
    private boolean busy = false;

    // Streaming state
    private VBox streamingBubble;
    private StringBuilder streamingText = new StringBuilder();
    private boolean streaming = false;

    public AiChatPanel(AiService aiService, AiHistoryService historyService,
                       AuthService authService, ToolRegistry toolRegistry) {
        this.aiService = aiService;
        this.historyService = historyService;
        this.authService = authService;
        this.toolRegistry = toolRegistry;

        getStyleClass().add("ai-panel");
        setPadding(new Insets(0));
        setSpacing(0);
        setFillWidth(true);

        buildUi();
        refreshSessions();
        ensureSession();
        applyLoginState();
        aiService.checkAvailability(available -> {
            Platform.runLater(() -> updateStatus(available));
        });
    }

    private void buildUi() {
        // Header
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: #2d2d2d; -fx-padding: 8 12; " +
                "-fx-border-color: transparent transparent #1e1e1e transparent; " +
                "-fx-border-width: 0 0 1 0;");

        headerLabel = new Label("AI Assistant");
        headerLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-weight: bold; -fx-font-size: 12px;");
        HBox.setHgrow(headerLabel, Priority.ALWAYS);

        loginButton = new Button();
        loginButton.setStyle("-fx-background-color: #0e639c; -fx-text-fill: white; " +
                "-fx-font-size: 11px; -fx-padding: 4 10; -fx-background-radius: 4; -fx-cursor: hand;");
        loginButton.setOnAction(e -> handleLoginClick());
        loginButton.setMinWidth(70);

        Button newChatBtn = new Button("+");
        newChatBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #cccccc; " +
                "-fx-cursor: hand; -fx-min-width: 24; -fx-min-height: 24; " +
                "-fx-background-radius: 4; -fx-font-size: 14px; -fx-font-weight: bold;");
        newChatBtn.setTooltip(new Tooltip("New chat"));
        newChatBtn.setOnAction(e -> startNewSession());

        Button historyBtn = new Button("History");
        historyBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #cccccc; " +
                "-fx-cursor: hand; -fx-min-height: 24; -fx-padding: 0 8; -fx-background-radius: 4;");
        historyBtn.setTooltip(new Tooltip("Show history"));
        historyBtn.setOnAction(e -> showHistoryDialog());

        // Agent mode toggle - lets the AI chain tools (read/edit/bash/git)
        ToggleButton agentBtn = new ToggleButton("Agent");
        agentBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #cccccc; " +
                "-fx-cursor: hand; -fx-min-height: 24; -fx-padding: 0 8; -fx-background-radius: 4;");
        agentBtn.setTooltip(new Tooltip("Agent mode: the AI can read, edit and run commands. " +
                "You approve destructive actions in a dialog."));
        agentBtn.setSelected(policy.getMode() == AgentApprovalPolicy.Mode.AGENT);
        agentBtn.setOnAction(e -> {
            policy.setMode(agentBtn.isSelected()
                    ? AgentApprovalPolicy.Mode.AGENT
                    : AgentApprovalPolicy.Mode.OFF);
            String text = agentBtn.isSelected() ? "Agent" : "Chat";
            agentBtn.setText(text);
            agentBtn.setStyle((agentBtn.isSelected()
                    ? "-fx-background-color: #0e639c; -fx-text-fill: white; "
                    : "-fx-background-color: transparent; -fx-text-fill: #cccccc; ") +
                    "-fx-cursor: hand; -fx-min-height: 24; -fx-padding: 0 8; " +
                    "-fx-background-radius: 4; -fx-font-weight: bold;");
        });

        header.getChildren().addAll(headerLabel, agentBtn, loginButton, historyBtn, newChatBtn);

        // Session selector
        HBox sessionRow = new HBox(8);
        sessionRow.setAlignment(Pos.CENTER_LEFT);
        sessionRow.setStyle("-fx-background-color: #2d2d2d; -fx-padding: 4 12 8 12;");
        Label sessionLabel = new Label("Session:");
        sessionLabel.setStyle("-fx-text-fill: #8b8b8b; -fx-font-size: 11px;");
        sessionCombo = new ComboBox<>();
        sessionCombo.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: #d4d4d4; -fx-font-size: 11px;");
        HBox.setHgrow(sessionCombo, Priority.ALWAYS);
        sessionCombo.setOnAction(e -> {
            String id = sessionCombo.getValue();
            if (id != null) switchSession(id);
        });
        sessionRow.getChildren().addAll(sessionLabel, sessionCombo);

        // Messages area
        messagesBox = new VBox(8);
        messagesBox.setPadding(new Insets(10, 12, 10, 12));
        messagesBox.setStyle("-fx-background-color: #1e1e1e;");
        messagesScroll = new ScrollPane(messagesBox);
        messagesScroll.setFitToWidth(true);
        messagesScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        messagesScroll.setStyle("-fx-background: #1e1e1e; -fx-background-color: #1e1e1e; " +
                "-fx-border-color: transparent;");
        VBox.setVgrow(messagesScroll, Priority.ALWAYS);

        // Quick actions (more useful prompts, with tooltips)
        HBox quickActions = new HBox(4);
        quickActions.setStyle("-fx-background-color: #2d2d2d; -fx-padding: 4 12; -fx-alignment: center-left;");
        for (String[] action : new String[][] {
            {"Explain",   "Explain the selected code in 2-3 sentences."},
            {"Refactor",  "Refactor the selected code. Return only the new code."},
            {"Document",  "Add Javadoc comments to the selected code."},
            {"Tests",     "Generate JUnit 5 unit tests for the selected code."},
            {"Fix bugs",  "Find and fix any bugs in the selected code."}
        }) {
            Button btn = new Button(action[0]);
            btn.setStyle("-fx-background-color: #3e3e3e; -fx-text-fill: #cccccc; -fx-font-size: 10px; " +
                    "-fx-padding: 4 10; -fx-background-radius: 3; -fx-cursor: hand;");
            btn.setTooltip(new Tooltip(action[1]));
            btn.setOnAction(e -> runQuickAction(action[0]));
            quickActions.getChildren().add(btn);
        }

        // Input area
        HBox inputRow = new HBox(6);
        inputRow.setStyle("-fx-background-color: #2d2d2d; -fx-padding: 8 12 8 12; " +
                "-fx-border-color: #1e1e1e transparent transparent transparent; " +
                "-fx-border-width: 1 0 0 0;");
        VBox.setVgrow(inputRow, Priority.NEVER);

        inputArea = new TextArea();
        inputArea.setPromptText("Ask anything... (Enter to send, Shift+Enter for newline)");
        inputArea.setPrefRowCount(3);
        inputArea.setWrapText(true);
        inputArea.setStyle("-fx-control-inner-background: #1e1e1e; " +
                "-fx-text-fill: #d4d4d4; -fx-font-size: 12px; " +
                "-fx-background-color: #1e1e1e; -fx-border-color: #454545; -fx-border-radius: 4;");
        HBox.setHgrow(inputArea, Priority.ALWAYS);

        VBox buttonCol = new VBox(4);
        sendButton = new Button("Send");
        sendButton.setStyle("-fx-background-color: #0e639c; -fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-padding: 6 14; -fx-background-radius: 4; -fx-cursor: hand;");
        sendButton.setOnAction(e -> sendMessage());
        sendButton.setMinWidth(70);

        stopButton = new Button("Stop");
        stopButton.setStyle("-fx-background-color: #5a2828; -fx-text-fill: white; " +
                "-fx-padding: 6 14; -fx-background-radius: 4; -fx-cursor: hand;");
        stopButton.setOnAction(e -> {
            aiService.shutdown();
            finishStreaming("Stopped.");
        });
        stopButton.setMinWidth(70);
        stopButton.setDisable(true);

        buttonCol.getChildren().addAll(sendButton, stopButton);
        inputRow.getChildren().addAll(inputArea, buttonCol);

        inputArea.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && !e.isShiftDown()) {
                e.consume();
                sendMessage();
            }
        });

        // Status bar
        statusLabel = new Label("Checking AI...");
        statusLabel.setStyle("-fx-text-fill: #8b8b8b; -fx-font-size: 10px; " +
                "-fx-padding: 4 12; -fx-background-color: #1e1e1e;");
        statusLabel.setMaxWidth(Double.MAX_VALUE);

        getChildren().addAll(header, sessionRow, messagesScroll, quickActions, inputRow, statusLabel);
    }

    public void applyLoginState() {
        boolean loggedIn = authService != null && authService.isLoggedIn();
        if (loggedIn) {
            String name = authService.getCurrentUser() != null
                    ? authService.getCurrentUser().username : "account";
            loginButton.setText("Logout (" + name + ")");
            loginButton.setStyle("-fx-background-color: #3e3e3e; -fx-text-fill: #d4d4d4; " +
                    "-fx-font-size: 11px; -fx-padding: 4 10; -fx-background-radius: 4; -fx-cursor: hand;");
            inputArea.setDisable(false);
            inputArea.setPromptText("Ask anything... (Enter to send, Shift+Enter for newline)");
            sendButton.setDisable(busy);
        } else {
            loginButton.setText("Login");
            loginButton.setStyle("-fx-background-color: #0e639c; -fx-text-fill: white; " +
                    "-fx-font-size: 11px; -fx-padding: 4 10; -fx-background-radius: 4; -fx-cursor: hand;");
            inputArea.setDisable(true);
            inputArea.setPromptText("Sign in to start chatting with the AI");
            sendButton.setDisable(true);
        }
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
            boolean ok = dlg.showAndWait();
            if (ok) {
                appendSystem("Signed in as " + authService.getCurrentUser().username);
            }
            applyLoginState();
        }
    }

    private void updateStatus(boolean available) {
        boolean loggedIn = authService != null && authService.isLoggedIn();
        if (!loggedIn) {
            statusLabel.setText("Sign in to chat - " + (available ? aiService.getModelName() : "AI offline"));
            statusLabel.setStyle("-fx-text-fill: #d4a85f; -fx-font-size: 10px; " +
                    "-fx-padding: 4 12; -fx-background-color: #1e1e1e;");
            return;
        }
        if (available) {
            statusLabel.setText("Ready - " + aiService.getModelName());
            statusLabel.setStyle("-fx-text-fill: #6a9955; -fx-font-size: 10px; " +
                    "-fx-padding: 4 12; -fx-background-color: #1e1e1e;");
        } else {
            statusLabel.setText("AI offline: " + aiService.getLastError());
            statusLabel.setStyle("-fx-text-fill: #f48771; -fx-font-size: 10px; " +
                    "-fx-padding: 4 12; -fx-background-color: #1e1e1e;");
        }
    }

    private void appendSystem(String text) {
        appendBubble("system", text);
    }

    /** Render a single message bubble.  Assistant messages are rendered as
     * Markdown, while user/system messages are plain text on a colored
     * background.  The bubble auto-scrolls into view. */
    private VBox appendBubble(String role, String content) {
        if (content == null) content = "";
        VBox bubble = new VBox(4);
        bubble.setFillWidth(true);
        String label;
        String bg;
        String styleClass;
        switch (role) {
            case "user":
                label = "You";
                bg = "#2d2d30";
                styleClass = "ai-bubble-user";
                break;
            case "system":
                label = "system";
                bg = "#252526";
                styleClass = "ai-bubble-system";
                break;
            default:
                label = "AI";
                bg = "#252526";
                styleClass = "ai-bubble-assistant";
        }
        bubble.getStyleClass().add(styleClass);
        Label header = new Label(label);
        header.setStyle("-fx-text-fill: #8b8b8b; -fx-font-size: 10px; -fx-font-weight: bold;");
        bubble.getChildren().add(header);

        Node body;
        if ("user".equals(role)) {
            Label l = new Label(content);
            l.setWrapText(true);
            l.setStyle("-fx-text-fill: #d4d4d4; -fx-font-size: 12px;");
            body = l;
        } else if ("system".equals(role)) {
            Label l = new Label(content);
            l.setWrapText(true);
            l.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px; -fx-font-style: italic;");
            body = l;
        } else {
            body = MarkdownRenderer.render(content);
        }
        bubble.getChildren().add(body);

        bubble.setPadding(new Insets(8, 10, 8, 10));
        bubble.setStyle("-fx-background-color: " + bg + "; -fx-background-radius: 6;");

        messagesBox.getChildren().add(bubble);
        Platform.runLater(() -> messagesScroll.setVvalue(1.0));
        return bubble;
    }

    /**
     * Create an empty assistant bubble that will be filled token by token
     * as the model streams output.  Returns the bubble so the streaming
     * loop can replace its body.
     */
    private VBox startStreamingBubble() {
        VBox bubble = new VBox(4);
        bubble.setFillWidth(true);
        bubble.getStyleClass().add("ai-bubble-assistant");
        Label header = new Label("AI  -  thinking");
        header.setStyle("-fx-text-fill: #8b8b8b; -fx-font-size: 10px; -fx-font-weight: bold;");
        bubble.getChildren().add(header);

        Label body = new Label("");
        body.setWrapText(true);
        body.setStyle("-fx-text-fill: #d4d4d4; -fx-font-size: 12px;");
        bubble.getChildren().add(body);

        bubble.setPadding(new Insets(8, 10, 8, 10));
        bubble.setStyle("-fx-background-color: #252526; -fx-background-radius: 6;");

        messagesBox.getChildren().add(bubble);
        Platform.runLater(() -> messagesScroll.setVvalue(1.0));
        return bubble;
    }

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
        if (currentSessionId != null) {
            sessionCombo.setValue(currentSessionId);
        }
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
    }

    private void showHistoryDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("AI History");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(8);
        content.setPadding(new Insets(16));
        content.setStyle("-fx-background-color: #2d2d2d;");
        content.setPrefSize(560, 480);

        ListView<String> list = new ListView<>();
        list.setStyle("-fx-background-color: #1e1e1e; -fx-control-inner-background: #1e1e1e;");
        for (AiHistoryService.AiSession s : historyService.getAllSessions()) {
            String label = s.title + "  (" + s.messages.size() + " msgs, " +
                    historyService.formatTimestamp(s.lastModified) + ")";
            list.getItems().add(s.id + "::" + label);
        }

        Button openBtn = new Button("Open");
        Button deleteBtn = new Button("Delete");
        Button clearBtn = new Button("Clear All");
        openBtn.setStyle(buttonStyle("#0e639c"));
        deleteBtn.setStyle(buttonStyle("#5a2828"));
        clearBtn.setStyle(buttonStyle("#5a2828"));

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
            showHistoryDialog();
        });

        HBox buttons = new HBox(8);
        buttons.getChildren().addAll(openBtn, deleteBtn, clearBtn);
        content.getChildren().addAll(new Label("Sessions:"), list, buttons);
        list.setPrefHeight(380);

        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private String buttonStyle(String bg) {
        return "-fx-background-color: " + bg + "; -fx-text-fill: white; -fx-padding: 6 12; " +
               "-fx-background-radius: 4; -fx-cursor: hand;";
    }

    private void setBusy(boolean b) {
        busy = b;
        boolean loggedIn = authService != null && authService.isLoggedIn();
        sendButton.setDisable(b || !loggedIn);
        stopButton.setDisable(!b);
        inputArea.setDisable(b || !loggedIn);
    }

    private void sendMessage() {
        if (busy) return;

        if (authService == null || !authService.isLoggedIn()) {
            Alert a = new Alert(Alert.AlertType.INFORMATION,
                    "Please sign in with your Phiigrame Account to chat with the AI. " +
                    "The editor still works without signing in.",
                    ButtonType.OK);
            a.setHeaderText("Login required");
            a.showAndWait();
            handleLoginClick();
            return;
        }

        String text = inputArea.getText().trim();
        if (text.isEmpty()) return;

        ensureSession();

        appendBubble("user", text);
        inputArea.clear();

        AiHistoryService.AiSession session = historyService.findById(currentSessionId);
        if (session != null) {
            historyService.addMessage(currentSessionId, "user", text);
        }

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
        statusLabel.setStyle("-fx-text-fill: #d4d4d4; -fx-font-size: 10px; " +
                "-fx-padding: 4 12; -fx-background-color: #1e1e1e;");

        // Start the streaming bubble.
        streamingBubble = startStreamingBubble();
        streamingText = new StringBuilder();
        streaming = true;

        if (policy.getMode() == AgentApprovalPolicy.Mode.AGENT) {
            // Agent mode: run a tool-calling loop.  The model can chain
            // multiple tool calls; results are appended to the
            // conversation until the model emits a final answer with
            // no tool calls.
            ToolCallParser.ToolCallback callback = new ToolCallParser.ToolCallback() {
                @Override public boolean confirm(AiTool tool, ToolCallParser.ToolCall call) {
                    if (policy.canAutoApprove(tool)) {
                        appendToolEvent(tool, call, "auto-approved", null);
                        return true;
                    }
                    return askPermissionForTool(tool, call);
                }
                @Override public void onResult(AiTool tool, ToolCallParser.ToolCall call, String result) {
                    appendToolEvent(tool, call, "ok", result);
                }
            };
            aiService.chatWithTools(text, history, toolRegistry, callback, 8,
                    response -> Platform.runLater(() -> onAssistantResponse(response, true)),
                    () -> Platform.runLater(() -> finishStreaming(null)));
        } else {
            aiService.chatStream(text, history,
                    token -> onStreamToken(token),
                    response -> Platform.runLater(() -> onAssistantResponse(response, true)),
                    () -> Platform.runLater(() -> finishStreaming(null)));
        }
    }

    private void onStreamToken(String token) {
        if (!streaming || streamingBubble == null) return;
        streamingText.append(token);
        // Replace the body of the streaming bubble with a fresh render of
        // the accumulated text.  We use a plain Label while the model is
        // still streaming because Markdown re-rendering every token would
        // be janky; once the model is done we render the final result
        // with full Markdown (code blocks, syntax highlighting, ...).
        String current = streamingText.toString();
        Label body = (Label) streamingBubble.getChildren().get(1);
        body.setText(current);
        Platform.runLater(() -> messagesScroll.setVvalue(1.0));
    }

    private void onAssistantResponse(String response, boolean save) {
        // Streaming finished.  Strip tool blocks and re-render the
        // bubble with full Markdown so the user sees code blocks,
        // syntax highlighting and a copy button.
        String display = stripToolBlocks(response == null ? "" : response);
        if (streamingBubble != null) {
            // Replace the body with the rendered Markdown.
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
            boolean available = aiService.getLastError() == null;
            updateStatus(available);
            return;
        }
        if (replacement != null) {
            // User clicked "Stop" mid-stream: replace the partial
            // output with a marker.
            String current = streamingText.toString();
            onAssistantResponse(current + "\n\n_[" + replacement + "]_", false);
        } else {
            // Normal completion - if no onAssistantResponse arrived
            // (shouldn't happen but be safe), re-render the current
            // text via Markdown.
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
        boolean available = aiService.getLastError() == null;
        updateStatus(available);
    }

    private static String stripToolBlocks(String text) {
        if (text == null) return "";
        return text.replaceAll("```(?:tool|json)?\\s*\\n?\\{[\\s\\S]*?\\}\\s*\\n?```", "")
                .replaceAll("<tool\\s+[^/>]*/?>", "")
                .trim();
    }

    /**
     * Ask the user to confirm a destructive tool call.  Read-only tools
     * are auto-approved via the policy; this dialog only appears for
     * write/destructive tools.  Includes a "Remember my choice for
     * this tool" checkbox so the user does not have to re-approve the
     * same action 30 times in a row.
     */
    private boolean askPermissionForTool(AiTool tool, ToolCallParser.ToolCall call) {
        if (policy.canAutoApprove(tool)) return true;

        final boolean[] result = {false};
        Runnable prompt = () -> {
            String args = call.args == null || call.args.isEmpty()
                    ? "(no arguments)"
                    : call.args.toString();
            Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                    "Tool: " + tool.name() + "  (risk: " + tool.risk() + ")\n\n" +
                            "Arguments:\n" + args + "\n\nAllow this?",
                    ButtonType.CANCEL, ButtonType.OK);
            a.setTitle("AI agent wants to act");
            a.setHeaderText("Phiigrame AI is requesting permission");
            // "Always allow" checkbox
            CheckBox remember = new CheckBox("Always allow '" + tool.name() + "' this session");
            remember.setStyle("-fx-text-fill: #d4d4d4;");
            a.getDialogPane().setExpandableContent(remember);
            a.getDialogPane().setExpanded(true);
            a.showAndWait().ifPresent(bt -> {
                if (bt == ButtonType.OK) {
                    result[0] = true;
                    if (remember.isSelected()) {
                        policy.rememberAlwaysAllow(tool.name());
                    }
                }
            });
        };
        if (Platform.isFxApplicationThread()) prompt.run();
        else Platform.runLater(prompt);
        long deadline = System.currentTimeMillis() + 60_000L * 5; // 5min for human
        while (System.currentTimeMillis() < deadline) {
            try { Thread.sleep(50); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
            // result[0] is set on the FX thread; we observe it from here
            if (result[0]) break;
            // If the user closed the dialog without clicking OK,
            // result[0] stays false but the FX loop is idle - bail out
            // when the deadline is reached.
        }
        return result[0];
    }

    /**
     * Append a small "tool call" line to the chat so the user can see
     * what the agent did.  The bubble is intentionally tiny and
     * collapsed-by-default: a single label showing the tool name, args
     * (truncated), and outcome.
     */
    private void appendToolEvent(AiTool tool, ToolCallParser.ToolCall call, String status, String result) {
        if (tool == null || call == null) return;
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        String args = call.args == null || call.args.isEmpty()
                ? ""
                : " " + truncate(call.args.toString(), 60);
        String label = "\u2699 " + tool.name() + args + " \u2192 " + status;
        Label l = new Label(label);
        String color;
        switch (status) {
            case "ok": color = "#6a9955"; break;
            case "auto-approved": color = "#8b8b8b"; break;
            case "denied": color = "#f48771"; break;
            case "error": color = "#f48771"; break;
            default: color = "#d4d4d4";
        }
        l.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 10px; " +
                "-fx-font-family: 'JetBrains Mono', monospace;");
        row.getChildren().add(l);
        if (result != null && !result.isEmpty() && result.length() < 200) {
            Label r = new Label(" " + truncate(result.replace('\n', ' '), 60));
            r.setStyle("-fx-text-fill: #6a6a6a; -fx-font-size: 10px; " +
                    "-fx-font-family: 'JetBrains Mono', monospace;");
            row.getChildren().add(r);
        }
        messagesBox.getChildren().add(row);
        Platform.runLater(() -> messagesScroll.setVvalue(1.0));
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        String oneLine = s.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "...";
    }

    private void runQuickAction(String action) {
        if (busy) return;
        String prompt;
        switch (action) {
            case "Explain":  prompt = "Explain the selected code in 2-3 sentences."; break;
            case "Refactor": prompt = "Refactor the selected code to be cleaner. Return only the refactored code."; break;
            case "Document": prompt = "Generate Javadoc comments for the selected code. Return only the commented code."; break;
            case "Tests":    prompt = "Generate JUnit 5 unit tests for the selected code. Return only the test code."; break;
            case "Fix bugs": prompt = "Find and fix any bugs in the selected code. Return only the corrected code."; break;
            default: prompt = action;
        }
        inputArea.setText(prompt);
        sendMessage();
    }
}
