package io.ch.java.opt.chain.idea;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;

/** Highlight keys for the {@code ?.} operator. */
public final class OptChainColors {

  public static final TextAttributesKey OPT_CHAIN_OPERATOR =
      TextAttributesKey.createTextAttributesKey(
          "OPT_CHAIN_OPERATOR",
          DefaultLanguageHighlighterColors.OPERATION_SIGN);

  private OptChainColors() {}
}
