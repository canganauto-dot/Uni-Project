package com.professoros.model;

public enum ThemeMode {
    LIGHT, DARK, SYSTEM;
    public boolean dark(boolean systemDark) { return this == DARK || this == SYSTEM && systemDark; }
    public ThemeMode next() { return values()[(ordinal()+1)%values().length]; }
    @Override public String toString() { return switch(this) { case LIGHT -> "Light"; case DARK -> "Dark"; case SYSTEM -> "System"; }; }
}
