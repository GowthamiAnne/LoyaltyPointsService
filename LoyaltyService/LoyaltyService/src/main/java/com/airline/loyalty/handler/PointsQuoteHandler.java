package com.airline.loyalty.handler;

import com.airline.loyalty.exception.ValidationException;
import com.airline.loyalty.model.ErrorResponse;
import com.airline.loyalty.model.PointsQuoteRequest;
import com.airline.loyalty.service.PointsCalculationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.vertx.core.Handler;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import jakarta.validation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.time.Instant;
import java.util.Set;

public class PointsQuoteHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(PointsQuoteHandler.class);

    private final PointsCalculationService calculationService;
    private final Counter requestCounter;
    private final Counter errorCounter;
    private final Timer requestTimer;
    private final Validator validator;

    public PointsQuoteHandler(PointsCalculationService calculationService, MeterRegistry meterRegistry) {
        this.calculationService = calculationService;

        // Metrics
        this.requestCounter = Counter.builder("points_quote_requests_total")
                .description("Total number of points quote requests")
                .register(meterRegistry);

        this.errorCounter = Counter.builder("points_quote_errors_total")
                .description("Total number of points quote errors")
                .register(meterRegistry);

        this.requestTimer = Timer.builder("points_quote_duration_seconds")
                .description("Points quote request duration")
                .register(meterRegistry);

        // Hibernate Validator
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        this.validator = factory.getValidator();
    }

    @Override
    public void handle(RoutingContext ctx) {
        requestCounter.increment();
        Timer.Sample sample = Timer.start();

        PointsQuoteRequest request;

        // 1️⃣ Explicit JSON parsing
        try {
            request = ctx.body().asJsonObject().mapTo(PointsQuoteRequest.class);
        } catch (DecodeException e) {
            sample.stop(requestTimer);
            logger.warn("Malformed JSON: {}", e.getMessage());
            sendError(ctx, 400, "BAD_REQUEST", "Malformed JSON: " + e.getMessage());
            return;
        }

        // 2️⃣ Validate request fields
        Set<ConstraintViolation<PointsQuoteRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            sample.stop(requestTimer);
            StringBuilder sb = new StringBuilder();
            for (ConstraintViolation<PointsQuoteRequest> v : violations) {
                sb.append(v.getPropertyPath()).append(" ").append(v.getMessage()).append("; ");
            }
            logger.warn("Validation failed: {}", sb);
            sendError(ctx, 400, "VALIDATION_ERROR", sb.toString());
            return;
        }

        // 3️⃣ Process valid request
        logger.info("Processing points quote request for {} {} in {}",
                request.getFareAmount(), request.getCurrency(), request.getCabinClass());

        calculationService.calculatePoints(request)
                .onSuccess(calculation -> {
                    sample.stop(requestTimer);
                    ctx.response()
                            .setStatusCode(200)
                            .putHeader("Content-Type", "application/json")
                            .end(Json.encode(calculation.toResponse()));
                    logger.info("Points quote successful: {} total points", calculation.getTotalPoints());
                })
                .onFailure(err -> {
                    sample.stop(requestTimer);
                    handleError(ctx, err);
                });
    }

    private void handleError(RoutingContext ctx, Throwable err) {
        errorCounter.increment();

        if (err instanceof ValidationException) {
            logger.warn("Validation error: {}", err.getMessage());
            sendError(ctx, 400, "VALIDATION_ERROR", err.getMessage());
        } else if (err instanceof java.util.concurrent.TimeoutException
                || err.getCause() instanceof java.util.concurrent.TimeoutException) {
            logger.warn("Timeout error: {}", err.getMessage());
            sendError(ctx, 504, "TIMEOUT_ERROR", "External service timeout occurred");
        } else {
            logger.error("Unexpected error processing points quote", err);
            sendError(ctx, 500, "INTERNAL_ERROR", "An error occurred processing your request");
        }
    }

    private void sendError(RoutingContext ctx, int statusCode, String error, String message) {
        ErrorResponse errorResponse = new ErrorResponse(error, message, Instant.now().toString());
        ctx.response()
                .setStatusCode(statusCode)
                .putHeader("Content-Type", "application/json")
                .end(Json.encode(errorResponse));
    }
}
