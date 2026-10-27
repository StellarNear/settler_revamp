package com.apogames.settler.game.tiwanaku;

/**
 * Elapsed-time counter for a single puzzle attempt.
 * <p>
 * The clock only runs between the first value click and the moment the level is
 * solved, so it is driven by an explicit start/stop instead of a raw wall clock.
 */
public class GameTimer {

    /**
     * Upper bound for a single update, so a backgrounded tab (the web build stops
     * rendering while hidden) cannot dump minutes into the clock on the frame it
     * becomes visible again.
     */
    private static final float MAX_UPDATE_SECONDS = 1f;

    private float elapsedSeconds = 0f;
    private boolean running = false;
    private boolean stopped = false;

    /**
     * Clears the elapsed time and arms the timer for a new attempt.
     */
    public void reset() {
        this.elapsedSeconds = 0f;
        this.running = false;
        this.stopped = false;
    }

    /**
     * Starts counting. Has no effect once the timer has been stopped, so a solved
     * level keeps its final time.
     */
    public void start() {
        if (this.stopped) {
            return;
        }
        this.running = true;
    }

    /**
     * Freezes the elapsed time.
     */
    public void stop() {
        this.running = false;
        this.stopped = true;
    }

    /**
     * Advances the clock.
     *
     * @param deltaSeconds seconds since the last rendered frame
     */
    public void update(float deltaSeconds) {
        if (!this.running) {
            return;
        }
        this.elapsedSeconds += Math.min(deltaSeconds, MAX_UPDATE_SECONDS);
    }

    public boolean isRunning() {
        return this.running;
    }

    /**
     * @return true once the clock has been started or stopped, i.e. the attempt is
     *         under way or already finished
     */
    public boolean hasStarted() {
        return this.running || this.stopped;
    }

    public long getElapsedMillis() {
        return (long) (this.elapsedSeconds * 1000f);
    }

    /**
     * @return the elapsed time as {@code MM:SS}, or {@code H:MM:SS} past an hour
     */
    public String getFormattedTime() {
        long totalSeconds = (long) this.elapsedSeconds;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return hours + ":" + pad(minutes) + ":" + pad(seconds);
        }
        return pad(minutes) + ":" + pad(seconds);
    }

    private static String pad(long value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }
}
