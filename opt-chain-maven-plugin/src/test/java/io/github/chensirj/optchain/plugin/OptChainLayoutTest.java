package io.github.chensirj.optchain.plugin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptChainLayoutTest {

    @Test
    void currentAndLegacyMirrorsMatch() {
        assertTrue(OptChainLayout.isOptChainMirrorPath("D:/java-opt-chain/opt-chain-demo/target/opt-chain/sources"));
        assertTrue(OptChainLayout.isOptChainMirrorPath(
                "D:\\java-opt-chain\\opt-chain-demo\\target\\generated-sources\\opt-chain\\io\\github\\FieldUser.java"));
        assertTrue(OptChainLayout.isOptChainMirrorPath(
                "file:///D:/proj/target/generated-test-sources/opt-chain"));
        assertTrue(OptChainLayout.isOptChainMirrorPath("/tmp/proj/target/opt-chain/test-sources/demo/A.java"));
    }

    @Test
    void authoringSourcesAndSimilarNamesAreNotMirrors() {
        assertFalse(OptChainLayout.isOptChainMirrorPath(
                "D:/java-opt-chain/opt-chain-demo/src/main/java/io/github/chensirj/optchain/demo/FieldUser.java"));
        assertFalse(OptChainLayout.isOptChainMirrorPath(
                "D:/not-opt-chain/sources/FieldUser.java"));
        assertFalse(OptChainLayout.isOptChainMirrorPath(
                "D:/proj/target/generated-sources/annotations/Foo.java"));
    }
}
