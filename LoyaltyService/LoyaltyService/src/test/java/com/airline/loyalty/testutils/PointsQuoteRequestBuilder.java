package com.airline.loyalty.testutils;

import com.airline.loyalty.model.PointsQuoteRequest;

public class PointsQuoteRequestBuilder {

    private Double fareAmount = 1000.0;
    private String currency = "USD";
    private String cabinClass = "ECONOMY";
    private String customerTier = "SILVER";
    private String promoCode = null;

    public PointsQuoteRequestBuilder withFareAmount(Double fareAmount) {
        this.fareAmount = fareAmount;
        return this;
    }

    public PointsQuoteRequestBuilder withCurrency(String currency) {
        this.currency = currency;
        return this;
    }

    public PointsQuoteRequestBuilder withCabinClass(String cabinClass) {
        this.cabinClass = cabinClass;
        return this;
    }

    public PointsQuoteRequestBuilder withCustomerTier(String customerTier) {
        this.customerTier = customerTier;
        return this;
    }

    public PointsQuoteRequestBuilder withPromoCode(String promoCode) {
        this.promoCode = promoCode;
        return this;
    }

    // Build the record
    public PointsQuoteRequest build() {
        return new PointsQuoteRequest(fareAmount, currency, cabinClass, customerTier, promoCode);
    }
}
