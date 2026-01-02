package com.airline.loyalty.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PointsQuoteResponse(
	    @JsonProperty("basePoints") Integer basePoints,
	    @JsonProperty("tierBonus") Integer tierBonus,
	    @JsonProperty("promoBonus") Integer promoBonus,
	    @JsonProperty("totalPoints") Integer totalPoints,
	    @JsonProperty("effectiveFxRate") Double effectiveFxRate,
	    @JsonProperty("warnings") List<String> warnings
	) {}