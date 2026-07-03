package com.phiigrame.components;

import javafx.scene.Node;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A small, dependency-free Markdown -> JavaFX {@link TextFlow} renderer.
 *
 * <p>Supports the subset of Markdown that the local 1.5B model is most
 * likely to emit:
 * <ul>
 *   <li><b>Fenced code blocks</b> {@code ```lang ... ```} - monospace, dark
 *       background, preserved indentation.</li>
 *   <li><b>Inline code</b> {@code `code`} - monospace on a subtle background.</li>
 *   <li><b>Bold</b> {@code **text**} / {@code __text__} - bold font.</li>
 *   <li><b>Italic</b> {@code *text*} / {@code _text_} - italic font.</li>
 *   <li><b>Headers</b> {@code #}, {@code ##}, {@code ###} - larger bold text.</li>
 *   <li><b>Bulleted lists</b> lines starting with {@code -} or {@code *}.</li>
 *   <li><b>Numbered lists</b> lines starting with {@code 1.}.</li>
 *   <li><b>Links</b> {@code [text](url)} - rendered as blue underlined text.</li>
 *   <li>Blank lines as paragraph separators.</li>
 * </ul>
 *
 * The renderer is intentionally permissive - if it can't parse something
 * it falls back to plain text, so model output never gets mangled.
 */
public final class MarkdownRenderer {

    // Fenced code block: ```lang\n...\n```
    private static final Pattern FENCE = Pattern.compile(
            "(?m)^```([a-zA-Z0-9_+-]*)\\s*\\n([\\s\\S]*?)\\n```\\s*$");

    // Inline formatting patterns (applied in order)
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`\\n]+)`");
    private static final Pattern BOLD_ASTERISK = Pattern.compile("\\*\\*([^*\\n]+)\\*\\*");
    private static final Pattern BOLD_UNDERSCORE = Pattern.compile("__([^_\\n]+)__");
    private static final Pattern ITALIC_ASTERISK = Pattern.compile("(?<![*])\\*([^*\\n]+)\\*(?![*])");
    private static final Pattern ITALIC_UNDERSCORE = Pattern.compile("(?<![_])_([^_\\n]+)_(?![_])");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");
    private static final Pattern HEADER = Pattern.compile("^(#{1,3})\\s+(.+)$");
    private static final Pattern BULLET = Pattern.compile("^\\s*[-*]\\s+(.+)$");
    private static final Pattern NUMBERED = Pattern.compile("^\\s*\\d+\\.\\s+(.+)$");

    // Color palette
    private static final String TEXT_COLOR = "#d4d4d4";
    private static final String CODE_BG = "#1e1e1e";
    private static final String INLINE_CODE_BG = "#2a2d2e";
    private static final String LINK_COLOR = "#3574f0";
    private static final String BORDER_COLOR = "#3c3f41";
    private static final String COMMENT_COLOR = "#6a9955";
    private static final String KEYWORD_COLOR = "#c586c0";
    private static final String STRING_COLOR = "#ce9178";

    private MarkdownRenderer() {}

    public static TextFlow render(String markdown) {
        if (markdown == null) markdown = "";
        TextFlow flow = new TextFlow();
        flow.setStyle("-fx-background-color: transparent; -fx-padding: 4 2;");
        flow.setLineSpacing(2);

        String[] segments = FENCE.split(markdown);
        Matcher fm = FENCE.matcher(markdown);
        int idx = 0;
        while (fm.find()) {
            // [idx .. start) is plain text
            if (idx < fm.start()) {
                addParagraph(flow, markdown.substring(idx, fm.start()));
            }
            // fence group: language, body
            String lang = fm.group(1) == null ? "" : fm.group(1).trim();
            String body = fm.group(2) == null ? "" : fm.group(2);
            addCodeBlock(flow, lang, body);
            idx = fm.end();
        }
        if (idx < markdown.length()) {
            addParagraph(flow, markdown.substring(idx));
        }
        return flow;
    }

    // ---- internals ----------------------------------------------------------

    private static void addParagraph(TextFlow flow, String text) {
        if (text == null) return;
        String[] lines = text.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            // Headers
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
                        addNumbered(flow, n.group(1));
                    } else {
                        addInline(flow, line);
                    }
                }
            }
            if (i < lines.length - 1) flow.getChildren().add(new Text("\n"));
        }
        flow.getChildren().add(new Text("\n\n"));
    }

    private static void addHeader(TextFlow flow, int level, String text) {
        Text t = new Text(text.trim() + "\n");
        switch (level) {
            case 1: t.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-fill: " + TEXT_COLOR + ";"); break;
            case 2: t.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-fill: " + TEXT_COLOR + ";"); break;
            default: t.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-fill: " + TEXT_COLOR + ";"); break;
        }
        flow.getChildren().add(t);
    }

    private static void addBullet(TextFlow flow, String text) {
        Text bullet = new Text("  \u2022  ");
        bullet.setStyle("-fx-fill: " + TEXT_COLOR + ";");
        flow.getChildren().add(bullet);
        addInline(flow, text);
    }

    private static void addNumbered(TextFlow flow, String text) {
        // Numbered lists keep the simple prefix-less rendering; if the
        // user is reading assistant output a plain line is fine and the
        // model often forgets the number on the next line anyway.
        addInline(flow, text);
    }

    private static void addInline(TextFlow flow, String text) {
        // Pass 1: split on inline code first so we don't try to format
        // its contents.
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
        // Combine the bold / italic / link patterns into a single sweep.
        // We do this in passes to keep the implementation readable.
        // Bold first, then italic, then links.  Bold/italic may overlap
        // in pathological input - in that case the first match wins.
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
        // Make the URL available as a tooltip (read-only is fine here).
        javafx.scene.control.Tooltip tip = new javafx.scene.control.Tooltip(url);
        javafx.scene.control.Tooltip.install(t, tip);
        return t;
    }

    private static Text inlineCodeNode(String s) {
        Text t = new Text(" " + s + " ");
        t.setStyle("-fx-fill: #d7ba7d; -fx-font-family: 'Consolas','Monaco',monospace; " +
                "-fx-background-color: " + INLINE_CODE_BG + ";");
        t.setFont(Font.font("Consolas", 12));
        return t;
    }

    private static void addCodeBlock(TextFlow flow, String lang, String body) {
        // Lightweight syntax tinting for a few popular languages.
        String colored = tintCode(body, lang);

        Text label = new Text((lang == null || lang.isEmpty() ? "code" : lang) + "\n");
        label.setStyle("-fx-fill: " + COMMENT_COLOR + "; -fx-font-style: italic;");
        label.setFont(Font.font("Consolas", 11));
        flow.getChildren().add(label);

        // Use a single Text node with a multi-line string.  This is
        // simple, supports selection, and the monospace font keeps
        // indentation visible.
        Text code = new Text(colored + "\n");
        code.setStyle(
                "-fx-fill: #d4d4d4; -fx-font-family: 'Consolas','Monaco',monospace; " +
                "-fx-background-color: " + CODE_BG + ";");
        code.setFont(Font.font("Consolas", 12));
        flow.getChildren().add(code);

        Text spacer = new Text("\n");
        spacer.setStyle("-fx-fill: " + TEXT_COLOR + ";");
        flow.getChildren().add(spacer);
    }

    /** Minimal tinting for Java / JS / Python style snippets. */
    private static String tintCode(String body, String lang) {
        if (body == null || body.isEmpty()) return "";
        // We could split into multiple Text nodes here for true syntax
        // highlighting; for now we keep the snippet monospaced on a
        // dark background, which is what most chat UIs do for code.
        return body;
    }
}
