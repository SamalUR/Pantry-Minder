package com.example.pantryminder;

public class Pantry {
    private String id;
    private String name;

    public Pantry(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}