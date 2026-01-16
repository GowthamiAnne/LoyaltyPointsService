package com.airline.loyalty.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.*;

import java.io.Serializable;

public class PointsQuoteRequest implements Serializable {

    @NotNull(message = "fareAmount must not be null")
    @DecimalMin(value = "0.0", inclusive = true, message = "fareAmount must be >= 0")
    @JsonProperty("fareAmount")
    private Double fareAmount;

    @NotNull(message = "currency must not be null")
    @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO code")
    @JsonProperty("currency")
    private String currency;

    @NotNull(message = "cabinClass must not be null")
    @JsonProperty("cabinClass")
    private String cabinClass;

    @NotNull(message = "customerTier must not be null")
    @JsonProperty("customerTier")
    private String customerTier;

    @JsonProperty("promoCode")
    private String promoCode; // optional

    // Default constructor for Jackson
    public PointsQuoteRequest() {}

    // All-args constructor
    public PointsQuoteRequest(Double fareAmount, String currency, String cabinClass, String customerTier, String promoCode) {
        this.fareAmount = fareAmount;
        this.currency = currency;
        this.cabinClass = cabinClass;
        this.customerTier = customerTier;
        this.promoCode = promoCode;
    }

    // Getters and Setters
    public Double getFareAmount() { return fareAmount; }
    public void setFareAmount(Double fareAmount) { this.fareAmount = fareAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getCabinClass() { return cabinClass; }
    public void setCabinClass(String cabinClass) { this.cabinClass = cabinClass; }

    public String getCustomerTier() { return customerTier; }
    public void setCustomerTier(String customerTier) { this.customerTier = customerTier; }

    public String getPromoCode() { return promoCode; }
    public void setPromoCode(String promoCode) { this.promoCode = promoCode; }
}
