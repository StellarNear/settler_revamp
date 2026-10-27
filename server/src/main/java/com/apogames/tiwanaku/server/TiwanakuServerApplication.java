package com.apogames.tiwanaku.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TiwanakuServerApplication {
    public static void main(String[] args) throws Exception {
        if (java.util.Arrays.asList(args).contains("--migrate-legacy-store")) {
            if (args.length != 1) {
                throw new IllegalArgumentException("--migrate-legacy-store must be run on its own");
            }
            try (DataDirectoryLock directory = DataDirectoryLock.acquireFromEnvironment()) {
                LegacyStoreMigration.migrate(directory);
            }
            System.out.println("Local database migration complete. Start Tiwanaku normally.");
            return;
        }
        var application = SpringApplication.run(TiwanakuServerApplication.class, args);
        if (java.util.Arrays.asList(args).contains("--console")) {
            try (application) {
                System.out.println("Type quit and press Enter to stop Tiwanaku cleanly.");
                var input = new java.io.BufferedReader(
                        new java.io.InputStreamReader(System.in, java.nio.charset.StandardCharsets.UTF_8));
                String line;
                while ((line = input.readLine()) != null && !"quit".equalsIgnoreCase(line.trim())) {
                    System.out.println("Unknown console command. Type quit to stop, or use the printed localhost URL to play.");
                }
            }
        }
    }
}
