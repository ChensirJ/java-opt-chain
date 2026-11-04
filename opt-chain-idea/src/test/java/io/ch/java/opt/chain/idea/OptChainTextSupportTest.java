package io.ch.java.opt.chain.idea;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OptChainTextSupportTest {

  @Test
  void rewriteReplacesAllOperators() {
    String src = "u?.getAddress()?.getCity()?.getName()";
    assertEquals("u.getAddress().getCity().getName()", OptChainTextSupport.rewriteOptChainToDot(src));
  }

  @Test
  void adjustOffsetCountsRemovedQuestionMarks() {
    String src = "u?.ab";
    assertEquals(0, OptChainTextSupport.adjustOffsetAfterRewrite(src, 0));
    assertEquals(1, OptChainTextSupport.adjustOffsetAfterRewrite(src, 1));
    assertEquals(1, OptChainTextSupport.adjustOffsetAfterRewrite(src, 2));
    assertEquals(2, OptChainTextSupport.adjustOffsetAfterRewrite(src, 3));
    assertEquals(3, OptChainTextSupport.adjustOffsetAfterRewrite(src, 4));
  }

  @Test
  void findOperatorBeforeMember() {
    String src = "return u?.getName();";
    int caret = src.indexOf("getName") + "getName".length();
    int op = OptChainTextSupport.findOptChainOperatorBeforeMember(src, caret);
    assertEquals(src.indexOf("?."), op);
    assertEquals("getName", OptChainTextSupport.memberPrefixAfterOptChain(src, caret));
  }

  @Test
  void looksLikeOptChainErrorNearOperator() {
    String src = "x = a?.b;";
    int q = src.indexOf('?');
    assertTrue(OptChainTextSupport.looksLikeOptChainSyntaxError(src, q, q + 1, "unexpected token"));
  }

  @Test
  void doesNotSuppressUnrelatedLine() {
    String src = "int x = 1;\nx = a?.b;";
    assertFalse(
        OptChainTextSupport.looksLikeOptChainSyntaxError(src, 0, 3, "unexpected token"));
  }

  @Test
  void looksLikeOptChainErrorOnFollowingIdentifier() {
    String src = "return u?.getAddress()?.getCity();";
    int id = src.indexOf("getAddress");
    assertTrue(
        OptChainTextSupport.looksLikeOptChainSyntaxError(
            src, id, id + "getAddress".length(), "Unexpected token"));
  }

  @Test
  void looksLikeOptChainErrorSameLineFartherAway() {
    String src = "String n = u?.getAddress().getCity().getName();";
    int name = src.indexOf("getName");
    assertTrue(
        OptChainTextSupport.looksLikeOptChainSyntaxError(
            src, name, name + "getName".length(), "Cannot resolve method 'getName'"));
  }

  @Test
  void questionLooksLikeOptChainBareQuestion() {
    String src = "a?.b";
    int q = src.indexOf('?');
    assertTrue(OptChainTextSupport.questionLooksLikeOptChain(src, q, q + 1));
  }
}
