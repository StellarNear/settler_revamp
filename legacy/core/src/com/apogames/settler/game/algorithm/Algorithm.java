package com.apogames.settler.game.algorithm;

import com.apogames.settler.Constants;
import com.apogames.settler.asset.AssetLoader;
import com.apogames.settler.backend.DrawString;
import com.apogames.settler.backend.SequentiallyThinkingScreenModel;
import com.apogames.settler.common.Localization;
import com.apogames.settler.entity.ApoButton;
import com.apogames.settler.game.MainPanel;
import com.apogames.settler.level.Level;
import com.apogames.settler.level.LevelCreate;
import com.apogames.settler.level.algorithmX.AlgorithmX;
import com.apogames.settler.level.algorithmX.AlgorithmXSolve;
import com.apogames.settler.level.algorithmX.ColumnNode;
import com.apogames.settler.level.algorithmX.Node;
import com.apogames.settler.level.helper.Difficulty;
import com.apogames.settler.level.helper.Helper;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public class Algorithm extends SequentiallyThinkingScreenModel {

    private final int addX = 10;
    private final int addXAlgo = 250;
    private final int addY = 100;
    private final int addYAlgo = 100;
    private int addXScale = 0;

    public static final String FUNCTION_BACK = "ALGORITHM_QUIT";

    public static final String FUNCTION_STEP = "ALGORITHM_STEP";

    public static final String FUNCTION_RESTART = "ALGORITHM_RESTART";
    public static final String FUNCTION_NEW_LEVEL = "ALGORITHM_NEW_LEVEL";

    private final boolean[] keys = new boolean[256];

    private boolean isPressed = false;

    private Level level;

    private AlgorithmXSolve algoXSolve;

    private int step = 0;

    private byte[][] currentSolution;
    public Algorithm(final MainPanel game) {
        super(game);
    }

    public void setNeededButtonsVisible() {
        getMainPanel().getButtonByFunction(FUNCTION_BACK).setVisible(true);
        getMainPanel().getButtonByFunction(FUNCTION_STEP).setVisible(true);
        getMainPanel().getButtonByFunction(FUNCTION_RESTART).setVisible(true);
        getMainPanel().getButtonByFunction(FUNCTION_NEW_LEVEL).setVisible(true);
    }

    @Override
    public void init() {
        if (getGameProperties() == null) {
            setGameProperties(new AlgorithmPreferences(this));
            loadProperties();
        }

        this.newLevel();

        this.getMainPanel().resetSize(Constants.GAME_WIDTH, Constants.GAME_HEIGHT);

        this.setNeededButtonsVisible();
        this.setButtonsVisibility();
    }

    private void newLevel() {
        int xSize = 4;
        int ySize = 3;
        LevelCreate levelCreate = new LevelCreate(Difficulty.HARD);
        levelCreate.createLevel(xSize, ySize, 1, 2);
        this.level = levelCreate.getLevel();

        this.restartLevel();
    }

    private void restartLevel() {
        AlgorithmX algoX = new AlgorithmX();
        algoX.createMatrix(Helper.cloneArray(this.level.getBackground()), Helper.cloneArray(this.level.getStartLevel()), Helper.cloneArray(this.level.getRegion()));
        this.algoXSolve = new AlgorithmXSolve();
        this.algoXSolve.setUpEverything(this.level.getBackground()[0].length, this.level.getBackground().length, Helper.cloneArray(algoX.getMatrix()));

        this.currentSolution = this.algoXSolve.getCurrentSolution();

        this.step = 0;

        //Helper.printArray(this.algoXSolve.getMatrix());
    }

    @Override
    public void keyPressed(int keyCode, char character) {
        super.keyPressed(keyCode, character);

        setKeyState(keyCode, true);
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
            case Algorithm.FUNCTION_BACK:
                quit();
                break;
            case Algorithm.FUNCTION_STEP:
                makeStep();
                break;
            case Algorithm.FUNCTION_RESTART:
                restartLevel();
                break;
            case Algorithm.FUNCTION_NEW_LEVEL:
                this.newLevel();
                break;
        }
    }

    private void makeStep() {
        int oldStep = this.step;
        this.step = this.algoXSolve.searchIterativ(this.step);
        while (oldStep >= this.step && this.step > 0) {
            oldStep = this.step;
            this.step = this.algoXSolve.uncoverAndSearch(this.step-1);
            if (oldStep == this.step) {
                break;
            }
        }
        this.currentSolution = this.algoXSolve.getCurrentSolution();
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

        int tileSize = 6;
        if (this.algoXSolve.getxSize() * this.algoXSolve.getySize() > 10) {
            tileSize = 4;
        }
        int add = 2;
        int addXAlgo = this.addXAlgo;
        for (int y = 0; y < this.algoXSolve.getMatrix().length; y++) {
            for (int x = 0; x < this.algoXSolve.getMatrix()[0].length; x++) {
                if (this.algoXSolve.getMatrix()[y][x] > 0) {
                    getMainPanel().getRenderer().rect(addXAlgo + x * (tileSize + add), addYAlgo + y * (tileSize + add), tileSize, tileSize);
                }
                if (y == 0) {
                    getMainPanel().getRenderer().rect(addXAlgo + x * (tileSize + add), addYAlgo + (y - 1) * (tileSize + add), tileSize, tileSize);
                }
            }
        }

        int sum = this.algoXSolve.getxSize() * this.algoXSolve.getySize();
        getMainPanel().getRenderer().rect(addXAlgo - 0.5f + (sum) * (tileSize + add), addYAlgo - 30, 0.5f, (tileSize + 2) * this.algoXSolve.getMatrix().length + 30);
        getMainPanel().getRenderer().rect(addXAlgo - 0.5f + 2 * (sum) * (tileSize + add), addYAlgo - 30, 0.5f, (tileSize + 2) * this.algoXSolve.getMatrix().length + 30);

        getMainPanel().getRenderer().setColor(Constants.COLOR_GREY_BRIGHT[0], Constants.COLOR_GREY_BRIGHT[1], Constants.COLOR_GREY_BRIGHT[2], 1f);
        int startX = 0;
        int startY = 0;
        Node start = this.algoXSolve.getRoot().getRight();
        while (!start.equals(this.algoXSolve.getRoot())) {
            ColumnNode columnNode = (ColumnNode)start;
            while (columnNode.getX() != startX) {
                getMainPanel().getRenderer().rect(addXAlgo + startX * (tileSize + add), addYAlgo + (startY - 1) * (tileSize + add), tileSize, tileSize);

                for (int y = 0; y < this.algoXSolve.getMatrix().length; y++) {
                    if (this.algoXSolve.getMatrix()[y][startX] > 0) {
                        getMainPanel().getRenderer().rect(addXAlgo + startX * (tileSize + add), addYAlgo + y * (tileSize + add), tileSize, tileSize);
                    }
                }
                startX += 1;
            }

            Node nextDown = start.getDown();
            startY = nextDown.getY();
            for (int y = 0; y < this.algoXSolve.getMatrix().length; y++) {
                if (this.algoXSolve.getMatrix()[y][startX] > 0 && startY != y) {
                    getMainPanel().getRenderer().rect(addXAlgo + startX * (tileSize + add), addYAlgo + y * (tileSize + add), tileSize, tileSize);
                }
                if (y == startY) {
                    nextDown = nextDown.getDown();
                    startY = nextDown.getY();
                }
            }

            start = start.getRight();
            startX += 1;
            startY = 0;
        }

        getMainPanel().getRenderer().setColor(Constants.COLOR_GREEN[0], Constants.COLOR_GREEN[1], Constants.COLOR_GREEN[2], 1f);
        for (Node node : this.algoXSolve.getSolution()) {
            for (int x = 0; x < this.algoXSolve.getMatrix()[0].length; x++) {
                if (this.algoXSolve.getMatrix()[node.getY()][x] > 0) {
                    getMainPanel().getRenderer().rect(addXAlgo + x * (tileSize + add), addYAlgo + node.getY() * (tileSize + add), tileSize, tileSize);
                }
            }
        }

        if (this.algoXSolve.getSolution().size() > 0) {
            Node node = this.algoXSolve.getSolution().get(this.algoXSolve.getSolution().size() - 1);
            getMainPanel().getRenderer().rect(addXAlgo + node.getX() * (tileSize + add), addYAlgo - 20, tileSize, tileSize);
        }

        getMainPanel().getRenderer().setColor(Constants.COLOR_PURPLE[0], Constants.COLOR_PURPLE[1], Constants.COLOR_PURPLE[2], 1f);
        for (int y = 5; y < this.algoXSolve.getMatrix().length; y += 5) {
            getMainPanel().getRenderer().rect(addXAlgo, addYAlgo + y * (tileSize + add) - 0.9f, (tileSize + 2) * this.algoXSolve.getMatrix()[0].length, 0.7f);
        }

        getMainPanel().getRenderer().end();

        getMainPanel().spriteBatch.begin();

        int addSolution = 250;
        float smaller = 0.4f;
        int tileSizeWidth = (int)(AssetLoader.backgroundTextureRegion[4].getRegionWidth() * smaller);
        int tileSizeHeight = (int)(AssetLoader.backgroundTextureRegion[4].getRegionHeight() * smaller);
        for (int y = 0; y < this.level.getCurBackground().length; y++) {
            for (int x = 0; x < this.level.getCurBackground()[0].length; x++) {
                //if (this.level.getCurBackground()[y][x] > 0) {
                if (this.level.getBackground()[y][x] > 0) {
                    getMainPanel().spriteBatch.draw(AssetLoader.backgroundTextureRegion[this.level.getBackground()[y][x] - 1], x * tileSizeWidth + addX + addXScale, addY + y * tileSizeWidth - (tileSizeHeight - tileSizeWidth), tileSizeWidth, tileSizeHeight);

                    getMainPanel().spriteBatch.draw(AssetLoader.backgroundTextureRegion[this.level.getBackground()[y][x] - 1], x * tileSizeWidth + addX + addXScale, addSolution + addY + y * tileSizeWidth - (tileSizeHeight - tileSizeWidth), tileSizeWidth, tileSizeHeight);
                } else {
                    getMainPanel().spriteBatch.draw(AssetLoader.backgroundTextureRegion[4], x * tileSizeWidth + addX + addXScale, addY + y * tileSizeWidth - (tileSizeHeight - tileSizeWidth), tileSizeWidth, tileSizeHeight);
                }
                if (this.level.getCurNumber()[y][x] > 0) {
                    getMainPanel().spriteBatch.draw(AssetLoader.circlesTextureRegion[this.level.getCurNumber()[y][x] - 1], x * tileSizeWidth + addX + addXScale + tileSizeWidth / 2f - AssetLoader.circlesTextureRegion[this.level.getCurNumber()[y][x] - 1].getRegionWidth() * smaller / 2f, addY + y * tileSizeWidth + tileSizeWidth / 2f - AssetLoader.circlesTextureRegion[this.level.getCurNumber()[y][x] - 1].getRegionHeight() * smaller / 2f, AssetLoader.circlesTextureRegion[5].getRegionWidth() * smaller, AssetLoader.circlesTextureRegion[5].getRegionHeight() * smaller);
                    //getMainPanel().drawString(String.valueOf(this.level.getCurNumber()[y][x]), x * tileSizeWidth + addX + tileSizeWidth/2f, addY + y * tileSizeWidth + tileSizeWidth/2f, Constants.COLOR_WHITE, AssetLoader.font40, DrawString.MIDDLE, true, false);
                    getMainPanel().drawString(String.valueOf(this.level.getCurNumber()[y][x]), x * tileSizeWidth + addX + addXScale + tileSizeWidth / 2f, addY + y * tileSizeWidth + tileSizeWidth / 2f + 4, Constants.COLOR_BLACK, AssetLoader.font20, DrawString.MIDDLE, true, false);
                }

                if (this.currentSolution[y][x] > 0) {
                    getMainPanel().spriteBatch.draw(AssetLoader.circlesTextureRegion[this.currentSolution[y][x] - 1], x * tileSizeWidth + addX + addXScale + tileSizeWidth / 2f - AssetLoader.circlesTextureRegion[this.currentSolution[y][x] - 1].getRegionWidth() * smaller / 2f, addSolution + addY + y * tileSizeWidth + tileSizeWidth / 2f - AssetLoader.circlesTextureRegion[this.currentSolution[y][x] - 1].getRegionHeight() * smaller / 2f, AssetLoader.circlesTextureRegion[5].getRegionWidth() * smaller, AssetLoader.circlesTextureRegion[5].getRegionHeight() * smaller);

                    getMainPanel().drawString(String.valueOf(this.currentSolution[y][x]), x * tileSizeWidth + addX + addXScale + tileSizeWidth / 2f, addSolution + addY + y * tileSizeWidth + tileSizeWidth / 2f + 4, Constants.COLOR_BLACK, AssetLoader.font20, DrawString.MIDDLE, true, false);
                }
            }
        }

        getMainPanel().drawString(Localization.getInstance().getCommon().get("button_algorithm"), Constants.GAME_WIDTH/2f, 40, Constants.COLOR_PURPLE, AssetLoader.font40, DrawString.MIDDLE, true, false);

        getMainPanel().spriteBatch.end();

        drawFixedNumberRings(tileSizeWidth, smaller);

        for (ApoButton button : this.getMainPanel().getButtons()) {
            button.render(this.getMainPanel());
        }
    }

    private void drawFixedNumberRings(int tileSizeWidth, float scale) {
        float ringRadius = AssetLoader.circlesTextureRegion[5].getRegionWidth() * scale / 2f;
        float ringThickness = Math.max(4f, 7f * scale);

        getMainPanel().getRenderer().begin(ShapeRenderer.ShapeType.Filled);
        getMainPanel().getRenderer().setColor(Constants.COLOR_RED[0], Constants.COLOR_RED[1], Constants.COLOR_RED[2], Constants.COLOR_RED[3]);
        for (int y = 0; y < this.level.getCurNumber().length; y++) {
            for (int x = 0; x < this.level.getCurNumber()[0].length; x++) {
                if (this.level.getStartLevel()[y][x] > 0 && this.level.getCurNumber()[y][x] > 0) {
                    float ringCx = x * tileSizeWidth + addX + addXScale + tileSizeWidth / 2f;
                    float ringCy = addY + y * tileSizeWidth + tileSizeWidth / 2f;
                    getMainPanel().getRenderer().drawThickCircleOutline(ringCx, ringCy, ringRadius, ringThickness);
                }
            }
        }
        getMainPanel().getRenderer().end();
    }

//	        Gdx.graphics.getGL20().glEnable(GL20.GL_BLEND);
//			Gdx.graphics.getGL20().glDisable(GL20.GL_BLEND);
//
//			getMainPanel().getRenderer().begin(ShapeType.Line);
//			getMainPanel().getRenderer().setColor(Constants.COLOR_WHITE[0], Constants.COLOR_WHITE[1], Constants.COLOR_WHITE[2], 1f);
//			getMainPanel().getRenderer().roundedRectLine((WIDTH - width)/2f, startY, width, height, 5);
//			getMainPanel().getRenderer().end();


    public void drawOverlay() {
    }

    @Override
    public void dispose() {
    }
}
