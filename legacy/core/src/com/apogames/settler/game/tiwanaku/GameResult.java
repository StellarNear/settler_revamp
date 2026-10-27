package com.apogames.settler.game.tiwanaku;

public class GameResult {
    private final String mapSize;
    private final long timeMillis;
    private final int accuracyPercent;
    private final long dateMillis;
    private final String playerTrigram;
    private final int totalClicks;
    private final int totalMissClicks;

    public GameResult(String mapSize, long timeMillis, int accuracyPercent) {
        this(mapSize, timeMillis, accuracyPercent, 0, "", 0, 0);
    }

    public GameResult(String mapSize, long timeMillis, int accuracyPercent, long dateMillis) {
        this(mapSize, timeMillis, accuracyPercent, dateMillis, "", 0, 0);
    }

    public GameResult(String mapSize, long timeMillis, int accuracyPercent, long dateMillis, String playerTrigram) {
        this(mapSize, timeMillis, accuracyPercent, dateMillis, playerTrigram, 0, 0);
    }

    public GameResult(String mapSize, long timeMillis, int accuracyPercent, long dateMillis, String playerTrigram, int totalClicks, int totalMissClicks) {
        this.mapSize = mapSize;
        this.timeMillis = timeMillis;
        this.accuracyPercent = accuracyPercent;
        this.dateMillis = dateMillis;
        this.playerTrigram = playerTrigram;
        this.totalClicks = totalClicks;
        this.totalMissClicks = totalMissClicks;
    }

    public String getMapSize() {
        return mapSize;
    }

    public long getTimeMillis() {
        return timeMillis;
    }

    public int getAccuracyPercent() {
        return accuracyPercent;
    }

    public long getDateMillis() {
        return dateMillis;
    }

    public String getPlayerTrigram() {
        return playerTrigram;
    }

    public int getTotalClicks() {
        return totalClicks;
    }

    public int getTotalMissClicks() {
        return totalMissClicks;
    }

    public String getFormattedDate() {
        if (dateMillis <= 0) {
            return "";
        }
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
        sdf.setTimeZone(java.util.TimeZone.getDefault());
        return sdf.format(new java.util.Date(dateMillis));
    }
}
