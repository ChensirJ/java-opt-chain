package io.github.chensirj.optchain.plugin;

import io.github.chensirj.optchain.OptChain;
import io.github.chensirj.optchain.OptChainOps;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Source-level preprocessor: wraps each {@code ?.} receiver in {@link OptChainOps#opt}
 * (so {@code a?.b} becomes {@code OptChainOps.opt(a).b}) outside comments and literals,
 * and marks methods that contain a marker with {@code @OptChain(precise = true)}.
 */
public final class OptChainSourceTransformer {

    private static final String IMPORT_LINE = "import " + OptChain.class.getName() + ";";
    /** Fully qualified call inserted in place of each {@code ?.} receiver. */
    static final String OPT_INVOKE = OptChainOps.class.getName() + ".opt(";
    private static final String ANNOTATION_NAME = "@OptChain";
    private static final String ANNOTATION = "@OptChain(precise = true)";

    private OptChainSourceTransformer() {
    }

    public static final class Result {
        private final String source;
        private final boolean changed;
        private final int replacements;

        public Result(String source, boolean changed, int replacements) {
            this.source = source;
            this.changed = changed;
            this.replacements = replacements;
        }

        public String source() {
            return source;
        }

        public boolean changed() {
            return changed;
        }

        public int replacements() {
            return replacements;
        }
    }

    public static Result transform(String input) {
        if (findOptChainPositions(input).isEmpty()) {
            return new Result(input, false, 0);
        }
        WrapResult wrapped = wrapOptChainReceivers(input);
        String annotated = ensureOptChainOnMethods(wrapped.source);
        annotated = ensureImport(annotated);
        return new Result(annotated, true, wrapped.replacements);
    }

    /**
     * Leftmost {@code ?.} first. The receiver is the postfix expression immediately before the
     * operator, so {@code a?.b(c?.d())} becomes {@code opt(a).b(opt(c).d())} and argument
     * expressions keep their source order.
     */
    private static WrapResult wrapOptChainReceivers(String src) {
        int replacements = 0;
        int guard = 0;
        while (guard++ <= src.length() + 8) {
            List<Integer> positions = findOptChainPositions(src);
            if (positions.isEmpty()) {
                return new WrapResult(src, replacements);
            }
            int question = positions.get(0);
            boolean[] code = codeMask(src);
            int start = findReceiverStart(src, code, question);
            if (start < 0 || start >= question || src.substring(start, question).isBlank()) {
                src = src.substring(0, question) + src.substring(question + 1);
                replacements++;
                continue;
            }
            String receiver = src.substring(start, question);
            src = src.substring(0, start) + OPT_INVOKE + receiver + ")." + src.substring(question + 2);
            replacements++;
        }
        throw new IllegalStateException("opt-chain preprocess did not finish rewriting ?.");
    }

    /** Positions of '?' in each {@code ?.} that is outside comments and string/char literals. */
    public static List<Integer> findOptChainPositions(String src) {
        boolean[] code = codeMask(src);
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i + 1 < src.length(); i++) {
            if (code[i] && code[i + 1] && src.charAt(i) == '?' && src.charAt(i + 1) == '.') {
                positions.add(i);
                i++;
            }
        }
        return positions;
    }

    /** {@code true} for characters that are not inside a comment or a string/char literal. */
    static boolean[] codeMask(String src) {
        boolean[] code = new boolean[src.length()];
        ScanState state = ScanState.CODE;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            char next = i + 1 < src.length() ? src.charAt(i + 1) : '\0';
            switch (state) {
                case CODE:
                    if (c == '/' && next == '/') {
                        state = ScanState.LINE_COMMENT;
                        i++;
                    } else if (c == '/' && next == '*') {
                        state = ScanState.BLOCK_COMMENT;
                        i++;
                    } else if (c == '"') {
                        state = ScanState.STRING;
                    } else if (c == '\'') {
                        state = ScanState.CHAR;
                    } else {
                        code[i] = true;
                    }
                    break;
                case LINE_COMMENT:
                    if (c == '\n') {
                        state = ScanState.CODE;
                    }
                    break;
                case BLOCK_COMMENT:
                    if (c == '*' && next == '/') {
                        state = ScanState.CODE;
                        i++;
                    }
                    break;
                case STRING:
                    if (c == '\\' && next != '\0') {
                        i++;
                    } else if (c == '"') {
                        state = ScanState.CODE;
                    }
                    break;
                case CHAR:
                    if (c == '\\' && next != '\0') {
                        i++;
                    } else if (c == '\'') {
                        state = ScanState.CODE;
                    }
                    break;
                default:
                    break;
            }
        }
        return code;
    }

    private static int findReceiverStart(String src, boolean[] code, int questionPos) {
        int end = skipNonCodeBackward(src, code, questionPos - 1);
        if (end < 0) {
            return -1;
        }
        int start = consumePostfixBackward(src, code, end);
        int before = skipNonCodeBackward(src, code, start - 1);
        if (before >= 0 && endsWithKeyword(src, before, "new")) {
            start = before - "new".length() + 1;
        }
        return start;
    }

    /**
     * Walks backward from the last character of a postfix expression
     * ({@code name}, {@code name(...)}, {@code name[...]}, chains of {@code .} / {@code ?.},
     * parenthesized expressions, and generic {@code name<...>}).
     */
    private static int consumePostfixBackward(String src, boolean[] code, int endInclusive) {
        int i = endInclusive;
        int start = endInclusive;
        while (i >= 0) {
            char c = src.charAt(i);
            if (c == ')' || c == ']') {
                char openCh = c == ')' ? '(' : '[';
                int open = findMatchingOpenBackward(src, code, i, openCh, c);
                if (open < 0) {
                    return start;
                }
                start = open;
                i = skipNonCodeBackward(src, code, open - 1);
                continue;
            }
            if (c == '>') {
                if (i > 0 && src.charAt(i - 1) == '-') {
                    return start;
                }
                int angle = findMatchingAngleBackward(src, code, i);
                if (angle < 0) {
                    return start;
                }
                start = angle;
                i = skipNonCodeBackward(src, code, angle - 1);
                continue;
            }
            if (isIdentPart(c)) {
                while (i >= 0 && code[i] && isIdentPart(src.charAt(i))) {
                    i--;
                }
                start = i + 1;
                int before = skipNonCodeBackward(src, code, i);
                if (before >= 0 && src.charAt(before) == '.') {
                    int preDot = skipNonCodeBackward(src, code, before - 1);
                    if (preDot >= 0 && src.charAt(preDot) == '?') {
                        i = skipNonCodeBackward(src, code, preDot - 1);
                    } else {
                        i = skipNonCodeBackward(src, code, before - 1);
                    }
                    continue;
                }
                return start;
            }
            return start;
        }
        return Math.max(start, 0);
    }

    private static int skipNonCodeBackward(String src, boolean[] code, int i) {
        while (i >= 0 && (!code[i] || Character.isWhitespace(src.charAt(i)))) {
            i--;
        }
        return i;
    }

    private static int findMatchingOpenBackward(String src, boolean[] code, int close, char open, char closeCh) {
        int depth = 0;
        for (int i = close; i >= 0; i--) {
            if (!code[i]) {
                continue;
            }
            char c = src.charAt(i);
            if (c == closeCh) {
                depth++;
            } else if (c == open) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int findMatchingAngleBackward(String src, boolean[] code, int gt) {
        int depth = 0;
        for (int i = gt; i >= 0; i--) {
            if (!code[i]) {
                continue;
            }
            char c = src.charAt(i);
            if (c == '>') {
                depth++;
            } else if (c == '<') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean endsWithKeyword(String src, int endInclusive, String keyword) {
        int start = endInclusive - keyword.length() + 1;
        if (start < 0 || endInclusive >= src.length()) {
            return false;
        }
        if (!src.regionMatches(start, keyword, 0, keyword.length())) {
            return false;
        }
        if (start > 0 && isIdentPart(src.charAt(start - 1))) {
            return false;
        }
        return endInclusive + 1 >= src.length() || !isIdentPart(src.charAt(endInclusive + 1));
    }

    private static final class WrapResult {
        final String source;
        final int replacements;

        WrapResult(String source, int replacements) {
            this.source = source;
            this.replacements = replacements;
        }
    }

    private enum ScanState {
        CODE, LINE_COMMENT, BLOCK_COMMENT, STRING, CHAR
    }

    private static String ensureOptChainOnMethods(String src) {
        List<MethodSpan> methods = findMethodSpans(src);
        // insertion index -> insert text (process from end so indices stay valid)
        List<int[]> insertions = new ArrayList<>(); // [index, unused]
        List<String> texts = new ArrayList<>();

        for (MethodSpan method : methods) {
            if (!containsMarker(src, method.bodyOpen, method.bodyClose)) {
                continue;
            }
            if (hasOptChainBefore(src, method.declStart, method.bodyOpen)) {
                continue;
            }
            String indent = indentOfLine(src, method.declStart);
            insertions.add(new int[]{method.declStart});
            // Insert at the first token of the declaration so existing indent stays on the annotation line
            // via the newline+indent before the method signature.
            texts.add(ANNOTATION + "\n" + indent);
        }

        if (insertions.isEmpty()) {
            return src;
        }

        StringBuilder sb = new StringBuilder(src);
        for (int i = insertions.size() - 1; i >= 0; i--) {
            int idx = insertions.get(i)[0];
            sb.insert(idx, texts.get(i));
        }
        return sb.toString();
    }

    /** True when the method body text contains an {@code OptChainOps.opt(} call outside comments/strings. */
    private static boolean containsMarker(String src, int bodyOpen, int bodyClose) {
        boolean[] code = codeMask(src);
        int end = Math.min(bodyClose, src.length());
        for (int i = bodyOpen + 1; i + OPT_INVOKE.length() <= end; i++) {
            if (code[i] && src.startsWith(OPT_INVOKE, i)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasOptChainBefore(String src, int declStart, int bodyOpen) {
        // Only the region after the previous code-level member boundary and before the method body.
        // Comments and strings are skipped, so javadoc text like "{@code @OptChain}" does not count.
        int boundary = lastCodeDelimiterBefore(src, declStart);
        int from = Math.max(0, boundary + 1);
        return containsOptChainAnnotation(src, from, bodyOpen);
    }

    /** True if {@code @OptChain} appears as code (not inside comments/strings) in [from, to). */
    private static boolean containsOptChainAnnotation(String src, int from, int to) {
        ScanState state = ScanState.CODE;
        for (int i = from; i < to && i < src.length(); i++) {
            char c = src.charAt(i);
            char next = i + 1 < src.length() ? src.charAt(i + 1) : '\0';
            if (state == ScanState.CODE) {
                if (c == '/' && next == '/') {
                    state = ScanState.LINE_COMMENT;
                    i++;
                } else if (c == '/' && next == '*') {
                    state = ScanState.BLOCK_COMMENT;
                    i++;
                } else if (c == '"') {
                    state = ScanState.STRING;
                } else if (c == '\'') {
                    state = ScanState.CHAR;
                } else if (c == '@' && regionStartsWith(src, i, ANNOTATION_NAME)) {
                    int after = i + ANNOTATION_NAME.length();
                    if (after >= src.length() || !isIdentPart(src.charAt(after))) {
                        return true;
                    }
                }
            } else if (state == ScanState.LINE_COMMENT) {
                if (c == '\n') {
                    state = ScanState.CODE;
                }
            } else if (state == ScanState.BLOCK_COMMENT) {
                if (c == '*' && next == '/') {
                    state = ScanState.CODE;
                    i++;
                }
            } else if (state == ScanState.STRING) {
                if (c == '\\' && next != '\0') {
                    i++;
                } else if (c == '"') {
                    state = ScanState.CODE;
                }
            } else if (state == ScanState.CHAR) {
                if (c == '\\' && next != '\0') {
                    i++;
                } else if (c == '\'') {
                    state = ScanState.CODE;
                }
            }
        }
        return false;
    }

    private static String indentOfLine(String src, int pos) {
        int lineStart = pos;
        while (lineStart > 0 && src.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }
        StringBuilder indent = new StringBuilder();
        for (int i = lineStart; i < pos; i++) {
            char c = src.charAt(i);
            if (c == ' ' || c == '\t') {
                indent.append(c);
            } else {
                break;
            }
        }
        if (indent.length() == 0) {
            return "    ";
        }
        return indent.toString();
    }

    /**
     * Heuristic method finder: at class-member brace depth, a '{'-opened block whose
     * header ends with {@code )} (optionally throws) and is not a control keyword.
     */
    static List<MethodSpan> findMethodSpans(String src) {
        List<MethodSpan> methods = new ArrayList<>();
        ScanState state = ScanState.CODE;
        int braceDepth = 0;
        int i = 0;
        while (i < src.length()) {
            char c = src.charAt(i);
            char next = i + 1 < src.length() ? src.charAt(i + 1) : '\0';

            if (state == ScanState.CODE) {
                if (c == '/' && next == '/') {
                    state = ScanState.LINE_COMMENT;
                    i += 2;
                    continue;
                }
                if (c == '/' && next == '*') {
                    state = ScanState.BLOCK_COMMENT;
                    i += 2;
                    continue;
                }
                if (c == '"') {
                    state = ScanState.STRING;
                    i++;
                    continue;
                }
                if (c == '\'') {
                    state = ScanState.CHAR;
                    i++;
                    continue;
                }
                if (c == '{') {
                    // Before increasing depth: if we are about to enter a method body at depth 1
                    // (inside a top-level or nested type body), try to parse a method header.
                    if (braceDepth >= 1) {
                        MethodSpan span = tryParseMethodEndingAt(src, i, braceDepth);
                        if (span != null) {
                            int close = findMatchingBrace(src, i);
                            if (close > i) {
                                span.bodyOpen = i;
                                span.bodyClose = close;
                                methods.add(span);
                            }
                        }
                    }
                    braceDepth++;
                    i++;
                    continue;
                }
                if (c == '}') {
                    braceDepth--;
                    i++;
                    continue;
                }
                i++;
                continue;
            }

            if (state == ScanState.LINE_COMMENT) {
                if (c == '\n') {
                    state = ScanState.CODE;
                }
                i++;
                continue;
            }
            if (state == ScanState.BLOCK_COMMENT) {
                if (c == '*' && next == '/') {
                    state = ScanState.CODE;
                    i += 2;
                } else {
                    i++;
                }
                continue;
            }
            if (state == ScanState.STRING) {
                if (c == '\\' && next != '\0') {
                    i += 2;
                } else if (c == '"') {
                    state = ScanState.CODE;
                    i++;
                } else {
                    i++;
                }
                continue;
            }
            if (state == ScanState.CHAR) {
                if (c == '\\' && next != '\0') {
                    i += 2;
                } else if (c == '\'') {
                    state = ScanState.CODE;
                    i++;
                } else {
                    i++;
                }
                continue;
            }
            i++;
        }
        methods.sort(Comparator.comparingInt(m -> m.declStart));
        return methods;
    }

    /**
     * Looking backwards from {@code openBrace} ('{'), decide if this opens a method body.
     */
    private static MethodSpan tryParseMethodEndingAt(String src, int openBrace, int braceDepth) {
        // Skip whitespace before {
        int j = openBrace - 1;
        while (j >= 0 && Character.isWhitespace(src.charAt(j))) {
            j--;
        }
        if (j < 0) {
            return null;
        }
        // Optional: throws Clause — walk back over identifiers, dots, commas until ')'
        // Expect the last non-ws before { (or after throws clause) to be ')'
        // Handle: ) {   or  ) throws Foo, Bar {
        if (src.charAt(j) != ')') {
            // might be throws
            // Walk back a "throws Type, Type" segment
            int k = j;
            // consume identifier/comma/dot/whitespace region then expect ')'
            while (k >= 0) {
                char ch = src.charAt(k);
                if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '.' || ch == ',' || Character.isWhitespace(ch) || ch == '[' || ch == ']') {
                    k--;
                    continue;
                }
                break;
            }
            // check for "throws" keyword ending at k+1..
            String maybe = src.substring(Math.max(0, k + 1), j + 1).trim();
            if (!maybe.startsWith("throws") && !maybe.contains("throws")) {
                // still might just be garbage
            }
            // find ')' before throws
            while (k >= 0 && Character.isWhitespace(src.charAt(k))) {
                k--;
            }
            if (k < 0 || src.charAt(k) != ')') {
                return null;
            }
            j = k;
        }

        // j is at ')'
        int closeParen = j;
        int openParen = findMatchingParenBackward(src, closeParen);
        if (openParen < 0) {
            return null;
        }

        // method name is identifier before (
        int nameEnd = openParen - 1;
        while (nameEnd >= 0 && Character.isWhitespace(src.charAt(nameEnd))) {
            nameEnd--;
        }
        if (nameEnd < 0 || !isIdentPart(src.charAt(nameEnd))) {
            return null;
        }
        int nameStart = nameEnd;
        while (nameStart > 0 && isIdentPart(src.charAt(nameStart - 1))) {
            nameStart--;
        }
        String name = src.substring(nameStart, nameEnd + 1);
        if (isNotMethodName(name)) {
            return null;
        }

        // Walk back over return type / type params / modifiers / annotations to find decl start
        int declStart = findDeclStart(src, nameStart);
        return new MethodSpan(declStart, openBrace, openBrace);
    }

    private static boolean isNotMethodName(String name) {
        switch (name) {
            case "if":
            case "for":
            case "while":
            case "switch":
            case "catch":
            case "synchronized":
            case "try":
            case "do":
            case "else":
            case "return":
            case "new":
            case "class":
            case "interface":
            case "enum":
            case "record":
                return true;
            default:
                return false;
        }
    }

    private static int findDeclStart(String src, int nameStart) {
        // Walk backwards skipping type/modifiers/annotations. Stop at ; or { or } or line with only annotations boundary.
        // Practical approach: go to beginning of contiguous declaration — skip tokens that look like
        // modifiers, types, annotations, generics.
        int i = nameStart - 1;
        while (i >= 0 && Character.isWhitespace(src.charAt(i))) {
            i--;
        }
        // Skip return type (possibly generic / array / qualified)
        i = skipTypeBackward(src, i);
        // Skip modifiers and annotations repeatedly
        boolean progressed = true;
        while (progressed && i >= 0) {
            progressed = false;
            while (i >= 0 && Character.isWhitespace(src.charAt(i))) {
                i--;
            }
            if (i >= 0 && src.charAt(i) == ')') {
                // annotation with elements: @Foo(...)
                int open = findMatchingParenBackward(src, i);
                if (open < 0) {
                    break;
                }
                int at = open - 1;
                while (at >= 0 && Character.isWhitespace(src.charAt(at))) {
                    at--;
                }
                while (at >= 0 && (isIdentPart(src.charAt(at)) || src.charAt(at) == '.')) {
                    at--;
                }
                while (at >= 0 && Character.isWhitespace(src.charAt(at))) {
                    at--;
                }
                if (at >= 0 && src.charAt(at) == '@') {
                    i = at - 1;
                    progressed = true;
                    continue;
                }
                break;
            }
            if (i >= 0 && isIdentPart(src.charAt(i))) {
                int end = i;
                while (i >= 0 && (isIdentPart(src.charAt(i)) || src.charAt(i) == '.')) {
                    i--;
                }
                String word = src.substring(i + 1, end + 1);
                if (isModifier(word)) {
                    progressed = true;
                    continue;
                }
                // Could be annotation without parens: @OptChain — but that starts with @
                // Or leftover type token — treat as part of decl and continue one type
                if (word.contains(".") || Character.isUpperCase(word.charAt(0))) {
                    // another type? unusual before modifiers; stop and include it
                    // actually return type already skipped; this is likely a modifier we missed or annotation name without @
                    i = end; // restore
                    break;
                }
                // unknown keyword — include if modifier-like else stop
                i = end;
                break;
            }
            if (i >= 0 && src.charAt(i) == '@') {
                // @Anno without reading name? name should have been consumed — handle @ before ident
                // Looking backward we see @ at the start of annotation
                // Format when scanning back: we see Identifier then need @ before it
                break;
            }
            // Check for @Annotation (name already passed as ident — handle by scanning '@' before ident)
            break;
        }

        // First code token after the previous member. Braces inside comments or
        // literals (javadoc {@code ...}, strings) are not member boundaries.
        int boundary = lastCodeDelimiterBefore(src, nameStart);
        return skipWhitespaceAndComments(src, boundary + 1, nameStart);
    }

    /** Index of the last {@code ;}, <code>{</code>, or <code>}</code> in code before {@code limit}, or {@code -1}. */
    private static int lastCodeDelimiterBefore(String src, int limit) {
        ScanState state = ScanState.CODE;
        int boundary = -1;
        int end = Math.min(limit, src.length());
        for (int i = 0; i < end; i++) {
            char c = src.charAt(i);
            char next = i + 1 < src.length() ? src.charAt(i + 1) : '\0';
            if (state == ScanState.CODE) {
                if (c == '/' && next == '/') {
                    state = ScanState.LINE_COMMENT;
                    i++;
                } else if (c == '/' && next == '*') {
                    state = ScanState.BLOCK_COMMENT;
                    i++;
                } else if (c == '"') {
                    state = ScanState.STRING;
                } else if (c == '\'') {
                    state = ScanState.CHAR;
                } else if (c == ';' || c == '{' || c == '}') {
                    boundary = i;
                }
            } else if (state == ScanState.LINE_COMMENT) {
                if (c == '\n') {
                    state = ScanState.CODE;
                }
            } else if (state == ScanState.BLOCK_COMMENT) {
                if (c == '*' && next == '/') {
                    state = ScanState.CODE;
                    i++;
                }
            } else if (state == ScanState.STRING) {
                if (c == '\\' && next != '\0') {
                    i++;
                } else if (c == '"') {
                    state = ScanState.CODE;
                }
            } else if (state == ScanState.CHAR) {
                if (c == '\\' && next != '\0') {
                    i++;
                } else if (c == '\'') {
                    state = ScanState.CODE;
                }
            }
        }
        return boundary;
    }

    /** First code token in {@code [from, limit)}, skipping whitespace and comments. */
    private static int skipWhitespaceAndComments(String src, int from, int limit) {
        ScanState state = ScanState.CODE;
        int end = Math.min(limit, src.length());
        for (int i = Math.max(0, from); i < end; i++) {
            char c = src.charAt(i);
            char next = i + 1 < src.length() ? src.charAt(i + 1) : '\0';
            if (state == ScanState.CODE) {
                if (Character.isWhitespace(c)) {
                    continue;
                }
                if (c == '/' && next == '/') {
                    state = ScanState.LINE_COMMENT;
                    i++;
                    continue;
                }
                if (c == '/' && next == '*') {
                    state = ScanState.BLOCK_COMMENT;
                    i++;
                    continue;
                }
                return i;
            } else if (state == ScanState.LINE_COMMENT) {
                if (c == '\n') {
                    state = ScanState.CODE;
                }
            } else if (state == ScanState.BLOCK_COMMENT) {
                if (c == '*' && next == '/') {
                    state = ScanState.CODE;
                    i++;
                }
            }
        }
        return Math.max(0, from);
    }

    private static int skipTypeBackward(String src, int i) {
        if (i < 0) {
            return i;
        }
        // arrays ]
        while (i >= 0) {
            while (i >= 0 && Character.isWhitespace(src.charAt(i))) {
                i--;
            }
            if (i >= 0 && src.charAt(i) == ']') {
                while (i >= 0 && src.charAt(i) != '[') {
                    i--;
                }
                if (i >= 0) {
                    i--;
                }
                continue;
            }
            break;
        }
        while (i >= 0 && Character.isWhitespace(src.charAt(i))) {
            i--;
        }
        // generics >
        if (i >= 0 && src.charAt(i) == '>') {
            int depth = 0;
            while (i >= 0) {
                char ch = src.charAt(i);
                if (ch == '>') {
                    depth++;
                } else if (ch == '<') {
                    depth--;
                    if (depth == 0) {
                        i--;
                        break;
                    }
                }
                i--;
            }
            while (i >= 0 && Character.isWhitespace(src.charAt(i))) {
                i--;
            }
        }
        // type name
        if (i >= 0 && isIdentPart(src.charAt(i))) {
            while (i >= 0 && (isIdentPart(src.charAt(i)) || src.charAt(i) == '.')) {
                i--;
            }
        }
        return i;
    }

    private static boolean isModifier(String word) {
        switch (word) {
            case "public":
            case "protected":
            case "private":
            case "static":
            case "final":
            case "native":
            case "synchronized":
            case "abstract":
            case "default":
            case "strictfp":
            case "transient":
            case "volatile":
                return true;
            default:
                return false;
        }
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static int findMatchingParenBackward(String src, int closeParen) {
        int depth = 0;
        ScanState state = ScanState.CODE;
        for (int i = closeParen; i >= 0; i--) {
            char c = src.charAt(i);
            // crude: ignore comments/strings when scanning back by detecting quotes and comment ends
            if (state == ScanState.CODE) {
                if (c == ')') {
                    depth++;
                } else if (c == '(') {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                } else if (c == '"' ) {
                    // jump back over string — scan back until unescaped "
                    i = skipStringBackward(src, i);
                } else if (c == '\'') {
                    i = skipCharBackward(src, i);
                } else if (c == '/' && i > 0 && src.charAt(i - 1) == '*') {
                    // end of block comment */
                    i = skipBlockCommentBackward(src, i - 1);
                }
            }
        }
        return -1;
    }

    private static int skipStringBackward(String src, int openQuote) {
        for (int i = openQuote - 1; i >= 0; i--) {
            if (src.charAt(i) == '"') {
                int bs = 0;
                int j = i - 1;
                while (j >= 0 && src.charAt(j) == '\\') {
                    bs++;
                    j--;
                }
                if (bs % 2 == 0) {
                    return i;
                }
            }
        }
        return 0;
    }

    private static int skipCharBackward(String src, int openQuote) {
        for (int i = openQuote - 1; i >= 0; i--) {
            if (src.charAt(i) == '\'') {
                int bs = 0;
                int j = i - 1;
                while (j >= 0 && src.charAt(j) == '\\') {
                    bs++;
                    j--;
                }
                if (bs % 2 == 0) {
                    return i;
                }
            }
        }
        return 0;
    }

    private static int skipBlockCommentBackward(String src, int starBeforeSlash) {
        // at * of */
        for (int i = starBeforeSlash - 1; i > 0; i--) {
            if (src.charAt(i) == '*' && src.charAt(i - 1) == '/') {
                return i - 1;
            }
            if (src.charAt(i) == '/' && src.charAt(i - 1) == '*') {
                // nested? keep going for /*
            }
            if (src.charAt(i - 1) == '/' && src.charAt(i) == '*') {
                return i - 1;
            }
        }
        return 0;
    }

    private static int findMatchingBrace(String src, int openBrace) {
        ScanState state = ScanState.CODE;
        int depth = 0;
        for (int i = openBrace; i < src.length(); i++) {
            char c = src.charAt(i);
            char next = i + 1 < src.length() ? src.charAt(i + 1) : '\0';
            if (state == ScanState.CODE) {
                if (c == '/' && next == '/') {
                    state = ScanState.LINE_COMMENT;
                    i++;
                } else if (c == '/' && next == '*') {
                    state = ScanState.BLOCK_COMMENT;
                    i++;
                } else if (c == '"') {
                    state = ScanState.STRING;
                } else if (c == '\'') {
                    state = ScanState.CHAR;
                } else if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            } else if (state == ScanState.LINE_COMMENT) {
                if (c == '\n') {
                    state = ScanState.CODE;
                }
            } else if (state == ScanState.BLOCK_COMMENT) {
                if (c == '*' && next == '/') {
                    state = ScanState.CODE;
                    i++;
                }
            } else if (state == ScanState.STRING) {
                if (c == '\\' && next != '\0') {
                    i++;
                } else if (c == '"') {
                    state = ScanState.CODE;
                }
            } else if (state == ScanState.CHAR) {
                if (c == '\\' && next != '\0') {
                    i++;
                } else if (c == '\'') {
                    state = ScanState.CODE;
                }
            }
        }
        return -1;
    }

    private static String ensureImport(String src) {
        if (src.contains(IMPORT_LINE)) {
            return src;
        }
        // Also accept star or already imported somehow
        if (src.contains("import io.github.chensirj.optchain.OptChain")) {
            return src;
        }
        // Insert after package declaration, or at top
        int insertAt = 0;
        ScanState state = ScanState.CODE;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            char next = i + 1 < src.length() ? src.charAt(i + 1) : '\0';
            if (state == ScanState.CODE) {
                if (c == '/' && next == '/') {
                    state = ScanState.LINE_COMMENT;
                    i++;
                    continue;
                }
                if (c == '/' && next == '*') {
                    state = ScanState.BLOCK_COMMENT;
                    i++;
                    continue;
                }
                if (c == '"') {
                    state = ScanState.STRING;
                    continue;
                }
                // detect package
                if (regionStartsWith(src, i, "package") && (i == 0 || !isIdentPart(src.charAt(i - 1)))) {
                    int semi = src.indexOf(';', i);
                    if (semi > 0) {
                        insertAt = semi + 1;
                        // skip newlines after ;
                        while (insertAt < src.length() && (src.charAt(insertAt) == '\r')) {
                            insertAt++;
                        }
                        if (insertAt < src.length() && src.charAt(insertAt) == '\n') {
                            insertAt++;
                        }
                        return src.substring(0, insertAt) + "\n" + IMPORT_LINE + "\n" + src.substring(insertAt);
                    }
                }
                if (regionStartsWith(src, i, "import") && (i == 0 || !isIdentPart(src.charAt(i - 1)))) {
                    // insert before first import
                    return src.substring(0, i) + IMPORT_LINE + "\n" + src.substring(i);
                }
                if (regionStartsWith(src, i, "class") || regionStartsWith(src, i, "interface")
                        || regionStartsWith(src, i, "enum") || regionStartsWith(src, i, "record")
                        || regionStartsWith(src, i, "@")) {
                    return src.substring(0, i) + IMPORT_LINE + "\n\n" + src.substring(i);
                }
            } else if (state == ScanState.LINE_COMMENT) {
                if (c == '\n') {
                    state = ScanState.CODE;
                }
            } else if (state == ScanState.BLOCK_COMMENT) {
                if (c == '*' && next == '/') {
                    state = ScanState.CODE;
                    i++;
                }
            } else if (state == ScanState.STRING) {
                if (c == '\\' && next != '\0') {
                    i++;
                } else if (c == '"') {
                    state = ScanState.CODE;
                }
            }
        }
        return IMPORT_LINE + "\n\n" + src;
    }

    private static boolean regionStartsWith(String src, int i, String keyword) {
        if (i + keyword.length() > src.length()) {
            return false;
        }
        if (!src.startsWith(keyword, i)) {
            return false;
        }
        int after = i + keyword.length();
        return after >= src.length() || !isIdentPart(src.charAt(after));
    }

    static final class MethodSpan {
        final int declStart;
        int bodyOpen;
        int bodyClose;

        MethodSpan(int declStart, int bodyOpen, int bodyClose) {
            this.declStart = declStart;
            this.bodyOpen = bodyOpen;
            this.bodyClose = bodyClose;
        }
    }
}
