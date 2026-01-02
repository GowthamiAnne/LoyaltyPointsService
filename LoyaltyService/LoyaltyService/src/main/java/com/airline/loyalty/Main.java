package com.airline.loyalty;

import io.vertx.core.Launcher;

public class Main {
    public static void main(String[] args) {
        Launcher.main(new String[]{
            "run", 
            "com.airline.loyalty.MainVerticle", 
            "-conf", "src/main/resources/config.json"
        });
    }
}
