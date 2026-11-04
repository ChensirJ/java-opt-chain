package io.github.chensirj.optchain.idea;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Suppresses ERROR (and severe) {@link HighlightInfo}s caused by {@code ?.} parse fallout.
 * {@link OptChainHighlightErrorFilter} only covers {@code PsiErrorElement}; IntelliJ 2024+/2025
 * also surfaces many follow-on issues as HighlightInfo (and Java code-server errors).
 */
public class OptChainHighlightInfoFilter implements HighlightInfoFilter {

  @Override
  public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile file) {
    if (file == null || !file.getName().endsWith(".java")) {
      return true;
    }
    HighlightSeverity severity = highlightInfo.getSeverity();
    // Keep INFO/TYPO/WEAK_WARNING; only strip hard ERROR/WARNING that look like ?. fallout.
    if (!HighlightSeverity.ERROR.equals(severity)
        && !HighlightSeverity.WARNING.equals(severity)) {
      return true;
    }

    CharSequence text = file.getViewProvider().getContents();
    int start = highlightInfo.getStartOffset();
    int end = highlightInfo.getEndOffset();
    if (end < start) {
      end = start;
    }
    String description = highlightInfo.getDescription();

    if (OptChainTextSupport.looksLikeOptChainSyntaxError(text, start, end, description)) {
      return false;
    }

    // HighlightInfo often marks only the identifier after a broken '?.' — still on same line.
    if (HighlightSeverity.ERROR.equals(severity)
        && OptChainTextSupport.looksLikeOptChainSyntaxError(
            text, start, end, description == null ? "unexpected token" : description)) {
      return false;
    }

    return true;
  }
}
