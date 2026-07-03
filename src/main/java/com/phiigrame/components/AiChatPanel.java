package com.phiigrame.components;

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
 * configured AI model. Stores conversation in AiHistoryService.
 *
 * <p>The editor works without a login, but starting a chat requires one
 * (the {@link AuthService} is consulted on every send).  The model can
 * also call a {@link ToolRegistry} of file tools while answering.
 */
public class AiChatPanel extends VBox {

    private final AiService aiService;
    private final AiHistoryService historyService;
    private final AuthService authService;
    private final ToolRegistry toolRegistry;

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

        // Login / current user button
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

        header.getChildren().addAll(headerLabel, loginButton, historyBtn, newChatBtn);

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

        // Messages area (rich-text markdown)
        messagesBox = new VBox(8);
        messagesBox.setPadding(new Insets(10, 12, 10, 12));
        messagesBox.setStyle("-fx-background-color: #1e1e1e;");
        messagesScroll = new ScrollPane(messagesBox);
        messagesScroll.setFitToWidth(true);
        messagesScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        messagesScroll.setStyle("-fx-background: #1e1e1e; -fx-background-color: #1e1e1e; " +
                "-fx-border-color: transparent;");
        VBox.setVgrow(messagesScroll, Priority.ALWAYS);

        // Quick actions
        HBox quickActions = new HBox(4);
        quickActions.setStyle("-fx-background-color: #2d2d2d; -fx-padding: 4 12;");
        for (String[] action : new String[][] {
            {"Explain", "Explain the selected code"},
            {"Refactor", "Refactor the selected code"},
            {"Document", "Generate documentation"},
            {"Tests", "Generate unit tests"}
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
        stopButton.setOnAction(e -> aiService.shutdown());
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

    /** Update header / input / send button to reflect the current login state. */
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
        appendBubble("system", "[system] " + text);
    }

    /**
     * Render a single message bubble.  Assistant messages are rendered as
     * Markdown, while user/system messages are plain text on a colored
     * background.  The bubble auto-scrolls into view.
     */
    private void appendBubble(String role, String content) {
        if (content == null) content = "";
        VBox bubble = new VBox(4);
        bubble.setFillWidth(true);
        String label;
        String bg;
        switch (role) {
            case "user":
                label = "You";
                bg = "#2d2d30";
                break;
            case "system":
                label = "system";
                bg = "#252526";
                break;
            default:
                label = "AI";
                bg = "#1e1e1e";
        }
        Label header = new Label(label);
        header.setStyle("-fx-text-fill: #8b8b8b; -fx-font-size: 10px; -fx-font-weight: bold;");
        bubble.getChildren().add(header);

        if ("user".equals(role) || "system".equals(role)) {
            Label body = new Label(content);
            body.setWrapText(true);
            body.setStyle("-fx-text-fill: #d4d4d4; -fx-font-size: 12px;");
            bubble.getChildren().add(body);
        } else {
            bubble.getChildren().add(MarkdownRenderer.render(content));
        }

        bubble.setPadding(new Insets(8, 10, 8, 10));
        bubble.setStyle("-fx-background-color: " + bg + "; -fx-background-radius: 6;");

        messagesBox.getChildren().add(bubble);
        // Auto-scroll to the bottom
        Platform.runLater(() -> messagesScroll.setVvalue(1.0));
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
            String label = s.title + "  -  " + historyService.formatTimestamp(s.lastModified);
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
        // don't re-enable the send button here if the user isn't logged in
        boolean loggedIn = authService != null && authService.isLoggedIn();
        sendButton.setDisable(b || !loggedIn);
        stopButton.setDisable(!b);
        inputArea.setDisable(b || !loggedIn);
    }

    private void sendMessage() {
        if (busy) return;

        // Hard-stop if not logged in: prompt the user to sign in.
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

        // Build history for context
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

        // Run with tool support if we have a registry; fall back to plain chat.
        if (toolRegistry != null && !toolRegistry.all().isEmpty()) {
            aiService.chatWithTools(text, history, toolRegistry,
                    new ToolCallParser.ToolCallback() {
                        @Override public boolean confirm(AiTool tool, ToolCallParser.ToolCall call) {
                            return askPermissionForTool(tool, call);
                        }
                        @Override public void onResult(AiTool tool, ToolCallParser.ToolCall call, String result) {
                            // Append a small log so the user can see what happened.
                            String line = "tool: " + call.name + " -> " + truncate(result, 160);
                            Platform.runLater(() -> appendBubble("system", line));
                        }
                    },
                    3, // up to 3 tool-using rounds per turn
                response -> onAssistantResponse(response, true),
                () -> Platform.runLater(() -> setBusy(false))
            );
        } else {
            aiService.chat(text, history,
                response -> onAssistantResponse(response, true),
                () -> Platform.runLater(() -> setBusy(false))
            );
        }
    }

    private void onAssistantResponse(String response, boolean save) {
        // Strip any ```tool ... ``` blocks from the displayed text.  The
        // tool log bubbles already tell the user what happened; the raw
        // JSON would be noisy.
        String display = stripToolBlocks(response);
        appendBubble("assistant", display);
        if (save) {
            historyService.addMessage(currentSessionId, "assistant", response);
        }
        refreshSessions();
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
     * (read_file, list_dir) are auto-approved because they make no changes.
     */
    private boolean askPermissionForTool(AiTool tool, ToolCallParser.ToolCall call) {
        String name = tool.name();
        // Auto-approve read-only tools
        if ("read_file".equals(name) || "list_dir".equals(name)) return true;

        // For everything else, build a modal confirmation on the FX thread.
        final boolean[] result = {false};
        Runnable prompt = () -> {
            String args = call.args == null || call.args.isEmpty()
                    ? "(no arguments)"
                    : call.args.toString();
            Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                    "The AI wants to call: " + name + "\n\nArguments:\n" + args +
                            "\n\nAllow this change?",
                    ButtonType.CANCEL, ButtonType.OK);
            a.setTitle("AI tool permission");
            a.setHeaderText("Phiigrame AI wants to modify your project");
            a.showAndWait().ifPresent(bt -> result[0] = (bt == ButtonType.OK));
        };
        if (Platform.isFxApplicationThread()) prompt.run();
        else Platform.runLater(prompt);
        // Block until the user answers.
        long deadline = System.currentTimeMillis() + 60_000L;
        while (System.currentTimeMillis() < deadline) {
            try { Thread.sleep(50); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
            if (result[0]) break;
        }
        return result[0];
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
            case "Explain": prompt = "Explain the selected code in 2-3 sentences."; break;
            case "Refactor": prompt = "Refactor the selected code to be cleaner. Return only the refactored code."; break;
            case "Document": prompt = "Generate Javadoc comments for the selected code. Return only the commented code."; break;
            case "Tests": prompt = "Generate JUnit 5 unit tests for the selected code. Return only the test code."; break;
            default: prompt = action;
        }
        inputArea.setText(prompt);
        sendMessage();
    }
}
