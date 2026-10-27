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
        @Min(-1) @Max(9) Integer value,
        @Min(0) Long timingSequence,
        @Min(0) Long elapsedMillis) {
    public AttemptCommand(String commandId, long expectedRevision, String editorSessionId,
                          CommandType type, Integer cellIndex, Integer value) {
        this(commandId, expectedRevision, editorSessionId, type, cellIndex, value, null, null);
    }

    public enum CommandType {
        SET_VALUE,
        CYCLE_VALUE,
        TOGGLE_NOTE,
        UNDO,
        REDO,
        CHECKPOINT,
        RESTORE_CHECKPOINT,
        CHECK,
        SHOW_CONFLICTS,
        CANDIDATES,
        EXPLAIN,
        REVEAL,
        HEARTBEAT,
        PAUSE,
        RESUME,
        TAKEOVER
    }
}
