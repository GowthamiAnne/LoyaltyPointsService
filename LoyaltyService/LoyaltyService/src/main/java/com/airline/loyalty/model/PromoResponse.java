package com.airline.loyalty.model;



import com.fasterxml.jackson.annotation.JsonProperty;

public record PromoResponse(
    @JsonProperty("promoCode") String promoCode,
    @JsonProperty("bonusMultiplier") Double bonusMultiplier,
    @JsonProperty("expiryDate") String expiryDate,
    @JsonProperty("active") Boolean active
) {}