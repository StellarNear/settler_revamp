package com.apogames.tiwanaku.server;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class BootstrapController {
    private final String instanceId = UUID.randomUUID().toString();
    private final LocalRequestToken token;

    public BootstrapController(LocalRequestToken token) {
        this.token = token;
    }

    @GetMapping("/api/v1/bootstrap")
    public BootstrapResponse bootstrap() {
        return new BootstrapResponse("v1", instanceId, token.value(), 240, 9);
    }

    public record BootstrapResponse(
            String apiVersion,
            String processInstanceId,
            String requestToken,
            int maxCells,
            int maxRegionSize) {
    }
}
