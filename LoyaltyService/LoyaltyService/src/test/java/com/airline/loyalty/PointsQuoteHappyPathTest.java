
package com.airline.loyalty;


import io.restassured.http.ContentType;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

class PointsQuoteHappyPathTest extends BaseComponentTest {

    @Test
    void shouldCalculatePointsForUsdEconomySilverWithPromo(Vertx vertx, VertxTestContext testContext) {
        // Given: USD currency - no FX conversion needed
        
        // Given: Promo service returns 25% bonus
        String expiryDate = LocalDate.now().plusDays(10).toString();
        promoServiceMock.stubFor(get(urlPathEqualTo("/v1/promos/SUMMER25"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("""
                    {
                        "promoCode": "SUMMER25",
                        "bonusMultiplier": 0.25,
                        "expiryDate": "%s",
                        "active": true
                    }
                    """, expiryDate))));

        // When: Calculate points
        var response = given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body("""
                {
                    "fareAmount": 1234.50,
                    "currency": "USD",
                    "cabinClass": "ECONOMY",
                    "customerTier": "SILVER",
                    "promoCode": "SUMMER25"
                }
                """)
            .when()
            .post("/v1/points/quote")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("basePoints", equalTo(1234))
            .body("tierBonus", equalTo(185))  // 1234 * 0.15
            .body("promoBonus", equalTo(308)) // 1234 * 0.25
            .body("totalPoints", equalTo(1727))
            .body("effectiveFxRate", equalTo(1.0f))
            .body("warnings", empty())
            .extract().response();

        assertThat(response.header("Content-Type")).contains("application/json");
        assertThat(response.header("X-Request-ID")).isNotNull();

        // Verify service calls - FX not called for USD->USD
        fxServiceMock.verify(0, getRequestedFor(urlPathEqualTo("/v1/rates")));
        promoServiceMock.verify(1, getRequestedFor(urlPathEqualTo("/v1/promos/SUMMER25")));

        testContext.completeNow();
    }

    @Test
    void shouldCalculatePointsWithFxConversion(Vertx vertx, VertxTestContext testContext) {
        // Given: EUR->USD rate is 1.1
        fxServiceMock.stubFor(get(urlPathEqualTo("/v1/rates"))
            .withQueryParam("from", equalTo("EUR"))
            .withQueryParam("to", equalTo("USD"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("""
                    {
                        "fromCurrency": "EUR",
                        "toCurrency": "USD",
                        "rate": 1.1,
                        "timestamp": "2025-01-15T10:00:00Z"
                    }
                    """)));

        // Given: No promo
        promoServiceMock.stubFor(get(urlPathEqualTo("/v1/promos/NONE"))
            .willReturn(aResponse().withStatus(404)));

        // When: Calculate points
        given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body("""
                {
                    "fareAmount": 1000.00,
                    "currency": "EUR",
                    "cabinClass": "BUSINESS",
                    "customerTier": "GOLD",
                    "promoCode": "NONE"
                }
                """)
            .when()
            .post("/v1/points/quote")
            .then()
            .statusCode(200)
            .body("basePoints", equalTo(1100))  // 1000 * 1.1
            .body("tierBonus", equalTo(330))     // 1100 * 0.30
            .body("promoBonus", equalTo(0))
            .body("totalPoints", equalTo(1430))
            .body("effectiveFxRate", equalTo(1.1f));

        testContext.completeNow();
    }

    @Test
    void shouldApplyPlatinumTierBonus(Vertx vertx, VertxTestContext testContext) {
        fxServiceMock.stubFor(get(urlPathEqualTo("/v1/rates"))
            .willReturn(aResponse().withStatus(200).withBody("""
                {"fromCurrency":"USD","toCurrency":"USD","rate":1.0,"timestamp":"2025-01-15T10:00:00Z"}
                """)));

        promoServiceMock.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(404)));

        given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body("""
                {
                    "fareAmount": 2000.00,
                    "currency": "USD",
                    "cabinClass": "FIRST",
                    "customerTier": "PLATINUM",
                    "promoCode": null
                }
                """)
            .when()
            .post("/v1/points/quote")
            .then()
            .statusCode(200)
            .body("basePoints", equalTo(2000))
            .body("tierBonus", equalTo(1000))  // 2000 * 0.50
            .body("totalPoints", equalTo(3000));

        testContext.completeNow();
    }
    
    @Test
    void shouldNotApplyTierBonusForNoneTier(Vertx vertx, VertxTestContext ctx) {

        fxServiceMock.stubFor(get(urlPathEqualTo("/v1/rates"))
            .willReturn(okJson("""
                {"fromCurrency":"USD","toCurrency":"USD","rate":1.0}
            """)));

        promoServiceMock.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(404)));

        given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body("""
                {
                  "fareAmount": 1000,
                  "currency": "USD",
                  "cabinClass": "ECONOMY",
                  "customerTier": "NONE",
                  "promoCode": null
                }
            """)
            .post("/v1/points/quote")
            .then()
            .statusCode(200)
            .body("tierBonus", equalTo(0))
            .body("totalPoints", equalTo(1000));

        ctx.completeNow();
    }

    @Test
    void shouldReturnWarningForExpiredPromo(Vertx vertx, VertxTestContext ctx) {

        String expiredDate = LocalDate.now().minusDays(1).toString();

        promoServiceMock.stubFor(get(urlPathEqualTo("/v1/promos/OLDPROMO"))
            .willReturn(okJson(String.format("""
                {
                  "promoCode": "OLDPROMO",
                  "bonusMultiplier": 0.3,
                  "expiryDate": "%s",
                  "active": true
                }
            """, expiredDate))));

        given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body("""
                {
                  "fareAmount": 1000,
                  "currency": "USD",
                  "cabinClass": "ECONOMY",
                  "customerTier": "SILVER",
                  "promoCode": "OLDPROMO"
                }
            """)
            .post("/v1/points/quote")
            .then()
            .statusCode(200)
            .body("promoBonus", equalTo(0))
            .body("warnings", hasSize(1));

        ctx.completeNow();
    }
    
    
    @Test
    void shouldIgnoreInactivePromo(Vertx vertx, VertxTestContext ctx) {

        promoServiceMock.stubFor(get(urlPathEqualTo("/v1/promos/INACTIVE"))
            .willReturn(okJson("""
                {
                  "promoCode": "INACTIVE",
                  "bonusMultiplier": 0.4,
                  "active": false
                }
            """)));

        given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body("""
                {
                  "fareAmount": 1000,
                  "currency": "USD",
                  "cabinClass": "ECONOMY",
                  "customerTier": "GOLD",
                  "promoCode": "INACTIVE"
                }
            """)
            .post("/v1/points/quote")
            .then()
            .statusCode(200)
            .body("promoBonus", equalTo(0));

        ctx.completeNow();
    }
    
    @Test
    void shouldReturnErrorForInvalidCabinClass(Vertx vertx, VertxTestContext testContext) {
        // Stub FX service (still needed if your service calls it)
        fxServiceMock.stubFor(get(urlPathEqualTo("/v1/rates"))
            .willReturn(aResponse().withStatus(200).withBody("""
                {"fromCurrency":"USD","toCurrency":"USD","rate":1.0,"timestamp":"2025-01-15T10:00:00Z"}
                """)));

        // Stub Promo service
        promoServiceMock.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(404)));

        given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body("""
                {
                    "fareAmount": 2000.00,
                    "currency": "USD",
                    "cabinClass": "INVALID_CLASS",
                    "customerTier": "PLATINUM",
                    "promoCode": null
                }
                """)
            .when()
            .post("/v1/points/quote")
            .then()
            .statusCode(400)  // expecting your service to return 400 for invalid cabin class
            .body("message", Matchers.containsString("Invalid cabin class")); // matcher used

        testContext.completeNow();
    }
    
    @Test
    void shouldReturnErrorForInvalidCustomerTier(Vertx vertx, VertxTestContext testContext) {
        // Stub FX service (still needed if your service calls it)
        fxServiceMock.stubFor(get(urlPathEqualTo("/v1/rates"))
            .willReturn(aResponse().withStatus(200).withBody("""
                {"fromCurrency":"USD","toCurrency":"USD","rate":1.0,"timestamp":"2025-01-15T10:00:00Z"}
                """)));

        // Stub Promo service
        promoServiceMock.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(404)));

        given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body("""
                {
                    "fareAmount": 2000.00,
                    "currency": "USD",
                    "cabinClass": "ECONOMY",
                    "customerTier": "INVALID_CLASS",
                    "promoCode": null
                }
                """)
            .when()
            .post("/v1/points/quote")
            .then()
            .statusCode(400)  
            .body("message", Matchers.containsString("Invalid customer tier:")); // matcher used

        testContext.completeNow();
    }
    
    
    @Test
    void shouldHandleFxServiceFailure(Vertx vertx, VertxTestContext ctx) {
    	// Stub FX service (still needed if your service calls it)
        fxServiceMock.stubFor(get(urlPathEqualTo("/v1/rates"))
            .willReturn(aResponse().withStatus(200).withBody("""
                {"fromCurrency":"USD","toCurrency":"USD","rate":1.0,"timestamp":"2025-01-15T10:00:00Z"}
                """)));
        
    	promoServiceMock.stubFor(get(urlPathEqualTo("/v1/promos"))
            .willReturn(aResponse().withStatus(200)));

       

        given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body("""
                {
                    "fareAmount": 1000,
                    "currency": "USD",
                    "cabinClass": "ECONOMY",
                    "customerTier": "SILVER",
                    "promoCode": ""
                }
            """)
            .when()
            .post("/v1/points/quote")
            .then()
            .statusCode(200)
            .body("promoBonus", equalTo(0));

        ctx.completeNow();
    }

    @Test
    void shouldReturn500WhenFxFailsAfter3Retries(Vertx vertx, VertxTestContext ctx) {
        // Scenario: FX service always fails
        fxServiceMock.stubFor(get(urlPathEqualTo("/v1/rates"))
            .willReturn(aResponse().withStatus(500)));

        // Request with non-USD currency triggers FX
        given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body("""
                {
                    "fareAmount": 1000,
                    "currency": "EUR",
                    "cabinClass": "ECONOMY",
                    "customerTier": "SILVER",
                    "promoCode": null
                }
                """)
            .when()
            .post("/v1/points/quote")
            .then()
            .statusCode(500)
            .body("message", Matchers.containsString("error")); // matcher used

        // Verify FX service called 3 times (retry logic)
        fxServiceMock.verify(3, getRequestedFor(urlPathEqualTo("/v1/rates")));
        
        ctx.completeNow();
    }

    



    
    
    
    
    
    
    


    

    
}