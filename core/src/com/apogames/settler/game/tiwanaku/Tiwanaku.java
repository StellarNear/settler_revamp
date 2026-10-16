package com.apogames.settler.game.tiwanaku;

import com.apogames.settler.Constants;
import com.apogames.settler.asset.AssetLoader;
import com.apogames.settler.backend.DrawString;
import com.apogames.settler.backend.SequentiallyThinkingScreenModel;
import com.apogames.settler.common.Localization;
import com.apogames.settler.entity.ApoButton;
import com.apogames.settler.entity.HintBubble;
import com.apogames.settler.game.MainPanel;
import com.apogames.settler.game.leaderboard.PlayerSelection;
import com.apogames.settler.level.LevelCreate;
import com.apogames.settler.level.Level;
import com.apogames.settler.level.NewLevelCreate;
import com.apogames.settler.level.Solve;
import com.apogames.settler.level.helper.Difficulty;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.ArrayList;

public class Tiwanaku extends SequentiallyThinkingScreenModel {

    private final int addX = 10;
    private final int addY = 100;

    private int addXScale = 0;

    public static final String FUNCTION_TIWANAKU_BACK = "TIWANAKU_QUIT";
    public static final String FUNCTION_NEW_LEVEL = "TIWANAKU_NEW_LEVEL";
    public static final String FUNCTION_RESTART = "TIWANAKU_RESTART";
    public static final String FUNCTION_FIX = "TIWANAKU_FIX";
    public static final String FUNCTION_HELP = "TIWANAKU_HELP";

    public static final String FUNCTION_FINISH_BACK = "TIWANAKU_FINISH_QUIT";
    public static final String FUNCTION_FINISH_NEW_LEVEL = "TIWANAKU_FINISH_NEW_LEVEL";
    public static final String FUNCTION_FINISH_RESTART = "TIWANAKU_FINISH_RESTART";

    public static final int HINT_VALUE_COUNT = 9;
    public static final int HUD_BUBBLE_SIZE = 80;
    public static final int HUD_BUBBLE_SPACING = 10;
    public static final int HUD_BUBBLE_START_Y = 170;

    private static final int HUD_TEXT_LEFT = 30;
    private static final int HUD_TEXT_WIDTH = 165;
    private static final int HUD_TEXT_LINE_HEIGHT = 22;
    private static final int HUD_TITLE_Y = 130;
    private static final int HUD_INSTRUCTION_Y = 180;
    private static final int HUD_EXIT_Y = 290;
    // The New Level button (see ButtonProvider) covers the banner from y=593 down,
    // so all HUD text has to stay above it.
    private static final int HUD_CONTENT_MIN_Y = 534;
    private static final int HUD_CONTENT_MAX_Y = 578;
    private static final int HUD_TIME_LABEL_Y = 252;
    private static final int HUD_TIME_VALUE_Y = 278;

    // Congratulation banner is 300 high and the finish buttons start at y=436,
    // which leaves room for a title, a subtitle, the resolution time and the stats.
    private static final int WON_TITLE_OFFSET = 40;
    private static final int WON_SUBTITLE_OFFSET = 78;
    private static final int WON_TIME_OFFSET = 108;
    private static final int WON_STATS_OFFSET = 145;

    public static final String[] FUNCTION_HINT = {
            "TIWANAKU_HINT_1", "TIWANAKU_HINT_2", "TIWANAKU_HINT_3", "TIWANAKU_HINT_4", "TIWANAKU_HINT_5",
            "TIWANAKU_HINT_6", "TIWANAKU_HINT_7", "TIWANAKU_HINT_8", "TIWANAKU_HINT_9"
    };

    private final boolean[] keys = new boolean[256];

    private boolean isPressed = false;

    private NewLevelCreate levelCreate;

    private Level level;

    private LevelSize levelSize = LevelSize.SMALL;
    private int maxRegionSize = 5;

    private boolean newLevel = false;

    private Solve solve;

    private GameState gameState = GameState.PLAY;

    private boolean hintMode;
    private int selectedX = -1;
    private int selectedY = -1;
    private int helpHintX = -1;
    private int helpHintY = -1;

    private Difficulty difficulty;

    private byte[][] error = null;

    // Level generation is spread across frames (see doThink) instead of blocking
    // the render thread, so large sizes (Uber/Titan) can't freeze the app.
    private static final int GENERATION_ATTEMPTS_PER_TICK = 1;
    private boolean generating = false;
    private boolean generationFailed = false;
    private boolean generationStatusRendered = false;
    private boolean generationRenderingActive = false;
    private boolean timerRenderingActive = false;

    // Continuous rendering is a shared resource: level generation and the running
    // timer both need it (the web build otherwise only redraws on input), so they
    // take a reference instead of saving and restoring the flag themselves.
    private int continuousRenderingRequests = 0;
    private boolean continuousRenderingBeforeRequests = true;

    private final GameTimer timer = new GameTimer();

    private Scoreboard scoreboard;

    public Tiwanaku(final MainPanel game) {
        super(game);
    }

    public void setNeededButtonsVisible() {
        if (this.generating) {
            getMainPanel().getButtonByFunction(FUNCTION_TIWANAKU_BACK).setVisible(true);
            getMainPanel().getButtonByFunction(FUNCTION_NEW_LEVEL).setVisible(true);
            getMainPanel().getButtonByFunction(FUNCTION_RESTART).setVisible(false);
            getMainPanel().getButtonByFunction(FUNCTION_FIX).setVisible(false);
            getMainPanel().getButtonByFunction(FUNCTION_HELP).setVisible(false);
            getMainPanel().getButtonByFunction(FUNCTION_FINISH_BACK).setVisible(false);
            getMainPanel().getButtonByFunction(FUNCTION_FINISH_RESTART).setVisible(false);
            getMainPanel().getButtonByFunction(FUNCTION_FINISH_NEW_LEVEL).setVisible(false);
            for (String fn : FUNCTION_HINT) {
                getMainPanel().getButtonByFunction(fn).setVisible(false);
            }
            return;
        }
        getMainPanel().getButtonByFunction(FUNCTION_TIWANAKU_BACK).setVisible(true);
        getMainPanel().getButtonByFunction(FUNCTION_RESTART).setVisible(true);
        ApoButton fixButton = getMainPanel().getButtonByFunction(FUNCTION_FIX);
        fixButton.setVisible(true);
        fixButton.setSelect(hintMode);
        getMainPanel().getButtonByFunction(FUNCTION_NEW_LEVEL).setVisible(!hintMode);
        getMainPanel().getButtonByFunction(FUNCTION_HELP).setVisible(!hintMode);
        getMainPanel().getButtonByFunction(FUNCTION_FINISH_BACK).setVisible(false);
        getMainPanel().getButtonByFunction(FUNCTION_FINISH_RESTART).setVisible(false);
        getMainPanel().getButtonByFunction(FUNCTION_FINISH_NEW_LEVEL).setVisible(false);
        for (String fn : FUNCTION_HINT) {
            getMainPanel().getButtonByFunction(fn).setVisible(true);
        }
    }

    public boolean isHintMode() {
        return this.hintMode;
    }

    public boolean isHintBubbleVisible(int value) {
        if (!this.hintMode || this.selectedX < 0 || value > getSelectedRegionSize()) {
            return false;
        }
        return !this.level.hasClueInBiomeOf(this.selectedX, this.selectedY, value);
    }

    public boolean isHintActive(int value) {
        if (this.selectedX < 0) {
            return false;
        }
        return this.level.hasHint(this.selectedX, this.selectedY, value);
    }

    private void resetHintState() {
        this.hintMode = false;
        this.selectedX = -1;
        this.selectedY = -1;
        this.helpHintX = -1;
        this.helpHintY = -1;
    }

    public void setValues(LevelSize levelSize, boolean newLevel, Difficulty difficulty) {
        setValues(levelSize, newLevel, difficulty, 5);
    }

    public void setValues(LevelSize levelSize, boolean newLevel, Difficulty difficulty, int maxRegionSize) {
        this.levelSize = levelSize;
        this.newLevel = newLevel || this.maxRegionSize != maxRegionSize;
        this.maxRegionSize = maxRegionSize;
        this.difficulty = difficulty;
    }

    @Override
    public void init() {
        if (getGameProperties() == null) {
            setGameProperties(new TiwanakuPreferences(this));
            loadProperties();
        }
        if (this.levelCreate == null || this.newLevel) {
            this.createNewLevel();
        }
        if (this.generating) {
            startGenerationRendering();
        }

        this.gameState = GameState.PLAY;
        this.resetTimer();
        this.scoreboard = new Scoreboard();
        this.getMainPanel().resetSize(Constants.GAME_WIDTH, Constants.GAME_HEIGHT);

        this.setNeededButtonsVisible();
    }

    @Override
    public void keyPressed(int keyCode, char character) {
        super.keyPressed(keyCode, character);

        keys[keyCode] = true;
    }

    private void createNewLevel() {
        this.levelCreate = new NewLevelCreate(this.difficulty, this.maxRegionSize);
        this.levelCreate.beginGeneration(this.levelSize.getX(), this.levelSize.getY(), this.levelSize.getFiveCount(), this.levelSize.getFourCount());
        this.level = null;
        this.generating = true;
        startGenerationRendering();
        this.generationFailed = false;
        this.generationStatusRendered = false;
        this.gameState = GameState.PLAY;
        this.resetHintState();
        this.error = null;
        this.resetTimer();

        setNeededButtonsVisible();
    }

    private void startGenerationRendering() {
        if (!this.generationRenderingActive) {
            this.generationRenderingActive = true;
            requestContinuousRendering();
        }
    }

    private void stopGenerationRendering() {
        if (this.generationRenderingActive) {
            this.generationRenderingActive = false;
            releaseContinuousRendering();
        }
    }

    private void startTimerRendering() {
        if (!this.timerRenderingActive) {
            this.timerRenderingActive = true;
            requestContinuousRendering();
        }
    }

    private void stopTimerRendering() {
        if (this.timerRenderingActive) {
            this.timerRenderingActive = false;
            releaseContinuousRendering();
        }
    }

    private void requestContinuousRendering() {
        if (this.continuousRenderingRequests == 0) {
            this.continuousRenderingBeforeRequests = Gdx.graphics.isContinuousRendering();
        }
        this.continuousRenderingRequests += 1;
        Gdx.graphics.setContinuousRendering(true);
    }

    private void releaseContinuousRendering() {
        if (this.continuousRenderingRequests <= 0) {
            return;
        }
        this.continuousRenderingRequests -= 1;
        if (this.continuousRenderingRequests > 0) {
            return;
        }
        Gdx.graphics.setContinuousRendering(this.continuousRenderingBeforeRequests);
        if (!this.continuousRenderingBeforeRequests) {
            Gdx.graphics.requestRendering();
        }
    }

    private void resetTimer() {
        this.timer.reset();
        stopTimerRendering();
    }

    private void startTimer() {
        if (this.timer.hasStarted()) {
            return;
        }
        this.timer.start();
        startTimerRendering();
    }

    private void stopTimer() {
        this.timer.stop();
        stopTimerRendering();
    }

    private void finalizeGeneratedLevel() {
        this.level = this.levelCreate.getLevel();
        this.setSolve();

        this.addXScale = (int)((9 - this.level.getBackground()[0].length) * AssetLoader.backgroundTextureRegion[4].getRegionWidth() / 2f);
        if (this.level.getBackground().length > 5) {
            int tileSize = 640 / this.level.getCurNumber().length;
            float hudStartX = Constants.GAME_WIDTH - 5 - AssetLoader.hudRightTextureRegion.getRegionWidth();
            float availableWidth = hudStartX - 20;
            float gridWidth = tileSize * this.level.getCurNumber()[0].length;
            this.addXScale = (int)((availableWidth - gridWidth) / 2f - this.addX);
        }

        setNeededButtonsVisible();
    }

    private void setSolve() {
        this.solve = new Solve(this.level.getBackground());
        this.solve.setPossibleValues(this.level.getCurNumber());
    }

    @Override
    public void keyButtonReleased(int keyCode, char character) {
        super.keyButtonReleased(keyCode, character);

        if (keyCode == Input.Keys.N) {
            this.createNewLevel();
        }

        keys[keyCode] = false;
    }

    public void mouseMoved(int mouseX, int mouseY) {
    }

    public void mouseButtonReleased(int mouseX, int mouseY, boolean isRightButton) {
        this.isPressed = false;

        if (this.gameState == GameState.SOLVED) {
            return;
        }

        if (this.level == null) {
            return;
        }

        int tileSize = getTileSize();
        int gridX0 = this.addX + this.addXScale;
        int gridY0 = this.addY;
        int gridW = tileSize * this.level.getBackground()[0].length;
        int gridH = tileSize * this.level.getBackground().length;
        boolean inGrid = mouseX >= gridX0 && mouseX < gridX0 + gridW
                      && mouseY >= gridY0 && mouseY < gridY0 + gridH;

        if (this.hintMode) {
            handleHintModeClick(mouseX, mouseY, inGrid, gridX0, gridY0, tileSize);
        } else if (inGrid) {
            int x = (mouseX - gridX0) / tileSize;
            int y = (mouseY - gridY0) / tileSize;
            handleNormalGridClick(x, y, isRightButton);
        }
    }

    private int getTileSize() {
        if (this.level.getCurNumber().length > 5) {
            return 640 / this.level.getCurNumber().length;
        }
        return AssetLoader.backgroundTextureRegion[4].getRegionWidth();
    }

    private void handleHintModeClick(int mouseX, int mouseY, boolean inGrid, int gridX0, int gridY0, int tileSize) {
        if (inGrid) {
            int x = (mouseX - gridX0) / tileSize;
            int y = (mouseY - gridY0) / tileSize;
            if (this.level.getCurNumber()[y][x] > 0 || (x == this.selectedX && y == this.selectedY)) {
                this.selectedX = -1;
                this.selectedY = -1;
            } else {
                this.selectedX = x;
                this.selectedY = y;
            }
            return;
        }
        this.selectedX = -1;
        this.selectedY = -1;
    }

    public int getSelectedRegionSize() {
        if (this.selectedX < 0 || this.selectedY < 0) {
            return 0;
        }
        int regionSize = this.level.getRegion()[this.selectedY][this.selectedX];
        return Math.min(regionSize, HINT_VALUE_COUNT);
    }

    private void layoutHintButtons() {
        boolean expanded = getSelectedRegionSize() > 5;
        int size = expanded ? 52 : HUD_BUBBLE_SIZE;
        float hudStartX = Constants.GAME_WIDTH - 5 - AssetLoader.hudRightTextureRegion.getRegionWidth();
        float groupWidth = expanded ? size * 3 + HUD_BUBBLE_SPACING * 2 : size;
        float startX = hudStartX + (AssetLoader.hudRightTextureRegion.getRegionWidth() - groupWidth) / 2f;
        for (int index = 0; index < FUNCTION_HINT.length; index++) {
            ApoButton button = getMainPanel().getButtonByFunction(FUNCTION_HINT[index]);
            button.setWidth(size);
            button.setHeight(size);
            button.setX(startX + (expanded ? index % 3 * (size + HUD_BUBBLE_SPACING) : 0));
            button.setY(HUD_BUBBLE_START_Y + (expanded ? index / 3 : index) * (size + HUD_BUBBLE_SPACING));
        }
    }

    private void handleNormalGridClick(int x, int y, boolean isRightButton) {
        if (this.level.getStartLevel()[y][x] > 0) {
            this.level.registerWastedClick();
            return;
        }
        this.startTimer();
        this.error = null;
        byte add = (byte) (isRightButton ? -1 : 1);
        this.level.getCurNumber()[y][x] = (byte) (this.level.getCurNumber()[y][x] + add);
        if (this.level.getCurNumber()[y][x] > this.level.getRegion()[y][x]) {
            this.level.getCurNumber()[y][x] = 0;
        }
        if (this.level.getCurNumber()[y][x] < 0) {
            this.level.getCurNumber()[y][x] = this.level.getRegion()[y][x];
        }
        this.level.registerValueClick(x, y);
        this.setSolve();

        if (this.helpHintX == x && this.helpHintY == y && this.level.getCurNumber()[y][x] != 0) {
            this.helpHintX = -1;
            this.helpHintY = -1;
        }

        if (this.level.isSolved()) {
            this.gameSolved();
        } else {
            this.updateError();
        }
    }

    private void gameSolved() {
        this.gameState = GameState.SOLVED;
        this.stopTimer();
        String sizeName = this.levelSize.name();
        long timeMillis = this.timer.getElapsedMillis();
        int accuracy = this.level.getAccuracyPercent();
        int totalClicks = this.level.getTotalClicks();
        int totalMissClicks = this.level.getTotalMissClicks();
        GameResult result = new GameResult(sizeName, timeMillis, accuracy, System.currentTimeMillis(), "", totalClicks, totalMissClicks);
        this.resetHintState();
        getMainPanel().changeModel(new PlayerSelection(this.getMainPanel(), result));
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
            case Tiwanaku.FUNCTION_TIWANAKU_BACK:
            case Tiwanaku.FUNCTION_FINISH_BACK:
                quit();
                break;
            case Tiwanaku.FUNCTION_NEW_LEVEL:
            case Tiwanaku.FUNCTION_FINISH_NEW_LEVEL:
                createNewLevel();
                break;
            case Tiwanaku.FUNCTION_RESTART:
            case Tiwanaku.FUNCTION_FINISH_RESTART:
                restartLevel();
                break;
            case Tiwanaku.FUNCTION_FIX:
                toggleHintMode();
                break;
            case Tiwanaku.FUNCTION_HELP:
                findHelpHint();
                break;
            default:
                ApoButton clicked = getMainPanel().getButtonByFunction(function);
                if (clicked instanceof HintBubble) {
                    toggleHintOnSelected(((HintBubble) clicked).getValue());
                }
                break;
        }
    }

    private void toggleHintOnSelected(int value) {
        if (this.selectedX >= 0 && this.selectedY >= 0) {
            this.level.toggleHint(this.selectedX, this.selectedY, value);
        }
    }

    private void toggleHintMode() {
        this.hintMode = !this.hintMode;
        this.selectedX = -1;
        this.selectedY = -1;
        this.helpHintX = -1;
        this.helpHintY = -1;
        setNeededButtonsVisible();
    }

    private void findHelpHint() {
        byte[][] currentErrors = this.level.getError();
        if (hasErrors(currentErrors)) {
            this.error = currentErrors;
            this.helpHintX = -1;
            this.helpHintY = -1;
            return;
        }
        this.error = null;
        this.setSolve();
        ArrayList<int[]> candidates = new ArrayList<>();
        for (int y = 0; y < this.level.getCurNumber().length; y++) {
            for (int x = 0; x < this.level.getCurNumber()[0].length; x++) {
                if (this.level.getCurNumber()[y][x] == 0
                    && this.level.getBackground()[y][x] > 0
                    && this.solve.getPossibleValues()[y][x].getPossibleValues().size() == 1) {
                    candidates.add(new int[]{x, y});
                }
            }
        }
        if (candidates.isEmpty()) {
            this.helpHintX = -1;
            this.helpHintY = -1;
            return;
        }
        int[] pick = candidates.get((int) (Math.random() * candidates.size()));
        this.helpHintX = pick[0];
        this.helpHintY = pick[1];
    }

    private boolean hasErrors(byte[][] error) {
        if (error == null) {
            return false;
        }
        for (int y = 0; y < error.length; y++) {
            for (int x = 0; x < error[0].length; x++) {
                if (error[y][x] > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private void updateError() {
        if (this.level.isFull()) {
            this.error = this.level.getError();
        } else {
            this.error = null;
        }
    }

    private void restartLevel() {
        this.level.restart();
        this.gameState = GameState.PLAY;
        this.resetHintState();
        this.error = null;
        this.resetTimer();
        this.setNeededButtonsVisible();
    }

    private void setButtonsVisibility() {
        getMainPanel().getButtonByFunction(FUNCTION_TIWANAKU_BACK).setVisible(false);
        getMainPanel().getButtonByFunction(FUNCTION_RESTART).setVisible(false);
        getMainPanel().getButtonByFunction(FUNCTION_FIX).setVisible(false);
        getMainPanel().getButtonByFunction(FUNCTION_NEW_LEVEL).setVisible(false);
        getMainPanel().getButtonByFunction(FUNCTION_HELP).setVisible(false);
        getMainPanel().getButtonByFunction(FUNCTION_FINISH_BACK).setVisible(true);
        getMainPanel().getButtonByFunction(FUNCTION_FINISH_RESTART).setVisible(true);
        getMainPanel().getButtonByFunction(FUNCTION_FINISH_NEW_LEVEL).setVisible(true);
    }

    public void mouseWheelChanged(int changed) {
    }

    @Override
    protected void quit() {
        getMainPanel().changeToMenu();
    }

    @Override
    public void doThink(float delta) {
        if (!this.generating || !this.generationStatusRendered) {
            return;
        }
        this.generationStatusRendered = false;
        boolean done = this.levelCreate.stepGeneration(GENERATION_ATTEMPTS_PER_TICK);
        if (!done) {
            return;
        }
        this.generating = false;
        stopGenerationRendering();
        if (this.levelCreate.isGenerationSuccessful()) {
            Gdx.app.log("Tiwanaku", "Level generated (" + this.levelSize.getX() + "x" + this.levelSize.getY() + ") after " + this.levelCreate.getAttemptCount() + " attempt(s)");
            finalizeGeneratedLevel();
        } else {
            this.generationFailed = true;
            Gdx.app.log("Tiwanaku", "Level generation FAILED for " + this.levelSize.getX() + "x" + this.levelSize.getY() + " after " + this.levelCreate.getAttemptCount() + " attempt(s)");
        }
    }

    @Override
    public void render() {
        this.timer.update(Gdx.graphics.getDeltaTime());
        layoutHintButtons();
        getMainPanel().getRenderer().begin(ShapeRenderer.ShapeType.Filled);

        getMainPanel().getRenderer().setColor(Constants.COLOR_PURPLE_MENU[0], Constants.COLOR_PURPLE_MENU[1], Constants.COLOR_PURPLE_MENU[2], 1f);

        getMainPanel().getRenderer().end();

        getMainPanel().spriteBatch.begin();

        float hudStartX = Constants.GAME_WIDTH - 5 - AssetLoader.hudRightTextureRegion.getRegionWidth();
        getMainPanel().spriteBatch.draw(AssetLoader.hudRightTextureRegion, hudStartX, 5);

        renderHudContent(hudStartX);

        getMainPanel().spriteBatch.draw(AssetLoader.titleTextureRegion, (Constants.GAME_WIDTH - AssetLoader.hudRightTextureRegion.getRegionWidth() - AssetLoader.titleTextureRegion.getRegionWidth())/2f, 5);
        getMainPanel().drawString(Constants.PROPERTY_NAME, (Constants.GAME_WIDTH - AssetLoader.hudRightTextureRegion.getRegionWidth())/2f, 44, Constants.COLOR_WHITE, AssetLoader.font30, DrawString.MIDDLE, true, false);

        getMainPanel().spriteBatch.end();

        if (this.level == null) {
            renderGenerationStatus(hudStartX);
            for (ApoButton button : this.getMainPanel().getButtons()) {
                button.render(this.getMainPanel());
            }
            return;
        }

        int tileSizeWidth = AssetLoader.backgroundTextureRegion[4].getRegionWidth();
        int tileSizeHeight = AssetLoader.backgroundTextureRegion[4].getRegionHeight();
        float scale = 1.0f;
        if (this.level.getCurNumber().length > 5) {
            tileSizeWidth = 640 / this.level.getCurNumber().length;
            tileSizeHeight = (int)(tileSizeWidth * 1.5f);
            scale = (float)tileSizeWidth / AssetLoader.backgroundTextureRegion[4].getRegionWidth();
        }

        float ringRadius = AssetLoader.circlesTextureRegion[5].getRegionWidth() * scale / 2f;
        float clueHalo = Constants.IS_MOBILE ? Math.max(2f, 3f * scale) : 0;
        int rows = this.level.getCurBackground().length;
        int cols = this.level.getCurBackground()[0].length;
        BitmapFont numberFont = scale < 1.0f ? AssetLoader.font30 : AssetLoader.font40;

        for (int y = 0; y < rows; y++) {
            getMainPanel().spriteBatch.begin();
            for (int x = 0; x < cols; x++) {
                int biomeIndex = this.level.getBackground()[y][x] > 0 ? this.level.getBackground()[y][x] - 1 : 4;
                getMainPanel().spriteBatch.draw(AssetLoader.backgroundTextureRegion[biomeIndex], x * tileSizeWidth + addX + addXScale, addY + y * tileSizeWidth - (tileSizeHeight - tileSizeWidth), tileSizeWidth, tileSizeHeight);
            }
            getMainPanel().spriteBatch.end();

            float outlineThickness = Math.max(4f, 7f * scale);
            getMainPanel().getRenderer().begin(ShapeRenderer.ShapeType.Filled);
            getMainPanel().getRenderer().setColor(Constants.COLOR_RED[0], Constants.COLOR_RED[1], Constants.COLOR_RED[2], Constants.COLOR_RED[3]);
            for (int x = 0; x < cols; x++) {
                if (this.level.getStartLevel()[y][x] > 0 && this.level.getCurNumber()[y][x] > 0) {
                    float ringCx = x * tileSizeWidth + addX + addXScale + tileSizeWidth / 2f;
                    float ringCy = addY + y * tileSizeWidth + tileSizeWidth / 2f;
                    getMainPanel().getRenderer().circle(ringCx, ringCy, ringRadius + clueHalo);
                }
            }
            if (this.helpHintY == y && this.helpHintX >= 0 && this.level.getCurNumber()[y][this.helpHintX] == 0) {
                getMainPanel().getRenderer().setColor(Constants.COLOR_GREEN_BRIGHT[0], Constants.COLOR_GREEN_BRIGHT[1], Constants.COLOR_GREEN_BRIGHT[2], 1f);
                getMainPanel().getRenderer().drawThickRectOutline(this.helpHintX * tileSizeWidth + addX + addXScale, addY + y * tileSizeWidth, tileSizeWidth, tileSizeWidth, outlineThickness);
            }
            if (this.hintMode && this.selectedY == y && this.selectedX >= 0) {
                getMainPanel().getRenderer().setColor(Constants.COLOR_YELLOW[0], Constants.COLOR_YELLOW[1], Constants.COLOR_YELLOW[2], 1f);
                getMainPanel().getRenderer().drawThickRectOutline(this.selectedX * tileSizeWidth + addX + addXScale, addY + y * tileSizeWidth, tileSizeWidth, tileSizeWidth, outlineThickness);
            }
            getMainPanel().getRenderer().end();

            getMainPanel().spriteBatch.begin();
            for (int x = 0; x < cols; x++) {
                if (this.level.getCurNumber()[y][x] > 0) {
                    TextureRegion numberTexture = AssetLoader.numberTextureRegion[this.level.getCurNumber()[y][x] - 1];
                    getMainPanel().spriteBatch.draw(numberTexture, x * tileSizeWidth + addX + addXScale + tileSizeWidth/2f - numberTexture.getRegionWidth()*scale/2f, addY + y * tileSizeWidth + tileSizeWidth/2f - numberTexture.getRegionHeight()*scale/2f, numberTexture.getRegionWidth()*scale, numberTexture.getRegionHeight()*scale);

                    if (this.level.getStartLevel()[y][x] > 0 && !Constants.IS_MOBILE) {
                        getMainPanel().spriteBatch.draw(AssetLoader.circlesTextureRegion[5], x * tileSizeWidth + addX + addXScale + tileSizeWidth/2f - AssetLoader.circlesTextureRegion[5].getRegionWidth()*scale/2f, addY + y * tileSizeWidth + tileSizeWidth/2f - AssetLoader.circlesTextureRegion[5].getRegionHeight()*scale/2f, AssetLoader.circlesTextureRegion[5].getRegionWidth()*scale, AssetLoader.circlesTextureRegion[5].getRegionHeight()*scale);
                    }

                    float[] color = Constants.COLOR_BLACK;
                    if (this.error != null && this.error[y][x] > 0) {
                        color = Constants.COLOR_RED;
                    }
                    getMainPanel().drawString(String.valueOf(this.level.getCurNumber()[y][x]), x * tileSizeWidth + addX + addXScale + tileSizeWidth/2f, addY + y * tileSizeWidth + tileSizeWidth/2f + 4, color, numberFont, DrawString.MIDDLE, true, false);
                } else if (this.level.getBackground()[y][x] > 0) {
                    for (int value = 1; value <= this.level.getRegion()[y][x]; value++) {
                        if (!this.level.hasHint(x, y, value)) {
                            continue;
                        }
                        TextureRegion hintTexture = AssetLoader.numberTextureRegion[value - 1];
                        if (this.level.getRegion()[y][x] > 5) {
                            float spacing = tileSizeWidth / 3f;
                            float hintSize = spacing - 2;
                            float hintX = x * tileSizeWidth + addX + addXScale + (value - 1) % 3 * spacing + 1;
                            float hintY = addY + y * tileSizeWidth + (value - 1) / 3 * spacing + 1;
                            getMainPanel().spriteBatch.draw(hintTexture, hintX, hintY, hintSize, hintSize);
                            getMainPanel().drawString(String.valueOf(value), hintX + hintSize / 2f, hintY + hintSize / 2f + 2, Constants.COLOR_BLACK, AssetLoader.font15, DrawString.MIDDLE, true, false);
                            continue;
                        }
                        int addValueX = (value - 1) * tileSizeWidth/4 + 10;
                        if (value > 3) {
                            addValueX = (value - 4) * tileSizeWidth/4 + 10;
                        }
                        int addValueY = -15;
                        if (value > 3) {
                            addValueY += tileSizeWidth/4;
                        }
                        getMainPanel().spriteBatch.draw(hintTexture, x * tileSizeWidth + addX + addXScale + addValueX, addY + addValueY + y * tileSizeWidth + tileSizeWidth/2f - hintTexture.getRegionHeight()/2f, hintTexture.getRegionWidth()/2f, hintTexture.getRegionHeight()/2f);
                    }
                }
            }
            getMainPanel().spriteBatch.end();
        }

        if (this.gameState == GameState.SOLVED) {
            getMainPanel().spriteBatch.begin();
            float wonX = (Constants.GAME_WIDTH - AssetLoader.hudRightTextureRegion.getRegionWidth())/2f;
            float wonY = Constants.GAME_HEIGHT/2f - AssetLoader.wonTextureRegion.getRegionHeight()/2f;
            getMainPanel().spriteBatch.draw(AssetLoader.wonTextureRegion, wonX - AssetLoader.wonTextureRegion.getRegionWidth()/2f, wonY);
            getMainPanel().drawString(Localization.getInstance().getCommon().get("won"), wonX, wonY + WON_TITLE_OFFSET, Constants.COLOR_BLACK, AssetLoader.font30, DrawString.MIDDLE, false, false);

            getMainPanel().drawString(Localization.getInstance().getCommon().get("won_text"), wonX, wonY + WON_SUBTITLE_OFFSET, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.MIDDLE, false, false);

            getMainPanel().drawString(Localization.getInstance().getCommon().format("won_time", this.timer.getFormattedTime()), wonX, wonY + WON_TIME_OFFSET, Constants.COLOR_WHITE, AssetLoader.font30, DrawString.MIDDLE, false, false);

            getMainPanel().drawString(Localization.getInstance().getCommon().format("won_stats",
                    String.valueOf(this.level.getTotalClicks()),
                    String.valueOf(this.level.getTotalMissClicks()),
                    String.valueOf(this.level.getAccuracyPercent())),
                    wonX, wonY + WON_STATS_OFFSET, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.MIDDLE, false, false);

            getMainPanel().drawString(Localization.getInstance().getCommon().get("won"), hudStartX + AssetLoader.hudRightTextureRegion.getRegionWidth()/2f, 70, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.MIDDLE, false, false);
            getMainPanel().spriteBatch.end();
        }

        for (ApoButton button : this.getMainPanel().getButtons()) {
            button.render(this.getMainPanel());
        }
    }

//	        Gdx.graphics.getGL20().glEnable(GL20.GL_BLEND);
//			Gdx.graphics.getGL20().glDisable(GL20.GL_BLEND);
//
//			getMainPanel().getRenderer().begin(ShapeType.Line);
//			getMainPanel().getRenderer().setColor(Constants.COLOR_WHITE[0], Constants.COLOR_WHITE[1], Constants.COLOR_WHITE[2], 1f);
//			getMainPanel().getRenderer().roundedRectLine((WIDTH - width)/2f, startY, width, height, 5);
//			getMainPanel().getRenderer().end();


    private void renderGenerationStatus(float hudStartX) {
        getMainPanel().spriteBatch.begin();
        float centerX = (Constants.GAME_WIDTH - AssetLoader.hudRightTextureRegion.getRegionWidth()) / 2f;
        float centerY = Constants.GAME_HEIGHT / 2f;
        if (this.generationFailed) {
            getMainPanel().drawString("Unable to create this puzzle.", centerX, centerY, Constants.COLOR_BLACK, AssetLoader.font25, DrawString.MIDDLE, false, false);
            getMainPanel().drawString("Press New Level to try again.", centerX, centerY + 30, Constants.COLOR_BLACK, AssetLoader.font20, DrawString.MIDDLE, false, false);
        } else {
            getMainPanel().drawString("Creating your puzzle (" + this.levelSize.getX() + "x" + this.levelSize.getY() + ")...", centerX, centerY, Constants.COLOR_BLACK, AssetLoader.font25, DrawString.MIDDLE, false, false);
            getMainPanel().drawString(this.levelCreate.getGenerationStatus(), centerX, centerY + 30, Constants.COLOR_BLACK, AssetLoader.font20, DrawString.MIDDLE, false, false);
            getMainPanel().drawString("Clue checks can take longer on larger boards. Please wait.", centerX, centerY + 60, Constants.COLOR_BLACK, AssetLoader.font20, DrawString.MIDDLE, false, false);
        }
        getMainPanel().spriteBatch.end();
        this.generationStatusRendered = true;
    }

    private void renderHudContent(float hudStartX) {
        if (!this.hintMode) {
            int startY = 128;
            getMainPanel().drawString("Level:", hudStartX + 30, startY, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.BEGIN, false, false);
            getMainPanel().drawString(this.levelSize.getX()+"x"+this.levelSize.getY(), hudStartX + 60, startY + 30, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.BEGIN, false, false);

            getMainPanel().drawString(Localization.getInstance().getCommon().get("menu_difficulty")+":", hudStartX + 30, startY + 64, Constants.COLOR_WHITE, AssetLoader.font15, DrawString.BEGIN, false, false);
            getMainPanel().drawString(this.difficulty.toString(), hudStartX + 60, startY + 94, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.BEGIN, false, false);

            int blockHeaderY = startY + 180;
            int blockStartY = startY + 208;
            int contentBottomY;
            if (hasErrors(this.error)) {
                getMainPanel().drawString(Localization.getInstance().getCommon().get("hud_help")+":", hudStartX + 30, blockHeaderY, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.BEGIN, false, false);
                contentBottomY = blockStartY + drawWrappedHudText(hudStartX, "hud_help_errors", blockStartY) * HUD_TEXT_LINE_HEIGHT;
            } else if (this.helpHintX >= 0) {
                getMainPanel().drawString(Localization.getInstance().getCommon().get("hud_help")+":", hudStartX + 30, blockHeaderY, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.BEGIN, false, false);
                contentBottomY = blockStartY + drawWrappedHudText(hudStartX, "hud_help_explanation", blockStartY) * HUD_TEXT_LINE_HEIGHT;
            } else {
                getMainPanel().drawString(Localization.getInstance().getCommon().get("hud_rules")+":", hudStartX + 30, blockHeaderY, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.BEGIN, false, false);
                int lines1 = drawWrappedHudText(hudStartX, "hud_rule_1", blockStartY);
                int rule2Y = blockStartY + (lines1 + 1) * HUD_TEXT_LINE_HEIGHT;
                int lines2 = drawWrappedHudText(hudStartX, "hud_rule_2", rule2Y);
                contentBottomY = rule2Y + lines2 * HUD_TEXT_LINE_HEIGHT;
            }
            // The rule texts wrap differently per language, so the click hint is
            // pushed down when needed instead of relying on a fixed offset.
            int clickHintY = Math.max(HUD_CONTENT_MIN_Y, contentBottomY + HUD_TEXT_LINE_HEIGHT);
            drawWrappedHudText(hudStartX, "hud_click_hint", Math.min(clickHintY, HUD_CONTENT_MAX_Y));
        } else {
            getMainPanel().drawString(
                    Localization.getInstance().getCommon().get("hud_hint_title"),
                    hudStartX + HUD_TEXT_LEFT, HUD_TITLE_Y,
                    Constants.COLOR_WHITE, AssetLoader.font25, DrawString.BEGIN, false, false);
            if (this.selectedX < 0) {
                drawWrappedHudText(hudStartX, "hud_hint_instruction", HUD_INSTRUCTION_Y);
                drawWrappedHudText(hudStartX, "hud_hint_exit", HUD_EXIT_Y);
            }
        }
        renderTimerContent(hudStartX);
    }

    private void renderTimerContent(float hudStartX) {
        getMainPanel().drawString(Localization.getInstance().getCommon().get("hud_time")+":", hudStartX + HUD_TEXT_LEFT,
                HUD_TIME_LABEL_Y, Constants.COLOR_WHITE, AssetLoader.font15, DrawString.BEGIN, false, false);
        getMainPanel().drawString(this.timer.getFormattedTime(), hudStartX + HUD_TEXT_LEFT,
                HUD_TIME_VALUE_Y, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.BEGIN, false, false);
    }

    private int drawWrappedHudText(float hudStartX, String key, int startY) {
        String text = Localization.getInstance().getCommon().get(key);
        BitmapFont font = AssetLoader.font15;
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int lineIdx = 0;
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            String candidate = line.length() == 0 ? word : line + " " + word;
            Constants.glyphLayout.setText(font, candidate);
            if (Constants.glyphLayout.width > HUD_TEXT_WIDTH && line.length() > 0) {
                getMainPanel().drawString(line.toString(), hudStartX + HUD_TEXT_LEFT, startY + lineIdx * HUD_TEXT_LINE_HEIGHT,
                        Constants.COLOR_WHITE, font, DrawString.BEGIN, false, false);
                line.setLength(0);
                line.append(word);
                lineIdx++;
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        if (line.length() > 0) {
            getMainPanel().drawString(line.toString(), hudStartX + HUD_TEXT_LEFT, startY + lineIdx * HUD_TEXT_LINE_HEIGHT,
                    Constants.COLOR_WHITE, font, DrawString.BEGIN, false, false);
            lineIdx++;
        }
        return lineIdx;
    }

    public void drawOverlay() {
    }

    @Override
    public void dispose() {
        stopGenerationRendering();
        stopTimerRendering();
    }
}
