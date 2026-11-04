package io.github.chensirj.optchain.demo;

import java.util.function.Supplier;

/**
 * Precise {@code ?.} sites mixed with plain dots, including static methods,
 * lambdas, and nested classes. Only the {@code ?.} receiver is null-checked.
 */
public class OptChainPreciseExamples {

    /**
     * The {@code ?.} read is optional. The plain {@code .} chain still throws when {@code u} is null.
     */
    public String plainCity(User u) {
        if (u?.getAddress() == null) {
            // Optional read only. The return below is a plain chain.
        }
        return u.getAddress().getCity().getName();
    }

    public String optionalCity(User u) {
        return u?.getAddress()?.getCity()?.getName();
    }

    /**
     * Unlike JavaScript, a plain {@code .} after {@code ?.} is still evaluated.
     * A null {@code u} makes {@code getAddress()} return null, then {@code getCity()} throws.
     */
    public String optionalThenPlain(User u) {
        return u?.getAddress().getCity().getName();
    }

    /** Two independent chains on one line. */
    public String twoCities(User a, User b) {
        return a?.getAddress()?.getCity()?.getName() + ":" + b?.getAddress()?.getCity()?.getName();
    }

    /** A plain chain and an optional chain on one line. */
    public String mixedLine(User plain, User optional) {
        return plain.getAddress().getCity().getName() + ":" + optional?.getAddress()?.getCity()?.getName();
    }

    public static String staticCity(User u) {
        return u?.getAddress()?.getCity()?.getName();
    }

    public static String fromLambda(User u) {
        Supplier<String> supplier = () -> u?.getAddress()?.getCity()?.getName();
        return supplier.get();
    }

    public String fromAnonymous(User u) {
        Supplier<String> supplier = new Supplier<>() {
            @Override
            public String get() {
                return u?.getAddress()?.getCity()?.getName();
            }
        };
        return supplier.get();
    }

    public String fromInner(User u) {
        class Inner {
            String city() {
                return u?.getAddress()?.getCity()?.getName();
            }
        }
        return new Inner().city();
    }
}
