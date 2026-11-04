package io.ch.java.opt.chain.demo;

/**
 * Public-field stand-in for {@link Address}.
 */
public class FieldAddress {
    public FieldCity city;
    public boolean primary;

    public FieldAddress(FieldCity city) {
        this(city, false);
    }

    public FieldAddress(FieldCity city, boolean primary) {
        this.city = city;
        this.primary = primary;
    }
}
