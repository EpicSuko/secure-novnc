package com.suko.vnc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
public class VNCAuthResourceTest {

    @BeforeEach
    public void setup() {
        System.out.println("🧪 Starting authentication test");
    }

    @Test
    public void testValidAuthentication() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"demo\",\"preAuthPassword\":\"demo123\"}")
        .when()
            .post("/api/vnc/authenticate")
        .then()
            .statusCode(200)
            .body("success", is(true))
            .body("sessionId", notNullValue())
            .body("userId", is("demo"))
            .body("wsUrl", notNullValue());
    }

    @Test
    public void testInvalidAuthentication() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"invalid\",\"preAuthPassword\":\"wrong\"}")
        .when()
            .post("/api/vnc/authenticate")
        .then()
            .statusCode(401)
            .body("success", is(false))
            .body("message", is("Invalid username or password"));
    }

    @Test
    public void testMissingCredentials() {
        given()
            .contentType(ContentType.JSON)
            .body("{}")
        .when()
            .post("/api/vnc/authenticate")
        .then()
            .statusCode(400)
            .body("success", is(false))
            .body("message", is("Username and password are required"));
    }

    @Test
    public void testEmptyCredentials() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"\",\"preAuthPassword\":\"\"}")
        .when()
            .post("/api/vnc/authenticate")
        .then()
            .statusCode(400)
            .body("success", is(false))
            .body("message", is("Username and password cannot be empty"));
    }

    @Test
    public void testSessionValidation() {
        // First authenticate to get a session
        String sessionId = given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"demo\",\"preAuthPassword\":\"demo123\"}")
        .when()
            .post("/api/vnc/authenticate")
        .then()
            .statusCode(200)
            .extract().path("sessionId");

        // Test session validation
        given()
        .when()
            .get("/api/vnc/session/" + sessionId + "/validate")
        .then()
            .statusCode(200)
            .body("valid", is(true))
            .body("userId", is("demo"))
            .body("wsUrl", notNullValue());
    }

    @Test
    public void testInvalidSession() {
        given()
        .when()
            .get("/api/vnc/session/invalid-session-id/validate")
        .then()
            .statusCode(404)
            .body("valid", is(false))
            .body("message", is("Session not found or expired"));
    }

    @Test
    public void testSessionLogout() {
        // First authenticate to get a session
        String sessionId = given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"demo\",\"preAuthPassword\":\"demo123\"}")
        .when()
            .post("/api/vnc/authenticate")
        .then()
            .statusCode(200)
            .extract().path("sessionId");

        // Logout
        given()
        .when()
            .delete("/api/vnc/session/" + sessionId)
        .then()
            .statusCode(200)
            .body("valid", is(true))
            .body("message", is("Session invalidated successfully"));

        // Verify session is invalid after logout
        given()
        .when()
            .get("/api/vnc/session/" + sessionId + "/validate")
        .then()
            .statusCode(404)
            .body("valid", is(false));
    }

    @Test
    public void testActiveSessionsEndpoint() {
        // Create a session first
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"demo\",\"preAuthPassword\":\"demo123\"}")
        .when()
            .post("/api/vnc/authenticate")
        .then()
            .statusCode(200);

        // Check active sessions
        given()
        .when()
            .get("/api/vnc/sessions")
        .then()
            .statusCode(200)
            .body("activeSessionCount", notNullValue())
            .body("sessions", notNullValue());
    }

    @Test
    public void testHealthEndpointWithAuth() {
        given()
        .when()
            .get("/api/health")
        .then()
            .statusCode(200)
            .body("status", is("UP"))
            .body("phase", is("Phase 2 - Authentication API (with Quarkus Scheduler)"))
            .body("activeSessions", notNullValue());
    }

}
