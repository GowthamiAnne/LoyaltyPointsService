package com.airline.loyalty.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class PointsCalculation {
    private final int basePoints;
    private final int tierBonus;
    private final int promoBonus;
    private final int totalPoints;
    private final double effectiveFxRate;
    private final List<String> warnings;

    private PointsCalculation(Builder builder) {
        this.basePoints = builder.basePoints;
        this.tierBonus = builder.tierBonus;
        this.promoBonus = builder.promoBonus;
        this.totalPoints = builder.totalPoints;
        this.effectiveFxRate = builder.effectiveFxRate;
        this.warnings = List.copyOf(builder.warnings);
    }

    public PointsQuoteResponse toResponse() {
        return new PointsQuoteResponse(
            basePoints,
            tierBonus,
            promoBonus,
            totalPoints,
            effectiveFxRate,
            warnings
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int basePoints;
        private int tierBonus;
        private int promoBonus;
        private int totalPoints;
        private double effectiveFxRate;
        private List<String> warnings = new ArrayList<>();

        public Builder basePoints(int basePoints) {
            this.basePoints = basePoints;
            return this;
        }

        public Builder tierBonus(int tierBonus) {
            this.tierBonus = tierBonus;
            return this;
        }

        public Builder promoBonus(int promoBonus) {
            this.promoBonus = promoBonus;
            return this;
        }

        public Builder totalPoints(int totalPoints) {
            this.totalPoints = totalPoints;
            return this;
        }

        public Builder effectiveFxRate(double effectiveFxRate) {
            this.effectiveFxRate = effectiveFxRate;
            return this;
        }

        public Builder warnings(List<String> warnings) {
            this.warnings = new ArrayList<>(warnings);
            return this;
        }

        public Builder addWarning(String warning) {
            this.warnings.add(warning);
            return this;
        }

        public PointsCalculation build() {
            return new PointsCalculation(this);
        }
    }

    // Getters
    public int getBasePoints() { return basePoints; }
    public int getTierBonus() { return tierBonus; }
    public int getPromoBonus() { return promoBonus; }
    public int getTotalPoints() { return totalPoints; }
    public double getEffectiveFxRate() { return effectiveFxRate; }
    public List<String> getWarnings() { return warnings; }
}

