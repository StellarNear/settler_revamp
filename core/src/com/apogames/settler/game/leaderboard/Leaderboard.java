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
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.List;

public class Leaderboard extends SequentiallyThinkingScreenModel {

    public static final String FUNCTION_BACK = Menu.FUNCTION_BACK;
    public static final String FUNCTION_RESET = "LEADERBOARD_RESET";

    private final boolean[] keys = new boolean[256];

    private boolean isPressed = false;

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
        keys[keyCode] = true;
    }

    @Override
    public void keyButtonReleased(int keyCode, char character) {
        super.keyButtonReleased(keyCode, character);
        keys[keyCode] = false;
    }

    public void mouseMoved(int mouseX, int mouseY) {
    }

    public void mouseButtonReleased(int mouseX, int mouseY, boolean isRightButton) {
        this.isPressed = false;
    }

    public void mousePressed(int x, int y, boolean isRightButton) {
        if (isRightButton && !this.isPressed) {
            this.isPressed = true;
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
    }

    @Override
    protected void quit() {
        getMainPanel().changeToMenu();
    }

    @Override
    public void doThink(float delta) {
    }

    @Override
    public void render() {
        getMainPanel().getRenderer().begin(ShapeRenderer.ShapeType.Filled);
        getMainPanel().getRenderer().setColor(Constants.COLOR_PURPLE[0], Constants.COLOR_PURPLE[1], Constants.COLOR_PURPLE[2], 1f);
        getMainPanel().getRenderer().rect(0, 0, Constants.GAME_WIDTH, Constants.GAME_HEIGHT);
        getMainPanel().getRenderer().end();

        float startY = Constants.GAME_HEIGHT / 2f + 120;
        float lineHeight = 45;

        Scoreboard scoreboard = new Scoreboard();
        List<GameResult> results = scoreboard.loadResults();

        if (!results.isEmpty()) {
            float headerY = startY + 10;
            float headerHeight = 40f;
            float tableWidth = 650f;
            float tableX = Constants.GAME_WIDTH / 2f - tableWidth / 2f;

            getMainPanel().getRenderer().begin(ShapeRenderer.ShapeType.Filled);
            getMainPanel().getRenderer().setColor(Constants.COLOR_RED_DARK[0], Constants.COLOR_RED_DARK[1], Constants.COLOR_RED_DARK[2], 1f);
            getMainPanel().getRenderer().rect(tableX, headerY, tableWidth, headerHeight);
            getMainPanel().getRenderer().end();
        }

        getMainPanel().spriteBatch.begin();
        getMainPanel().drawString(Localization.getInstance().getCommon().get("title"), Constants.GAME_WIDTH / 2f, 90, Constants.COLOR_WHITE, AssetLoader.font40, DrawString.MIDDLE, true, false);

        if (results.isEmpty()) {
            getMainPanel().drawString("No results yet", Constants.GAME_WIDTH / 2f, Constants.GAME_HEIGHT / 2f, Constants.COLOR_WHITE, AssetLoader.font30, DrawString.MIDDLE, true, false);
        } else {
            float headerY = startY + 10;
            float headerHeight = 40f;
            float tableWidth = 650f;
            float tableX = Constants.GAME_WIDTH / 2f - tableWidth / 2f;

            float textY = headerY + headerHeight / 2f + 5;
            getMainPanel().drawString("Date", tableX + 10, textY, Constants.COLOR_WHITE, AssetLoader.font25, DrawString.BEGIN, true, false);
            getMainPanel().drawString("Size", tableX + 220, textY, Constants.COLOR_WHITE, AssetLoader.font25, DrawString.BEGIN, true, false);
            getMainPanel().drawString("Time", tableX + 360, textY, Constants.COLOR_WHITE, AssetLoader.font25, DrawString.BEGIN, true, false);
            getMainPanel().drawString("Accuracy", tableX + 500, textY, Constants.COLOR_WHITE, AssetLoader.font25, DrawString.BEGIN, true, false);

            float recordY = headerY + headerHeight + 15;
            for (GameResult result : results) {
                getMainPanel().drawString(result.getFormattedDate(), tableX + 10, recordY, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.BEGIN, true, false);
                getMainPanel().drawString(result.getMapSize(), tableX + 220, recordY, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.BEGIN, true, false);
                getMainPanel().drawString(formatTime(result.getTimeMillis()), tableX + 360, recordY, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.BEGIN, true, false);
                getMainPanel().drawString(result.getAccuracyPercent() + "%", tableX + 500, recordY, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.BEGIN, true, false);
                recordY += lineHeight;
            }
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

    public void drawOverlay() {
    }

    @Override
    public void dispose() {
    }
}
