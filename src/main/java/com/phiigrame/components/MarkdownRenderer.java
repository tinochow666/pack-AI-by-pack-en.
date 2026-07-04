package com.phiigrame.components;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpans;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A small, dependency-free Markdown -> JavaFX renderer used by the
 * AI chat panel.  Produces a {@link VBox} containing the rendered
 * paragraphs and fenced code blocks.  Fenced code blocks are rendered
 * with real syntax highlighting (multiple {@link Text} nodes styled
 * by {@link SyntaxHighlighter}) and a small "copy" button.
 *
 * <p>Supported Markdown:
 * <ul>
 *   <li>Fenced code blocks {@code ```lang ... ```}</li>
 *   <li>Inline code {@code `code`}</li>
 *   <li>Bold {@code **text**} / {@code __text__}</li>
 *   <li>Italic {@code *text*} / {@code _text_}</li>
 *   <li>Headers {@code #}, {@code ##}, {@code ###}</li>
 *   <li>Bulleted and numbered lists</li>
 *   <li>Links {@code [text](url)}</li>
 * </ul>
 */
public final class MarkdownRenderer {

    private static final Pattern FENCE = Pattern.compile(
            "(?m)^```([a-zA-Z0-9_+-]*)\\s*\\n([\\s\\S]*?)\\n```\\s*$");

    private static final Pattern INLINE_CODE = Pattern.compile("`([^`\\n]+)`");
    private static final Pattern BOLD_ASTERISK = Pattern.compile("\\*\\*([^*\\n]+)\\*\\*");
    private static final Pattern BOLD_UNDERSCORE = Pattern.compile("__([^_\\n]+)__");
    private static final Pattern ITALIC_ASTERISK = Pattern.compile("(?<![*])\\*([^*\\n]+)\\*(?![*])");
    private static final Pattern ITALIC_UNDERSCORE = Pattern.compile("(?<![_])_([^_\\n]+)_(?![_])");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");
    private static final Pattern HEADER = Pattern.compile("^(#{1,3})\\s+(.+)$");
    private static final Pattern BULLET = Pattern.compile("^\\s*[-*]\\s+(.+)$");
    private static final Pattern NUMBERED = Pattern.compile("^\\s*\\d+\\.\\s+(.+)$");

    // Colors
    private static final String TEXT_COLOR     = "#d4d4d4";
    private static final String CODE_BG        = "#1e1e1e";
    private static final String INLINE_CODE_BG = "#2a2d2e";
    private static final String LINK_COLOR     = "#3574f0";
    private static final String BORDER_COLOR   = "#3c3f41";
    private static final String COMMENT_COLOR  = "#6a9955";
    private static final String KEYWORD_COLOR  = "#c586c0";
    private static final String STRING_COLOR   = "#ce9178";
    private static final String NUMBER_COLOR   = "#b5cea8";
    private static final String FUNCTION_COLOR = "#dcdcaa";
    private static final String TYPE_COLOR     = "#4ec9b0";
    private static final String TAG_COLOR      = "#569cd6";
    private static final String ATTR_COLOR     = "#9cdcfe";
    private static final String PROP_COLOR     = "#9cdcfe";

    private MarkdownRenderer() {}

    public static Node render(String markdown) {
        if (markdown == null) markdown = "";
        VBox root = new VBox();
        root.setStyle("-fx-background-color: transparent; -fx-padding: 4 2;");
        root.setSpacing(6);

        Matcher fm = FENCE.matcher(markdown);
        int idx = 0;
        while (fm.find()) {
            if (idx < fm.start()) {
                addParagraph(root, markdown.substring(idx, fm.start()));
            }
            String lang = fm.group(1) == null ? "" : fm.group(1).trim();
            String body = fm.group(2) == null ? "" : fm.group(2);
            addCodeBlock(root, lang, body);
            idx = fm.end();
        }
        if (idx < markdown.length()) {
            addParagraph(root, markdown.substring(idx));
        }
        return root;
    }

    // ---- internals ----------------------------------------------------------

    private static void addParagraph(VBox root, String text) {
        if (text == null) return;
        TextFlow flow = new TextFlow();
        flow.setLineSpacing(2);
        flow.setStyle("-fx-background-color: transparent;");
        String[] lines = text.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher h = HEADER.matcher(line);
            if (h.matches()) {
                addHeader(flow, h.group(1).length(), h.group(2));
            } else {
                Matcher b = BULLET.matcher(line);
                if (b.matches()) {
                    addBullet(flow, b.group(1));
                } else {
                    Matcher n = NUMBERED.matcher(line);
                    if (n.matches()) {
                        Text prefix = new Text("  1.  ");
                        prefix.setStyle("-fx-fill: " + TEXT_COLOR + ";");
                        flow.getChildren().add(prefix);
                        addInline(flow, n.group(1));
                    } else {
                        addInline(flow, line);
                    }
                }
            }
            if (i < lines.length - 1) flow.getChildren().add(new Text("\n"));
        }
        root.getChildren().add(flow);
    }

    private static void addHeader(TextFlow flow, int level, String text) {
        Text t = new Text(text.trim());
        switch (level) {
            case 1: t.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-fill: " + TEXT_COLOR + ";"); break;
            case 2: t.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-fill: " + TEXT_COLOR + ";"); break;
            default: t.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-fill: " + TEXT_COLOR + ";"); break;
        }
        t.setFont(Font.font("System", 12));
        flow.getChildren().add(t);
        flow.getChildren().add(new Text("\n"));
    }

    private static void addBullet(TextFlow flow, String text) {
        Text bullet = new Text("  \u2022  ");
        bullet.setStyle("-fx-fill: " + TEXT_COLOR + ";");
        flow.getChildren().add(bullet);
        addInline(flow, text);
    }

    private static void addInline(TextFlow flow, String text) {
        // Pass 1: split on inline code first.
        List<Node> nodes = new ArrayList<>();
        Matcher m = INLINE_CODE.matcher(text);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) nodes.addAll(richTextSpans(text.substring(last, m.start())));
            nodes.add(inlineCodeNode(m.group(1)));
            last = m.end();
        }
        if (last < text.length()) nodes.addAll(richTextSpans(text.substring(last)));
        if (nodes.isEmpty()) nodes.add(plainText(""));
        flow.getChildren().addAll(nodes);
    }

    private static List<Node> richTextSpans(String text) {
        List<Node> out = new ArrayList<>();
        String s = text;
        s = BOLD_ASTERISK.matcher(s).replaceAll("\u0001$1\u0001");
        s = BOLD_UNDERSCORE.matcher(s).replaceAll("\u0001$1\u0001");
        s = ITALIC_ASTERISK.matcher(s).replaceAll("\u0002$1\u0002");
        s = ITALIC_UNDERSCORE.matcher(s).replaceAll("\u0002$1\u0002");
        s = LINK.matcher(s).replaceAll("\u0003$1\u0004$2\u0004");

        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\u0001') {
                int end = s.indexOf('\u0001', i + 1);
                if (end < 0) { out.add(plainText(s.substring(i + 1))); break; }
                out.add(boldText(s.substring(i + 1, end)));
                i = end + 1;
            } else if (c == '\u0002') {
                int end = s.indexOf('\u0002', i + 1);
                if (end < 0) { out.add(plainText(s.substring(i + 1))); break; }
                out.add(italicText(s.substring(i + 1, end)));
                i = end + 1;
            } else if (c == '\u0003') {
                int sep = s.indexOf('\u0004', i + 1);
                int end = sep >= 0 ? s.indexOf('\u0004', sep + 1) : -1;
                if (sep < 0 || end < 0) { out.add(plainText(s.substring(i + 1))); break; }
                out.add(linkText(s.substring(i + 1, sep), s.substring(sep + 1, end)));
                i = end + 1;
            } else {
                int next = nextSpecial(s, i + 1);
                out.add(plainText(s.substring(i, next)));
                i = next;
            }
        }
        return out;
    }

    private static int nextSpecial(String s, int from) {
        for (int j = from; j < s.length(); j++) {
            char c = s.charAt(j);
            if (c == '\u0001' || c == '\u0002' || c == '\u0003') return j;
        }
        return s.length();
    }

    private static Text plainText(String s) {
        Text t = new Text(s);
        t.setStyle("-fx-fill: " + TEXT_COLOR + ";");
        t.setFont(Font.font("System", 12));
        return t;
    }

    private static Text boldText(String s) {
        Text t = new Text(s);
        t.setStyle("-fx-fill: " + TEXT_COLOR + "; -fx-font-weight: bold;");
        t.setFont(Font.font("System", 12));
        return t;
    }

    private static Text italicText(String s) {
        Text t = new Text(s);
        t.setStyle("-fx-fill: " + TEXT_COLOR + "; -fx-font-style: italic;");
        t.setFont(Font.font("System", 12));
        return t;
    }

    private static Text linkText(String label, String url) {
        Text t = new Text(label);
        t.setStyle("-fx-fill: " + LINK_COLOR + "; -fx-underline: true;");
        t.setFont(Font.font("System", 12));
        t.getStyleClass().add("md-link");
        javafx.scene.control.Tooltip tip = new javafx.scene.control.Tooltip(url);
        javafx.scene.control.Tooltip.install(t, tip);
        return t;
    }

    private static Text inlineCodeNode(String s) {
        Text t = new Text(" " + s + " ");
        t.setStyle("-fx-fill: #d7ba7d; -fx-font-family: 'JetBrains Mono','Consolas',monospace; " +
                "-fx-background-color: " + INLINE_CODE_BG + ";");
        t.setFont(Font.font("JetBrains Mono", 12));
        t.getStyleClass().add("md-inline-code");
        return t;
    }

    // ---------------------------------------------------------------- code

    private static void addCodeBlock(VBox root, String lang, String body) {
        VBox block = new VBox();
        block.getStyleClass().add("ai-code-block");
        block.setStyle("-fx-background-color: " + CODE_BG + "; -fx-background-radius: 4; " +
                "-fx-border-color: " + BORDER_COLOR + "; -fx-border-radius: 4; " +
                "-fx-border-width: 1;");
        block.setFillWidth(true);

        // Header with language label and copy button
        HBox header = new HBox();
        header.setStyle("-fx-background-color: #2d2d30; -fx-background-radius: 4 4 0 0; " +
                "-fx-padding: 4 10;");
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label langLabel = new Label(lang == null || lang.isEmpty() ? "code" : lang);
        langLabel.setStyle("-fx-text-fill: " + COMMENT_COLOR + "; -fx-font-style: italic; " +
                "-fx-font-size: 11px;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button copyBtn = new Button("Copy");
        copyBtn.setStyle("-fx-background-color: #3e3e42; -fx-text-fill: #cccccc; " +
                "-fx-background-radius: 3; -fx-padding: 2 10; -fx-cursor: hand; -fx-font-size: 10px;");
        copyBtn.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(body == null ? "" : body);
            Clipboard.getSystemClipboard().setContent(content);
            copyBtn.setText("Copied!");
            javafx.application.Platform.runLater(() -> {
                try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
                copyBtn.setText("Copy");
            });
        });
        header.getChildren().addAll(langLabel, spacer, copyBtn);

        // Code body with real syntax highlighting
        TextFlow code = buildHighlightedCode(body == null ? "" : body, lang);
        code.setStyle("-fx-padding: 8 12; -fx-background-color: " + CODE_BG + "; " +
                "-fx-background-radius: 0 0 4 4;");

        block.getChildren().addAll(header, code);
        root.getChildren().add(block);
    }

    private static TextFlow buildHighlightedCode(String body, String lang) {
        TextFlow flow = new TextFlow();
        flow.setLineSpacing(1);
        try {
            String language = lang == null || lang.isEmpty() ? "text" : lang;
            StyleSpans<Collection<String>> spans =
                    SyntaxHighlighter.computeHighlightingForLanguage(body, language);
            int pos = 0;
            for (StyleSpan<Collection<String>> span : spans) {
                int len = span.getLength();
                if (len <= 0) continue;
                int end = Math.min(body.length(), pos + len);
                if (end <= pos) continue;
                String piece = body.substring(pos, end);
                Text t = new Text(piece);
                t.setStyle(styleForSpan(span.getStyle()));
                t.setFont(Font.font("JetBrains Mono", 12));
                flow.getChildren().add(t);
                pos = end;
            }
        } catch (Throwable ignored) {
            // Fall back to a single monospaced text
            Text t = new Text(body);
            t.setStyle("-fx-fill: " + TEXT_COLOR + "; -fx-font-family: 'JetBrains Mono','Consolas',monospace;");
            flow.getChildren().add(t);
        }
        return flow;
    }

    private static String styleForSpan(Collection<String> classes) {
        String fill = TEXT_COLOR;
        String extra = "";
        if (classes.contains("keyword"))   { fill = KEYWORD_COLOR;  extra = " -fx-font-weight: bold;"; }
        if (classes.contains("string"))    { fill = STRING_COLOR;   }
        if (classes.contains("comment"))   { fill = COMMENT_COLOR;  extra = " -fx-font-style: italic;"; }
        if (classes.contains("number"))    { fill = NUMBER_COLOR;   }
        if (classes.contains("function"))  { fill = FUNCTION_COLOR; }
        if (classes.contains("type"))      { fill = TYPE_COLOR;     }
        if (classes.contains("annotation")){ fill = FUNCTION_COLOR; }
        if (classes.contains("tag"))       { fill = TAG_COLOR;      extra = " -fx-font-weight: bold;"; }
        if (classes.contains("attribute")) { fill = ATTR_COLOR;     }
        if (classes.contains("property"))  { fill = PROP_COLOR;     }
        if (classes.contains("builtin"))   { fill = FUNCTION_COLOR; }
        return "-fx-fill: " + fill + ";" + extra;
    }
}
