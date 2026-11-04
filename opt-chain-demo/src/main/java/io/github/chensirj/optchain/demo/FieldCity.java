package io.github.chensirj.optchain.demo;

/**
 * Public-field stand-in for {@link City}. Chains such as {@code city?.name} compile to {@code GETFIELD}.
 */
public class FieldCity {
    public String name;
    public long id;
    public double latitude;

    public FieldCity(String name) {
        this(name, 0L, 0.0);
    }

    public FieldCity(String name, long id, double latitude) {
        this.name = name;
        this.id = id;
        this.latitude = latitude;
    }
}
