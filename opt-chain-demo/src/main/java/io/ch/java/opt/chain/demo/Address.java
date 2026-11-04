package io.ch.java.opt.chain.demo;

public class Address {
    private final City city;

    public Address(City city) {
        this.city = city;
    }

    public City getCity() {
        return city;
    }
}
