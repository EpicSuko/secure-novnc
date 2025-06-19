package com.suko.vnc;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class VNCApplication implements QuarkusApplication {

    public static void main(String... args) {
        System.out.println("🚀 Starting Secure VNC Application - Phase 1");
        Quarkus.run(VNCApplication.class, args);
    }

    @Override
    public int run(String... args) throws Exception {
        System.out.println("✅ Secure VNC Application started successfully!");
        System.out.println("🌐 Server running at: http://localhost:8080");
        System.out.println("❤️  Health check: http://localhost:8080/q/health");
        System.out.println("🛠️  Dev UI: http://localhost:8080/q/dev/");
    

        Quarkus.waitForExit();
        return 0;
    }

}
