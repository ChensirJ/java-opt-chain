package io.ch.java.opt.chain.demo;

/**
 * Public-field stand-in for {@link User}. Optional chains read {@code address} directly.
 */
public class FieldUser {
    public FieldAddress address;

    public FieldUser(FieldAddress address) {
        this.address = address;
    }
}
