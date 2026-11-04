package io.ch.java.opt.chain.demo;

/**
 * Sample methods using source-level {@code ?.} optional chaining.
 * <p>
 * {@code opt-chain:preprocess} rewrites {@code ?.} to {@code .} and ensures
 * {@code @OptChain}; {@code opt-chain:weave} then null-safes the bytecode.
 * </p>
 */
public class OptChainExamples {

    public String cityOf(User u) {
        return u?.getAddress()?.getCity()?.getName();
    }

    public int nameLength(User u) {
        // When the chain short-circuits to null String, length() is skipped and int defaults to 0.
        return u?.getAddress()?.getCity()?.getName()?.length();
    }
}
