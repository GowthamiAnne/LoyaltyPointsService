package com.airline.loyalty;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;


public class Main {

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        String profile = System.getProperty(
            "vertx.profile",
            System.getenv().getOrDefault("VERTX_PROFILE", "dev")
        );
        
        

        ConfigStoreOptions fileStore = new ConfigStoreOptions()
            .setType("file")
            .setConfig(new JsonObject()
                .put("path", "src/main/resources/application-" + profile + ".json"));

        System.out.println(profile+"profile");
        ConfigRetriever retriever = ConfigRetriever.create(
            vertx,
            new ConfigRetrieverOptions().addStore(fileStore)
        );

        retriever.getConfig(ar -> {
            if (ar.failed()) {
                ar.cause().printStackTrace();
                return;
            }

            DeploymentOptions options =
                new DeploymentOptions().setConfig(ar.result());

            vertx.deployVerticle(new MainVerticle(), options);
        });
    }
}

