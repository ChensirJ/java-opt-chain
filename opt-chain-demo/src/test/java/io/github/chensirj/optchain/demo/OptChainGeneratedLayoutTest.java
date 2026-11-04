package io.github.chensirj.optchain.demo;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Preprocess must keep the legal mirror out of {@code target/generated-sources},
 * which IntelliJ would import as a second source root.
 */
class OptChainGeneratedLayoutTest {

    @Test
    void mirrorIsOutsideGeneratedSources() throws Exception {
        Path module = Path.of("").toAbsolutePath();
        Path mirror = module.resolve("target/opt-chain/sources/io/github/chensirj/optchain/demo/OptChainExamples.java");
        Path fieldUser = module.resolve("target/opt-chain/sources/io/github/chensirj/optchain/demo/FieldUser.java");
        assertTrue(Files.isRegularFile(mirror), mirror.toString());
        assertTrue(Files.isRegularFile(fieldUser), fieldUser.toString());

        String rewritten = Files.readString(mirror);
        // Javadoc may still mention ?.; the executable return must not.
        assertFalse(rewritten.contains("return u?."));
        assertTrue(rewritten.contains("io.github.chensirj.optchain.OptChainOps.opt("));

        assertFalse(Files.exists(module.resolve("target/generated-sources/opt-chain")),
                "legacy generated-sources/opt-chain must not be a source tree");
        assertFalse(Files.exists(module.resolve("target/generated-test-sources/opt-chain")));
    }
}
