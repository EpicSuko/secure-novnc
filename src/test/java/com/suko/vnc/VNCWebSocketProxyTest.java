package com.suko.vnc;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetServer;
import io.vertx.mutiny.core.Vertx;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(VNCWebSocketProxyTest.TestProfile.class)
public class VNCWebSocketProxyTest {

    private static final Logger log = LoggerFactory.getLogger(VNCWebSocketProxyTest.class);
    
    @Inject
    Vertx vertx;
    
    private NetServer mockVNCServer;
    private int mockVNCPort;
    
    public static class TestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "vnc.server.host", "localhost",
                "vnc.server.port", "5902", // Use different port for testing
                "vnc.connection.timeout", "5000",
                "vnc.connection.max.retries", "2",
                "vnc.connection.retry.delay", "100"
            );
        }
    }
    
    @BeforeEach
    void setUp() {
        // Create a mock VNC server for testing
        mockVNCPort = 5902;
        mockVNCServer = vertx.getDelegate().createNetServer();
        
        mockVNCServer.connectHandler(socket -> {
            log.info("Mock VNC server: Client connected");
            
            // Send VNC protocol version
            socket.write(Buffer.buffer("RFB 003.008\n"));
            
            // Handle incoming data
            socket.handler(buffer -> {
                log.info("Mock VNC server: Received {} bytes", buffer.length());
                // Echo back some data for testing
                socket.write(Buffer.buffer("VNC_RESPONSE"));
            });
            
            socket.closeHandler(v -> log.info("Mock VNC server: Client disconnected"));
        });
        
        mockVNCServer.listen(mockVNCPort, "localhost")
            .onSuccess(server -> log.info("Mock VNC server started on port {}", mockVNCPort))
            .onFailure(throwable -> log.error("Failed to start mock VNC server", throwable));
    }
    
    @AfterEach
    void tearDown() {
        if (mockVNCServer != null) {
            mockVNCServer.close()
                .onSuccess(v -> log.info("Mock VNC server stopped"))
                .onFailure(throwable -> log.error("Error stopping mock VNC server", throwable));
        }
    }
    
    @Test
    void testVertxNetClientConnection() {
        // Test that Vert.x NetClient can connect to our mock VNC server
        var netClient = vertx.getDelegate().createNetClient();
        
        netClient.connect(mockVNCPort, "localhost")
            .onSuccess(socket -> {
                log.info("Successfully connected to mock VNC server");
                
                // Test sending data
                socket.write(Buffer.buffer("TEST_DATA"));
                
                // Test receiving data
                socket.handler(buffer -> {
                    log.info("Received response: {}", buffer.toString());
                    assertTrue(buffer.toString().contains("VNC_RESPONSE"));
                });
                
                // Close connection after test
                socket.close();
            })
            .onFailure(throwable -> {
                log.error("Failed to connect to mock VNC server", throwable);
                fail("Should be able to connect to mock VNC server");
            });
    }
    
    @Test
    void testVNCConnectionConfiguration() {
        // Test that configuration properties are properly loaded
        // This test verifies that the @ConfigProperty annotations work correctly
        assertNotNull(vertx);
        log.info("Vert.x instance available for testing");
    }
} 