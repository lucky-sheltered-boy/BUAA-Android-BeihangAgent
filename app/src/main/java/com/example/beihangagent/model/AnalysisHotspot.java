package com.example.beihangagent.model;

public class AnalysisHotspot {

    public enum Type {
        ERROR,
        TOPIC
    }

    public final String label;
    public final String sample;
    public final int count;
    public final long lastSeen;
    public final Type type;

    public AnalysisHotspot(String label, String sample, int count, long lastSeen, Type type) {
        this.label = label;
        this.sample = sample;
        this.count = count;
        this.lastSeen = lastSeen;
        this.type = type;
    }
}
