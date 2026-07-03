package com.phiigrame.components;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SyntaxHighlighter {
    
    private static final Pattern JAVA_KEYWORDS = Pattern.compile(
        "\\b(abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|" +
        "else|enum|extends|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|" +
        "long|native|new|null|package|private|protected|public|return|short|static|strictfp|super|switch|" +
        "synchronized|this|throw|throws|transient|try|void|volatile|while|true|false)\\b"
    );
    
    private static final Pattern KOTLIN_KEYWORDS = Pattern.compile(
        "\\b(abstract|annotation|as|break|by|catch|class|companion|const|constructor|continue|crossinline|" +
        "data|delegate|do|dynamic|else|enum|expect|external|false|field|file|final|finally|for|fun|" +
        "get|if|import|in|infix|init|inline|inner|interface|internal|is|lateinit|noinline|null|object|" +
        "open|operator|out|override|package|param|private|property|protected|public|reified|return|sealed|" +
        "set|internal|super|suspend|tailrec|this|throw|true|try|typealias|val|var|vararg|when|where|while)\\b"
    );
    
    private static final Pattern GROOVY_KEYWORDS = Pattern.compile(
        "\\b(as|assert|break|case|catch|class|const|continue|def|default|do|else|enum|extends|false|finally|" +
        "for|goto|if|implements|import|in|instanceof|interface|new|null|package|private|protected|public|" +
        "return|super|switch|this|throw|throws|true|try|void|while|abstract|final|static|volatile|transient|" +
        "synchronized|native|strictfp|boolean|byte|char|double|float|int|long|short)\\b"
    );
    
    private static final Pattern STRING_LITERAL = Pattern.compile(
        "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'"
    );
    
    private static final Pattern MULTI_LINE_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    
    private static final Pattern SINGLE_LINE_COMMENT = Pattern.compile("//.*$", Pattern.MULTILINE);
    
    private static final Pattern ANNOTATION = Pattern.compile("@\\w+");
    
    private static final Pattern NUMBER = Pattern.compile("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b");
    
    private static final Pattern TYPE = Pattern.compile(
        "\\b(String|Integer|Long|Double|Float|Boolean|Char|Byte|Short|Void|Object|" +
        "List|Map|Set|Collection|ArrayList|HashMap|LinkedList|HashSet|" +
        "StringBuilder|StringBuffer|Date|Calendar|File|Exception|RuntimeException|" +
        "Optional|Stream|LocalDate|LocalTime|LocalDateTime)\\b"
    );
    
    private static final Pattern FUNCTION_CALL = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(");
    
    public static StyleSpans<Collection<String>> computeHighlighting(String text, String fileName) {
        String language = getLanguageFromExtension(fileName);
        return computeHighlightingForLanguage(text, language);
    }
    
    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        return computeHighlightingForLanguage(text, "java");
    }
    
    private static StyleSpans<Collection<String>> computeHighlightingForLanguage(String text, String language) {
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        
        Pattern keywordPattern;
        switch (language.toLowerCase()) {
            case "kotlin":
                keywordPattern = KOTLIN_KEYWORDS;
                break;
            case "groovy":
                keywordPattern = GROOVY_KEYWORDS;
                break;
            default:
                keywordPattern = JAVA_KEYWORDS;
                break;
        }
        
        Pattern[] patterns = {
            MULTI_LINE_COMMENT,
            SINGLE_LINE_COMMENT,
            STRING_LITERAL,
            ANNOTATION,
            NUMBER,
            TYPE,
            FUNCTION_CALL,
            keywordPattern
        };
        
        String[] styleClasses = {
            "comment",
            "comment",
            "string",
            "annotation",
            "number",
            "type",
            "function",
            "keyword"
        };
        
        int[] startIndices = new int[patterns.length];
        int[] endIndices = new int[patterns.length];
        Matcher[] matchers = new Matcher[patterns.length];
        
        for (int i = 0; i < patterns.length; i++) {
            matchers[i] = patterns[i].matcher(text);
            if (matchers[i].find()) {
                startIndices[i] = matchers[i].start();
                endIndices[i] = matchers[i].end();
            } else {
                startIndices[i] = Integer.MAX_VALUE;
                endIndices[i] = Integer.MAX_VALUE;
            }
        }
        
        int lastPos = 0;
        
        while (true) {
            int minStart = Integer.MAX_VALUE;
            int minEnd = Integer.MAX_VALUE;
            int minIndex = -1;
            
            for (int i = 0; i < patterns.length; i++) {
                if (startIndices[i] < minStart) {
                    minStart = startIndices[i];
                    minEnd = endIndices[i];
                    minIndex = i;
                }
            }
            
            if (minIndex == -1 || minStart == Integer.MAX_VALUE) {
                break;
            }
            
            if (minStart > lastPos) {
                spansBuilder.add(Collections.emptyList(), minStart - lastPos);
            }
            
            spansBuilder.add(Collections.singleton(styleClasses[minIndex]), minEnd - minStart);
            
            lastPos = minEnd;
            
            if (matchers[minIndex].find()) {
                startIndices[minIndex] = matchers[minIndex].start();
                endIndices[minIndex] = matchers[minIndex].end();
            } else {
                startIndices[minIndex] = Integer.MAX_VALUE;
                endIndices[minIndex] = Integer.MAX_VALUE;
            }
        }
        
        if (lastPos < text.length()) {
            spansBuilder.add(Collections.emptyList(), text.length() - lastPos);
        }
        
        return spansBuilder.create();
    }
    
    public static String getLanguageFromExtension(String fileName) {
        if (fileName == null) return "java";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".kt") || lower.endsWith(".kts")) return "kotlin";
        if (lower.endsWith(".java")) return "java";
        if (lower.endsWith(".groovy")) return "groovy";
        if (lower.endsWith(".gradle") || lower.endsWith(".gradle.kts")) return "groovy";
        if (lower.endsWith(".xml")) return "xml";
        if (lower.endsWith(".properties")) return "properties";
        if (lower.endsWith(".bat") || lower.endsWith(".cmd")) return "batch";
        if (lower.endsWith(".sh")) return "shell";
        if (lower.endsWith(".py")) return "python";
        if (lower.endsWith(".js")) return "javascript";
        if (lower.endsWith(".ts")) return "typescript";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) return "yaml";
        if (lower.endsWith(".md")) return "markdown";
        if (lower.endsWith(".txt")) return "text";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "html";
        if (lower.endsWith(".css")) return "css";
        if (lower.endsWith(".sql")) return "sql";
        if (lower.endsWith(".cs")) return "csharp";
        if (lower.endsWith(".cpp") || lower.endsWith(".cc") || lower.endsWith(".cxx")) return "cpp";
        if (lower.endsWith(".c")) return "c";
        if (lower.endsWith(".h")) return "cpp";
        if (lower.endsWith(".rs")) return "rust";
        if (lower.endsWith(".go")) return "go";
        return "text";
    }
}