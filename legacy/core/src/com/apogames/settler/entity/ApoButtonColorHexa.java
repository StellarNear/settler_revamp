package com.apogames.settler.entity;

import com.apogames.settler.backend.GameScreen;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Polygon;

/**
 * The type Apo button color hexa.
 */
public class ApoButtonColorHexa extends ApoButtonColor {

	private Polygon polygon;

	private float lineWidth;

    /**
     * Instantiates a new Apo button color hexa.
     *
     * @param x           the x
     * @param y           the y
     * @param width       the width
     * @param height      the height
     * @param function    the function
     * @param color       the color
     * @param colorBorder the color border
     */
    public ApoButtonColorHexa(int x, int y, int width, int height, String function, float[] color, float[] colorBorder) {
		this(x, y, width, height, function, "", color, colorBorder, 3f);
	}

    /**
     * Instantiates a new Apo button color hexa.
     *
     * @param x           the x
     * @param y           the y
     * @param width       the width
     * @param height      the height
     * @param function    the function
     * @param text        the text
     * @param color       the color
     * @param colorBorder the color border
     * @param lineWidth   the line width
     */
    public ApoButtonColorHexa(int x, int y, int width, int height, String function, String text, float[] color, float[] colorBorder, float lineWidth) {
		super(x, y, width, height, function, text, color, colorBorder);

		this.lineWidth = lineWidth;

		this.polygon = new Polygon();
		polygon.setVertices(new float[] {
				x, y + height/4f,
				x + width/2f, y,
				x + width, y + height/4f,
				x + width, y + height*3/4f,
				x + width/2f, y + height,
				x, y + height*3/4f
		});
	}

	public boolean intersects(float x, float y) {
		boolean intersect = super.intersects(x, y);
		if (intersect) {
			return polygon.contains(x, y);
		}
		return false;
	}

	/**
	 * malt den Button an die Stelle getX() + changeX und getY() + changeY hin
	 * @param changeX: Verschiebung in x-Richtung
	 * @param changeY: Verschiebung in y-Richtung
	 */
	public void render(GameScreen screen, int changeX, int changeY ) {
		if ( this.isVisible() ) {
			if (!this.isOnlyText()) {
				screen.getRenderer().begin(ShapeType.Filled);
				screen.getRenderer().setColor(getColor()[0], getColor()[1], getColor()[2], getColor()[3]);

				screen.getRenderer().rect(getX(), getY() + getHeight()/4f, getWidth(), getHeight()/2);
				screen.getRenderer().triangle(getX(), getY() + getHeight()/4f, getX() + getWidth(), getY() + getHeight()/4f, getX() + 0.5f * getWidth(), getY());
				screen.getRenderer().triangle(getX(), getY() + getHeight()/4f + getHeight()/2, getX() + getWidth(), getY() + getHeight()/4f + getHeight()/2, getX() + 0.5f * getWidth(), getY() + getHeight()/4f + getHeight()/2 + 0.25f * getHeight());

				screen.getRenderer().setColor(getColorBorder()[0], getColorBorder()[1], getColorBorder()[2], 1f);
				if (( this.isBPressed() ) || (isSelect())) {
					screen.getRenderer().setColor(255f/ 255.0f, 0f / 255.0f, 0f / 255.0f, 1f);
				} else if ( this.isBOver() ) {
					screen.getRenderer().setColor(255f/ 255.0f, 255f / 255.0f, 0f / 255.0f, 1f);
				}
				for (int index = 0; index < polygon.getVertices().length; index += 2) {
					if (index < polygon.getVertices().length - 2) {
						screen.getRenderer().rectLine(polygon.getVertices()[index], polygon.getVertices()[index + 1], polygon.getVertices()[index + 2], polygon.getVertices()[index + 3], this.lineWidth);
					} else {
						screen.getRenderer().rectLine(polygon.getVertices()[index], polygon.getVertices()[index + 1], polygon.getVertices()[0], polygon.getVertices()[1], this.lineWidth);
					}
				}

				screen.getRenderer().end();
				if (getImage() != null) {
					screen.spriteBatch.begin();
					screen.spriteBatch.enableBlending();
					renderImage(screen, changeX, changeY);
					screen.spriteBatch.end();
				} else {
					drawString(screen, changeX, changeY - 3, getColorBorder());
				}
			}
		}
	}
}
