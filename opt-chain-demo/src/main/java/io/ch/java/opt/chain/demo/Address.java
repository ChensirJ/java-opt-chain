package io.ch.java.opt.chain.demo;

public class Address {
    private final City city;
    private final boolean primary;

    public Address(City city) {
        this(city, false);
    }

    public Address(City city, boolean primary) {
        this.city = city;
        this.primary = primary;
    }

    public City getCity() {
        return city;
    }

    public boolean isPrimary() {
        return primary;
    }
}
