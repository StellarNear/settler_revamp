package com.apogames.tiwanaku.server;

public record AttemptSnapshot(
        String attemptId,
        String puzzleId,
        int columns,
        int rows,
        int[] regionIds,
        int[] givens,
        int[] values,
        String status,
        long revision,
        String editorSessionId,
        String editorLeaseExpiresAt,
        int moveCount) {
}
