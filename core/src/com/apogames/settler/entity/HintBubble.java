package com.apogames.settler.entity;

import com.apogames.settler.Constants;
import com.apogames.settler.asset.AssetLoader;
import com.apogames.settler.backend.DrawString;
import com.apogames.settler.backend.GameScreen;
import com.apogames.settler.game.MainPanel;
import com.apogames.settler.game.tiwanaku.Tiwanaku;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public class HintBubble extends ApoButton {

    private static final float INACTIVE_ALPHA = 0.35f;
    private static final float OUTLINE_THICKNESS = 5f;

    private final int value;
    private final MainPanel panel;

    public HintBubble(int x, int y, int size, int value, String function, MainPanel panel) {
        super(x, y, size, size, function, "");
        this.value = value;
        this.panel = panel;
    }

    public int getValue() {
        return value;
    }

    @Override
    public boolean isVisible() {
        if (!super.isVisible()) {
            return false;
        }
        Tiwanaku game = panel.getGame();
        return game != null && game.isHintBubbleVisible(this.value);
    }

    @Override
    public void render(GameScreen screen, int changeX, int changeY) {
        if (!isVisible()) {
            return;
        }
        Tiwanaku game = panel.getGame();
        boolean active = game.isHintActive(this.value);
        float alpha = active ? 1f : INACTIVE_ALPHA;

        screen.spriteBatch.begin();
        screen.spriteBatch.setColor(1f, 1f, 1f, alpha);
        screen.spriteBatch.draw(AssetLoader.numberTextureRegion[this.value - 1],
                this.getX() + changeX, this.getY() + changeY, this.getWidth(), this.getHeight());
        screen.spriteBatch.setColor(1f, 1f, 1f, 1f);
        screen.drawString(String.valueOf(this.value),
                this.getX() + changeX + this.getWidth() / 2f,
                this.getY() + changeY + this.getHeight() / 2f + 4,
                Constants.COLOR_BLACK, this.getWidth() < 64 ? AssetLoader.font30 : AssetLoader.font40, DrawString.MIDDLE, true, false);
        screen.spriteBatch.end();

        if (this.isBOver()) {
            screen.getRenderer().begin(ShapeRenderer.ShapeType.Filled);
            screen.getRenderer().setColor(Constants.COLOR_YELLOW[0], Constants.COLOR_YELLOW[1], Constants.COLOR_YELLOW[2], 1f);
            screen.getRenderer().drawThickCircleOutline(
                    this.getX() + changeX + this.getWidth() / 2f,
                    this.getY() + changeY + this.getHeight() / 2f,
                    this.getWidth() / 2f,
                    OUTLINE_THICKNESS);
            screen.getRenderer().end();
        }
    }
}
