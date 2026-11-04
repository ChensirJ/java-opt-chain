package io.ch.java.opt.chain.idea;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.Nullable;

/**
 * Go to Declaration for fields and methods accessed via {@code ?.}.
 *
 * <p>Field chains ({@code u?.address?.city}) and call chains share one rewrite to {@code .}.
 * If {@code resolve()} is empty, {@link OptChainPsiRewrite} falls back to a field (plain access)
 * or method (call) of the qualifier type by name. A field-shaped member is also probed at the
 * end of its identifier so the caret can sit on {@code ?.} or in the middle of the name.
 */
public class OptChainGotoDeclarationHandler implements GotoDeclarationHandler {

  @Override
  public PsiElement @Nullable [] getGotoDeclarationTargets(
      @Nullable PsiElement sourceElement, int offset, Editor editor) {
    if (sourceElement == null) {
      return null;
    }
    PsiFile file = sourceElement.getContainingFile();
    if (file == null || !file.getName().endsWith(".java")) {
      return null;
    }
    String text = file.getText();
    // Only intervene when there is a ?. before this member (or caret is on ?. itself)
    int op = OptChainTextSupport.findOptChainOperatorBeforeMember(text, offset);
    if (op < 0 && !OptChainTextSupport.isAtOptChainOperator(text, offset)) {
      // Also: caret on member name after ?.
      int probe = offset;
      while (probe < text.length() && Character.isJavaIdentifierPart(text.charAt(probe))) {
        probe++;
      }
      op = OptChainTextSupport.findOptChainOperatorBeforeMember(text, probe);
      if (op < 0) {
        return null;
      }
    }
    if (op < 0) {
      op = OptChainTextSupport.optChainOperatorStart(text, offset);
    }

    PsiJavaFile rewritten = OptChainPsiRewrite.reparseWithoutOptChain(file.getProject(), file);
    if (rewritten == null) {
      return null;
    }

    PsiElement resolved = OptChainPsiRewrite.resolveReference(rewritten, text, offset);
    if (resolved == null && op >= 0) {
      resolved = OptChainPsiRewrite.resolveReference(rewritten, text, op + 2);
    }
    if (resolved == null
        && op >= 0
        && OptChainTextSupport.memberAfterOptChainLooksLikeField(text, Math.max(offset, op + 2))) {
      int memberEnd = op + 2;
      while (memberEnd < text.length() && Character.isJavaIdentifierPart(text.charAt(memberEnd))) {
        memberEnd++;
      }
      if (memberEnd != op + 2) {
        resolved = OptChainPsiRewrite.resolveReference(rewritten, text, memberEnd);
      }
    }
    if (resolved == null) {
      return null;
    }
    return new PsiElement[] {resolved};
  }
}
