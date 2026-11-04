package io.github.chensirj.optchain.idea;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * Offers field and method completion after {@code receiver?.}.
 *
 * <p>Both use the same {@code ?.} → {@code .} reparse and the qualifier type. Public field chains
 * such as {@code u?.address?.city} are ordinary fields on that type. Highlight filters are separate
 * and are not changed here.
 */
public class OptChainCompletionContributor extends CompletionContributor {

  public OptChainCompletionContributor() {
    extend(
        CompletionType.BASIC,
        PlatformPatterns.psiElement(),
        new CompletionProvider<>() {
          @Override
          protected void addCompletions(
              @NotNull CompletionParameters parameters,
              @NotNull ProcessingContext context,
              @NotNull CompletionResultSet result) {
            PsiFile file = parameters.getOriginalFile();
            if (!file.getName().endsWith(".java")) {
              return;
            }
            String text = file.getText();
            int offset = parameters.getOffset();
            int op = OptChainTextSupport.findOptChainOperatorBeforeMember(text, offset);
            if (op < 0) {
              return;
            }

            PsiJavaFile rewritten =
                OptChainPsiRewrite.reparseWithoutOptChain(file.getProject(), file);
            if (rewritten == null) {
              return;
            }

            // Point just after the rewritten '.' so we land on the member / empty identifier
            int afterDotOriginal = op + 2;
            PsiExpression qualifier =
                OptChainPsiRewrite.qualifierOfMemberAccess(rewritten, text, afterDotOriginal + 1);
            if (qualifier == null) {
              // try caret offset
              qualifier = OptChainPsiRewrite.qualifierOfMemberAccess(rewritten, text, offset);
            }
            if (qualifier == null) {
              return;
            }

            PsiType type = qualifier.getType();
            PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(type);
            if (psiClass == null) {
              psiClass = PsiUtil.resolveClassInType(type);
            }
            if (psiClass == null) {
              return;
            }

            String prefix = OptChainTextSupport.memberPrefixAfterOptChain(text, offset);
            CompletionResultSet filtered =
                prefix.isEmpty() ? result : result.withPrefixMatcher(prefix);

            for (PsiField field : psiClass.getAllFields()) {
              String fieldName = field.getName();
              // Inner-class this$0 is not a useful ?. target; real fields (including public ones) stay.
              if (fieldName.startsWith("this$")) {
                continue;
              }
              if (field.hasModifierProperty(PsiModifier.PRIVATE)
                  && field.getContainingClass() != null
                  && !field.getContainingClass().equals(psiClass)) {
                continue;
              }
              filtered.addElement(
                  LookupElementBuilder.create(field)
                      .withIcon(field.getIcon(0))
                      .withTypeText(field.getType().getPresentableText(), true));
            }
            for (PsiMethod method : psiClass.getAllMethods()) {
              if (method.isConstructor()) {
                continue;
              }
              if (method.hasModifierProperty(PsiModifier.PRIVATE)
                  && method.getContainingClass() != null
                  && !method.getContainingClass().equals(psiClass)) {
                continue;
              }
              filtered.addElement(
                  LookupElementBuilder.create(method)
                      .withPresentableText(method.getName())
                      .withTailText(parameterTail(method), true)
                      .withIcon(method.getIcon(0))
                      .withTypeText(
                          method.getReturnType() != null
                              ? method.getReturnType().getPresentableText()
                              : "",
                          true));
            }
          }
        });
  }

  private static @NotNull String parameterTail(@NotNull PsiMethod method) {
    StringBuilder sb = new StringBuilder("(");
    var params = method.getParameterList().getParameters();
    for (int i = 0; i < params.length; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(params[i].getType().getPresentableText());
    }
    sb.append(")");
    return sb.toString();
  }
}
