package io.ch.java.opt.chain.demo;

/**
 * Runnable demo for optional-chain weaving.
 * <p>
 * Prints four scenarios: full chain, null address, null city, null user.
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

        System.out.println("Done. No NullPointerException should occur above.");
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
