package com.suko.vnc.config;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Help.Visibility;

@Command(name = "vnc-server", description = "VNC Application", mixinStandardHelpOptions = true, exitCodeOnUsageHelp = 1, exitCodeOnVersionHelp = 1) // we want to exit with help or version is provided
public class VNCConfigCli implements Runnable {

    @Option(names = {"--cert"}, description = "SSL certificate Path", required = false)
    String cert;

    @Option(names = {"--key"}, description = "SSL key Path", required = false)
    String key;

    @Option(names = {"--listen"}, description = "Host address and port to listen on", required = false, defaultValue = "localhost:8080", showDefaultValue = Visibility.ALWAYS)
    String listen;

    @Option(names = {"--vnc"}, description = "VNC host address and port to connect to", required = true)
    String vnc;

    @Option(names = {"--vnc-password"}, description = "Server's VNC password", required = true)
    String vncPassword;

    @Option(names = {"--username"}, description = "Username to use for authentication", required = true)
    String username;

    @Option(names = {"--password"}, description = "Password to use for authentication", required = true)
    String password;

    @Override
    public void run() {
        // Set system properties based on parsed arguments
        if (cert != null && key == null) {
            throw new IllegalArgumentException("Key is required when cert is provided");
        }

        if (key != null && cert == null) {
            throw new IllegalArgumentException("Cert is required when key is provided");
        }
        if (cert != null && key != null) {
            System.setProperty("quarkus.tls.key-store.pem.0.cert", cert);
            System.setProperty("quarkus.tls.key-store.pem.0.key", key);
            System.setProperty("quarkus.http.insecure-requests", "disabled"); // never use redirect on localhost or you gotta clear the file cache
        }

        if (listen != null) {
            if(isIPv6Address(listen.split(":")[0])) {
                throw new IllegalArgumentException("IPv6 addresses are not supported at this time");
            }
            String[] parts = listen.split(":", 2);
            System.setProperty("quarkus.http.host", parts[0]);
            if (parts.length > 1) {
                try {
                    int port = Integer.parseInt(parts[1]);
                    if (port < 1 || port > 65535) {
                        throw new IllegalArgumentException("Port must be between 1 and 65535");
                    }
                    if(cert != null && key != null) {
                        System.setProperty("quarkus.http.ssl-port", parts[1]);
                    } else {
                        System.setProperty("quarkus.http.port", parts[1]);
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid port number: " + parts[1]);
                }
            }
        }
        if (vnc != null) {
            String[] parts = vnc.split(":", 2);
            System.setProperty("vnc.server.host", parts[0]);
            if (parts.length > 1) {
                try {
                    int port = Integer.parseInt(parts[1]);
                    if (port < 1 || port > 65535) {
                        throw new IllegalArgumentException("VNC port must be between 1 and 65535");
                    }
                    System.setProperty("vnc.server.port", parts[1]);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid VNC port number: " + parts[1]);
                }
            }
        }
        if (vncPassword != null) {
            System.setProperty("vnc.server.password", vncPassword);
        }
        if (username != null) {
            System.setProperty("vnc.user.username", username);
        }
        if (password != null) {
            System.setProperty("vnc.user.password", password);
        }
    }

    private boolean isIPv6Address(String address) {
        return address.contains(":") && !address.matches("^[^:]+$");
    }

}
