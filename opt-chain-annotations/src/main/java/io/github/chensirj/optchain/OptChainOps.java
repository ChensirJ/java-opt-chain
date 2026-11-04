package io.github.chensirj.optchain;

/**
 * Identity marker the preprocessor inserts around each {@code ?.} receiver.
 * <p>
 * {@code a?.b} becomes {@code OptChainOps.opt(a).b}. The weaver tracks that value and
 * null-checks only the following read or call, then removes the {@code opt} invocation.
 * If weaving does not run, {@code opt} returns its argument and a later dereference still
 * throws {@link NullPointerException}.
 * </p>
 * <p>
 * This type is an implementation detail of the toolchain. Call it only from code the
 * preprocessor generated.
 * </p>
 */
public final class OptChainOps {

    private OptChainOps() {
    }

    /**
     * Returns {@code value}. The weaver treats the result as an optional-chain receiver.
     *
     * @param <T> the receiver type
     * @param value the receiver expression, which may be {@code null}
     * @return {@code value}
     */
    public static <T> T opt(T value) {
        return value;
    }
}
