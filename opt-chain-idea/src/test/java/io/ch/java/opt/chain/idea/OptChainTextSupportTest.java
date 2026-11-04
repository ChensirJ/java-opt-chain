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

  @Test
  void fieldChainRewritePrefixAndFieldShape() {
    String src = "return u?.address?.city?.name;";
    assertEquals("return u.address.city.name;", OptChainTextSupport.rewriteOptChainToDot(src));

    int name = src.indexOf("name");
    int caret = name + "name".length();
    assertEquals(src.lastIndexOf("?."), OptChainTextSupport.findOptChainOperatorBeforeMember(src, caret));
    assertEquals("name", OptChainTextSupport.memberPrefixAfterOptChain(src, caret));
    assertTrue(OptChainTextSupport.memberAfterOptChainLooksLikeField(src, caret));
    assertTrue(OptChainTextSupport.memberAfterOptChainLooksLikeField(src, name + 2));

    int address = src.indexOf("address") + "address".length();
    assertEquals("address", OptChainTextSupport.memberPrefixAfterOptChain(src, address));
    assertTrue(OptChainTextSupport.memberAfterOptChainLooksLikeField(src, address));

    int q = src.lastIndexOf("?.");
    assertEquals(q, OptChainTextSupport.optChainOperatorStart(src, q));
    assertEquals(q, OptChainTextSupport.optChainOperatorStart(src, q + 1));
    assertTrue(OptChainTextSupport.memberAfterOptChainLooksLikeField(src, q));
  }

  @Test
  void callAfterOptChainIsNotAField() {
    String src = "return u?.getName();";
    int caret = src.indexOf("getName") + 3;
    assertFalse(OptChainTextSupport.memberAfterOptChainLooksLikeField(src, caret));
    assertEquals("get", OptChainTextSupport.memberPrefixAfterOptChain(src, caret));
  }

  @Test
  void fieldChainSameLineStillLooksLikeOptChainError() {
    String src = "return u?.address?.city?.name;";
    int name = src.indexOf("name");
    assertTrue(
        OptChainTextSupport.looksLikeOptChainSyntaxError(
            src, name, name + "name".length(), "Cannot resolve symbol 'name'"));
  }

  @Test
  void operatorStartOnEitherCharacter() {
    String src = "u?.name";
    int q = src.indexOf('?');
    assertEquals(q, OptChainTextSupport.optChainOperatorStart(src, q));
    assertEquals(q, OptChainTextSupport.optChainOperatorStart(src, q + 1));
    assertTrue(OptChainTextSupport.isAtOptChainOperator(src, q));
    assertTrue(OptChainTextSupport.isAtOptChainOperator(src, q + 1));
    assertEquals(-1, OptChainTextSupport.optChainOperatorStart(src, 0));
  }
}
