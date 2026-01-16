
package com.airline.loyalty;


import io.restassured.http.ContentType;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.airline.loyalty.model.CabinClass;
import com.airline.loyalty.model.CustomerTier;
import com.airline.loyalty.model.PointsQuoteRequest;
import com.airline.loyalty.testutils.PointsQuoteRequestBuilder;

import java.time.LocalDate;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.equalTo;

class PointsQuoteTests extends BaseComponentTest {
	
	
	
	
	@Test
    void shouldHandleInvalidFareAmount(Vertx vertx, VertxTestContext testContext) {
        
     // Build the request using builder
        PointsQuoteRequest
        
        request = new PointsQuoteRequestBuilder()
        	.withFareAmount(0.0)
            .build();

        postQuote(request)
        .statusCode(400)
        .body("message", Matchers.containsString("Fare amount must be greater than zero"));

        testContext.completeNow();
    }
	
	@ParameterizedTest
    @EnumSource(CabinClass.class)
    void shouldAcceptAllValidCabinClasses(CabinClass cabin, Vertx vertx, VertxTestContext ctx) {
        
        
     // Build the request using builder
        PointsQuoteRequest request = new PointsQuoteRequestBuilder()
        	    .withFareAmount(1000.0)
        	    .withCurrency("USD")                 // required
        	    .withCabinClass(cabin.name())
        	    .withCustomerTier("SILVER")          // required
        	    .withPromoCode(null)                 // optional
        	    .build();

            postQuote(request)
            .statusCode(200)
            .body("basePoints", equalTo(1000))
            .body("tierBonus", equalTo(150))   // SILVER tier
            .body("totalPoints", equalTo(1150))
            .body("warnings", empty());

        ctx.completeNow();
    }
	
	@ParameterizedTest
    @EnumSource(CustomerTier.class)
    void shouldAcceptAllValidCustomerTier(CustomerTier customerTier, Vertx vertx, VertxTestContext ctx) {
        

     // Build the request using builder
        PointsQuoteRequest request = new PointsQuoteRequestBuilder()
        	    .withFareAmount(1000.0)
        	    .withCurrency("USD")                 // required
        	    .withCabinClass("ECONOMY")
        	    .withCustomerTier(customerTier.name())          // required
        	    .withPromoCode(null)                 // optional
        	    .build();


             // Send request
             postQuote(request)
            .statusCode(200)
            .body("basePoints", equalTo(1000))
            .body("warnings", empty());

        ctx.completeNow();
    }
	
	@Test
    void shouldHandleNoneTierWithoutPromo(Vertx vertx, VertxTestContext testContext) {
		
        
        PointsQuoteRequest request = new PointsQuoteRequestBuilder()               
        	    .build();

            postQuote(request)
            .statusCode(200)
            .body("basePoints", equalTo(1000))
            .body("tierBonus", equalTo(150))
            .body("promoBonus", equalTo(0))
            .body("totalPoints", equalTo(1150));

        testContext.completeNow();
    }
	   
    @Test
    void shouldApplyPlatinumTierBonus(Vertx vertx, VertxTestContext testContext) {

     // Build the request using builder
        PointsQuoteRequest request = new PointsQuoteRequestBuilder()
        	.withFareAmount(2000.00)
        	.withCabinClass("FIRST")
        	.withCustomerTier("PLATINUM")
            .build();
        
            postQuote(request)
            .statusCode(200)
            .body("basePoints", equalTo(2000))
            .body("tierBonus", equalTo(1000))  // 2000 * 0.50
            .body("totalPoints", equalTo(3000));

        testContext.completeNow();
    }
    
    @Test
    void shouldNotApplyTierBonusForNoneTier(Vertx vertx, VertxTestContext ctx) {

        PointsQuoteRequest request = new PointsQuoteRequestBuilder()
            	.withCustomerTier("NONE")
                .build();
        
            postQuote(request)
            .statusCode(200)
            .body("tierBonus", equalTo(0))
            .body("totalPoints", equalTo(1000));

        ctx.completeNow();
    }
    
    @Test
    void shouldReturnErrorForInvalidCabinClass(Vertx vertx, VertxTestContext testContext) {
    	
        PointsQuoteRequest request = new PointsQuoteRequestBuilder()
            	.withCabinClass("INVALID_CLASS")
                .build();

             postQuote(request)
            .statusCode(400)  // expecting your service to return 400 for invalid cabin class
            .body("message", Matchers.containsString("Invalid cabin class")); // matcher used

        testContext.completeNow();
    }
    
    @Test
    void shouldReturnErrorForInvalidCustomerTier(Vertx vertx, VertxTestContext testContext) {
    	
        PointsQuoteRequest request = new PointsQuoteRequestBuilder()
            	.withCustomerTier("INVALID_TIER")
                .build();

            postQuote(request)
            .statusCode(400)  
            .body("message", Matchers.containsString("Invalid customer tier:")); // matcher used

        testContext.completeNow();
    }
    
    @Test
    void shouldHandleMultipleTiersCabinsAndCurrencies(Vertx vertx, VertxTestContext testContext) {
        
        stubFxRate("JPY","USD",0.0067);
        PointsQuoteRequest request = new PointsQuoteRequestBuilder()
            	.withFareAmount(150000.00)
            	.withCurrency("JPY")
            	.withCabinClass("FIRST")
            	.withCustomerTier("PLATINUM")
                .build();

            postQuote(request)
            .statusCode(200)
            .body("basePoints", equalTo(1005))  // 150000 * 0.0067
            .body("tierBonus", equalTo(502))    // 1005 * 0.50
            .body("totalPoints", equalTo(1507));

        testContext.completeNow();
    }
    

    @Test
    void shouldReturnWarningForExpiredPromo(Vertx vertx, VertxTestContext ctx) {

        LocalDate expiredDate = LocalDate.now().minusDays(1);

        
        stubPromo("OLDPROMO",0.3,expiredDate,true);
        
        PointsQuoteRequest request = new PointsQuoteRequestBuilder()
            	.withPromoCode("OLDPROMO")
                .build();

            postQuote(request)
            .statusCode(200)
            .body("promoBonus", equalTo(0))
            .body("warnings", hasSize(1));

        ctx.completeNow();
    }
    
    
    @Test
    void shouldIgnoreInactivePromo(Vertx vertx, VertxTestContext ctx) {

    	 LocalDate expiryDate = LocalDate.now().plusDays(10);
    	 
    	 stubPromo("INACTIVE",0.4,expiryDate,false);
        
        PointsQuoteRequest request = new PointsQuoteRequestBuilder()
            	.withPromoCode("INACTIVE")
                .build();

            postQuote(request)
            .statusCode(200)
            .body("promoBonus", equalTo(0));

        ctx.completeNow();
    }


    @Test
    void shouldCalculatePointsForUsdEconomySilverWithPromo(Vertx vertx, VertxTestContext testContext) {
    	
        LocalDate expiryDate = LocalDate.now().plusDays(10);
   	 
   	    stubPromo("SUMMER25",0.25,expiryDate,true);
        
     // Build the request using builder
        PointsQuoteRequest request = new PointsQuoteRequestBuilder()
        	.withFareAmount(1234.50)
        	.withPromoCode("SUMMER25")
            .build();

        var response = postQuote(request)
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
        
        
        stubFxRate("EUR","USD",1.1);
        
        PointsQuoteRequest request = new PointsQuoteRequestBuilder()
            	.withCurrency("EUR")
            	.withCabinClass("BUSINESS")
            	.withCustomerTier("GOLD")
                .build();

        // When: Calculate points
        postQuote(request)
            .statusCode(200)
            .body("basePoints", equalTo(1100))  // 1000 * 1.1
            .body("tierBonus", equalTo(330))     // 1100 * 0.30
            .body("promoBonus", equalTo(0))
            .body("totalPoints", equalTo(1430))
            .body("effectiveFxRate", equalTo(1.1f));

        testContext.completeNow();
    }
    
    @Test
    void shouldReturn500WhenFxFailsAfter3Retries(Vertx vertx, VertxTestContext ctx) {
        // Scenario: FX service always fails
        fxServiceMock.stubFor(get(urlPathEqualTo("/v1/rates"))
            .willReturn(aResponse().withStatus(500)));
        
        PointsQuoteRequest request = new PointsQuoteRequestBuilder()
        		.withCurrency("EUR")
                .build();

        // Request with non-USD currency triggers FX
        postQuote(request)
            .statusCode(500)
            .body("message", Matchers.containsString("error")); // matcher used

        // Verify FX service called 3 times (retry logic)
        fxServiceMock.verify(3, getRequestedFor(urlPathEqualTo("/v1/rates")));
        
        ctx.completeNow();
    }   
    
    @Test
    void shouldHandleCompleteInternationalBookingScenario(Vertx vertx, VertxTestContext testContext) {
        
        stubFxRate("GBP","USD",1.27);

        LocalDate  expiryDate = LocalDate.now().plusDays(15);
        
        stubPromo("WINTER25",0.30,expiryDate,true);
        
        PointsQuoteRequest request = new PointsQuoteRequestBuilder()
        		.withFareAmount(2500.00)
        		.withCurrency("GBP")
        		.withCabinClass("BUSINESS")
        		.withCustomerTier("GOLD")
        		.withPromoCode("WINTER25")
                .build();

        postQuote(request)
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
    void shouldHandleFxServiceFailure(Vertx vertx, VertxTestContext ctx) {
    	
    	LocalDate expiryDate = LocalDate.now().plusDays(10);
      	 
    	
   	    stubPromo("",0,expiryDate,true);
   	    
   	      PointsQuoteRequest request = new PointsQuoteRequestBuilder()
     		 .withPromoCode("")
             .build();

   	   postQuote(request)
            .statusCode(200)
            .body("promoBonus", equalTo(0));

        ctx.completeNow();
    }
    
    @Test
    void shouldEnforceMaxPointsCap(Vertx vertx, VertxTestContext ctx) {

        PointsQuoteRequest request = new PointsQuoteRequestBuilder()
            .withFareAmount(200000.00) // huge fare
            .withCurrency("USD")
            .withCabinClass("FIRST")
            .withCustomerTier("PLATINUM")
            .build();

        postQuote(request)
            .statusCode(200)
            .body("totalPoints", lessThanOrEqualTo(50000));

        ctx.completeNow();
    }

    @Test
    void shouldReturnErrorOnFxTimeout(Vertx vertx, VertxTestContext ctx) {

        // Simulate FX service delay
        fxServiceMock.stubFor(get(urlPathEqualTo("/v1/rates"))
            .willReturn(aResponse().withFixedDelay(5000))); // 5s delay

        PointsQuoteRequest request = new PointsQuoteRequestBuilder()
            .withCurrency("EUR")
            .withCabinClass("ECONOMY")
            .withCustomerTier("SILVER")
            .build();

        postQuote(request)
            .statusCode(504)
            .body("message", containsString("timeout"));

        ctx.completeNow();
    }
    
    @Test
    void shouldHandleConcurrentRequests(Vertx vertx, VertxTestContext ctx) {
        int concurrentRequests = 10;

        for (int i = 0; i < concurrentRequests; i++) {
            PointsQuoteRequest request = new PointsQuoteRequestBuilder()
                .withFareAmount(1000.00 + i)
                .withCurrency("USD")
                .withCabinClass("ECONOMY")
                .withCustomerTier("SILVER")
                .build();

            postQuote(request)
                .statusCode(200)
                .body("totalPoints", greaterThan(0));
        }

        ctx.completeNow();
    }
    
    @Test
    void shouldIncrementRequestAndErrorCounters(Vertx vertx, VertxTestContext ctx) {

        // Trigger a validation error
        PointsQuoteRequest badRequest = new PointsQuoteRequestBuilder()
            .withFareAmount(-100.0)
            .build();

        postQuote(badRequest)
            .statusCode(400);

        // Call metrics endpoint
        given()
            .baseUri("http://localhost:9090")
            .get("/metrics")
            .then()
            .statusCode(200)
            .body(containsString("points_quote_requests_total"))
            .body(containsString("points_quote_errors_total"));

        ctx.completeNow();
    }
}
