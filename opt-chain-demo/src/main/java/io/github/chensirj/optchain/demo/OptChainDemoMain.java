package io.github.chensirj.optchain.demo;

/**
 * Runnable demo for optional-chain weaving.
 * <p>
 * Prints the four city/name scenarios (full chain, null address, null city, null user),
 * primitive short-circuit defaults ({@code boolean} / {@code long} / {@code double}),
 * the same cases on public fields ({@code u?.address?.city?.name}),
 * and a precise-site check where a plain {@code .} still throws.
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

        System.out.println("=== Field chains (u?.address?.city?.name) ===");
        System.out.println();
        FieldUser fieldFull = new FieldUser(new FieldAddress(new FieldCity("Shanghai", 21L, 31.5), true));
        printFieldCase("8) Full field chain", fieldFull, examples);
        printFieldCase("9) Address field is null", new FieldUser(null), examples);
        printFieldCase(
                "10) City field is null, address is primary",
                new FieldUser(new FieldAddress(null, true)),
                examples);
        printFieldCase("11) User is null", null, examples);

        System.out.println("=== Precise ?. (plain '.' still throws) ===");
        System.out.println();
        OptChainPreciseExamples precise = new OptChainPreciseExamples();
        System.out.println("   twoCities(null, null) => " + precise.twoCities(null, null));
        System.out.println("   staticCity(null)      => " + OptChainPreciseExamples.staticCity(null));
        System.out.println("   fromLambda(Shanghai)  => " + OptChainPreciseExamples.fromLambda(full));
        try {
            precise.plainCity(null);
            System.out.println("   plainCity(null)       => (no exception)");
        } catch (NullPointerException ex) {
            System.out.println("   plainCity(null)       => NullPointerException");
        }
        System.out.println();

        System.out.println("Done. Cases 1-11 must not throw. plainCity(null) must throw.");
    }

    private static void printFieldCase(String title, FieldUser user, OptChainExamples examples) {
        System.out.println(title);
        String city = examples.fieldCityName(user);
        System.out.println("   fieldCityName     => " + (city == null ? "null" : city));
        System.out.println("   fieldNameLength   => " + examples.fieldNameLength(user));
        System.out.println("   fieldPrimary      => " + examples.fieldPrimary(user));
        System.out.println("   fieldCityId       => " + examples.fieldCityId(user));
        System.out.println("   fieldCityLatitude => " + examples.fieldCityLatitude(user));
        FieldCity fieldCity = examples.fieldCity(user);
        System.out.println("   fieldCity         => " + (fieldCity == null ? "null" : fieldCity.name));
        System.out.println();
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
