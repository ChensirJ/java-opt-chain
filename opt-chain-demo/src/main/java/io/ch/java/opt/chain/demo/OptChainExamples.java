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
        // When the chain short-circuits, length() is skipped and int defaults to 0.
        return u?.getAddress()?.getCity()?.getName()?.length();
    }

    /** {@code boolean} short-circuit default is {@code false}. */
    public boolean primaryAddress(User u) {
        return u?.getAddress()?.isPrimary();
    }

    /** {@code long} short-circuit default is {@code 0L}. */
    public long cityId(User u) {
        return u?.getAddress()?.getCity()?.getId();
    }

    /** {@code double} short-circuit default is {@code 0.0}. */
    public double cityLatitude(User u) {
        return u?.getAddress()?.getCity()?.getLatitude();
    }

    /**
     * Reference result: the {@link City} when every link is non-null, otherwise {@code null}.
     */
    public City city(User u) {
        return u?.getAddress()?.getCity();
    }
}
