package com.airline.loyalty.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PointsQuoteRequest(
    @JsonProperty("fareAmount") Double fareAmount,
    @JsonProperty("currency") String currency,
    @JsonProperty("cabinClass") String cabinClass,
    @JsonProperty("customerTier") String customerTier,
    @JsonProperty("promoCode") String promoCode
) {}
