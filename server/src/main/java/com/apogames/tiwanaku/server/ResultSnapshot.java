package com.apogames.tiwanaku.server;

public record ResultSnapshot(
        String resultId,
        String attemptId,
        int[] values,
        int moveCount,
        String completedAt,
        String playerTag,
        AttemptSnapshot.ProgressSnapshot progress) {
}
