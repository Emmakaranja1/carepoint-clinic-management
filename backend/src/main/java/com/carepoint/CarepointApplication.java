package com.carepoint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication
@EnableScheduling
public class CarepointApplication {

    public static void main(String[] args) {
        // .env lives at the repo root (one file for compose + app); the app
        // runs from backend/, so look one level up. Shell-exported vars win.
        Dotenv.configure()
                .directory("../")
                .ignoreIfMissing()
                .systemProperties()
                .load();
        SpringApplication.run(CarepointApplication.class, args);
    }
}
