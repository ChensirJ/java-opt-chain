package io.github.chensirj.optchain.idea;

import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Suppresses red-line syntax errors that come from writing JS-style {@code ?.} in Java sources.
 * IDEA's own build rewrites {@code ?.} before javac; Maven preprocess remains the CLI path.
 */
public class OptChainHighlightErrorFilter extends HighlightErrorFilter {

  @Override
  public boolean shouldHighlightErrorElement(@NotNull PsiErrorElement element) {
    PsiFile file = element.getContainingFile();
    if (file == null || !isJavaFile(file)) {
      return true;
    }
    CharSequence text = file.getViewProvider().getContents();
    int start = element.getTextRange().getStartOffset();
    int end = element.getTextRange().getEndOffset();

    String description = element.getErrorDescription();
    if (OptChainTextSupport.looksLikeOptChainSyntaxError(text, start, end, description)) {
      return false;
    }

    // Eat a bare '?' error token even when the reported range is tiny.
    String elementText = element.getText();
    if (elementText != null && elementText.contains("?")) {
      if (OptChainTextSupport.questionLooksLikeOptChain(text, start, Math.max(end, start + 2))
          || OptChainTextSupport.containsOptChainOperator(
              text,
              Math.max(0, start - OptChainTextSupport.NEAR_WINDOW),
              Math.min(text.length(), end + OptChainTextSupport.NEAR_WINDOW))) {
        return false;
      }
    }

    // Parent / sibling context: parser often wraps '?' in a larger broken expression.
    PsiElement parent = element.getParent();
    if (parent != null && parent.getTextRange() != null) {
      int pStart = parent.getTextRange().getStartOffset();
      int pEnd = parent.getTextRange().getEndOffset();
      if (OptChainTextSupport.containsOptChainOperator(text, pStart, pEnd)) {
        return false;
      }
    }

    return true;
  }

  private static boolean isJavaFile(@NotNull PsiFile file) {
    String name = file.getName();
    return name.endsWith(".java");
  }
}
