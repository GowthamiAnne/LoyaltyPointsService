package com.airline.loyalty;

import com.airline.loyalty.model.PointsQuoteRequest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ServerSocket;
import java.io.IOException;
import java.time.LocalDate;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;

import static io.restassured.RestAssured.given;

@ExtendWith(VertxExtension.class)
public abstract class BaseComponentTest {

    private static final Logger logger = LoggerFactory.getLogger(BaseComponentTest.class);
    protected WireMockServer fxServiceMock;
    protected WireMockServer promoServiceMock;
    protected int appPort;
    protected String baseUrl;
    

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) throws IOException {
        // Find random available ports
        appPort = findRandomPort();
        int fxPort = findRandomPort();
        int promoPort = findRandomPort();
        int metricsPort = findRandomPort();

        baseUrl = "http://localhost:" + appPort;

        // Start WireMock servers
        fxServiceMock = new WireMockServer(WireMockConfiguration.options().port(fxPort));
        fxServiceMock.start();

        promoServiceMock = new WireMockServer(WireMockConfiguration.options().port(promoPort));
        promoServiceMock.start();
        
     // Resolve profile (default = test)
        String profile = System.getProperty(
            "vertx.profile",
            System.getenv().getOrDefault("VERTX_PROFILE", "test")
        );

        // Load profile config
        ConfigStoreOptions fileStore = new ConfigStoreOptions()
            .setType("file")
            .setConfig(new JsonObject()
                .put("path", "src/main/resources/application-" + profile + ".json"));

        ConfigRetriever retriever = ConfigRetriever.create(
            vertx,
            new ConfigRetrieverOptions().addStore(fileStore)
        );
        
        retriever.getConfig(ar -> {
            if (ar.failed()) {
                testContext.failNow(ar.cause());
                return;
            }

            JsonObject config = ar.result();
         // 🔹 Override dynamic test values
            config.getJsonObject("http")
                .put("port", appPort);

            config.getJsonObject("fxService")
                .put("port", fxPort);

            config.getJsonObject("promoService")
                .put("port", promoPort);
            
            config.getJsonObject("observability")
            .put("host", "localhost")
            .put("port", metricsPort);

        logger.debug("FX Service Port"+fxPort);
        logger.debug("PROMO Service Port"+promoPort);
        
        DeploymentOptions options =
                new DeploymentOptions().setConfig(config);

            vertx.deployVerticle(new MainVerticle(), options)
                .onComplete(testContext.succeedingThenComplete());
        });
    }

    @AfterEach
    void tearDown(Vertx vertx, VertxTestContext testContext) {
        fxServiceMock.stop();
        promoServiceMock.stop();
        vertx.close().onComplete(testContext.succeedingThenComplete());
    }

    private int findRandomPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
    
    public void stubFxRate(String from, String to, double rate) {
        fxServiceMock.stubFor(get(urlPathEqualTo("/v1/rates"))
            .withQueryParam("from", equalTo(from))
            .withQueryParam("to", equalTo(to))
            .willReturn(okJson("""
                    {"fromCurrency":"%s","toCurrency":"%s","rate":%f,"timestamp":"%s"}
                    """.formatted(from, to, rate, LocalDate.now().toString()))));
    }
	
	public void stubPromo(String promoCode, double multiplier, LocalDate expiry, boolean active) {
        promoServiceMock.stubFor(get(urlPathEqualTo("/v1/promos/%s".formatted(promoCode)))
            .willReturn(okJson("""
                    {
                        "promoCode": "%s",
                        "bonusMultiplier": %f,
                        "expiryDate": "%s",
                        "active": %s
                    }
                    """.formatted(promoCode, multiplier, expiry.toString(), active))));
    }

	public ValidatableResponse postQuote(PointsQuoteRequest request) {
	    return given()
	            .baseUri(baseUrl)
	            .contentType(ContentType.JSON)
	            .body(request)
	            .when()
	            .post("/v1/points/quote")
	            .then();
	}

}
