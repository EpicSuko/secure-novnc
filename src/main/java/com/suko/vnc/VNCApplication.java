package com.suko.vnc;

import com.suko.vnc.config.VNCConfigCli;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import picocli.CommandLine;

@QuarkusMain
public class VNCApplication implements QuarkusApplication {

    public static void main(String... args) {
        // Parse command line arguments first
        VNCConfigCli cli = new VNCConfigCli();
        CommandLine cmd = new CommandLine(cli);
        int exitCode = cmd.execute(args);
        
        if (exitCode != 0) {
            System.exit(exitCode);
        }
        
        // Now start Quarkus
        Quarkus.run(VNCApplication.class, args);
    }

    @Override
    public int run(String... args) throws Exception {
        Quarkus.waitForExit();
        return 0;
    }
}

