package io.ch.java.opt.chain.demo;

/**
 * Runnable demo for optional-chain weaving.
 * <p>
 * Prints the four city/name scenarios (full chain, null address, null city, null user)
 * and primitive short-circuit defaults ({@code boolean} / {@code long} / {@code double}).
 * </p>
 */
public final class OptChainDemoMain {

    private OptChainDemoMain() {
    }

    public static void main(String[] args) {
        OptChainExamples examples = new OptChainExamples();

        System.out.println("=== Opt Chain Demo (u?.getAddress()?.getCity()?.getName()) ===");
        System.out.println();

        User full = new User(new Address(new City("Shanghai")));
        printCase("1) Full chain (user → address → city)", full, examples);

        User noAddress = new User(null);
        printCase("2) Address is null", noAddress, examples);

        User noCity = new User(new Address(null));
        printCase("3) City is null", noCity, examples);

        printCase("4) User is null", null, examples);

        System.out.println("=== Primitive defaults (false / 0L / 0.0, City reference) ===");
        System.out.println();
        printPrimitives("5) User is null", null, examples);
        printPrimitives("6) City is null, address is primary", new User(new Address(null, true)), examples);
        City measured = new City("Shanghai", 21L, 31.5);
        printPrimitives("7) Full chain", new User(new Address(measured, true)), examples);

        System.out.println("Done. No NullPointerException should occur above.");
    }

    private static void printPrimitives(String title, User user, OptChainExamples examples) {
        System.out.println(title);
        System.out.println("   primaryAddress => " + examples.primaryAddress(user));
        System.out.println("   cityId         => " + examples.cityId(user));
        System.out.println("   cityLatitude   => " + examples.cityLatitude(user));
        City city = examples.city(user);
        System.out.println("   city           => " + (city == null ? "null" : city.getName()));
        System.out.println();
    }

    private static void printCase(String title, User user, OptChainExamples examples) {
        System.out.println(title);
        String city = examples.cityOf(user);
        int length = examples.nameLength(user);
        System.out.println("   cityOf     => " + (city == null ? "null" : city));
        System.out.println("   nameLength => " + length);
        System.out.println();
    }
}
