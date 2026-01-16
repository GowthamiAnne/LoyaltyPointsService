package com.airline.loyalty.service;

import com.airline.loyalty.exception.ExternalServiceException;
import com.airline.loyalty.model.FxRateResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FxServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(FxServiceClient.class);

    private final Vertx vertx;
    private final WebClient client;
    private final String path;
    private final int timeout;
    private final int maxRetries;
    private final long initialBackoffMillis;
    private final CircuitBreaker circuitBreaker;

    private final Counter retryCounter;
    private final Counter failureCounter;

    public FxServiceClient(Vertx vertx, JsonObject config, MeterRegistry meterRegistry) {
        this.vertx = vertx;
        JsonObject fxConfig = config.getJsonObject("fxService");

        String host = fxConfig.getString("host");
        int port = fxConfig.getInteger("port");
        this.path = fxConfig.getString("path");
        this.timeout = fxConfig.getInteger("timeout", 3000);
        this.maxRetries = fxConfig.getInteger("retries", 3);
        this.initialBackoffMillis = fxConfig.getLong("initialBackoffMillis", 100L);

        // WebClient configuration
        WebClientOptions options = new WebClientOptions()
                .setDefaultHost(host)
                .setDefaultPort(port)
                .setSsl(fxConfig.getBoolean("ssl", true))
                .setTrustAll(fxConfig.getBoolean("trustAll", false))
                .setConnectTimeout(timeout)
                .setIdleTimeout(timeout);

        this.client = WebClient.create(vertx, options);

        // Circuit breaker configuration
        this.circuitBreaker = CircuitBreaker.create("fx-service-cb", vertx,
                new CircuitBreakerOptions()
                        .setMaxFailures(5)
                        .setTimeout(timeout)
                        .setFallbackOnFailure(true)
                        .setResetTimeout(10000)
        );

        // Metrics
        this.retryCounter = meterRegistry != null ? Counter.builder("fx_service_retries_total")
                .description("Total number of FX retries")
                .register(meterRegistry) : null;

        this.failureCounter = meterRegistry != null ? Counter.builder("fx_service_failures_total")
                .description("Total number of FX failures")
                .register(meterRegistry) : null;
    }

    /**
     * Public method to get FX rate
     */
    public Future<Double> getExchangeRate(String fromCurrency, String toCurrency) {
        return getExchangeRateWithRetry(fromCurrency, toCurrency, 0, initialBackoffMillis);
    }

    /**
     * Retry logic with exponential backoff and circuit breaker
     */
    private Future<Double> getExchangeRateWithRetry(String fromCurrency, String toCurrency,
                                                    int attempt, long backoffMillis) {
        Promise<Double> promise = Promise.promise();

        circuitBreaker.executeWithFallback(
                cbFuture -> {
                    sendFxRequest(fromCurrency, toCurrency)
                            .onSuccess(cbFuture::complete)
                            .onFailure(err -> {
                                if (attempt < maxRetries) {
                                    if (retryCounter != null) retryCounter.increment();
                                    long nextBackoff = backoffMillis * 2;
                                    logger.warn("FX call failed ({}), retrying in {} ms (attempt {}/{})",
                                            err.getMessage(), backoffMillis, attempt + 1, maxRetries);

                                    // Type-safe retry
                                    vertx.setTimer(backoffMillis, t ->
                                        getExchangeRateWithRetry(fromCurrency, toCurrency, attempt + 1, nextBackoff)
                                                .onComplete(ar -> {
                                                    if (ar.succeeded()) {
                                                        cbFuture.complete(ar.result());
                                                    } else {
                                                        cbFuture.fail(ar.cause());
                                                    }
                                                })
                                    );
                                } else {
                                    if (failureCounter != null) failureCounter.increment();
                                    logger.error("FX service call failed after {} attempts", maxRetries + 1, err);
                                    cbFuture.fail(new ExternalServiceException(
                                            "Failed to fetch exchange rate after " + (maxRetries + 1) + " attempts", err
                                    ));
                                }
                            });
                },
                fallback -> {
                    // Circuit breaker is open
                    if (failureCounter != null) failureCounter.increment();
                    promise.fail(new ExternalServiceException("FX service unavailable (circuit open)", null));
                    return null; // must return null for CircuitBreaker fallback lambda
                }
        );

        return promise.future();
    }

    /**
     * Actual HTTP request to FX service
     */
    private Future<Double> sendFxRequest(String fromCurrency, String toCurrency) {
        return client.get(path)
                .addQueryParam("from", fromCurrency)
                .addQueryParam("to", toCurrency)
                .timeout(timeout)
                .send()
                .compose(response -> {
                    if (response.statusCode() == 200) {
                        FxRateResponse fxRate = response.bodyAsJson(FxRateResponse.class);
                        logger.info("FX rate retrieved: {} -> {} = {}", fromCurrency, toCurrency, fxRate.rate());
                        return Future.succeededFuture(fxRate.rate());
                    } else {
                        return Future.failedFuture(new ExternalServiceException(
                                "FX service returned status " + response.statusCode(), null
                        ));
                    }
                });
    }
}
