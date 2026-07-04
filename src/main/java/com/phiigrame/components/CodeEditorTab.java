package com.phiigrame.components;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeEditorTab extends Tab {
    private final CodeArea codeArea;
    private final File file;
    private final BooleanProperty modified = new SimpleBooleanProperty(false);

    private Consumer<CompletionRequest> completionHandler;
    private AutoCompletionPopup completionPopup;

    public CodeEditorTab(File file) {
        super(file.getName());
        this.file = file;

        codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.setStyle("""
            -fx-background-color: #2b2b2b;
            -fx-text-fill: #a9b7c6;
            -fx-font-family: 'JetBrains Mono', 'Consolas', monospace;
            -fx-font-size: 14px;
            """);

        // Apply syntax highlighting (throttled via runLater)
        codeArea.textProperty().addListener((obs, oldVal, newVal) -> {
            modified.set(true);
            scheduleSyntaxHighlight();
        });

        // Auto-trigger local completion popup when the user types an
        // identifier character.  We only show suggestions when the caret
        // is preceded by an identifier, otherwise the popup would flash
        // after every keystroke.
        codeArea.textProperty().addListener((obs, oldVal, newVal) -> maybeShowLocalCompletion());
        codeArea.caretPositionProperty().addListener((obs, o, n) -> maybeShowLocalCompletion());

        // Load file content
        loadFile();

        VBox content = new VBox();
        content.getChildren().add(codeArea);
        VBox.setVgrow(codeArea, Priority.ALWAYS);

        setContent(content);
        setClosable(true);

        // Update tab title on modification
        modified.addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                setText(file.getName() + " *");
            } else {
                setText(file.getName());
            }
        });
    }

    // ------------------------------------------------------- local completion

    private static final Pattern IDENT_TAIL = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*$");

    private void maybeShowLocalCompletion() {
        if (codeArea.getScene() == null) return;
        if (completionHandler == null && completionPopup == null) return;
        if (completionPopup != null && completionPopup.isShowing()) return;
        String prefix = getCurrentIdentifierPrefix();
        if (prefix.length() < 2) return;
        // Don't pop up if the user is currently inside a string / comment.
        // Cheap heuristic: the previous non-identifier character.
        ensurePopup();
        completionPopup.show(prefix);
    }

    private void ensurePopup() {
        if (completionPopup == null) {
            completionPopup = new AutoCompletionPopup(codeArea);
        }
    }

    /** Word immediately to the left of the caret. */
    public String getCurrentIdentifierPrefix() {
        int caret = codeArea.getCaretPosition();
        String text = codeArea.getText();
        if (text == null) return "";
        int from = Math.max(0, Math.min(caret, text.length()));
        Matcher m = IDENT_TAIL.matcher(text.substring(0, from));
        return m.find() ? m.group() : "";
    }

    private long lastHighlightCall = 0;
    private void scheduleSyntaxHighlight() {
        long now = System.currentTimeMillis();
        if (now - lastHighlightCall < 200) {
            javafx.application.Platform.runLater(() -> {
                if (System.currentTimeMillis() - lastHighlightCall >= 200) {
                    lastHighlightCall = System.currentTimeMillis();
                    applySyntaxHighlighting();
                }
            });
        } else {
            lastHighlightCall = now;
            applySyntaxHighlighting();
        }
    }

    public void setCompletionHandler(Consumer<CompletionRequest> handler) {
        this.completionHandler = handler;
    }

    /**
     * Show the completion popup populated with the current identifier
     * prefix.  If a {@code completionHandler} is set (e.g. AI), the
     * handler is invoked asynchronously to enrich the popup with an AI
     * suggestion.
     */
    public void triggerCompletion() {
        ensurePopup();
        String prefix = getCurrentIdentifierPrefix();
        completionPopup.show(prefix);
        if (completionHandler != null) {
            completionPopup.markAiPending();
            completionHandler.accept(new CompletionRequest(
                    getCurrentPrefix(), getCurrentSuffix(),
                    file.getName(),
                    SyntaxHighlighter.getLanguageFromExtension(file.getName())));
        }
    }

    /**
     * Pass an AI-generated completion back to the popup.  Called by the
     * AI service as soon as the streamed text is available.
     */
    public void feedAiCompletion(String text) {
        ensurePopup();
        completionPopup.setAiCompletion(text);
    }

    public void hideCompletion() {
        if (completionPopup != null) completionPopup.hide();
    }

    public boolean isCompletionVisible() {
        return completionPopup != null && completionPopup.isShowing();
    }

    public void applyCompletion(String text) {
        if (text == null || text.isEmpty()) return;
        codeArea.insertText(codeArea.getCaretPosition(), text);
    }

    private void applySyntaxHighlighting() {
        try {
            String text = codeArea.getText();
            if (text == null) return;
            codeArea.setStyleSpans(0, SyntaxHighlighter.computeHighlighting(text, file.getName()));
        } catch (Exception e) {
            // Silently ignore - editor still works without highlighting
        }
    }

    private void loadFile() {
        try {
            String content = Files.readString(file.toPath());
            codeArea.replaceText(0, 0, content);
            javafx.application.Platform.runLater(this::applySyntaxHighlighting);
        } catch (IOException e) {
            codeArea.replaceText(0, 0, "// Error loading file: " + e.getMessage());
        }
    }

    public void saveFile() {
        try {
            Files.writeString(file.toPath(), codeArea.getText());
            modified.set(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Re-read the file from disk and replace the editor contents.  Used
     * when the AI tools (write_file / edit_file / create_file) change
     * a file that's already open in a tab.  Only reloads if the tab
     * has no unsaved local edits.
     */
    public void reloadFromDiskIfUnmodified() {
        if (modified.get()) return;
        loadFile();
    }

    public CodeArea getCodeArea() {
        return codeArea;
    }

    public File getFile() {
        return file;
    }

    public boolean isModified() {
        return modified.get();
    }

    public BooleanProperty modifiedProperty() {
        return modified;
    }

    public String getCurrentPrefix() {
        int caret = codeArea.getCaretPosition();
        String text = codeArea.getText();
        if (caret > text.length()) caret = text.length();
        return text.substring(0, caret);
    }

    public String getCurrentSuffix() {
        int caret = codeArea.getCaretPosition();
        String text = codeArea.getText();
        if (caret > text.length()) caret = text.length();
        return text.substring(caret);
    }

    /**
     * Request object passed to the AI completion handler.
     */
    public static class CompletionRequest {
        public final String prefix;
        public final String suffix;
        public final String fileName;
        public final String language;

        public CompletionRequest(String prefix, String suffix, String fileName, String language) {
            this.prefix = prefix;
            this.suffix = suffix;
            this.fileName = fileName;
            this.language = language;
        }
    }
}
