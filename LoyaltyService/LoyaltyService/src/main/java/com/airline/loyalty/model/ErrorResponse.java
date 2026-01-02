package com.airline.loyalty.model;


import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ErrorResponse(
	    @JsonProperty("error") String error,
	    @JsonProperty("message") String message,
	    @JsonProperty("timestamp") String timestamp
	) {}
