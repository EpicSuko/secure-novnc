package com.suko.vnc.rest;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/welcome")
@Produces(MediaType.APPLICATION_JSON)
public class WelcomeResource {

    @GET
    public Uni<WelcomeMessage> getWelcomeMessage() {
        return Uni.createFrom().item(new WelcomeMessage(
            "Welcome to Secure VNC Application!",
            "Phase 1: Basic Quarkus Setup Complete",
            "REad for Phase 2: Authentication API"
        ));
    }

    public static class WelcomeMessage {
        public String message;
        public String phase;
        public String nextStep;

        public WelcomeMessage(String message, String phase, String nextStep) {
            this.message = message;
            this.phase = phase;
            this.nextStep = nextStep;
        }
    }

}
