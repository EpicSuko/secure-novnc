package com.suko.vnc;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
public class HealthResourceTest {

    @Test
    public void testHealthEndpoint() {
        given()
            .when().get("/api/health")
            .then()
                .statusCode(200)
                .body("status", is("UP"))
                .body("phase", is("Phase 2 - Authentication API (with Quarkus Scheduler)"))
                .body("timestamp", notNullValue());
    }

    @Test
    public void testDetailedHealthEndpoint() {
        given()
          .when().get("/api/health/detailed")
          .then()
             .statusCode(200)
             .body("status", is("UP"))
             .body("application.name", is("secure-vnc-app"))
             .body("system.java-version", notNullValue());
    }

    @Test
    public void testWelcomeEndpoint() {
        given()
          .when().get("/api/welcome")
          .then()
             .statusCode(200)
             .body("message", is("Welcome to Secure VNC Application!"))
             .body("phase", is("Phase 1: Basic Quarkus Setup Complete"));
    }
}
