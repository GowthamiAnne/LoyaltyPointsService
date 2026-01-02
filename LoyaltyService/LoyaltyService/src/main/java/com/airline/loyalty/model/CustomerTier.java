package com.airline.loyalty.model;

public enum CustomerTier {
    NONE(0.0),
    SILVER(0.15),
    GOLD(0.30),
    PLATINUM(0.50);

    private final double multiplier;

    CustomerTier(double multiplier) {
        this.multiplier = multiplier;
    }

    public double getMultiplier() {
        return multiplier;
    }
}
