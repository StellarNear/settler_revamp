package com.apogames.tiwanaku.server;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AttemptCommand(
        @NotBlank @Size(max = 128) String commandId,
        @Min(0) long expectedRevision,
        @NotBlank @Size(min = 16, max = 128) String editorSessionId,
        @NotNull CommandType type,
        @Min(0) @Max(239) Integer cellIndex,
        @Min(0) @Max(9) Integer value) {
    public enum CommandType {
        SET_VALUE,
        PAUSE,
        RESUME,
        TAKEOVER
    }
}
