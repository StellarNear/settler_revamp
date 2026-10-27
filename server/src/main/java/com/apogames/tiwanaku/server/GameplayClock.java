package com.apogames.tiwanaku.server;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class GameplayClock {
    private final String processId = UUID.randomUUID().toString();

    public Instant now() {
        return Instant.now();
    }

    public long nanos() {
        return System.nanoTime();
    }

    public String processId() {
        return processId;
    }
}
