package com.apogames.tiwanaku.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.util.Map;

@SpringBootApplication
public class TiwanakuServerApplication {
    public static void main(String[] args) throws IOException {
        DataDirectoryLock dataDirectory = DataDirectoryLock.acquireFromEnvironment();
        SpringApplication application = new SpringApplication(TiwanakuServerApplication.class);
        application.addInitializers(context -> context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("tiwanakuDataDirectory", Map.of(
                        "spring.datasource.url", dataDirectory.jdbcUrl(),
                        "spring.datasource.driver-class-name", "org.h2.Driver",
                        "spring.flyway.enabled", "true",
                        "server.address", "127.0.0.1"))));
        application.addListeners((ContextClosedEvent event) -> dataDirectory.close());
        try {
            application.run(args);
        } catch (RuntimeException | Error error) {
            dataDirectory.close();
            throw error;
        }
    }
}
