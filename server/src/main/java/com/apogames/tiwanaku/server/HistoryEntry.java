package com.apogames.tiwanaku.server;

public record HistoryEntry(
        String recordId, String attemptId, String mapSize, Long completedMillis,
        Long elapsedMillis, String playerTag, String scoringVersion, Integer valueEntries,
        Integer legacyAccuracy, Long totalClicks, Long totalMissClicks,
        Assistance assistance, PuzzleSettings puzzleSettings) {
    public record PuzzleSettings(int columns, int rows, int maxRegionSize, String difficulty,
                                 String seed, String engineVersion, String generatorVersion,
                                 String shape) { }
    public record Assistance(Integer wrongEntries, Integer candidates, Integer explanations, Integer reveals,
                             Boolean continuousConflicts, Boolean replay) { }
}
