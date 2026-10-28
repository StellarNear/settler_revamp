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
        int moveCount,
        ProgressSnapshot progress,
        HistoryEntry.PuzzleSettings puzzleSettings,
        double[][] cellPolygons) {
    public record ProgressSnapshot(
            int[] notes, long elapsedMillis, boolean started, boolean replay,
            int valueEntryCount, Integer wrongEntryCount, int candidateRequests, int explanationRequests,
            long totalClicks, long totalMissClicks, Integer legacyAccuracy, boolean directValueEntryUsed,
            int reveals, boolean continuousConflictsUsed, boolean showConflicts, boolean canUndo, boolean canRedo,
            boolean hasCheckpoint, int[] conflicts, int[] candidates, String helpMessage,
            long timingSequence) {
    }
}
