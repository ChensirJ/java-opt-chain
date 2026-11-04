package io.ch.java.opt.chain.idea;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.lang.java.JavaLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Rewrites a Java file's {@code ?.} to {@code .}, reparses, and resolves types / references.
 * Used by completion and go-to-declaration when the real PSI is broken by {@code ?.}.
 */
public final class OptChainPsiRewrite {

  private OptChainPsiRewrite() {}

  public static @Nullable PsiJavaFile reparseWithoutOptChain(
      @NotNull Project project, @NotNull PsiFile original) {
    String rewritten = OptChainTextSupport.rewriteOptChainToDot(original.getText());
    PsiFile file = PsiFileFactory.getInstance(project)
        .createFileFromText(original.getName(), JavaLanguage.INSTANCE, rewritten);
    return file instanceof PsiJavaFile ? (PsiJavaFile) file : null;
  }

  public static @Nullable PsiElement findElementAtRewritten(
      @NotNull PsiJavaFile rewrittenFile, @NotNull String originalText, int originalOffset) {
    int adj = OptChainTextSupport.adjustOffsetAfterRewrite(originalText, originalOffset);
    if (adj < 0 || adj > rewrittenFile.getTextLength()) {
      return null;
    }
    // Prefer the element just before caret for references
    int probe = Math.max(0, adj - 1);
    PsiElement at = rewrittenFile.findElementAt(probe);
    if (at == null && adj < rewrittenFile.getTextLength()) {
      at = rewrittenFile.findElementAt(adj);
    }
    return at;
  }

  public static @Nullable PsiReferenceExpression findReferenceExpression(
      @NotNull PsiJavaFile rewrittenFile, @NotNull String originalText, int originalOffset) {
    PsiElement at = findElementAtRewritten(rewrittenFile, originalText, originalOffset);
    if (at == null) {
      return null;
    }
    PsiReferenceExpression ref = PsiTreeUtil.getParentOfType(at, PsiReferenceExpression.class, false);
    if (ref != null) {
      return ref;
    }
    PsiElement parent = at.getParent();
    if (parent instanceof PsiReferenceExpression) {
      return (PsiReferenceExpression) parent;
    }
    return null;
  }

  public static @Nullable PsiExpression qualifierOfMemberAccess(
      @NotNull PsiJavaFile rewrittenFile, @NotNull String originalText, int originalOffset) {
    PsiReferenceExpression ref = findReferenceExpression(rewrittenFile, originalText, originalOffset);
    if (ref == null) {
      return null;
    }
    return ref.getQualifierExpression();
  }

  public static @Nullable PsiElement resolveReference(
      @NotNull PsiJavaFile rewrittenFile, @NotNull String originalText, int originalOffset) {
    PsiReferenceExpression ref = findReferenceExpression(rewrittenFile, originalText, originalOffset);
    if (ref == null) {
      return null;
    }
    PsiElement resolved = ref.resolve();
    if (resolved != null) {
      return resolved;
    }
    PsiReference[] refs = ref.getReferences();
    for (PsiReference r : refs) {
      PsiElement e = r.resolve();
      if (e != null) {
        return e;
      }
    }
    return null;
  }
}
