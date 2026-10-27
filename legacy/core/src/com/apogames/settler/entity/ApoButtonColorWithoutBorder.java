package com.apogames.settler.entity;

import com.apogames.settler.Constants;
import com.apogames.settler.backend.GameScreen;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;

/**
 * The type Apo button color without border.
 */
public class ApoButtonColorWithoutBorder extends ApoButtonColor {

    /**
     * Instantiates a new Apo button color without border.
     *
     * @param x           the x
     * @param y           the y
     * @param width       the width
     * @param height      the height
     * @param function    the function
     * @param text        the text
     * @param color       the color
     * @param colorBorder the color border
     */
    public ApoButtonColorWithoutBorder(int x, int y, int width, int height, String function, String text, float[] color, float[] colorBorder) {
		super(x, y, width, height, function, text, color, colorBorder);
	}

	/**
	 * malt den Button an die Stelle getX() + changeX und getY() + changeY hin
	 * @param changeX: Verschiebung in x-Richtung
	 * @param changeY: Verschiebung in y-Richtung
	 */
	public void render(GameScreen screen, int changeX, int changeY ) {
		if ( this.isVisible() ) {
			if (!this.isOnlyText()) {
				if (getColor()[3] > 0f) {
					int rem = 0;
					if (getStroke() > 1) {
						rem = getStroke() / 2;
					}
					Gdx.graphics.getGL20().glEnable(GL20.GL_BLEND);
					screen.getRenderer().begin(ShapeType.Filled);
					screen.getRenderer().setColor(getColor()[0], getColor()[1], getColor()[2], getColor()[3]);
					screen.getRenderer().roundedRect(this.getX() + rem + changeX, this.getY() + rem + changeY, getWidth(), getHeight(), 3);
					screen.getRenderer().end();
					Gdx.graphics.getGL20().glDisable(GL20.GL_BLEND);
				}

				if (( this.isBPressed() ) || (isSelect())) {
					drawString(screen, changeX + 1, changeY + 1, Constants.COLOR_YELLOW);
				} else if ( this.isBOver() ) {
					drawString(screen, changeX + 1, changeY + 1, Constants.COLOR_RED);
				}
				drawString(screen, changeX, changeY, getColorBorder());
			}
		}
	}
}
