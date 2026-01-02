package com.airline.loyalty.handler;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class HealthCheckHandler implements Handler<RoutingContext> {
    @Override
    public void handle(RoutingContext ctx) {
        JsonObject health = new JsonObject()
            .put("status", "UP")
            .put("timestamp", System.currentTimeMillis());
        
        ctx.response()
            .setStatusCode(200)
            .putHeader("Content-Type", "application/json")
            .end(health.encode());
    }
}