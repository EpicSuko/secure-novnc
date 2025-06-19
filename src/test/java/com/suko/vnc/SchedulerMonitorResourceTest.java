package com.suko.vnc;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
public class SchedulerMonitorResourceTest {

    @Test
    public void testStatusEndpoint() {
        given()
            .when().get("/api/scheduler/status")
            .then()
                .statusCode(200)
                .body("schedulerRunning", is(true))
                .body("timestamp", notNullValue());
    }

    @Test
    public void testJobsEndpoint() {
        given()
            .when().get("/api/scheduler/jobs")
            .then()
                .statusCode(200)
                .body("scheduledJobs", notNullValue())
                .body("authServiceStats.timestamp", notNullValue());
    }

}
