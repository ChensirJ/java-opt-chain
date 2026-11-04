package io.github.chensirj.optchain.demo;

/**
 * Sample methods using source-level {@code ?.} optional chaining.
 * <p>
 * {@code opt-chain:preprocess} wraps each {@code ?.} receiver in {@code OptChainOps.opt}
 * and adds {@code @OptChain(precise = true)}. {@code opt-chain:weave} null-checks only
 * those receivers. A plain {@code .} in the same method still throws on null.
 * Getter chains ({@code getAddress()}) and public-field chains ({@code address})
 * share that rewrite. Field reads become {@code GETFIELD}, which weave already null-checks.
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

    /** Public-field twin of {@link #cityOf(User)}: {@code u?.address?.city?.name}. */
    public String fieldCityName(FieldUser u) {
        return u?.address?.city?.name;
    }

    /** Field chain plus {@code length()}; a null {@code name} yields {@code 0}. */
    public int fieldNameLength(FieldUser u) {
        return u?.address?.city?.name?.length();
    }

    /** {@code boolean} field. A null {@code address} yields {@code false}. */
    public boolean fieldPrimary(FieldUser u) {
        return u?.address?.primary;
    }

    /** {@code long} field. A null {@code city} yields {@code 0L}. */
    public long fieldCityId(FieldUser u) {
        return u?.address?.city?.id;
    }

    /** {@code double} field. A null {@code city} yields {@code 0.0}. */
    public double fieldCityLatitude(FieldUser u) {
        return u?.address?.city?.latitude;
    }

    /** Reference field. Null {@code address} or {@code city} yields {@code null}. */
    public FieldCity fieldCity(FieldUser u) {
        return u?.address?.city;
    }
}
