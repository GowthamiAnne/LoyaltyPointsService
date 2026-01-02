package com.airline.loyalty.service;

import com.airline.loyalty.exception.ExternalServiceException;
import com.airline.loyalty.model.FxRateResponse;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FxServiceClient provides a client abstraction for interacting with
 * an external Foreign Exchange (FX) service.
 *
 * <p>This client is responsible to retrieve exchange rate.
 * 
 */
public class FxServiceClient {
    private static final Logger logger = LoggerFactory.getLogger(FxServiceClient.class);
    private final WebClient client;
    private final String host;
    private final int port;
    private final String path;
    private final int timeout;
    private final int maxRetries;

    public FxServiceClient(Vertx vertx, JsonObject config) {
        JsonObject fxConfig = config.getJsonObject("fxService");
        this.host = fxConfig.getString("host");
        this.port = fxConfig.getInteger("port");
        this.path = fxConfig.getString("path");
        this.timeout = fxConfig.getInteger("timeout");
        this.maxRetries = fxConfig.getInteger("retries");

        WebClientOptions options = new WebClientOptions()
            .setDefaultHost(host)
            .setDefaultPort(port)
            .setSsl(fxConfig.getBoolean("ssl", true))
            .setConnectTimeout(timeout)
            .setIdleTimeout(timeout);

        this.client = WebClient.create(vertx, options);
    }

    public Future<Double> getExchangeRate(String fromCurrency, String toCurrency) {
        return getExchangeRateWithRetry(fromCurrency, toCurrency, 0);
    }

    private Future<Double> getExchangeRateWithRetry(String fromCurrency, String toCurrency, int attempt) {
        logger.debug("Fetching FX rate {} -> {} (attempt {})", fromCurrency, toCurrency, attempt + 1);

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
                        "FX service returned status " + response.statusCode(),
                        null
                    ));
                }
            })
            .recover(err -> {
                if (attempt < maxRetries) {
                    logger.warn("FX service call failed, retrying... ({})", err.getMessage());
                    return getExchangeRateWithRetry(fromCurrency, toCurrency, attempt + 1);
                } else {
                    logger.error("FX service call failed after {} attempts", maxRetries + 1, err);
                    return Future.failedFuture(new ExternalServiceException(
                        "Failed to fetch exchange rate after " + (maxRetries + 1) + " attempts",
                        err
                    ));
                    
                }
            });
    }
}