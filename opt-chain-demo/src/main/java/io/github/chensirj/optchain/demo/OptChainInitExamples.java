package io.github.chensirj.optchain.demo;

/**
 * {@code ?.} in a constructor body, an instance field initializer ({@code <init>}),
 * and static initializers ({@code <clinit>}).
 */
public class OptChainInitExamples {

    public final User userField = new User(new Address(new City("FieldCity")));
    public final String fieldCaptured = userField?.getAddress()?.getCity()?.getName();

    public static final User STATIC_USER = new User(new Address(new City("StaticCity")));
    public static final String STATIC_OK = STATIC_USER?.getAddress()?.getCity()?.getName();
    public static final User STATIC_NULL = null;
    public static final String STATIC_MISSING = STATIC_NULL?.getAddress()?.getCity()?.getName();

    public final String ctorBody;

    public OptChainInitExamples(User user) {
        this.ctorBody = user?.getAddress()?.getCity()?.getName();
    }

    public String plainCtor(User user) {
        return new PlainHolder(user).value;
    }

    public static class PlainHolder {
        public final String value;

        public PlainHolder(User user) {
            String keep = user?.getAddress() == null ? "" : "x";
            this.value = user.getAddress().getCity().getName();
        }
    }
}
