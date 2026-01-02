package com.airline.loyalty.service;

import com.airline.loyalty.model.PromoResponse;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * PromoServiceClient provides a client abstraction for interacting with
 * the Promotion Service.
 **/
public class PromoServiceClient {
    private static final Logger logger = LoggerFactory.getLogger(PromoServiceClient.class);
    private final WebClient client;
    private final String host;
    private final int port;
    private final String path;
    private final int timeout;

    public PromoServiceClient(Vertx vertx, JsonObject config) {
        JsonObject promoConfig = config.getJsonObject("promoService");
        this.host = promoConfig.getString("host");
        this.port = promoConfig.getInteger("port");
        this.path = promoConfig.getString("path");
        this.timeout = promoConfig.getInteger("timeout");

        WebClientOptions options = new WebClientOptions()
            .setDefaultHost(host)
            .setDefaultPort(port)
            .setSsl(promoConfig.getBoolean("ssl", true))
            .setConnectTimeout(timeout)
            .setIdleTimeout(timeout);

        this.client = WebClient.create(vertx, options);
    }

    public Future<Optional<PromoResponse>> getPromoDetails(String promoCode) {
        if (promoCode == null || promoCode.isBlank()) {
            return Future.succeededFuture(Optional.empty());
        }

        logger.debug("Fetching promo details for code: {}", promoCode);

        return client.get(path + "/" + promoCode)
            .timeout(timeout)
            .send()
            .compose(response -> {
                if (response.statusCode() == 200) {
                    PromoResponse promo = response.bodyAsJson(PromoResponse.class);
                    logger.info("Promo details retrieved: {}", promoCode);
                    return Future.succeededFuture(Optional.<PromoResponse>of(promo));
                } else if (response.statusCode() == 404) {
                    logger.info("Promo code not found: {}", promoCode);
                    return Future.succeededFuture(Optional.<PromoResponse>empty());
                } else {
                    logger.warn("Promo service returned unexpected status: {}", response.statusCode());
                    return Future.succeededFuture(Optional.<PromoResponse>empty());
                }
            })
            .recover(err -> {
                logger.warn("Promo service call failed, continuing without promo: {}", err.getMessage());
                return Future.succeededFuture(Optional.<PromoResponse>empty());
            });
    }
}
