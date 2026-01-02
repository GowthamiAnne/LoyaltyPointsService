package com.airline.loyalty;

import io.restassured.http.ContentType;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

class PointsQuoteHttpTest extends BaseComponentTest {

    @Test
    void shouldReturnCorrectContentTypeHeader(Vertx vertx, VertxTestContext testContext) {
        fxServiceMock.stubFor(get(urlPathEqualTo("/v1/rates"))
            .willReturn(aResponse().withStatus(200).withBody("""
                {"fromCurrency":"USD","toCurrency":"USD","rate":1.0,"timestamp":"2025-01-15T10:00:00Z"}
                """)));
        promoServiceMock.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(404)));

        var response = given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body("""
                {
                    "fareAmount": 1000.0,
                    "currency": "USD",
                    "cabinClass": "ECONOMY",
                    "customerTier": "NONE",
                    "promoCode": null
                }
                """)
            .when()
            .post("/v1/points/quote")
            .then()
            .statusCode(200)
            .header("Content-Type", containsString("application/json"))
            .extract().response();

        assertThat(response.header("Content-Type")).isNotNull();
        
        testContext.completeNow();
    }
    
    @Test
    void shouldPreserveRequestIdHeader(Vertx vertx, VertxTestContext testContext) {
        fxServiceMock.stubFor(get(urlPathEqualTo("/v1/rates"))
            .willReturn(aResponse().withStatus(200).withBody("""
                {"fromCurrency":"USD","toCurrency":"USD","rate":1.0,"timestamp":"2025-01-15T10:00:00Z"}
                """)));
        promoServiceMock.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(404)));

        String requestId = "test-request-123";
        var response = given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .header("X-Request-ID", requestId)
            .body("""
                {
                    "fareAmount": 1000.0,
                    "currency": "USD",
                    "cabinClass": "ECONOMY",
                    "customerTier": "SILVER",
                    "promoCode": null
                }
                """)
            .when()
            .post("/v1/points/quote")
            .then()
            .statusCode(200)
            .extract().response();

        assertThat(response.header("X-Request-ID")).isEqualTo(requestId);
        
        testContext.completeNow();
    }

    @Test
    void shouldGenerateRequestIdWhenNotProvided(Vertx vertx, VertxTestContext testContext) {
        fxServiceMock.stubFor(get(urlPathEqualTo("/v1/rates"))
            .willReturn(aResponse().withStatus(200).withBody("""
                {"fromCurrency":"USD","toCurrency":"USD","rate":1.0,"timestamp":"2025-01-15T10:00:00Z"}
                """)));
        promoServiceMock.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(404)));

        var response = given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body("""
                {
                    "fareAmount": 1000.0,
                    "currency": "USD",
                    "cabinClass": "ECONOMY",
                    "customerTier": "GOLD",
                    "promoCode": null
                }
                """)
            .when()
            .post("/v1/points/quote")
            .then()
            .statusCode(200)
            .extract().response();

        String requestId = response.header("X-Request-ID");
        assertThat(requestId).isNotNull();
        assertThat(requestId).isNotEmpty();
        
        testContext.completeNow();
    }

	
	 
}

