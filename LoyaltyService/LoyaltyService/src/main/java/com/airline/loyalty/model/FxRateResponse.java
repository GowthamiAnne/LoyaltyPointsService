package com.airline.loyalty.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FxRateResponse(
    @JsonProperty("fromCurrency") String fromCurrency,
    @JsonProperty("toCurrency") String toCurrency,
    @JsonProperty("rate") Double rate,
    @JsonProperty("timestamp") String timestamp
) {}

