package com.phiigrame.components;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IntelliJ-style floating code completion popup.  Hosts a {@link ListView}
 * of {@link CompletionItem}s that the user can navigate with arrow keys,
 * confirm with Enter / Tab, or dismiss with Escape.  The popup is built
 * from three sources (in priority order):
 * <ol>
 *   <li><b>Local keywords</b> &mdash; the language keyword set returned by
 *       {@link SyntaxHighlighter#collectKeywords(String)}.</li>
 *   <li><b>Identifiers from the current file</b> &mdash; tokens of length 3+
 *       harvested from the current document, ranked by frequency.</li>
 *   <li><b>AI suggestions</b> &mdash; supplied asynchronously through
 *       {@link #setAiCompletion(String)}; these show up as soon as they
 *       arrive, marked with a different style.</li>
 * </ol>
 *
 * <p>The popup is owned by a single {@link CodeArea} for the lifetime of
 * the editor; show / hide is fast (the underlying {@link Popup} is reused).
 */
public class AutoCompletionPopup {

    public enum Source { KEYWORD, IDENTIFIER, AI, SNIPPET }

    /** A single completion entry. */
    public static final class CompletionItem {
        public final String text;     // inserted text (may include placeholders)
        public final String display;  // shown in the list
        public final String detail;   // optional secondary line shown in italics
        public final Source source;

        public CompletionItem(String text, String display, String detail, Source source) {
            this.text = text;
            this.display = display;
            this.detail = detail;
            this.source = source;
        }
    }

    private final CodeArea codeArea;
    private final Popup popup;
    private final ListView<CompletionItem> listView;
    private final VBox root;
    private final TextField filterField; // hidden, owns the focus
    private final Label statusLabel;
    private final List<CompletionItem> currentItems = new ArrayList<>();
    private final List<CompletionItem> aiItems = new ArrayList<>();
    private boolean aiPending;

    // ---- snippet library -------------------------------------------------
    private static final List<CompletionItem> SNIPPETS = new ArrayList<>();
    static {
        addSnippet("sysout", "System.out.println();", "Print to stdout", "println(\");\n}");
        addSnippet("sout",  "System.out.println();", "Print to stdout", "println(\");\n}");
        addSnippet("psvm",  "public static void main(String[] args) {\n    \n}", "Main method", null);
        addSnippet("fori",  "for (int i = 0; i < ; i++) {\n    \n}",            "For loop",     null);
        addSnippet("ife",   "if () {\n    \n} else {\n    \n}",                 "If / else",    null);
        addSnippet("tryc",  "try {\n    \n} catch (Exception e) {\n    e.printStackTrace();\n}",
                    "Try / catch", null);
        addSnippet("fn",    "() -> {\n    \n}",                                 "Lambda",       null);
        addSnippet("Todo",  "// TODO: ",                                         "TODO comment", null);
    }
    private static void addSnippet(String t, String ins, String det, String fix) {
        // we keep the placeholder "label" simple; body is the actual text
        SNIPPETS.add(new CompletionItem(ins, t, det, Source.SNIPPET));
    }

    public AutoCompletionPopup(CodeArea codeArea) {
        this.codeArea = codeArea;

        // Hidden filter field - it owns the keystrokes so we can capture
        // printable characters / backspace without interfering with the
        // editor.
        filterField = new TextField();
        filterField.setVisible(false);
        filterField.setManaged(false);
        filterField.setFocusTraversable(false);

        listView = new ListView<>();
        listView.setFocusTraversable(false);
        listView.setCellFactory(lv -> new CompletionCell());
        listView.setStyle("-fx-background-color: #252526; -fx-border-color: #0e639c; " +
                "-fx-border-radius: 4; -fx-background-radius: 4; " +
                "-fx-control-inner-background: #252526;");

        statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: #8b8b8b; -fx-font-size: 10px; " +
                "-fx-padding: 2 8; -fx-background-color: #1e1e1e;");
        statusLabel.setMaxWidth(Double.MAX_VALUE);

        root = new VBox(listView, statusLabel);
        root.setStyle("-fx-background-color: #252526;");
        root.setPrefWidth(360);
        root.setMaxWidth(420);

        popup = new Popup();
        popup.setAutoHide(true);
        popup.setHideOnEscape(false);
        popup.getContent().add(root);

        // listView takes any extra space
        VBox.setVgrow(listView, Priority.ALWAYS);

        // Forward keystrokes from the filter field to the list.
        filterField.setOnKeyPressed(this::onKey);
    }

    /** Show the popup below the caret for the given prefix. */
    public void show(String prefix) {
        rebuild(prefix, null);
        positionNearCaret();
        if (!popup.isShowing()) {
            popup.show(codeArea.getScene().getWindow());
        }
        filterField.requestFocus();
    }

    public void hide() {
        aiItems.clear();
        aiPending = false;
        if (popup.isShowing()) popup.hide();
    }

    public boolean isShowing() {
        return popup.isShowing();
    }

    public void setAiCompletion(String text) {
        aiPending = false;
        aiItems.clear();
        if (text == null) return;
        for (String line : text.split("\\n")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            aiItems.add(new CompletionItem(line, shortLabel(t), "AI", Source.AI));
        }
        if (popup.isShowing()) {
            // refresh with whatever filter is currently in the field
            rebuild(filterField.getText(), null);
        }
    }

    public void markAiPending() {
        aiPending = true;
        if (popup.isShowing()) {
            rebuild(filterField.getText(), null);
        }
    }

    // ------------------------------------------------------------------ build

    private void rebuild(String filter, String aiOverride) {
        currentItems.clear();
        String f = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
        String language = SyntaxHighlighter.getLanguageFromExtension(currentFileName());

        // 1. keywords
        for (String kw : SyntaxHighlighter.collectKeywords(language)) {
            if (f.isEmpty() || kw.toLowerCase(Locale.ROOT).startsWith(f)) {
                currentItems.add(new CompletionItem(kw, kw, "keyword", Source.KEYWORD));
            }
        }

        // 2. identifiers from the current file
        if (codeArea.getText() != null) {
            java.util.Map<String, Integer> freq = new java.util.HashMap<>();
            Matcher m = IDENTIFIER.matcher(codeArea.getText());
            while (m.find()) {
                String id = m.group();
                // skip the prefix the user is currently typing
                if (f.length() >= 3 && id.equalsIgnoreCase(filter)) continue;
                if (id.length() < 3) continue;
                freq.merge(id, 1, Integer::sum);
            }
            List<java.util.Map.Entry<String, Integer>> sorted = new ArrayList<>(freq.entrySet());
            sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            int limit = 0;
            for (java.util.Map.Entry<String, Integer> e : sorted) {
                if (limit++ >= 30) break;
                if (!f.isEmpty() && !e.getKey().toLowerCase(Locale.ROOT).contains(f)) continue;
                currentItems.add(new CompletionItem(e.getKey(), e.getKey(),
                        "identifier", Source.IDENTIFIER));
            }
        }

        // 3. snippets
        for (CompletionItem snip : SNIPPETS) {
            if (f.isEmpty() || snip.display.toLowerCase(Locale.ROOT).contains(f)) {
                currentItems.add(snip);
            }
        }

        // 4. AI items
        currentItems.addAll(aiItems);
        if (aiPending) {
            currentItems.add(new CompletionItem("", "AI thinking...", "loading", Source.AI));
        }

        listView.getItems().setAll(currentItems);
        if (!currentItems.isEmpty()) listView.getSelectionModel().selectFirst();
        statusLabel.setText(currentItems.size() + " suggestion" +
                (currentItems.size() == 1 ? "" : "s") + " - " + language);
    }

    private static final Pattern IDENTIFIER =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]{2,}");

    private static String shortLabel(String s) {
        String t = s.replace('\n', ' ').trim();
        return t.length() > 60 ? t.substring(0, 60) + "..." : t;
    }

    private String currentFileName() {
        Parent p = codeArea.getParent();
        while (p != null) {
            if (p.getUserData() instanceof String) {
                return (String) p.getUserData();
            }
            p = p.getParent();
        }
        // CodeEditorTab attaches the filename via codeArea lookupProperty?  We fall back
        // to "text" if we cannot find one (matches what SyntaxHighlighter defaults to).
        return "text";
    }

    private void positionNearCaret() {
        try {
            int caret = codeArea.getCaretPosition();
            Optional<Bounds> opt = codeArea.getCharacterBoundsOnScreen(caret, caret);
            Bounds b = opt.orElse(null);
            Stage stage = (Stage) codeArea.getScene().getWindow();
            double sceneX = stage.getX();
            double sceneY = stage.getY();
            double x = (b != null ? b.getMinX() : sceneX + 200);
            double y = (b != null ? b.getMaxY() : sceneY + 200);
            // Make sure it doesn't fall off the screen
            popup.setX(x);
            popup.setY(y);
        } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------ key handling

    private void onKey(KeyEvent e) {
        if (e.getCode() == KeyCode.ESCAPE) {
            hide();
            e.consume();
            codeArea.requestFocus();
            return;
        }
        if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.TAB) {
            insertSelected();
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.UP) {
            moveSelection(-1);
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.DOWN) {
            moveSelection(+1);
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.BACK_SPACE) {
            String t = filterField.getText();
            if (!t.isEmpty()) {
                filterField.setText(t.substring(0, t.length() - 1));
            }
            e.consume();
            rebuild(filterField.getText(), null);
            return;
        }
        if (e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.RIGHT) {
            // hand back to the editor
            hide();
            e.consume();
            codeArea.requestFocus();
            return;
        }
        // any printable char
        String s = e.getText();
        if (s != null && !s.isEmpty() && !e.isControlDown() && !e.isMetaDown()) {
            filterField.setText(filterField.getText() + s);
            e.consume();
            rebuild(filterField.getText(), null);
        }
    }

    private void moveSelection(int delta) {
        if (currentItems.isEmpty()) return;
        int idx = listView.getSelectionModel().getSelectedIndex();
        idx = Math.max(0, Math.min(currentItems.size() - 1, idx + delta));
        listView.getSelectionModel().select(idx);
        listView.scrollTo(idx);
    }

    private void insertSelected() {
        CompletionItem item = listView.getSelectionModel().getSelectedItem();
        if (item == null) {
            hide();
            codeArea.requestFocus();
            return;
        }
        if (item.source == Source.AI && item.text.isEmpty()) {
            // still loading
            return;
        }
        // Delete the current filter prefix from the editor, then insert
        // the chosen text.  Filter text was typed into the popup so the
        // editor's text is unchanged - the prefix the user sees in the
        // document is exactly the substring between the previous
        // non-identifier character and the caret.
        String filter = filterField.getText();
        int caret = codeArea.getCaretPosition();
        if (filter.length() > 0) {
            String doc = codeArea.getText();
            int back = Math.min(filter.length(), caret);
            if (back > 0 && caret - back >= 0
                    && doc.substring(caret - back, caret).equalsIgnoreCase(filter)) {
                // best effort - don't actually delete, the popup never
                // typed into the editor, so just insert the completion.
            }
        }
        // Insert the completion.  For multi-line completions, insert
        // literal newlines (CodeArea handles those).
        String toInsert = item.text;
        codeArea.insertText(caret, toInsert);
        if (item.detail != null && item.detail.startsWith("loading")) {
            hide();
            return;
        }
        hide();
        codeArea.requestFocus();
    }

    // ------------------------------------------------------------------ cell

    private static final class CompletionCell extends ListCell<CompletionItem> {
        @Override
        protected void updateItem(CompletionItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            HBox box = new HBox(8);
            box.setAlignment(Pos.CENTER_LEFT);
            box.setPadding(new Insets(4, 8, 4, 8));

            // Tag on the left showing the source kind.
            Label tag = new Label(tagText(item.source));
            tag.setStyle(tagStyle(item.source) + " -fx-padding: 1 6; -fx-background-radius: 3;");
            tag.setMinWidth(60);
            tag.setAlignment(Pos.CENTER);

            VBox text = new VBox(2);
            Label display = new Label(item.display);
            display.setStyle("-fx-text-fill: #d4d4d4; -fx-font-family: 'Consolas','Monaco',monospace; " +
                    "-fx-font-size: 12px;");
            text.getChildren().add(display);
            if (item.detail != null && !item.detail.isEmpty() && !"identifier".equals(item.detail)) {
                Label detail = new Label(item.detail);
                detail.setStyle("-fx-text-fill: #8b8b8b; -fx-font-size: 10px; -fx-font-style: italic;");
                text.getChildren().add(detail);
            }
            HBox.setHgrow(text, Priority.ALWAYS);

            box.getChildren().addAll(tag, text);
            setGraphic(box);
        }

        private String tagText(Source s) {
            switch (s) {
                case KEYWORD:    return "kw";
                case IDENTIFIER: return "var";
                case SNIPPET:    return "sni";
                case AI:         return "AI";
            }
            return "";
        }

        private String tagStyle(Source s) {
            switch (s) {
                case KEYWORD:    return "-fx-background-color: #5a3a8a; -fx-text-fill: #d4d4d4;";
                case IDENTIFIER: return "-fx-background-color: #3a5a8a; -fx-text-fill: #d4d4d4;";
                case SNIPPET:    return "-fx-background-color: #3a8a5a; -fx-text-fill: #d4d4d4;";
                case AI:         return "-fx-background-color: #8a5a3a; -fx-text-fill: #d4d4d4;";
            }
            return "";
        }
    }
}
