package com.phiigrame.components;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Per-language syntax highlighter.  Picks a language from the file
 * extension and applies a set of regex-based styles in a single sweep.
 *
 * <p>Supported languages: java, kotlin, groovy, javascript/typescript,
 * python, go, rust, c/cpp/csharp, json, yaml, markdown, sql, html, css,
 * shell, batch, properties, xml, text.
 *
 * <p>The styles map to the CSS classes {@code keyword}, {@code type},
 * {@code string}, {@code comment}, {@code number}, {@code annotation},
 * {@code function}, {@code operator}, {@code builtin}, {@code property},
 * {@code tag}, {@code attribute}.
 */
public class SyntaxHighlighter {

    // A pattern that never matches.  Used as a placeholder for language
    // slots that don't have any keywords / types / builtins (so we can
    // build Lang values uniformly).
    private static final Pattern NEVER = Pattern.compile("(?!)");

    // ---------------------------------------------------------------- base patterns
    // C-style comments + strings shared by Java/Kotlin/JS/TS/C/C++/Go/Rust/etc.
    private static final Pattern ML_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern SL_COMMENT = Pattern.compile("//.*$", Pattern.MULTILINE);
    private static final Pattern DQUOTE_STRING = Pattern.compile("\"([^\"\\\\\\n]|\\\\.)*\"");
    private static final Pattern SQUOTE_STRING = Pattern.compile("'([^'\\\\\\n]|\\\\\\\\.)*'");
    private static final Pattern BACKTICK_STRING = Pattern.compile("`([^`\\\\]|\\\\.)*`");
    private static final Pattern NUMBER = Pattern.compile("\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[fFdDlL]?\\b");
    private static final Pattern HEX = Pattern.compile("\\b0[xX][0-9a-fA-F]+\\b");

    // Python / shell / Ruby-style comments
    private static final Pattern HASH_COMMENT = Pattern.compile("#[^!].*$", Pattern.MULTILINE);

    // HTML-style tags / attributes
    private static final Pattern HTML_TAG = Pattern.compile("</?[A-Za-z][A-Za-z0-9-]*");
    private static final Pattern HTML_ATTR = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_-]*)(?=\\s*=)");

    // CSS
    private static final Pattern CSS_SELECTOR = Pattern.compile("^[^{}@]+(?=\\s*\\{)", Pattern.MULTILINE);
    private static final Pattern CSS_PROPERTY = Pattern.compile("\\b[a-z-]+(?=\\s*:)", Pattern.MULTILINE);
    private static final Pattern CSS_AT_RULE = Pattern.compile("^@[A-Za-z-]+", Pattern.MULTILINE);
    private static final Pattern CSS_HEX = Pattern.compile("#[0-9a-fA-F]{3,8}\\b");

    // Markdown
    private static final Pattern MD_HEADER = Pattern.compile("^(#{1,6})\\s+.*$", Pattern.MULTILINE);
    private static final Pattern MD_BOLD = Pattern.compile("\\*\\*[^*\\n]+\\*\\*");
    private static final Pattern MD_ITALIC = Pattern.compile("(?<![*])\\*[^*\\n]+\\*(?![*])");
    private static final Pattern MD_CODE = Pattern.compile("`[^`\\n]+`");
    private static final Pattern MD_LINK = Pattern.compile("\\[([^\\]]+)\\]\\([^)]+\\)");
    private static final Pattern MD_BULLET = Pattern.compile("^\\s*[-*+]\\s+", Pattern.MULTILINE);
    private static final Pattern MD_QUOTE = Pattern.compile("^>\\s.*$", Pattern.MULTILINE);

    // JSON / YAML punctuation
    private static final Pattern JSON_KEY = Pattern.compile("\"[^\"\\\\\\n]+\"(?=\\s*:)");

    // ----------------------------------------------------------------- per-language

    /** Everything the highlighter needs to know about a language. */
    private static final class Lang {
        final Pattern keywords;
        final Pattern types;
        final Pattern builtins;
        final Pattern annotation; // optional, may be null
        final boolean cStyleComments;
        final boolean hashComments;
        final boolean backtickStrings; // JS/TS template literals
        final boolean rawHtml;        // allow HTML tags embedded

        Lang(Pattern keywords, Pattern types, Pattern builtins, Pattern annotation,
             boolean cStyleComments, boolean hashComments,
             boolean backtickStrings, boolean rawHtml) {
            this.keywords = keywords;
            this.types = types;
            this.builtins = builtins;
            this.annotation = annotation;
            this.cStyleComments = cStyleComments;
            this.hashComments = hashComments;
            this.backtickStrings = backtickStrings;
            this.rawHtml = rawHtml;
        }
    }

    private static String kw(String... words) {
        StringBuilder sb = new StringBuilder("\\b(");
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append('|');
            sb.append(words[i]);
        }
        sb.append(")\\b");
        return sb.toString();
    }

    private static String regexOr(String... words) {
        return "\\b(" + String.join("|", words) + ")\\b";
    }

    private static final Lang JAVA = new Lang(
        Pattern.compile(kw("abstract","assert","boolean","break","byte","case","catch",
            "char","class","const","continue","default","do","double","else","enum",
            "extends","final","finally","float","for","goto","if","implements","import",
            "instanceof","int","interface","long","native","new","null","package","private",
            "protected","public","return","short","static","strictfp","super","switch",
            "synchronized","this","throw","throws","transient","try","void","volatile",
            "while","true","false","yield","record","sealed","non-sealed","var")),
        Pattern.compile(regexOr("String","Integer","Long","Double","Float","Boolean",
            "Char","Byte","Short","Void","Object","List","Map","Set","Collection",
            "ArrayList","HashMap","LinkedList","HashSet","StringBuilder","StringBuffer",
            "Date","Calendar","File","Exception","RuntimeException","Optional","Stream",
            "LocalDate","LocalTime","LocalDateTime","Duration","BigInteger","BigDecimal",
            "Path","Paths","Files","Pattern","Matcher","Thread","Runnable","Supplier",
            "Consumer","Function","Predicate")),
        Pattern.compile(regexOr("System","out","err","println","print","length","size",
            "isEmpty","contains","equals","hashCode","toString","valueOf","parseInt",
            "parseLong","parseDouble","get","set","add","remove","put","clear","map",
            "filter","collect","forEach","stream","toList","toArray","split","trim",
            "substring","toLowerCase","toUpperCase","startsWith","endsWith","charAt")),
        Pattern.compile("@[A-Za-z_][A-Za-z0-9_]*"),
        true, false, false, false
    );

    private static final Lang KOTLIN = new Lang(
        Pattern.compile(kw("abstract","actual","annotation","as","break","by","catch",
            "class","companion","const","constructor","continue","crossinline","data",
            "do","dynamic","else","enum","expect","external","false","final","finally",
            "for","fun","get","if","import","in","infix","init","inline","inner","interface",
            "internal","is","lateinit","noinline","null","object","open","operator","out",
            "override","package","private","protected","public","reified","return","sealed",
            "set","super","suspend","tailrec","this","throw","true","try","typealias",
            "val","var","vararg","when","where","while")),
        Pattern.compile(regexOr("String","Int","Long","Double","Float","Boolean","Char",
            "Byte","Short","Unit","Any","Nothing","List","Map","Set","MutableList",
            "ArrayList","HashMap","Array","Sequence","Pair","Triple","Result","Throwable",
            "Exception","RuntimeException","Optional")),
        Pattern.compile(regexOr("println","print","readLine","lazy","arrayOf","listOf",
            "mutableListOf","mapOf","mutableMapOf","setOf","mutableSetOf","let","run",
            "apply","also","with","to","until","step","downTo")),
        Pattern.compile("@[A-Za-z_][A-Za-z0-9_]*"),
        true, false, false, false
    );

    private static final Lang GROOVY = new Lang(
        Pattern.compile(kw("as","assert","break","case","catch","class","const","continue",
            "def","default","do","else","enum","extends","false","finally","for","goto",
            "if","implements","import","in","instanceof","interface","new","null","package",
            "private","protected","public","return","super","switch","this","throw","throws",
            "true","try","void","while","abstract","final","static","volatile","transient",
            "synchronized","native","strictfp","trait","yield")),
        Pattern.compile(regexOr("String","Integer","Long","Double","Float","Boolean",
            "Char","Byte","Short","Void","Object","List","Map","Set","Collection",
            "ArrayList","HashMap","LinkedList","HashSet")),
        Pattern.compile(regexOr("println","print","each","collect","findAll","find",
            "inject","sort","reverse","unique","flatten","groupBy","countBy","sum",
            "min","max","any","every")),
        Pattern.compile("@[A-Za-z_][A-Za-z0-9_]*"),
        true, false, false, false
    );

    private static final Lang JS_TS = new Lang(
        Pattern.compile(kw("abstract","as","async","await","break","case","catch","class",
            "const","continue","debugger","default","delete","do","else","enum","export",
            "extends","false","finally","for","from","function","get","if","implements",
            "import","in","instanceof","interface","is","keyof","let","new","null","of",
            "package","private","protected","public","readonly","return","set","static",
            "super","switch","this","throw","true","try","type","typeof","undefined",
            "var","void","while","with","yield","declare","namespace","module","require")),
        Pattern.compile(regexOr("string","number","boolean","any","unknown","never",
            "object","Array","Promise","Map","Set","WeakMap","WeakSet","Date","RegExp",
            "Error","Symbol","Iterator","Iterable","Partial","Readonly","Pick","Omit",
            "Record")),
        Pattern.compile(regexOr("console","log","warn","error","info","debug","length",
            "push","pop","shift","unshift","slice","splice","join","concat","map","filter",
            "reduce","forEach","find","findIndex","includes","indexOf","toString","parseInt",
            "parseFloat","JSON","Math","Object","Array","Number","String","Boolean",
            "document","window","setTimeout","setInterval","clearTimeout","clearInterval",
            "Promise","resolve","reject","then","catch","finally")),
        null,
        true, false, true, false
    );

    private static final Lang PYTHON = new Lang(
        Pattern.compile(kw("False","None","True","and","as","assert","async","await",
            "break","class","continue","def","del","elif","else","except","finally",
            "for","from","global","if","import","in","is","lambda","nonlocal","not",
            "or","pass","raise","return","try","while","with","yield","match","case")),
        Pattern.compile(regexOr("int","float","str","bool","bytes","list","tuple","dict",
            "set","frozenset","object","type","range","iter","map","filter","zip",
            "Optional","Any","Union","Callable","Iterable","Iterator","Generator",
            "Awaitable","Coroutine","TypeVar","Generic")),
        Pattern.compile(regexOr("print","len","abs","min","max","sum","sorted","reversed",
            "enumerate","zip","map","filter","all","any","open","input","isinstance",
            "issubclass","hasattr","getattr","setattr","delattr","repr","format",
            "super","self","cls","__init__","__name__","__main__","__str__","__repr__",
            "__len__","__getitem__","__setitem__","__iter__","__next__","__enter__",
            "__exit__","__call__","__eq__","__hash__","__bool__","__add__","__sub__",
            "__mul__","__truediv__","__contains__","True","False","None")),
        Pattern.compile("@[A-Za-z_][A-Za-z0-9_]*"),
        false, true, false, false
    );

    private static final Lang GO = new Lang(
        Pattern.compile(kw("break","case","chan","const","continue","default","defer",
            "else","fallthrough","for","func","go","goto","if","import","interface",
            "map","package","range","return","select","struct","switch","type","var",
            "true","false","nil","iota")),
        Pattern.compile(regexOr("string","int","int8","int16","int32","int64","uint",
            "uint8","uint16","uint32","uint64","byte","rune","float32","float64",
            "complex64","complex128","bool","error","any")),
        Pattern.compile(regexOr("make","new","len","cap","append","copy","delete","panic",
            "recover","close","print","println","sprintf","errorf","Sprint","Fprint",
            "Fprintln","Fprintf","Error","String","Format","MarshalJSON","UnmarshalJSON",
            "Marshal","Unmarshal")),
        null,
        true, false, true, false
    );

    private static final Lang RUST = new Lang(
        Pattern.compile(kw("as","async","await","break","const","continue","crate","dyn",
            "else","enum","extern","false","fn","for","if","impl","in","let","loop",
            "match","mod","move","mut","pub","ref","return","Self","self","static",
            "struct","super","trait","true","type","unsafe","use","where","while",
            "abstract","become","box","do","final","macro","override","priv","try",
            "typeof","unsized","virtual","yield")),
        Pattern.compile(regexOr("i8","i16","i32","i64","i128","isize","u8","u16","u32",
            "u64","u128","usize","f32","f64","bool","char","str","String","Vec","Option",
            "Result","Box","Rc","Arc","Cell","RefCell","HashMap","HashSet","BTreeMap",
            "BTreeSet","LinkedList","VecDeque","String","Cow","PhantomData")),
        Pattern.compile(regexOr("println","print","format","vec","panic","unimplemented",
            "unreachable","todo","include_str","include_bytes","env","cfg","dbg","assert",
            "assert_eq","assert_ne","ok","err","some","none","ok_or","ok_or_else",
            "unwrap","expect","clone","copy","drop","default","from","into","iter",
            "into_iter","iter_mut","collect","map","filter","fold","for_each","any","all",
            "find","position","skip","take","rev","chain","zip","enumerate","sum",
            "product","min","max","min_by","max_by","rev","sort","sort_by","dedup",
            "push","pop","insert","remove","len","is_empty","capacity","with_capacity",
            "as_str","as_bytes","as_ref","as_mut","to_string","to_owned","to_vec",
            "contains","starts_with","ends_with","trim","split","join","replace","parse",
            "read","write","flush","open","create","exists","metadata","read_to_string",
            "read_dir","File","OpenOptions","BufReader","BufWriter","Read","Write",
            "Seek","BufRead","Stdin","Stdout","Stderr","Command","args","env","current_dir",
            "set_current_dir","args_os","args","Command")),
        null,
        true, false, false, false
    );

    private static final Lang C_CPP = new Lang(
        Pattern.compile(kw("auto","break","case","catch","class","const","constexpr",
            "continue","decltype","default","delete","do","dynamic_cast","else",
            "explicit","export","extern","false","for","friend","goto","if","inline",
            "mutable","namespace","new","noexcept","nullptr","operator","private",
            "protected","public","register","reinterpret_cast","return","sizeof","static",
            "static_cast","struct","switch","template","this","throw","true","try",
            "typedef","typeid","typename","union","unsigned","using","virtual","void",
            "volatile","while","asm","typename","concept","requires","co_await","co_return",
            "co_yield","module","import","export","true")),
        Pattern.compile(regexOr("int","char","short","long","float","double","bool",
            "void","wchar_t","size_t","ssize_t","int8_t","int16_t","int32_t","int64_t",
            "uint8_t","uint16_t","uint32_t","uint64_t","string","vector","map","set",
            "unordered_map","unordered_set","array","deque","list","pair","tuple",
            "shared_ptr","unique_ptr","weak_ptr","function","atomic","mutex","thread",
            "string_view","optional","variant")),
        Pattern.compile(regexOr("printf","scanf","malloc","free","calloc","realloc",
            "memcpy","memset","strlen","strcpy","strcmp","strcat","fopen","fclose",
            "fread","fwrite","fprintf","stdout","stdin","stderr","std","cout","cin",
            "cerr","endl","getline","to_string","stoi","stol","stof","make_pair",
            "make_shared","make_unique","make_tuple","get","ifstream","ofstream",
            "stringstream","ostringstream","istringstream","ios","sync_with_stdio",
            "tie","tuple_size","tuple_element")),
        null,
        true, false, false, false
    );

    private static final Lang CSHARP = new Lang(
        Pattern.compile(kw("abstract","as","base","bool","break","byte","case","catch",
            "char","checked","class","const","continue","decimal","default","delegate",
            "do","double","else","enum","event","explicit","extern","false","finally",
            "fixed","float","for","foreach","goto","if","implicit","in","int","interface",
            "internal","is","lock","long","namespace","new","null","object","operator",
            "out","override","params","private","protected","public","readonly","ref",
            "return","sbyte","sealed","short","sizeof","stackalloc","static","string",
            "struct","switch","this","throw","true","try","typeof","uint","ulong",
            "unchecked","unsafe","ushort","using","var","virtual","void","volatile",
            "while","async","await","record","init","with")),
        Pattern.compile(regexOr("Int32","Int64","Int16","Byte","SByte","UInt16","UInt32",
            "UInt64","Single","Double","Decimal","Boolean","Char","String","Object",
            "DateTime","DateOnly","TimeOnly","TimeSpan","Guid","List","Dictionary",
            "HashSet","Queue","Stack","IEnumerable","IList","ICollection","IDictionary",
            "IReadOnlyList","IReadOnlyDictionary","Task","Nullable","Action","Func",
            "Exception")),
        Pattern.compile(regexOr("Console","WriteLine","Write","ReadLine","Read","Length",
            "Count","Add","Remove","Contains","Clear","ToString","ToArray","ToList",
            "Parse","TryParse","Convert","Math","Convert","StringBuilder","Path","File",
            "Directory","Stream","StreamReader","StreamWriter","HttpClient","JsonSerializer",
            "Task","Run","Wait","Result","ContinueWith","ConfigureAwait","GetAwaiter")),
        null,
        true, false, false, false
    );

    private static final Lang JSON = new Lang(
        Pattern.compile("\\b(true|false|null)\\b"),
        NEVER, NEVER, NEVER,
        false, false, false, false
    );

    private static final Lang YAML = new Lang(
        Pattern.compile("\\b(true|false|yes|no|null|on|off)\\b"),
        NEVER, NEVER, NEVER,
        false, false, false, false
    );

    private static final Lang SQL = new Lang(
        Pattern.compile(kw("select","from","where","group","by","having","order","limit",
            "offset","insert","into","values","update","set","delete","create","table",
            "index","view","drop","alter","add","column","primary","key","foreign",
            "references","join","inner","left","right","full","outer","on","as","and",
            "or","not","in","is","null","like","between","exists","any","all","case",
            "when","then","else","end","union","distinct","count","sum","avg","min","max",
            "with","recursive","returning","cascade","constraint","default","check",
            "unique","begin","commit","rollback","transaction","declare","cursor","fetch",
            "into","procedure","function","trigger","if","return","returns","varchar",
            "char","text","integer","bigint","smallint","numeric","decimal","real",
            "double","boolean","date","time","timestamp","interval","true","false")),
        Pattern.compile(regexOr("INT","INTEGER","VARCHAR","CHAR","TEXT","DATE","DATETIME",
            "TIMESTAMP","BOOLEAN","BOOL","FLOAT","DOUBLE","DECIMAL","NUMERIC","BIGINT",
            "SMALLINT","TINYINT","BLOB","CLOB","UUID","JSON","JSONB")),
        NEVER, NEVER,
        true, true, false, false
    );

    private static final Lang HTML = new Lang(
        NEVER, NEVER, NEVER, NEVER,
        false, false, false, true
    );

    private static final Lang CSS = new Lang(
        NEVER, NEVER, NEVER, NEVER,
        false, false, false, false
    );

    private static final Lang SHELL = new Lang(
        Pattern.compile(kw("if","then","else","elif","fi","for","while","do","done",
            "case","esac","in","function","return","break","continue","exit","export",
            "local","readonly","declare","set","unset","shift","source","trap","true",
            "false")),
        Pattern.compile(""),
        Pattern.compile(regexOr("echo","cd","ls","cat","grep","sed","awk","cp","mv",
            "rm","mkdir","rmdir","touch","chmod","chown","ps","kill","sudo","apt",
            "yum","brew","wget","curl","tar","zip","unzip","git","npm","yarn","pnpm",
            "python","node","java","javac","gradle","mvn","docker","kubectl","ssh",
            "scp","rsync","find","head","tail","less","more","wc","sort","uniq","tr",
            "cut","xargs","date","sleep","wait","pwd","whoami","hostname")),
        NEVER,
        false, true, false, false
    );

    private static final Lang BATCH = new Lang(
        Pattern.compile(kw("if","else","endif","for","in","do","goto","call","set",
            "setlocal","endlocal","echo","exit","rem","not","exist","errorlevel",
            "defined","equ","neq","lss","leq","gtr","geq","forfiles","choice","pause")),
        NEVER, NEVER, NEVER,
        false, true, false, false
    );

    private static final Lang MARKDOWN = new Lang(
        NEVER, NEVER, NEVER, NEVER,
        false, false, false, false
    );

    private static final Lang XML = new Lang(
        NEVER, NEVER, NEVER, NEVER,
        false, false, false, true
    );

    private static final Lang PROPERTIES = new Lang(
        NEVER, NEVER, NEVER, NEVER,
        false, true, false, false
    );

    private static final Lang TEXT = new Lang(
        NEVER, NEVER, NEVER, NEVER,
        false, false, false, false
    );

    // ---------------------------------------------------------------- public API

    public static StyleSpans<Collection<String>> computeHighlighting(String text, String fileName) {
        return computeHighlightingForLanguage(text, getLanguageFromExtension(fileName));
    }

    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        return computeHighlightingForLanguage(text, "java");
    }

    public static String getLanguageFromExtension(String fileName) {
        if (fileName == null) return "text";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".kt") || lower.endsWith(".kts")) return "kotlin";
        if (lower.endsWith(".java")) return "java";
        if (lower.endsWith(".groovy")) return "groovy";
        if (lower.endsWith(".gradle") || lower.endsWith(".gradle.kts")) return "groovy";
        if (lower.endsWith(".js") || lower.endsWith(".mjs") || lower.endsWith(".cjs") ||
            lower.endsWith(".jsx") || lower.endsWith(".tsx")) return "javascript";
        if (lower.endsWith(".ts")) return "typescript";
        if (lower.endsWith(".py") || lower.endsWith(".pyw")) return "python";
        if (lower.endsWith(".go")) return "go";
        if (lower.endsWith(".rs")) return "rust";
        if (lower.endsWith(".cs")) return "csharp";
        if (lower.endsWith(".c") || lower.endsWith(".h")) return "c";
        if (lower.endsWith(".cpp") || lower.endsWith(".cc") || lower.endsWith(".cxx") ||
            lower.endsWith(".hpp") || lower.endsWith(".hh")) return "cpp";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) return "yaml";
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return "markdown";
        if (lower.endsWith(".sql")) return "sql";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "html";
        if (lower.endsWith(".css")) return "css";
        if (lower.endsWith(".sh") || lower.endsWith(".bash") || lower.endsWith(".zsh")) return "shell";
        if (lower.endsWith(".bat") || lower.endsWith(".cmd")) return "batch";
        if (lower.endsWith(".xml")) return "xml";
        if (lower.endsWith(".properties") || lower.endsWith(".ini") || lower.endsWith(".toml")) return "properties";
        if (lower.endsWith(".txt") || lower.endsWith(".log")) return "text";
        return "text";
    }

    public static StyleSpans<Collection<String>> computeHighlightingForLanguage(String text, String language) {
        if (text == null || text.isEmpty()) {
            return new StyleSpansBuilder<Collection<String>>().create();
        }
        Lang lang = langFor(language);
        StyleSpansBuilder<Collection<String>> spans = new StyleSpansBuilder<>();
        // Build a list of (Pattern, [styleClasses]) pairs in priority order.
        // Earlier patterns win when spans overlap.
        List<StyleRule> rules = new ArrayList<>();
        addCommonRules(rules, lang);
        addLanguageRules(rules, lang);
        applyRules(text, spans, rules);
        return spans.create();
    }

    private static Lang langFor(String language) {
        if (language == null) return TEXT;
        switch (language.toLowerCase()) {
            case "kotlin":  return KOTLIN;
            case "groovy":  return GROOVY;
            case "javascript":
            case "typescript": return JS_TS;
            case "python":  return PYTHON;
            case "go":      return GO;
            case "rust":    return RUST;
            case "c":       return C_CPP;
            case "cpp":     return C_CPP;
            case "csharp":  return CSHARP;
            case "json":    return JSON;
            case "yaml":    return YAML;
            case "sql":     return SQL;
            case "html":    return HTML;
            case "css":     return CSS;
            case "shell":   return SHELL;
            case "batch":   return BATCH;
            case "markdown":return MARKDOWN;
            case "xml":     return XML;
            case "properties": return PROPERTIES;
            default:        return TEXT;
        }
    }

    private static void addCommonRules(List<StyleRule> rules, Lang lang) {
        if (lang.cStyleComments) {
            rules.add(new StyleRule(ML_COMMENT, Collections.singletonList("comment")));
            rules.add(new StyleRule(SL_COMMENT, Collections.singletonList("comment")));
        }
        if (lang.hashComments) {
            rules.add(new StyleRule(HASH_COMMENT, Collections.singletonList("comment")));
        }
        if (lang.rawHtml) {
            // HTML/XML mode: tag + attribute styling
            rules.add(new StyleRule(HTML_TAG, Collections.singletonList("tag")));
            rules.add(new StyleRule(HTML_ATTR, Collections.singletonList("attribute")));
        }
        // Strings - one or more flavours depending on language
        rules.add(new StyleRule(DQUOTE_STRING, Collections.singletonList("string")));
        if (languageHasSingleQuoteStrings(lang)) {
            rules.add(new StyleRule(SQUOTE_STRING, Collections.singletonList("string")));
        }
        if (lang.backtickStrings) {
            rules.add(new StyleRule(BACKTICK_STRING, Collections.singletonList("string")));
        }
        if (lang == CSS) {
            rules.add(new StyleRule(CSS_HEX, Collections.singletonList("number")));
        }
        rules.add(new StyleRule(NUMBER, Collections.singletonList("number")));
        rules.add(new StyleRule(HEX, Collections.singletonList("number")));
    }

    private static boolean languageHasSingleQuoteStrings(Lang lang) {
        return lang == JAVA || lang == KOTLIN || lang == GROOVY
                || lang == C_CPP || lang == CSHARP || lang == RUST
                || lang == SQL;
    }

    private static void addLanguageRules(List<StyleRule> rules, Lang lang) {
        if (lang == MARKDOWN) {
            rules.add(new StyleRule(MD_HEADER, Collections.singletonList("keyword")));
            rules.add(new StyleRule(MD_BOLD, Collections.singletonList("keyword")));
            rules.add(new StyleRule(MD_ITALIC, Collections.singletonList("type")));
            rules.add(new StyleRule(MD_CODE, Collections.singletonList("string")));
            rules.add(new StyleRule(MD_LINK, Collections.singletonList("function")));
            rules.add(new StyleRule(MD_BULLET, Collections.singletonList("operator")));
            rules.add(new StyleRule(MD_QUOTE, Collections.singletonList("comment")));
            return;
        }
        if (lang == CSS) {
            rules.add(new StyleRule(CSS_SELECTOR, Collections.singletonList("keyword")));
            rules.add(new StyleRule(CSS_PROPERTY, Collections.singletonList("type")));
            rules.add(new StyleRule(CSS_AT_RULE, Collections.singletonList("annotation")));
            return;
        }
        if (lang == JSON || lang == YAML) {
            if (lang == JSON) {
                rules.add(new StyleRule(JSON_KEY, Collections.singletonList("property")));
            } else {
                // YAML: treat `key:` as property
                rules.add(new StyleRule("^[\\s-]*[A-Za-z_][\\w.-]*(?=:)", Collections.singletonList("property")));
            }
            return;
        }
        if (lang == HTML || lang == XML) {
            // Already added tag / attribute rules; add a colour for values
            rules.add(new StyleRule("=\\s*\"[^\"]*\"", Collections.singletonList("string")));
            return;
        }
        if (lang == PROPERTIES) {
            rules.add(new StyleRule("^[\\s]*[A-Za-z0-9_.\\-]+(?=\\s*=)", Collections.singletonList("property")));
            return;
        }
        if (lang == SHELL) {
            // Variables like $foo, ${foo}
            rules.add(new StyleRule("\\$\\{?[A-Za-z_][A-Za-z0-9_]*\\}?", Collections.singletonList("builtin")));
            return;
        }
        if (lang == BATCH) {
            // %FOO% variables
            rules.add(new StyleRule("%[A-Za-z_][A-Za-z0-9_]*%", Collections.singletonList("builtin")));
            return;
        }
        if (lang == TEXT) {
            return;
        }
        if (lang.annotation != null) {
            rules.add(new StyleRule(lang.annotation, Collections.singletonList("annotation")));
        }
        if (lang.keywords != NEVER) {
            rules.add(new StyleRule(lang.keywords, Collections.singletonList("keyword")));
        }
        if (lang.types != NEVER) {
            rules.add(new StyleRule(lang.types, Collections.singletonList("type")));
        }
        if (lang.builtins != NEVER) {
            rules.add(new StyleRule(lang.builtins, Collections.singletonList("builtin")));
        }
        // Function-call style colouring for C-family languages
        if (lang == JAVA || lang == KOTLIN || lang == GROOVY || lang == JS_TS
                || lang == PYTHON || lang == C_CPP || lang == CSHARP || lang == RUST
                || lang == GO) {
            rules.add(new StyleRule("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(",
                    Collections.singletonList("function")));
        }
    }

    // ----------------------------------------------------------- span merge core

    private static final class StyleRule {
        final Pattern pattern;
        final Collection<String> styles;
        StyleRule(Pattern pattern, Collection<String> styles) {
            this.pattern = pattern;
            this.styles = styles;
        }
        StyleRule(String regex, Collection<String> styles) {
            this(Pattern.compile(regex), styles);
        }
    }

    /** Mutable, indexed match view used to walk all rules in parallel. */
    private static final class Cursor {
        int start;
        int end;
        Matcher matcher;
        StyleRule rule;
    }

    private static void applyRules(String text, StyleSpansBuilder<Collection<String>> out,
                                   List<StyleRule> rules) {
        Cursor[] cursors = new Cursor[rules.size()];
        for (int i = 0; i < rules.size(); i++) {
            Cursor c = new Cursor();
            c.rule = rules.get(i);
            c.matcher = rules.get(i).pattern.matcher(text);
            c.start = Integer.MAX_VALUE;
            c.end = Integer.MAX_VALUE;
            cursors[i] = c;
        }
        // Prime each cursor with its first match
        for (Cursor c : cursors) {
            if (c.matcher.find()) {
                c.start = c.matcher.start();
                c.end = c.matcher.end();
            }
        }
        int pos = 0;
        while (true) {
            int bestStart = Integer.MAX_VALUE;
            int bestEnd = Integer.MAX_VALUE;
            int bestIdx = -1;
            for (int i = 0; i < cursors.length; i++) {
                Cursor c = cursors[i];
                if (c.start < bestStart) {
                    bestStart = c.start;
                    bestEnd = c.end;
                    bestIdx = i;
                }
            }
            if (bestIdx < 0 || bestStart == Integer.MAX_VALUE) break;
            // If the next match starts before our current position, advance it
            if (bestStart < pos) {
                Cursor c = cursors[bestIdx];
                if (c.matcher.find()) {
                    c.start = c.matcher.start();
                    c.end = c.matcher.end();
                } else {
                    c.start = Integer.MAX_VALUE;
                    c.end = Integer.MAX_VALUE;
                }
                continue;
            }
            if (bestStart > pos) {
                out.add(Collections.emptyList(), bestStart - pos);
            }
            out.add(cursors[bestIdx].rule.styles, bestEnd - bestStart);
            pos = bestEnd;
            // advance winning cursor
            Cursor c = cursors[bestIdx];
            if (c.matcher.find()) {
                c.start = c.matcher.start();
                c.end = c.matcher.end();
            } else {
                c.start = Integer.MAX_VALUE;
                c.end = Integer.MAX_VALUE;
            }
        }
        if (pos < text.length()) {
            out.add(Collections.emptyList(), text.length() - pos);
        }
    }

    // Used by MarkdownRenderer / chat for a "tinted snippet" preview.
    public static Set<String> collectKeywords(String language) {
        Lang l = langFor(language);
        Set<String> out = new LinkedHashSet<>();
        if (l.keywords.pattern().length() > 2) {
            Matcher m = l.keywords.matcher("");
            // Pattern was built with \b(...|...|...)\b; reuse the inner word list
            String src = l.keywords.pattern();
            int a = src.indexOf('(');
            int b = src.lastIndexOf(')');
            if (a >= 0 && b > a) {
                out.addAll(Arrays.asList(src.substring(a + 1, b).split("\\|")));
            }
        }
        return out;
    }
}
