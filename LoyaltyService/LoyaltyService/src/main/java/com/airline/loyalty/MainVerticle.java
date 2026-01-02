package com.airline.loyalty;

import com.airline.loyalty.handler.HealthCheckHandler;
import com.airline.loyalty.handler.PointsQuoteHandler;
import com.airline.loyalty.service.FxServiceClient;
import com.airline.loyalty.service.PointsCalculationService;
import com.airline.loyalty.service.PromoServiceClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.ext.web.handler.TimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MainVerticle is the primary deployment unit of the Vert.x application.
 *
 */
public class MainVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);
    private HttpServer server;
    private HttpServer metricsServer;

    @Override
    public void start(Promise<Void> startPromise) {
        JsonObject config = config();
        
        // Initialize metrics
        PrometheusMeterRegistry meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        
        // Initialize services
        FxServiceClient fxService = new FxServiceClient(vertx, config);
        PromoServiceClient promoService = new PromoServiceClient(vertx, config);
        PointsCalculationService calculationService = new PointsCalculationService(
            fxService, promoService, config
        );

        // Create routers
        Router router = createMainRouter(calculationService, meterRegistry);
        Router metricsRouter = createMetricsRouter(meterRegistry);

        // Start main server
        JsonObject httpConfig = config.getJsonObject("http");
        int port = httpConfig.getInteger("port");
        String host = httpConfig.getString("host");

        vertx.createHttpServer()
            .requestHandler(router)
            .listen(port, host)
            .onSuccess(s -> {
                server = s;
                logger.info("Points Quote Service listening on {}:{}", host, port);
                
                // Start metrics server
                startMetricsServer(metricsRouter, config, startPromise);
            })
            .onFailure(err -> {
                logger.error("Failed to start server", err);
                startPromise.fail(err);
            });
    }

    private Router createMainRouter(PointsCalculationService calculationService, MeterRegistry meterRegistry) {
        Router router = Router.router(vertx);

        // Global handlers
        router.route().handler(LoggerHandler.create());
        router.route().handler(BodyHandler.create());
        router.route().handler(TimeoutHandler.create(10000));
        router.route().handler(ctx -> {
            String requestId = ctx.request().getHeader("X-Request-ID");
            if (requestId == null || requestId.isEmpty()) {
                requestId = java.util.UUID.randomUUID().toString();
            }
            ctx.put("X-Request-ID", requestId);
            ctx.response().putHeader("X-Request-ID", requestId);
            ctx.next();
        });

        // Routes
        router.post("/v1/points/quote").handler(new PointsQuoteHandler(calculationService, meterRegistry));
        router.get("/health").handler(new HealthCheckHandler());

        // Error handler
        router.errorHandler(500, ctx -> {
            logger.error("Unhandled error", ctx.failure());
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end("{\"error\":\"INTERNAL_ERROR\",\"message\":\"An unexpected error occurred\"}");
        });

        return router;
    }

    private Router createMetricsRouter(PrometheusMeterRegistry meterRegistry) {
        Router router = Router.router(vertx);
        router.get("/metrics").handler(ctx -> {
            ctx.response()
                .putHeader("Content-Type", "text/plain")
                .end(meterRegistry.scrape());
        });
        return router;
    }

    private void startMetricsServer(Router metricsRouter, JsonObject config, Promise<Void> startPromise) {
        JsonObject obsConfig = config.getJsonObject("observability");
        if (!obsConfig.getBoolean("metricsEnabled")) {
            startPromise.complete();
            return;
        }

        int metricsPort = obsConfig.getInteger("metricsPort");
        vertx.createHttpServer()
            .requestHandler(metricsRouter)
            .listen(metricsPort, "0.0.0.0")
            .onSuccess(s -> {
                metricsServer = s;
                logger.info("Metrics server listening on port {}", metricsPort);
                startPromise.complete();
            })
            .onFailure(err -> {
                logger.warn("Failed to start metrics server, continuing without metrics", err);
                startPromise.complete();
            });
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        if (server != null) {
            server.close().onComplete(ar -> {
                if (metricsServer != null) {
                    metricsServer.close().onComplete(stopPromise);
                } else {
                    stopPromise.complete();
                }
            });
        } else {
            stopPromise.complete();
        }
    }
}