package com.airline.loyalty;

import com.airline.loyalty.model.CabinClass;
import com.airline.loyalty.model.CustomerTier;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.http.ContentType;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;

import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.beans.Customizer;
import java.time.LocalDate;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class PointsQuoteIntegrationTest extends BaseComponentTest {

    @Test
    void shouldHandleCompleteInternationalBookingScenario(Vertx vertx, VertxTestContext testContext) {
        // Scenario: International premium booking with active promo
        fxServiceMock.stubFor(get(urlPathEqualTo("/v1/rates"))
            .withQueryParam("from", equalTo("GBP"))
            .withQueryParam("to", equalTo("USD"))
            .willReturn(aResponse().withStatus(200).withBody("""
                {"fromCurrency":"GBP","toCurrency":"USD","rate":1.27,"timestamp":"2025-01-15T10:00:00Z"}
                """)));

        String expiryDate = LocalDate.now().plusDays(15).toString();
        promoServiceMock.stubFor(get(urlPathEqualTo("/v1/promos/WINTER25"))
            .willReturn(aResponse().withStatus(200).withBody(String.format("""
                {
                    "promoCode": "WINTER25",
                    "bonusMultiplier": 0.30,
                    "expiryDate": "%s",
                    "active": true
                }
                """, expiryDate))));

        given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body("""
                {
                    "fareAmount": 2500.00,
                    "currency": "GBP",
                    "cabinClass": "BUSINESS",
                    "customerTier": "GOLD",
                    "promoCode": "WINTER25"
                }
                """)
            .when()
            .post("/v1/points/quote")
            .then()
            .statusCode(200)
            .body("basePoints", equalTo(3175))  // 2500 * 1.27
            .body("tierBonus", equalTo(952))    // 3175 * 0.30
            .body("promoBonus", equalTo(952))   // 3175 * 0.30
            .body("totalPoints", equalTo(5079))
            .body("effectiveFxRate", equalTo(1.27f))
            .body("warnings", empty());

        testContext.completeNow();
    }

    @Test
    void shouldHandleMultipleTiersCabinsAndCurrencies(Vertx vertx, VertxTestContext testContext) {
        // Test with JPY (high value currency)
        fxServiceMock.stubFor(get(urlPathEqualTo("/v1/rates"))
            .withQueryParam("from", equalTo("JPY"))
            .withQueryParam("to", equalTo("USD"))
            .willReturn(aResponse().withStatus(200).withBody("""
                {"fromCurrency":"JPY","toCurrency":"USD","rate":0.0067,"timestamp":"2025-01-15T10:00:00Z"}
                """)));

        promoServiceMock.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(404)));

        given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body("""
                {
                    "fareAmount": 150000.00,
                    "currency": "JPY",
                    "cabinClass": "FIRST",
                    "customerTier": "PLATINUM",
                    "promoCode": null
                }
                """)
            .when()
            .post("/v1/points/quote")
            .then()
            .statusCode(200)
            .body("basePoints", equalTo(1005))  // 150000 * 0.0067
            .body("tierBonus", equalTo(502))    // 1005 * 0.50
            .body("totalPoints", equalTo(1507));

        testContext.completeNow();
    }

    @Test
    void shouldHandleNoneTierWithoutPromo(Vertx vertx, VertxTestContext testContext) {
        fxServiceMock.stubFor(get(urlPathEqualTo("/v1/rates"))
            .willReturn(aResponse().withStatus(200).withBody("""
                {"fromCurrency":"USD","toCurrency":"USD","rate":1.0,"timestamp":"2025-01-15T10:00:00Z"}
                """)));

        given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body("""
                {
                    "fareAmount": 500.00,
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
            .body("basePoints", equalTo(500))
            .body("tierBonus", equalTo(0))
            .body("promoBonus", equalTo(0))
            .body("totalPoints", equalTo(500));

        testContext.completeNow();
    }
    
    
    @ParameterizedTest
    @EnumSource(CabinClass.class)
    void shouldAcceptAllValidCabinClasses(CabinClass cabin, Vertx vertx, VertxTestContext ctx) {
        // Stub FX service
        fxServiceMock.stubFor(get(urlPathEqualTo("/v1/rates"))
            .willReturn(aResponse().withStatus(200)
                .withBody("""
                    {"fromCurrency":"USD","toCurrency":"USD","rate":1.0,"timestamp":"2025-01-15T10:00:00Z"}
                """)));

        // Stub promo service
        promoServiceMock.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(404)));

        // Send request
        given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body("""
                {
                    "fareAmount": 1000,
                    "currency": "USD",
                    "cabinClass": "%s",
                    "customerTier": "SILVER",
                    "promoCode": null
                }
                """.formatted(cabin))
            .when()
            .post("/v1/points/quote")
            .then()
            .statusCode(200)
            .body("basePoints", equalTo(1000))
            .body("tierBonus", equalTo(150))   // SILVER tier
            .body("totalPoints", equalTo(1150))
            .body("warnings", empty());

        ctx.completeNow();
    }
    
    @ParameterizedTest
    @EnumSource(CustomerTier.class)
    void shouldAcceptAllValidCabinClasses(CustomerTier customerTier, Vertx vertx, VertxTestContext ctx) {
        // Stub FX service
        fxServiceMock.stubFor(get(urlPathEqualTo("/v1/rates"))
            .willReturn(aResponse().withStatus(200)
                .withBody("""
                    {"fromCurrency":"USD","toCurrency":"USD","rate":1.0,"timestamp":"2025-01-15T10:00:00Z"}
                """)));

       

        // Send request
        given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body("""
                {
                    "fareAmount": 1000,
                    "currency": "USD",
                    "cabinClass": "ECONOMY",
                    "customerTier": "%s",
                    "promoCode": null
                }
                """.formatted(customerTier))
            .when()
            .post("/v1/points/quote")
            .then()
            .statusCode(200)
            .body("basePoints", equalTo(1000))
            .body("warnings", empty());

        ctx.completeNow();
    }
    
    @Test
    void shouldHandleInvalidFareAmount(Vertx vertx, VertxTestContext testContext) {
        fxServiceMock.stubFor(get(urlPathEqualTo("/v1/rates"))
            .willReturn(aResponse().withStatus(200).withBody("""
                {"fromCurrency":"USD","toCurrency":"USD","rate":1.0,"timestamp":"2025-01-15T10:00:00Z"}
                """)));

        given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body("""
                {
                    "fareAmount": 0,
                    "currency": "USD",
                    "cabinClass": "ECONOMY",
                    "customerTier": "NONE",
                    "promoCode": null
                }
                """)
            .when()
            .post("/v1/points/quote")
            .then()
            .statusCode(400)
           
            .body("message", Matchers.containsString("Fare amount must be greater than zero"));

        testContext.completeNow();
    }
    
    @Test
    void shouldHandleInvalidCurrencyCode(Vertx vertx, VertxTestContext testContext) {
        fxServiceMock.stubFor(get(urlPathEqualTo("/v1/rates"))
            .willReturn(aResponse().withStatus(200).withBody("""
                {"fromCurrency":"USD","toCurrency":"USD","rate":1.0,"timestamp":"2025-01-15T10:00:00Z"}
                """)));

        given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body("""
                {
                    "fareAmount": 1000,
                    "currency": "",
                    "cabinClass": "ECONOMY",
                    "customerTier": "SILVER",
                    "promoCode": null
                }
                """)
            .when()
            .post("/v1/points/quote")
            .then()
            .statusCode(400)
           
            .body("message", Matchers.containsString("Invalid currency code"));

        testContext.completeNow();
    }
    
}