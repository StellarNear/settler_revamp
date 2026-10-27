package com.apogames.settler.game.leaderboard;

import com.apogames.settler.Constants;
import com.apogames.settler.asset.AssetLoader;
import com.apogames.settler.backend.DrawString;
import com.apogames.settler.backend.SequentiallyThinkingScreenModel;
import com.apogames.settler.common.Localization;
import com.apogames.settler.entity.ApoButton;
import com.apogames.settler.game.MainPanel;
import com.apogames.settler.game.menu.Menu;
import com.apogames.settler.game.tiwanaku.GameResult;
import com.apogames.settler.game.tiwanaku.Scoreboard;
import com.apogames.settler.game.leaderboard.PlayerSelectionForEdit;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.ArrayList;
import java.util.List;

public class Leaderboard extends SequentiallyThinkingScreenModel {

    public static final String FUNCTION_BACK = Menu.FUNCTION_BACK;
    public static final String FUNCTION_RESET = "LEADERBOARD_RESET";

     private final boolean[] keys = new boolean[256];
     private boolean isPressed = false;
     private int mouseX = 0;
     private int mouseY = 0;
     private GameResult resultToEdit = null;
    private int scrollOffset = 0;
    private int resultsSize = 0;
    private String filterMapSize = null; // null = all
    private String filterPlayer = "";
    private int mapFilterIndex = -1;
    private int playerFilterIndex = -1;
    private List<String> mapOptions = new ArrayList<>();
    private List<String> playerOptions = new ArrayList<>();

    public Leaderboard(final MainPanel game) {
        super(game);
    }

    public void setNeededButtonsVisible() {
        getMainPanel().getButtonByFunction(FUNCTION_BACK).setVisible(true);
        getMainPanel().getButtonByFunction(FUNCTION_RESET).setVisible(true);
    }

    @Override
    public void init() {
        this.getMainPanel().resetSize(Constants.GAME_WIDTH, Constants.GAME_HEIGHT);
        this.setNeededButtonsVisible();
        this.setButtonsVisibility();
    }

    @Override
    public void keyPressed(int keyCode, char character) {
        super.keyPressed(keyCode, character);
        setKeyState(keyCode, true);
    }

    @Override
    public void keyTyped(char character) {
        super.keyTyped(character);
        if (character == 'm' || character == 'M') {
            cycleMapFilter();
        } else if (character == 'p' || character == 'P') {
            cyclePlayerFilter();
        }
    }

    private void cycleMapFilter() {
        // build options lazily
        Scoreboard sb = new Scoreboard();
        List<GameResult> all = sb.loadResults();
        List<String> opts = new ArrayList<>();
        opts.add(null); // all
        for (GameResult r : all) {
            if (!opts.contains(r.getMapSize())) opts.add(r.getMapSize());
        }
        mapOptions = opts;
        mapFilterIndex++;
        if (mapFilterIndex >= mapOptions.size()) mapFilterIndex = 0;
        filterMapSize = mapOptions.get(mapFilterIndex);
    }

    private void cyclePlayerFilter() {
        Scoreboard sb = new Scoreboard();
        List<GameResult> all = sb.loadResults();
        List<String> opts = new ArrayList<>();
        opts.add("");
        for (GameResult r : all) {
            String p = r.getPlayerTrigram();
            if (p != null && !p.isEmpty() && !opts.contains(p)) opts.add(p);
        }
        playerOptions = opts;
        playerFilterIndex++;
        if (playerFilterIndex >= playerOptions.size()) playerFilterIndex = 0;
        filterPlayer = playerOptions.get(playerFilterIndex);
    }

    @Override
    public void keyButtonReleased(int keyCode, char character) {
        super.keyButtonReleased(keyCode, character);
        setKeyState(keyCode, false);
    }

    /**
     * Stores the state of a key, key codes which are out of range are ignored.
     */
    private void setKeyState(int keyCode, boolean pressed) {
        if (keyCode >= 0 && keyCode < keys.length) {
            keys[keyCode] = pressed;
        }
    }

    public void mouseMoved(int mouseX, int mouseY) {
    }

    public void mouseButtonReleased(int mouseX, int mouseY, boolean isRightButton) {
        this.isPressed = false;
        // Check if click was on a player name to edit it
        if (!isRightButton) {
            checkForPlayerNameClick(mouseX, mouseY);
        }
    }

    public void mousePressed(int x, int y, boolean isRightButton) {
        if (isRightButton && !this.isPressed) {
            this.isPressed = true;
            this.mouseX = x;
            this.mouseY = y;
        }
    }

    public void mouseDragged(int x, int y, boolean isRightButton) {
        if (isRightButton) {
            if (!this.isPressed) {
                this.mousePressed(x, y, isRightButton);
            }
        }
    }

    @Override
    public void mouseButtonFunction(String function) {
        super.mouseButtonFunction(function);
        switch (function) {
            case Leaderboard.FUNCTION_BACK:
                quit();
                break;
            case Leaderboard.FUNCTION_RESET:
                new Scoreboard().clearResults();
                break;
        }
    }

    private void setButtonsVisibility() {
    }

    public void mouseWheelChanged(int changed) {
        // changed < 0 = scroll up, > 0 = scroll down (libgdx convention)
        int maxScroll = Math.max(0, (resultsSize - 5) * lineHeight());
        if (changed < 0) {
            scrollOffset = Math.max(0, scrollOffset - lineHeight());
        } else if (changed > 0) {
            scrollOffset = Math.min(maxScroll, scrollOffset + lineHeight());
        }
    }

    private int lineHeight() {
        return 45;
    }

    @Override
    protected void quit() {
        getMainPanel().changeToMenu();
    }

    @Override
    public void doThink(float delta) {
    }

    /**
     * Checks if a result matches the currently active filters.
     */
    private boolean matchesFilter(GameResult result) {
        if (filterMapSize != null && !filterMapSize.equals(result.getMapSize())) {
            return false;
        }
        if (!filterPlayer.isEmpty() && (result.getPlayerTrigram() == null
            || !result.getPlayerTrigram().toLowerCase().contains(filterPlayer.toLowerCase()))) {
            return false;
        }
        return true;
    }

    /**
     * Returns the results which are currently visible, the order is the same as in the render method.
     */
    private List<GameResult> getFilteredResults(List<GameResult> results) {
        List<GameResult> filtered = new ArrayList<>();
        for (GameResult result : results) {
            if (matchesFilter(result)) {
                filtered.add(result);
            }
        }
        return filtered;
    }

    @Override
    public void render() {
        getMainPanel().getRenderer().begin(ShapeRenderer.ShapeType.Filled);
        getMainPanel().getRenderer().setColor(Constants.COLOR_PURPLE[0], Constants.COLOR_PURPLE[1], Constants.COLOR_PURPLE[2], 1f);
        getMainPanel().getRenderer().rect(0, 0, Constants.GAME_WIDTH, Constants.GAME_HEIGHT);
        getMainPanel().getRenderer().end();

        float startY = Constants.GAME_HEIGHT / 2f + 40;
        float lineHeight = 45;

        Scoreboard scoreboard = new Scoreboard();
        List<GameResult> results = scoreboard.loadResults();
        List<GameResult> filtered = getFilteredResults(results);
        this.resultsSize = filtered.size();
        long totalTime = 0;
        double sumAcc = 0;
        for (GameResult r : filtered) {
            totalTime += r.getTimeMillis();
            sumAcc += r.getAccuracyPercent();
        }
        double meanAcc = filtered.isEmpty() ? 0 : sumAcc / filtered.size();

        float headerY = startY + 10 - scrollOffset;
        float headerHeight = 40f;
        float tableWidth = 650f;
        float tableX = Constants.GAME_WIDTH / 2f - tableWidth / 2f;

        if (!results.isEmpty()) {
            getMainPanel().getRenderer().begin(ShapeRenderer.ShapeType.Filled);
            getMainPanel().getRenderer().setColor(Constants.COLOR_RED_DARK[0], Constants.COLOR_RED_DARK[1], Constants.COLOR_RED_DARK[2], 1f);
            getMainPanel().getRenderer().rect(tableX, headerY, tableWidth, headerHeight);
            getMainPanel().getRenderer().end();
        }

        getMainPanel().spriteBatch.begin();
        getMainPanel().drawString(Localization.getInstance().getCommon().get("title"), Constants.GAME_WIDTH / 2f, 90, Constants.COLOR_WHITE, AssetLoader.font40, DrawString.MIDDLE, true, false);

        if (results.isEmpty()) {
            getMainPanel().drawString(Localization.getInstance().getCommon().get("leaderboard.no_results"), Constants.GAME_WIDTH / 2f, Constants.GAME_HEIGHT / 2f, Constants.COLOR_WHITE, AssetLoader.font30, DrawString.MIDDLE, true, false);
        } else {
            float textY = headerY + headerHeight / 2f + 5;
            getMainPanel().drawString("Date", tableX + 10, textY, Constants.COLOR_WHITE, AssetLoader.font25, DrawString.BEGIN, true, false);
            getMainPanel().drawString("Size", tableX + 220, textY, Constants.COLOR_WHITE, AssetLoader.font25, DrawString.BEGIN, true, false);
            getMainPanel().drawString("Time", tableX + 360, textY, Constants.COLOR_WHITE, AssetLoader.font25, DrawString.BEGIN, true, false);
            getMainPanel().drawString("Player", tableX + 470, textY, Constants.COLOR_WHITE, AssetLoader.font25, DrawString.BEGIN, true, false);
            getMainPanel().drawString("Accuracy", tableX + 580, textY, Constants.COLOR_WHITE, AssetLoader.font25, DrawString.BEGIN, true, false);

            float recordY = headerY + headerHeight + 15;
            if (filtered.isEmpty()) {
                getMainPanel().drawString(Localization.getInstance().getCommon().get("leaderboard.no_matching"), Constants.GAME_WIDTH / 2f, recordY, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.MIDDLE, true, false);
            } else {
                for (GameResult result : filtered) {
                    getMainPanel().drawString(result.getFormattedDate(), tableX + 10, recordY, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.BEGIN, true, false);
                    getMainPanel().drawString(result.getMapSize(), tableX + 220, recordY, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.BEGIN, true, false);
                    getMainPanel().drawString(formatTime(result.getTimeMillis()), tableX + 360, recordY, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.BEGIN, true, false);
                    String player = result.getPlayerTrigram();
                    if (player == null || player.isEmpty()) {
                        player = "N/A";
                    }
                    getMainPanel().drawString(player, tableX + 470, recordY, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.BEGIN, true, false);
                    getMainPanel().drawString(result.getAccuracyPercent() + "%", tableX + 580, recordY, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.BEGIN, true, false);
                    recordY += lineHeight;
                }
            }

            String mapLabel = filterMapSize == null ? Localization.getInstance().getCommon().get("leaderboard.all") : filterMapSize;
            String playerLabel = filterPlayer.isEmpty() ? Localization.getInstance().getCommon().get("leaderboard.none") : filterPlayer;
            getMainPanel().drawString(Localization.getInstance().getCommon().get("leaderboard.map") + ": " + mapLabel + "    " + Localization.getInstance().getCommon().get("leaderboard.player") + ": " + playerLabel, tableX, recordY + 20, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.BEGIN, true, false);
            getMainPanel().drawString(Localization.getInstance().getCommon().get("leaderboard.mean_accuracy") + ": " + String.format("%.1f", meanAcc) + "%    " + Localization.getInstance().getCommon().get("leaderboard.total_time") + ": " + formatTime(totalTime) + "    (" + filtered.size() + " " + Localization.getInstance().getCommon().get("leaderboard.games") + ")", tableX, recordY + 50, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.BEGIN, true, false);
            getMainPanel().drawString(Localization.getInstance().getCommon().get("leaderboard.hint_keys"), tableX, recordY + 80, Constants.COLOR_GREY_BRIGHT, AssetLoader.font15, DrawString.BEGIN, true, false);
        }
        getMainPanel().spriteBatch.end();

        for (ApoButton button : this.getMainPanel().getButtons()) {
            button.render(this.getMainPanel());
        }
    }

    private String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
     }

     /**
      * Checks if the mouse click was on a player name in the leaderboard
      * and prepares to edit that player's trigram.
      */
     private void checkForPlayerNameClick(int clickX, int clickY) {
         Scoreboard scoreboard = new Scoreboard();
         List<GameResult> results = scoreboard.loadResults();
         if (results.isEmpty()) {
             return;
         }

         // The click must be checked against the same list which is drawn, otherwise a filtered
         // leaderboard would edit a different result than the one the user has clicked on
         List<GameResult> filtered = getFilteredResults(results);
         List<Integer> resultIndices = new ArrayList<>();
         for (int i = 0; i < results.size(); i++) {
             if (matchesFilter(results.get(i))) {
                 resultIndices.add(i);
             }
         }
         if (filtered.isEmpty()) {
             return;
         }

        float startY = Constants.GAME_HEIGHT / 2f + 40;
        float lineHeight = 45;
        float headerY = startY + 10;
        float headerHeight = 40f;
        
        // Column widths and positions (must match render method)
        final int leftMargin = 10;
        final int dateWidth = 210; // from 10 to 220
        final int sizeWidth = 140; // from 220 to 360
        final int timeWidth = 110; // from 360 to 470
        final int playerWidth = 110; // from 470 to 580
        final int accuracyWidth = 70; // from 580 to 650
        final int tableWidth = leftMargin + dateWidth + sizeWidth + timeWidth + playerWidth + accuracyWidth;
        final float tableX = Constants.GAME_WIDTH / 2f - tableWidth / 2f;

        float recordY = headerY + headerHeight + 15 - scrollOffset;
        for (int i = 0; i < filtered.size(); i++) {
            GameResult result = filtered.get(i);
             float playerX = tableX + leftMargin + dateWidth + sizeWidth + timeWidth;
             float playerWidthActual = playerWidth; // 110
             float playerHeight = 20; // Approximate height for text

             if (clickX >= playerX && clickX <= playerX + playerWidthActual &&
                 clickY >= recordY - 10 && clickY <= recordY + playerHeight) {
                 // Click was on this player name, prepare to edit
                 this.resultToEdit = result;
                 showPlayerSelectionForEdit(result, resultIndices.get(i));
                 return;
             }
             recordY += lineHeight;
         }
     }
 
     /**
      * Shows a player selection screen for editing an existing result's player trigram.
      */
     private void showPlayerSelectionForEdit(GameResult resultToEdit, int resultIndex) {
         getMainPanel().changeModel(new PlayerSelectionForEdit(this.getMainPanel(), resultToEdit, resultIndex));
     }


     public void drawOverlay() {
    }

    @Override
    public void dispose() {
    }
}
