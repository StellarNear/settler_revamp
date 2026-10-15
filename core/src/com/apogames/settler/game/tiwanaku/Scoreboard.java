package com.apogames.settler.game.tiwanaku;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

import java.util.ArrayList;
import java.util.List;

public class Scoreboard {

    private static final String PREF_NAME = "TiwanakuScoreboard";
    private static final String KEY_RESULTS = "scoreboard_results";

    public void saveResult(GameResult result) {
        Preferences pref = Gdx.app.getPreferences(PREF_NAME);
        List<GameResult> results = loadResults();
        results.add(result);
        String serialized = serialize(results);
        pref.putString(KEY_RESULTS, serialized);
        pref.flush();
    }

    public void clearResults() {
        Preferences pref = Gdx.app.getPreferences(PREF_NAME);
        pref.putString(KEY_RESULTS, "");
        pref.flush();
    }

    /**
     * Updates an existing result in the scoreboard with a new result.
     * @param oldResult The result to replace
     * @param newResult The result to replace it with
     */
    public void updateResult(GameResult oldResult, GameResult newResult) {
        Preferences pref = Gdx.app.getPreferences(PREF_NAME);
        List<GameResult> results = loadResults();
        
        // Find and replace the result
        for (int i = 0; i < results.size(); i++) {
            GameResult result = results.get(i);
            // Compare all fields except playerTrigram to find the matching result
            if (result.getMapSize().equals(oldResult.getMapSize()) &&
                result.getTimeMillis() == oldResult.getTimeMillis() &&
                result.getAccuracyPercent() == oldResult.getAccuracyPercent() &&
                result.getDateMillis() == oldResult.getDateMillis()) {
                results.set(i, newResult);
                break;
            }
        }
        
        String serialized = serialize(results);
        pref.putString(KEY_RESULTS, serialized);
        pref.flush();
    }

    public List<GameResult> loadResults() {
        Preferences pref = Gdx.app.getPreferences(PREF_NAME);
        String serialized = pref.getString(KEY_RESULTS, "");
        return deserialize(serialized);
    }

    private String serialize(List<GameResult> results) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            GameResult r = results.get(i);
            sb.append(r.getMapSize());
            sb.append("|");
            sb.append(r.getTimeMillis());
            sb.append("|");
            sb.append(r.getAccuracyPercent());
            sb.append("|");
            sb.append(r.getDateMillis());
            sb.append("|");
            sb.append(r.getPlayerTrigram());
            if (i < results.size() - 1) {
                sb.append(";");
            }
        }
        return sb.toString();
    }

    private List<GameResult> deserialize(String data) {
        List<GameResult> results = new ArrayList<>();
        if (data == null || data.isEmpty()) {
            return results;
        }
        String[] entries = data.split(";");
        for (String entry : entries) {
            String[] parts = entry.split("\\|");
            if (parts.length == 5) {
                // New format: mapSize|timeMillis|accuracyPercent|dateMillis|playerTrigram
                String mapSize = parts[0];
                long timeMillis = Long.parseLong(parts[1]);
                int accuracyPercent = Integer.parseInt(parts[2]);
                long dateMillis = Long.parseLong(parts[3]);
                String playerTrigram = parts[4];
                results.add(new GameResult(mapSize, timeMillis, accuracyPercent, dateMillis, playerTrigram));
            } else if (parts.length == 4) {
                // Old format with date: mapSize|timeMillis|accuracyPercent|dateMillis
                String mapSize = parts[0];
                long timeMillis = Long.parseLong(parts[1]);
                int accuracyPercent = Integer.parseInt(parts[2]);
                long dateMillis = Long.parseLong(parts[3]);
                results.add(new GameResult(mapSize, timeMillis, accuracyPercent, dateMillis));
            } else if (parts.length == 3) {
                // Oldest format: mapSize|timeMillis|accuracyPercent
                String mapSize = parts[0];
                long timeMillis = Long.parseLong(parts[1]);
                int accuracyPercent = Integer.parseInt(parts[2]);
                results.add(new GameResult(mapSize, timeMillis, accuracyPercent));
            }
        }
        return results;
    }
}
