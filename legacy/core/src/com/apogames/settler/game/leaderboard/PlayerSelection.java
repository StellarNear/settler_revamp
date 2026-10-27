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
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen for selecting or adding a player trigram when completing a game.
 */
public class PlayerSelection extends SequentiallyThinkingScreenModel {

    public static final String FUNCTION_BACK = "MENU_QUIT";
    public static final String FUNCTION_CONFIRM = "PLAYER_SELECTION_CONFIRM";
    public static final String FUNCTION_ADD_PLAYER = "PLAYER_SELECTION_ADD";

    private static final int MAX_PLAYER_NAME_LENGTH = 3;

    private boolean isPressed = false;
    private int mouseX = 0;
    private int mouseY = 0;

    private final MainPanel mainPanel;
    private final GameResult gameResult; // The result to save after player selection
    private final Scoreboard scoreboard;

    private List<String> existingPlayers = new ArrayList<>();
    private String selectedPlayer = "";
    private String newPlayerInput = "";
    private boolean showingAddPlayer = false;
    private boolean resultSaved = false;

    public PlayerSelection(MainPanel game, GameResult gameResult) {
        super(game);
        this.mainPanel = game;
        this.gameResult = gameResult;
        this.scoreboard = new Scoreboard();
        this.loadExistingPlayers();
    }

    private void loadExistingPlayers() {
        // Extract unique player trigrams from existing scoreboard results
        List<GameResult> results = scoreboard.loadResults();
        existingPlayers.clear();
        for (GameResult result : results) {
            String player = result.getPlayerTrigram();
            if (!player.isEmpty() && !existingPlayers.contains(player)) {
                existingPlayers.add(player);
            }
        }
        // Sort alphabetically
        existingPlayers.sort(String::compareToIgnoreCase);
    }

    @Override
    public void init() {
        this.getMainPanel().resetSize(Constants.GAME_WIDTH, Constants.GAME_HEIGHT);
        this.setNeededButtonsVisible();
    }

    public void setNeededButtonsVisible() {
        setButtonVisible(FUNCTION_BACK);
        setButtonVisible(FUNCTION_CONFIRM);
        setButtonVisible(FUNCTION_ADD_PLAYER);
    }

    private void setButtonVisible(String function) {
        ApoButton button = getMainPanel().getButtonByFunction(function);
        if (button != null) {
            button.setVisible(true);
        }
    }

    @Override
    public void keyPressed(int keyCode, char character) {
        super.keyPressed(keyCode, character);
        if (!showingAddPlayer) {
            return;
        }
        if (keyCode == Input.Keys.BACKSPACE) {
            if (!newPlayerInput.isEmpty()) {
                newPlayerInput = newPlayerInput.substring(0, newPlayerInput.length() - 1);
            }
        } else if (keyCode == Input.Keys.ENTER) {
            confirmNewPlayer();
        }
    }

    @Override
    public void keyTyped(char character) {
        super.keyTyped(character);
        if (!showingAddPlayer) {
            return;
        }
        if (newPlayerInput.length() >= MAX_PLAYER_NAME_LENGTH) {
            return;
        }
        char c = Character.toUpperCase(character);
        if (c >= 'A' && c <= 'Z') {
            newPlayerInput += c;
        }
    }

    public void mouseMoved(int mouseX, int mouseY) {
    }

    public void mouseButtonReleased(int mouseX, int mouseY, boolean isRightButton) {
        this.isPressed = false;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        if (!isRightButton && !showingAddPlayer && !existingPlayers.isEmpty()) {
            checkForPlayerClick(mouseX, mouseY);
        }
    }

    private void checkForPlayerClick(int clickX, int clickY) {
        float centerX = Constants.GAME_WIDTH / 2f;
        float startY = Constants.GAME_HEIGHT / 2f + 220;
        float lineHeight = 30f;
        float yPos = startY + lineHeight; // Skip the "Existing Players:" label

        for (int i = 0; i < existingPlayers.size(); i++) {
            float itemY = yPos + (i * lineHeight);
            // Check if click is within the player item area
            if (clickX >= centerX - 100 && clickX <= centerX + 100 &&
                clickY >= itemY - 10 && clickY <= itemY + 10) {
                selectedPlayer = existingPlayers.get(i);
                break;
            }
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
            case PlayerSelection.FUNCTION_BACK:
                // Leave the screen, the result is saved nevertheless so that no game is lost
                saveResult(selectedPlayer.isEmpty() ? newPlayerInput : selectedPlayer);
                getMainPanel().changeToMenu();
                break;
            case PlayerSelection.FUNCTION_CONFIRM:
                // Save the game result, the player tag is optional but the result must never be lost
                saveResult(selectedPlayer.isEmpty() ? newPlayerInput : selectedPlayer);
                getMainPanel().changeToMenu();
                break;
            case PlayerSelection.FUNCTION_ADD_PLAYER:
                // Toggle add player input
                showingAddPlayer = !showingAddPlayer;
                if (!showingAddPlayer) {
                    newPlayerInput = "";
                }
                setNeededButtonsVisible();
                break;
        }
    }

    private void setButtonsVisibility() {
        // Button visibility is handled in setNeededButtonsVisible()
    }

    public void mouseWheelChanged(int changed) {
    }

    @Override
    protected void quit() {
        // The game was played, the result is saved even if the screen is left without a tag
        saveResult(selectedPlayer.isEmpty() ? newPlayerInput : selectedPlayer);
        getMainPanel().changeToMenu();
    }

    /**
     * Saves the game result with the given player tag, the result is only stored once.
     */
    private void saveResult(String player) {
        if (resultSaved) {
            return;
        }
        resultSaved = true;
        String normalized = player == null ? "" : player.trim().toUpperCase();
        if (!normalized.isEmpty() && !existingPlayers.contains(normalized)) {
            existingPlayers.add(normalized);
            existingPlayers.sort(String::compareToIgnoreCase);
        }
        scoreboard.saveResult(new GameResult(
            gameResult.getMapSize(),
            gameResult.getTimeMillis(),
            gameResult.getAccuracyPercent(),
            gameResult.getDateMillis(),
            normalized,
            gameResult.getTotalClicks(),
            gameResult.getTotalMissClicks()
        ));
    }

    @Override
    public void doThink(float delta) {
    }

    /**
     * Takes over the entered player name, adds it to the list of known players and selects it.
     */
    private void confirmNewPlayer() {
        if (newPlayerInput.isEmpty()) {
            return;
        }
        String normalized = newPlayerInput.toUpperCase();
        if (!existingPlayers.contains(normalized)) {
            existingPlayers.add(normalized);
            existingPlayers.sort(String::compareToIgnoreCase);
        }
        selectedPlayer = normalized;
        showingAddPlayer = false;
        newPlayerInput = "";
        setNeededButtonsVisible();
    }

    @Override
    public void render() {
        getMainPanel().getRenderer().begin(ShapeRenderer.ShapeType.Filled);
        getMainPanel().getRenderer().setColor(Constants.COLOR_PURPLE[0], Constants.COLOR_PURPLE[1], Constants.COLOR_PURPLE[2], 1f);
        getMainPanel().getRenderer().rect(0, 0, Constants.GAME_WIDTH, Constants.GAME_HEIGHT);
        getMainPanel().getRenderer().end();

        getMainPanel().spriteBatch.begin();
        
        float centerX = Constants.GAME_WIDTH / 2f;
        float startY = Constants.GAME_HEIGHT / 2f + 220;
        float lineHeight = 30f;
        
        // Won info (finish display preserved)
        long totalSeconds = gameResult.getTimeMillis() / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        String formattedTime = String.format("%d:%02d", minutes, seconds);
        getMainPanel().drawString(Localization.getInstance().getCommon().get("won"), centerX, Constants.GAME_HEIGHT / 2f + 160, Constants.COLOR_BLACK, AssetLoader.font30, DrawString.MIDDLE, true, false);
        getMainPanel().drawString(Localization.getInstance().getCommon().format("won_time", formattedTime), centerX, Constants.GAME_HEIGHT / 2f + 130, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.MIDDLE, true, false);
        getMainPanel().drawString(Localization.getInstance().getCommon().format("won_stats",
                String.valueOf(gameResult.getTotalClicks()),
                String.valueOf(gameResult.getTotalMissClicks()),
                String.valueOf(gameResult.getAccuracyPercent())),
                centerX, Constants.GAME_HEIGHT / 2f + 100, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.MIDDLE, true, false);
        
        // Title
        getMainPanel().drawString("Select Player", centerX, 50, Constants.COLOR_WHITE, AssetLoader.font30, DrawString.MIDDLE, true, false);
        
        if (showingAddPlayer) {
            // Show add player input
            getMainPanel().drawString("Enter new player (3 letters):", centerX, startY, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.MIDDLE, true, false);
            getMainPanel().drawString(newPlayerInput + "|", centerX, startY + lineHeight, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.MIDDLE, true, false);
            getMainPanel().drawString("(Enter to confirm)", centerX, startY + lineHeight * 2, Constants.COLOR_GREY_BRIGHT, AssetLoader.font15, DrawString.MIDDLE, true, false);
        } else {
            // Show existing players as radio button style selection
            float yPos = startY;
            getMainPanel().drawString("Existing Players:", centerX, yPos, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.MIDDLE, true, false);
            yPos += lineHeight;
            
            for (int i = 0; i < existingPlayers.size(); i++) {
                String player = existingPlayers.get(i);
                boolean isSelected = player.equals(selectedPlayer);
                
                // Draw selection indicator
                if (isSelected) {
                    getMainPanel().drawString("[X]", centerX - 100, yPos, Constants.COLOR_GREEN, AssetLoader.font20, DrawString.BEGIN, true, false);
                } else {
                    getMainPanel().drawString("[ ]", centerX - 100, yPos, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.BEGIN, true, false);
                }
                
                // Draw player name
                getMainPanel().drawString(player, centerX - 80, yPos, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.BEGIN, true, false);
                
                // Make it clickable - we'll handle this in mouseButtonFunction
                yPos += lineHeight;
            }
            
            // Instructions
            getMainPanel().drawString("Click to select, or press '+' to add new", centerX, yPos + lineHeight, Constants.COLOR_GREY_BRIGHT, AssetLoader.font15, DrawString.MIDDLE, true, false);
        }

        getMainPanel().spriteBatch.end();

        for (ApoButton button : this.getMainPanel().getButtons()) {
            button.render(this.getMainPanel());
        }
    }

    public void drawOverlay() {
    }

    @Override
    public void dispose() {
    }
}