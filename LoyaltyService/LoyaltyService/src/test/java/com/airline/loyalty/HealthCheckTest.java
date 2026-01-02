package com.airline.loyalty;

import io.restassured.http.ContentType;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class HealthCheckTest extends BaseComponentTest {

    @Test
    void shouldReturnHealthyStatus(Vertx vertx, VertxTestContext testContext) {
        given()
            .baseUri(baseUrl)
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("status", equalTo("UP"))
            .body("timestamp", notNullValue());

        testContext.completeNow();
    }
}