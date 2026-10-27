package com.apogames.tiwanaku.server;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public final class LocalServerReadyListener implements ApplicationListener<ApplicationReadyEvent> {
    private final DataDirectoryLock dataDirectoryLock;

    public LocalServerReadyListener(DataDirectoryLock dataDirectoryLock) {
        this.dataDirectoryLock = dataDirectoryLock;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        int port = event.getApplicationContext().getEnvironment().getProperty(
                "local.server.port",
                Integer.class,
                event.getApplicationContext().getEnvironment().getProperty("server.port", Integer.class, 8080));
        System.out.println("Tiwanaku ready: http://localhost:" + port);
        System.out.println("Tiwanaku data directory: " + dataDirectoryLock.directory());
        System.out.println("Tiwanaku logs: "
                + event.getApplicationContext().getEnvironment().getProperty("logging.file.path"));
    }
}
