package com.apogames.settler.game.tiwanaku;

public class GameResult {
    private final String mapSize;
    private final long timeMillis;
    private final int accuracyPercent;
    private final long dateMillis;

    public GameResult(String mapSize, long timeMillis, int accuracyPercent) {
        this(mapSize, timeMillis, accuracyPercent, 0);
    }

    public GameResult(String mapSize, long timeMillis, int accuracyPercent, long dateMillis) {
        this.mapSize = mapSize;
        this.timeMillis = timeMillis;
        this.accuracyPercent = accuracyPercent;
        this.dateMillis = dateMillis;
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

    public String getFormattedDate() {
        if (dateMillis <= 0) {
            return "";
        }
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
        sdf.setTimeZone(java.util.TimeZone.getDefault());
        return sdf.format(new java.util.Date(dateMillis));
    }
}
