package io.github.chensirj.optchain.demo;

public class User {
    private final Address address;

    public User(Address address) {
        this.address = address;
    }

    public Address getAddress() {
        return address;
    }
}
