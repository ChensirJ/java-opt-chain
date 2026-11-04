package io.github.chensirj.optchain.demo;

import io.github.chensirj.optchain.OptChain;

/**
 * Hand-written {@code @OptChain} with no {@code ?.} and no {@code OptChainOps.opt} markers.
 * Weave keeps the legacy behavior and null-checks every instance read and call.
 */
public class LegacyOptChainExamples {

    @OptChain
    public String cityOf(User u) {
        return u.getAddress().getCity().getName();
    }

    @OptChain
    public int nameLength(User u) {
        return u.getAddress().getCity().getName().length();
    }
}
