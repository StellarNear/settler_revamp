package com.apogames.settler.game.menu;

import com.apogames.settler.Constants;
import com.apogames.settler.asset.AssetLoader;
import com.apogames.settler.backend.DrawString;
import com.apogames.settler.backend.SequentiallyThinkingScreenModel;
import com.apogames.settler.common.Localization;
import com.apogames.settler.entity.ApoButton;
import com.apogames.settler.entity.ApoButtonImageWithThree;
import com.apogames.settler.game.MainPanel;
import com.apogames.settler.game.tiwanaku.LevelSize;
import com.apogames.settler.level.helper.Difficulty;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.Locale;

public class Menu extends SequentiallyThinkingScreenModel {

    public static final String FUNCTION_BACK = "MENU_QUIT";

    public static final String FUNCTION_PLAY = "MENU_PLAY";
    public static final String FUNCTION_ALGORITHM = "MENU_ALGORITHM";
    public static final String FUNCTION_LEADERBOARD = "MENU_LEADERBOARD";
    public static final String FUNCTION_LEVEL_LEFT = "MENU_LEVEL_LEFT";
    public static final String FUNCTION_LEVEL_RIGHT = "MENU_LEVEL_RIGHT";
    public static final String FUNCTION_DIFFICULTY_LEFT = "MENU_DIFFICULTY_LEFT";
    public static final String FUNCTION_DIFFICULTY_RIGHT = "MENU_DIFFICULTY_RIGHT";
    public static final String FUNCTION_REGION_LEFT = "MENU_REGION_LEFT";
    public static final String FUNCTION_REGION_RIGHT = "MENU_REGION_RIGHT";

    public static final String FUNCTION_LANGUAGE = "MENU_LANGUAGE";

    private final boolean[] keys = new boolean[256];

    private boolean isPressed = false;

    private boolean german = true;

    private Difficulty difficulty = Difficulty.EASY;
    private Difficulty oldDifficulty = difficulty;

    private LevelSize levelSize = LevelSize.SMALL;
    private LevelSize oldLevelSize = LevelSize.SMALL;
    private int maxRegionSize = 5;
    private int oldMaxRegionSize = 5;

    public Menu(final MainPanel game) {
        super(game);
    }

    public void setNeededButtonsVisible() {
        getMainPanel().getButtonByFunction(FUNCTION_BACK).setVisible(true);
        getMainPanel().getButtonByFunction(FUNCTION_PLAY).setVisible(true);
        getMainPanel().getButtonByFunction(FUNCTION_LANGUAGE).setVisible(true);
        getMainPanel().getButtonByFunction(FUNCTION_LEVEL_LEFT).setVisible(true);
        getMainPanel().getButtonByFunction(FUNCTION_LEVEL_RIGHT).setVisible(true);
        getMainPanel().getButtonByFunction(FUNCTION_DIFFICULTY_LEFT).setVisible(true);
        getMainPanel().getButtonByFunction(FUNCTION_DIFFICULTY_RIGHT).setVisible(true);
        getMainPanel().getButtonByFunction(FUNCTION_REGION_LEFT).setVisible(true);
        getMainPanel().getButtonByFunction(FUNCTION_REGION_RIGHT).setVisible(true);
        getMainPanel().getButtonByFunction(FUNCTION_ALGORITHM).setVisible(true);
        getMainPanel().getButtonByFunction(FUNCTION_LEADERBOARD).setVisible(true);
    }

    @Override
    public void init() {
        if (getGameProperties() == null) {
            setGameProperties(new MenuPreferences(this));
            loadProperties();
        }

        this.getMainPanel().resetSize(Constants.GAME_WIDTH, Constants.GAME_HEIGHT);

        this.german = Localization.getInstance().getLocale().getLanguage().equals("de");

        this.oldLevelSize = this.levelSize;
        this.oldDifficulty = this.difficulty;
        this.oldMaxRegionSize = this.maxRegionSize;

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
            case Menu.FUNCTION_BACK:
                quit();
                break;
            case Menu.FUNCTION_PLAY:
                getMainPanel().changeToGame(this.levelSize, this.oldLevelSize != this.levelSize
                    || !this.difficulty.equals(this.oldDifficulty) || this.oldMaxRegionSize != this.maxRegionSize,
                    this.difficulty, this.maxRegionSize);
                break;
            case Menu.FUNCTION_LEVEL_LEFT:
                this.levelSize = this.levelSize.next(-1);
                break;
            case Menu.FUNCTION_LEVEL_RIGHT:
                this.levelSize = this.levelSize.next(1);
                break;
            case Menu.FUNCTION_DIFFICULTY_LEFT:
                this.difficulty = this.difficulty.next(-1);
                break;
            case Menu.FUNCTION_DIFFICULTY_RIGHT:
                this.difficulty = this.difficulty.next(1);
                break;
            case Menu.FUNCTION_ALGORITHM:
                getMainPanel().changeToAlgorithm();
                break;
            case Menu.FUNCTION_LEADERBOARD:
                getMainPanel().changeToLeaderboard();
                break;
            case Menu.FUNCTION_REGION_LEFT:
                this.maxRegionSize = this.maxRegionSize == 5 ? 9 : this.maxRegionSize - 1;
                break;
            case Menu.FUNCTION_REGION_RIGHT:
                this.maxRegionSize = this.maxRegionSize == 9 ? 5 : this.maxRegionSize + 1;
                break;
            case Menu.FUNCTION_LANGUAGE:
                this.german = !this.german;

                if (this.german) {
                    Localization.getInstance().setLocale(Locale.GERMAN);
                } else {
                    Localization.getInstance().setLocale(Locale.ENGLISH);
                }

                changeIDForLanguage(function);
                break;
        }
    }

    private void setButtonsVisibility() {
        changeIDForLanguage(FUNCTION_LANGUAGE);

        if (Constants.IS_HTML) {
            getMainPanel().getButtonByFunction(Menu.FUNCTION_BACK).setVisible(false);
        }
    }

    private void changeIDForLanguage(String functionLanguage) {
        if (this.german) {
            getMainPanel().getButtonByFunction(functionLanguage).setId("button_language_de");
        } else {
            getMainPanel().getButtonByFunction(functionLanguage).setId("button_language_en");
        }
    }

    public void mouseWheelChanged(int changed) {
    }

    @Override
    protected void quit() {
        getMainPanel().quitGame();
    }

    @Override
    public void doThink(float delta) {

    }

    @Override
    public void render() {
        int startY = (int)(Constants.GAME_HEIGHT/2f + 50);

        getMainPanel().getRenderer().begin(ShapeRenderer.ShapeType.Filled);

        getMainPanel().getRenderer().setColor(Constants.COLOR_PURPLE_MENU[0], Constants.COLOR_PURPLE_MENU[1], Constants.COLOR_PURPLE_MENU[2], 1f);

        getMainPanel().getRenderer().end();

        getMainPanel().spriteBatch.begin();

        getMainPanel().spriteBatch.draw(AssetLoader.hudMenuTextureRegion, Constants.GAME_WIDTH/2f - AssetLoader.hudMenuTextureRegion.getRegionWidth()/2f, 5);

        getMainPanel().drawString(Localization.getInstance().getCommon().get("title"), Constants.GAME_WIDTH/2f, 90, Constants.COLOR_WHITE, AssetLoader.font40, DrawString.MIDDLE, true, false);
        getMainPanel().drawString(Localization.getInstance().getCommon().get("title_description"), Constants.GAME_WIDTH/2f, 135, Constants.COLOR_WHITE, AssetLoader.font25, DrawString.MIDDLE, true, false);

        ApoButton buttonLeft = getMainPanel().getButtonByFunction(FUNCTION_LEVEL_LEFT);
        getMainPanel().drawString(Localization.getInstance().getCommon().get("menu_levelsize"), Constants.GAME_WIDTH/2f, buttonLeft.getY() - 20, Constants.COLOR_WHITE, AssetLoader.font25, DrawString.MIDDLE, true, false);
        getMainPanel().drawString(this.levelSize.toString(), Constants.GAME_WIDTH/2f, buttonLeft.getY() + buttonLeft.getHeight()/2 + 3, Constants.COLOR_WHITE, AssetLoader.font30, DrawString.MIDDLE, true, false);
        String size = this.levelSize.getX() + " x " + this.levelSize.getY();
        getMainPanel().drawString(size, Constants.GAME_WIDTH/2f, buttonLeft.getY() + buttonLeft.getHeight()/2 + 43, Constants.COLOR_WHITE, AssetLoader.font20, DrawString.MIDDLE, true, false);

        ApoButton buttonDifficulty = getMainPanel().getButtonByFunction(FUNCTION_DIFFICULTY_LEFT);
        getMainPanel().drawString(Localization.getInstance().getCommon().get("menu_difficulty"), Constants.GAME_WIDTH/2f, buttonDifficulty.getY() - 20, Constants.COLOR_WHITE, AssetLoader.font25, DrawString.MIDDLE, true, false);
        getMainPanel().drawString(this.difficulty.toString(), Constants.GAME_WIDTH/2f, buttonDifficulty.getY() + buttonDifficulty.getHeight()/2 + 3, Constants.COLOR_WHITE, AssetLoader.font30, DrawString.MIDDLE, true, false);

        ApoButton buttonRegion = getMainPanel().getButtonByFunction(FUNCTION_REGION_LEFT);
        getMainPanel().drawString(Localization.getInstance().getCommon().get("menu_max_region"), Constants.GAME_WIDTH/2f, buttonRegion.getY() - 20, Constants.COLOR_WHITE, AssetLoader.font25, DrawString.MIDDLE, true, false);
        String maximum = this.maxRegionSize == 5 ? Localization.getInstance().getCommon().get("menu_default_region") : String.valueOf(this.maxRegionSize);
        getMainPanel().drawString(maximum, Constants.GAME_WIDTH/2f, buttonRegion.getY() + buttonRegion.getHeight()/2 + 3, Constants.COLOR_WHITE, AssetLoader.font25, DrawString.MIDDLE, true, false);

        getMainPanel().drawString("Version: "+Constants.VERSION, Constants.GAME_WIDTH/2f, Constants.GAME_HEIGHT - 20, Constants.COLOR_BLACK, AssetLoader.font15, DrawString.MIDDLE, false, false);

        getMainPanel().spriteBatch.end();

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


    public void drawOverlay() {
    }

    @Override
    public void dispose() {
    }
}
