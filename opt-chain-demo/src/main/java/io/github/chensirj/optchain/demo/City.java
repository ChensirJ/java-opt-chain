package io.github.chensirj.optchain.demo;

public class City {
    private final String name;
    private final long id;
    private final double latitude;

    public City(String name) {
        this(name, 0L, 0.0);
    }

    public City(String name, long id, double latitude) {
        this.name = name;
        this.id = id;
        this.latitude = latitude;
    }

    public String getName() {
        return name;
    }

    public long getId() {
        return id;
    }

    public double getLatitude() {
        return latitude;
    }
}
