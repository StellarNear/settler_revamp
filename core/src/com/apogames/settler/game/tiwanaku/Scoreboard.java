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
            if (parts.length == 4) {
                String mapSize = parts[0];
                long timeMillis = Long.parseLong(parts[1]);
                int accuracyPercent = Integer.parseInt(parts[2]);
                long dateMillis = Long.parseLong(parts[3]);
                results.add(new GameResult(mapSize, timeMillis, accuracyPercent, dateMillis));
            } else if (parts.length == 3) {
                String mapSize = parts[0];
                long timeMillis = Long.parseLong(parts[1]);
                int accuracyPercent = Integer.parseInt(parts[2]);
                results.add(new GameResult(mapSize, timeMillis, accuracyPercent));
            }
        }
        return results;
    }
}
