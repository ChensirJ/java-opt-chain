package io.ch.java.opt.chain.idea;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.Nullable;

/**
 * Go to Declaration for members accessed via {@code ?.}, using a {@code ?.}→{@.} reparse hack.
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

    PsiJavaFile rewritten = OptChainPsiRewrite.reparseWithoutOptChain(file.getProject(), file);
    if (rewritten == null) {
      return null;
    }

    PsiElement resolved = OptChainPsiRewrite.resolveReference(rewritten, text, offset);
    if (resolved == null && op >= 0) {
      resolved = OptChainPsiRewrite.resolveReference(rewritten, text, op + 2);
    }
    if (resolved == null) {
      return null;
    }
    return new PsiElement[] {resolved};
  }
}
