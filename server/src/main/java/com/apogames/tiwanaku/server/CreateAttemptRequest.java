package com.apogames.tiwanaku.server;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateAttemptRequest(
        @NotBlank @Size(max = 128) String requestId,
        UUID generationJobId,
        @NotBlank @Size(min = 16, max = 128) String editorSessionId) {
}
