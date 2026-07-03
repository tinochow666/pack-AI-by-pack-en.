package com.phiigrame.components;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeEditorTab extends Tab {
    private final CodeArea codeArea;
    private final File file;
    private final BooleanProperty modified = new SimpleBooleanProperty(false);
    
    private Consumer<CompletionRequest> completionHandler;
    private PopupView currentPopup;
    
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
        
        // Ctrl+Space is handled by the parent PhiigrameApp via setOnKeyPressed
        // on the code area, so we do not register a duplicate here.
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
    
    public void applyCompletion(String text) {
        if (text == null || text.isEmpty()) return;
        codeArea.insertText(codeArea.getCaretPosition(), text);
    }
    
    public void showCompletionPopup(String text) {
        if (text == null || text.isEmpty()) return;
        if (currentPopup != null) {
            currentPopup.hide();
        }
        StackPane root = (StackPane) codeArea.getScene().getRoot();
        // We use a non-blocking overlay for the suggestion
        currentPopup = new PopupView(text);
        // Place a small floating label near the caret (simplified)
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
    
    /**
     * Simple floating popup view for showing completion suggestions.
     */
    private static class PopupView {
        private final Label label;
        private final javafx.scene.layout.StackPane container;
        
        public PopupView(String text) {
            label = new Label(truncate(text, 80));
            label.setStyle("-fx-background-color: #2d2d2d; -fx-text-fill: #d4d4d4; " +
                    "-fx-padding: 6 10; -fx-background-radius: 4; -fx-font-family: monospace; " +
                    "-fx-font-size: 11px; -fx-border-color: #0e639c; -fx-border-radius: 4;");
            container = new javafx.scene.layout.StackPane(label);
        }
        
        public void hide() {
            if (container.getParent() != null) {
                ((javafx.scene.layout.Pane) container.getParent()).getChildren().remove(container);
            }
        }
        
        private String truncate(String s, int n) {
            if (s == null) return "";
            s = s.replace("\n", " ").replace("\r", "");
            return s.length() > n ? s.substring(0, n) + "..." : s;
        }
    }
}

