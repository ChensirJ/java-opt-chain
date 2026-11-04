package io.github.chensirj.optchain.idea;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Pure text helpers for {@code ?.} — safe to unit-test without an IDE runtime.
 */
public final class OptChainTextSupport {

  /** Tight scan around an error token for an adjacent {@code ?.}. */
  public static final int TIGHT_WINDOW = 6;

  /** Kept for callers that want a broader neighborhood (filters / parents). */
  public static final int NEAR_WINDOW = 64;

  private OptChainTextSupport() {}

  /** Replace every {@code ?.} with {@code .} (comments/strings not special-cased here). */
  public static @NotNull String rewriteOptChainToDot(@NotNull String source) {
    return source.replace("?.", ".");
  }

  /**
   * Map an offset in the original text (with {@code ?.}) to the rewritten text (with {@code .}).
   * Each {@code ?.} whose {@code ?} index is {@code < offset} removes one character.
   */
  public static int adjustOffsetAfterRewrite(@NotNull String original, int offset) {
    int clamped = Math.max(0, Math.min(offset, original.length()));
    int removed = 0;
    for (int i = 0; i < clamped; i++) {
      if (i + 1 < original.length()
          && original.charAt(i) == '?'
          && original.charAt(i + 1) == '.') {
        removed++;
        i++; // skip '.'
      }
    }
    return Math.max(0, clamped - removed);
  }

  /** True if offset is on a {@code ?.} operator. */
  public static boolean isAtOptChainOperator(@NotNull CharSequence text, int offset) {
    return optChainOperatorStart(text, offset) >= 0;
  }

  /**
   * Index of {@code ?} when {@code offset} sits on either character of {@code ?.}, else {@code -1}.
   * Field chains and method chains use the same operator.
   */
  public static int optChainOperatorStart(@NotNull CharSequence text, int offset) {
    if (offset >= 0
        && offset + 1 < text.length()
        && text.charAt(offset) == '?'
        && text.charAt(offset + 1) == '.') {
      return offset;
    }
    if (offset > 0
        && offset < text.length()
        && text.charAt(offset - 1) == '?'
        && text.charAt(offset) == '.') {
      return offset - 1;
    }
    return -1;
  }

  /**
   * Find the start offset of a {@code ?.} that immediately precedes a member name at caret,
   * e.g. {@code expr?.foo|} or {@code expr?.|}. Returns -1 if not in that context.
   */
  public static int findOptChainOperatorBeforeMember(@NotNull CharSequence text, int caretOffset) {
    int i = Math.min(caretOffset, text.length()) - 1;
    while (i >= 0 && isJavaIdentifierPart(text.charAt(i))) {
      i--;
    }
    if (i < 1 || text.charAt(i) != '.') {
      return -1;
    }
    if (text.charAt(i - 1) != '?') {
      return -1;
    }
    return i - 1;
  }

  /**
   * True when the member after {@code ?.} is not a call ({@code (} does not follow the identifier).
   *
   * <p>Field chains ({@code u?.address?.city?.name}) and method chains share
   * {@link #findOptChainOperatorBeforeMember}. This only classifies the tail.
   * During completion, before {@code (} is typed, a method name also looks like a field;
   * go-to-declaration on {@code getName()} does not.
   */
  public static boolean memberAfterOptChainLooksLikeField(
      @NotNull CharSequence text, int caretOffset) {
    int op = findOptChainOperatorBeforeMember(text, caretOffset);
    if (op < 0) {
      op = optChainOperatorStart(text, caretOffset);
    }
    if (op < 0) {
      return false;
    }
    int i = op + 2;
    while (i < text.length() && isJavaIdentifierPart(text.charAt(i))) {
      i++;
    }
    while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
      i++;
    }
    return i >= text.length() || text.charAt(i) != '(';
  }

  /** Extract member name prefix being typed after {@code ?.} (may be empty). */
  public static @NotNull String memberPrefixAfterOptChain(
      @NotNull CharSequence text, int caretOffset) {
    int op = findOptChainOperatorBeforeMember(text, caretOffset);
    if (op < 0) {
      return "";
    }
    int memberStart = op + 2; // after ?.
    int end = Math.min(caretOffset, text.length());
    if (memberStart > end) {
      return "";
    }
    return text.subSequence(memberStart, end).toString();
  }

  /** True if {@code ?.} appears in {@code [from, to)}. */
  public static boolean containsOptChainOperator(@NotNull CharSequence text, int from, int to) {
    int start = Math.max(0, from);
    int end = Math.min(text.length(), to);
    for (int i = start; i + 1 < end; i++) {
      if (text.charAt(i) == '?' && text.charAt(i + 1) == '.') {
        return true;
      }
    }
    for (int i = start; i < end; i++) {
      if (text.charAt(i) != '?') {
        continue;
      }
      int j = i + 1;
      while (j < text.length() && j <= i + 3 && Character.isWhitespace(text.charAt(j))) {
        j++;
      }
      if (j < text.length() && text.charAt(j) == '.') {
        return true;
      }
    }
    return false;
  }

  /** True if a lone {@code ?} in range is immediately followed (optional ws) by {@code .}. */
  public static boolean questionLooksLikeOptChain(
      @NotNull CharSequence text, int errorStart, int errorEnd) {
    int from = Math.max(0, errorStart);
    int to = Math.min(text.length(), Math.max(errorEnd, errorStart + 1));
    for (int i = from; i < to; i++) {
      if (text.charAt(i) != '?') {
        continue;
      }
      if (i + 1 < text.length() && text.charAt(i + 1) == '.') {
        return true;
      }
      int j = i + 1;
      while (j < text.length() && j <= i + 3 && Character.isWhitespace(text.charAt(j))) {
        j++;
      }
      if (j < text.length() && text.charAt(j) == '.') {
        return true;
      }
    }
    if (from < text.length() && text.charAt(from) == '?') {
      int j = from + 1;
      while (j < text.length() && j <= from + 3 && Character.isWhitespace(text.charAt(j))) {
        j++;
      }
      if (j < text.length() && text.charAt(j) == '.') {
        return true;
      }
    }
    return false;
  }

  private static int lineStart(@NotNull CharSequence text, int offset) {
    int i = Math.max(0, Math.min(offset, text.length()));
    while (i > 0 && text.charAt(i - 1) != '\n') {
      i--;
    }
    return i;
  }

  private static int lineEnd(@NotNull CharSequence text, int offset) {
    int i = Math.max(0, Math.min(offset, text.length()));
    while (i < text.length() && text.charAt(i) != '\n') {
      i++;
    }
    return i;
  }

  /**
   * True if a PsiError / HighlightInfo around the range is likely caused by {@code ?.}.
   *
   * <p>Suppress when the error is the {@code ?} token, tightly adjacent to {@code ?.}, or on the
   * same source line as a {@code ?.} (Java cascades parse errors onto later identifiers).
   */
  public static boolean looksLikeOptChainSyntaxError(
      @NotNull CharSequence text, int errorStart, int errorEnd, @Nullable String description) {
    int start = Math.max(0, errorStart);
    int end = Math.max(errorEnd, errorStart);

    if (questionLooksLikeOptChain(text, start, end)) {
      return true;
    }

    if (containsOptChainOperator(
        text,
        Math.max(0, start - TIGHT_WINDOW),
        Math.min(text.length(), end + TIGHT_WINDOW))) {
      return true;
    }

    // Same line as ?. — covers cascading fallout ("unexpected token" on getAddress, etc.)
    if (containsOptChainOperator(text, lineStart(text, start), lineEnd(text, end))) {
      return true;
    }

    // Description explicitly mentions '?' and a nearby ?. exists in NEAR_WINDOW on THIS line only
    // (already covered) or the highlighted slice itself contains '?'.
    if (description != null) {
      String d = description.toLowerCase();
      if (d.contains("?")) {
        CharSequence slice =
            text.subSequence(start, Math.min(text.length(), Math.max(end, start + 1)));
        if (slice.toString().contains("?")
            && containsOptChainOperator(
                text,
                Math.max(0, start - NEAR_WINDOW),
                Math.min(text.length(), end + NEAR_WINDOW))) {
          return true;
        }
      }
    }

    return false;
  }

  /** Heuristic for parser / unexpected-token style messages. */
  public static boolean isParseFalloutDescription(@NotNull String lowerDescription) {
    String d = lowerDescription;
    return d.contains("unexpected")
        || d.contains("illegal")
        || d.contains("token")
        || d.contains("expected")
        || d.contains("identifier")
        || d.contains("expression")
        || d.contains("java token")
        || d.contains("cannot resolve")
        || d.contains("can't resolve")
        || d.contains("unknown class")
        || d.contains("not a statement")
        || d.contains("')' expected")
        || d.contains("';' expected")
        || d.contains("':' expected");
  }

  public static boolean isJavaIdentifierPart(char c) {
    return Character.isJavaIdentifierPart(c);
  }
}
