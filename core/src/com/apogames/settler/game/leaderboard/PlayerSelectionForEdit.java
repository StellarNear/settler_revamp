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
import com.apogames.settler.utils.Logger;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen for selecting or changing a player trigram for an existing leaderboard entry.
 */
public class PlayerSelectionForEdit extends SequentiallyThinkingScreenModel {

    public static final String FUNCTION_BACK = "MENU_QUIT";
    public static final String FUNCTION_CONFIRM = "PLAYER_SELECTION_EDIT_CONFIRM";
    public static final String FUNCTION_ADD_PLAYER = "PLAYER_SELECTION_EDIT_ADD";

    private final boolean[] keys = new boolean[256];
    private boolean isPressed = false;
    private int mouseX = 0;
    private int mouseY = 0;

    private final MainPanel mainPanel;
    private final GameResult resultToEdit; // The result to update with new player selection
    private final Scoreboard scoreboard;

    private List<String> existingPlayers = new ArrayList<>();
    private String selectedPlayer = "";
    private String newPlayerInput = "";
    private boolean showingAddPlayer = false;

    public PlayerSelectionForEdit(MainPanel game, GameResult resultToEdit) {
        super(game);
        this.mainPanel = game;
        this.resultToEdit = resultToEdit;
        this.scoreboard = new Scoreboard();
        this.loadExistingPlayers();
        // Pre-select the current player if it exists
        if (resultToEdit != null && !resultToEdit.getPlayerTrigram().isEmpty()) {
            this.selectedPlayer = resultToEdit.getPlayerTrigram();
        }
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
        getMainPanel().getButtonByFunction(FUNCTION_BACK).setVisible(true);
        getMainPanel().getButtonByFunction(FUNCTION_CONFIRM).setVisible(true);
        getMainPanel().getButtonByFunction(FUNCTION_ADD_PLAYER).setVisible(true);
    }

    @Override
    public void keyPressed(int keyCode, char character) {
        super.keyPressed(keyCode, character);
        keys[keyCode] = true;
        Logger.debug("PlayerSelectionForEdit", "keyPressed keyCode=" + keyCode + " character=" + character + " showingAddPlayer=" + showingAddPlayer);
        if (showingAddPlayer) {
            processKeyInput(keyCode, character);
        }
    }

    @Override
    public void keyTyped(char character) {
        super.keyTyped(character);
        if (showingAddPlayer) {
            processTypedCharacter(character);
        }
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
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        Logger.debug("PlayerSelectionForEdit", "mouseButtonReleased x=" + mouseX + " y=" + mouseY + " right=" + isRightButton + " showingAddPlayer=" + showingAddPlayer);
        if (!isRightButton && !showingAddPlayer && !existingPlayers.isEmpty()) {
            checkForPlayerClick(mouseX, mouseY);
        }
    }

    private void checkForPlayerClick(int clickX, int clickY) {
        float centerX = Constants.GAME_WIDTH / 2f;
        float startY = Constants.GAME_HEIGHT / 2f + 100;
        float lineHeight = 30f;
        float yPos = startY + lineHeight; // Skip the "Existing Players:" label

        for (int i = 0; i < existingPlayers.size(); i++) {
            float itemY = yPos + (i * lineHeight);
            // Check if click is within the player item area
            if (clickX >= centerX - 100 && clickX <= centerX + 100 &&
                clickY >= itemY - 10 && clickY <= itemY + 10) {
                selectedPlayer = existingPlayers.get(i);
                Logger.debug("PlayerSelectionForEdit", "selected player '" + selectedPlayer + "' at index " + i + " click=(" + clickX + "," + clickY + ")");
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
            case PlayerSelectionForEdit.FUNCTION_BACK:
                // Go back to leaderboard without saving
                getMainPanel().changeToMenu();
                break;
            case PlayerSelectionForEdit.FUNCTION_CONFIRM:
                // Save the edited game result with selected player
                String playerToSave = selectedPlayer.isEmpty() ? newPlayerInput : selectedPlayer;
                Logger.debug("PlayerSelectionForEdit", "CONFIRM pressed, selectedPlayer='" + selectedPlayer + "' newPlayerInput='" + newPlayerInput + "' playerToSave='" + playerToSave + "'");
                if (!playerToSave.isEmpty()) {
                    String normalized = playerToSave.toUpperCase();
                    if (!existingPlayers.contains(normalized)) {
                        existingPlayers.add(normalized);
                        existingPlayers.sort(String::compareToIgnoreCase);
                        Logger.debug("PlayerSelectionForEdit", "added new player '" + normalized + "' to existing players on confirm");
                    }
                    selectedPlayer = normalized;
                    GameResult updatedResult = new GameResult(
                        resultToEdit.getMapSize(),
                        resultToEdit.getTimeMillis(),
                        resultToEdit.getAccuracyPercent(),
                        resultToEdit.getDateMillis(),
                        selectedPlayer,
                        resultToEdit.getTotalClicks(),
                        resultToEdit.getTotalMissClicks()
                    );
                    // Update the scoreboard with the new player trigram
                    scoreboard.updateResult(resultToEdit, updatedResult);
                    getMainPanel().changeToMenu();
                } else {
                    Logger.debug("PlayerSelectionForEdit", "CONFIRM pressed but no player selected or typed, ignoring");
                }
                break;
            case PlayerSelectionForEdit.FUNCTION_ADD_PLAYER:
                // Toggle add player input
                showingAddPlayer = !showingAddPlayer;
                Logger.debug("PlayerSelectionForEdit", "ADD_PLAYER toggled, showingAddPlayer=" + showingAddPlayer);
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
        getMainPanel().changeToMenu();
    }

    @Override
    public void doThink(float delta) {
    }

    private void processKeyInput(int keyCode, char character) {
        // Handle backspace
        if (keyCode == Input.Keys.BACKSPACE && !newPlayerInput.isEmpty()) {
            Logger.debug("PlayerSelectionForEdit", "backspace pressed, input was='" + newPlayerInput + "'");
            newPlayerInput = newPlayerInput.substring(0, newPlayerInput.length() - 1);
            Logger.debug("PlayerSelectionForEdit", "backspace applied, input now='" + newPlayerInput + "'");
        }
        // Handle enter to confirm new player
        else if (keyCode == Input.Keys.ENTER) {
            Logger.debug("PlayerSelectionForEdit", "enter pressed, input='" + newPlayerInput + "'");
            if (!newPlayerInput.isEmpty()) {
                // Add to existing players if not already there
                String normalized = newPlayerInput.toUpperCase();
                if (!existingPlayers.contains(normalized)) {
                    existingPlayers.add(normalized);
                    existingPlayers.sort(String::compareToIgnoreCase);
                    Logger.debug("PlayerSelectionForEdit", "added new player '" + normalized + "' to existing players");
                } else {
                    Logger.debug("PlayerSelectionForEdit", "player '" + normalized + "' already exists");
                }
                selectedPlayer = normalized;
                showingAddPlayer = false;
                newPlayerInput = "";
                setNeededButtonsVisible();
                Logger.debug("PlayerSelectionForEdit", "confirmed player '" + selectedPlayer + "', showingAddPlayer=false");
            } else {
                Logger.debug("PlayerSelectionForEdit", "enter pressed but input empty, ignoring");
            }
        }
    }

    private void processTypedCharacter(char character) {
        Logger.debug("PlayerSelectionForEdit", "keyTyped character=" + character + " input='" + newPlayerInput + "'");
        if (newPlayerInput.length() >= 3) {
            Logger.debug("PlayerSelectionForEdit", "input max length reached, ignoring char=" + character);
            return;
        }
        char c = Character.toUpperCase(character);
        if (c >= 'A' && c <= 'Z') {
            newPlayerInput += c;
            Logger.debug("PlayerSelectionForEdit", "accepted char '" + c + "', input now='" + newPlayerInput + "'");
        } else {
            Logger.debug("PlayerSelectionForEdit", "rejected non-letter char '" + c + "'");
        }
    }

    @Override
    public void render() {
        getMainPanel().getRenderer().begin(ShapeRenderer.ShapeType.Filled);
        getMainPanel().getRenderer().setColor(Constants.COLOR_PURPLE[0], Constants.COLOR_PURPLE[1], Constants.COLOR_PURPLE[2], 1f);
        getMainPanel().getRenderer().rect(0, 0, Constants.GAME_WIDTH, Constants.GAME_HEIGHT);
        getMainPanel().getRenderer().end();

        getMainPanel().spriteBatch.begin();
        
        float centerX = Constants.GAME_WIDTH / 2f;
        float startY = Constants.GAME_HEIGHT / 2f + 100;
        float lineHeight = 30f;
        
        // Title
        getMainPanel().drawString("Select Player for Edit", centerX, 50, Constants.COLOR_WHITE, AssetLoader.font30, DrawString.MIDDLE, true, false);
        
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