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
        SpringApplication.run(TiwanakuServerApplication.class, args);
    }
}
