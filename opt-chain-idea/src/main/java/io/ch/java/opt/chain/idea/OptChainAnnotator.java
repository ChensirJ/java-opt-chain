package io.ch.java.opt.chain.idea;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Highlights {@code ?.} operators in Java files (skipped inside comments / string literals when PSI allows).
 */
public class OptChainAnnotator implements Annotator {

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    // Run once per file root to avoid duplicate annotations
    PsiFile file = element instanceof PsiFile ? (PsiFile) element : null;
    if (file == null || !file.getName().endsWith(".java")) {
      return;
    }
    if (element != file) {
      return;
    }

    CharSequence text = file.getViewProvider().getContents();
    for (int i = 0; i < text.length() - 1; i++) {
      if (text.charAt(i) != '?' || text.charAt(i + 1) != '.') {
        continue;
      }
      PsiElement at = file.findElementAt(i);
      if (at != null && isCommentOrLiteral(at)) {
        continue;
      }
      TextRange range = TextRange.create(i, i + 2);
      holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
          .range(range)
          .textAttributes(OptChainColors.OPT_CHAIN_OPERATOR)
          .tooltip("Optional chain (?.): null-safe field or method access — compiled via opt-chain Maven plugin")
          .create();
    }
  }

  private static boolean isCommentOrLiteral(@NotNull PsiElement element) {
    PsiElement e = element;
    while (e != null) {
      if (e instanceof PsiComment || e instanceof PsiLiteralExpression) {
        return true;
      }
      e = e.getParent();
    }
    return false;
  }
}
