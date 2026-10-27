package com.apogames.settler.entity;

import com.apogames.settler.Constants;
import com.apogames.settler.backend.DrawString;
import com.apogames.settler.backend.GameScreen;
import com.apogames.settler.common.Localization;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;

/**
 * The type Apo button color.
 */
public class ApoButtonColor extends ApoButton {

	private float[] color;
	private float[] colorBorder;
	private float[] colorBorderSolved;

    /**
     * Instantiates a new Apo button color.
     *
     * @param x           the x
     * @param y           the y
     * @param width       the width
     * @param height      the height
     * @param function    the function
     * @param color       the color
     * @param colorBorder the color border
     */
    public ApoButtonColor(int x, int y, int width, int height, String function, float[] color, float[] colorBorder) {
		this(x, y, width, height, function, "", color, colorBorder);
	}

    /**
     * Instantiates a new Apo button color.
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
    public ApoButtonColor(int x, int y, int width, int height, String function, String text, float[] color, float[] colorBorder) {
		super(x, y, width, height, function, text);
		this.color = color;
		this.colorBorder = colorBorder;
		this.colorBorderSolved = new float[] {colorBorder[0]+0.1f, colorBorder[1]+0.1f, colorBorder[2]+0.1f, 1f};
	}

	public ApoButtonColor(int x, int y, int width, int height, String function, String text, float[] color, float[] colorBorder, String id) {
		this(x, y, width, height, function, text, color, colorBorder);

		this.setId(id);
	}

	/**
     * Get color border solved float [ ].
     *
     * @return the float [ ]
     */
    public float[] getColorBorderSolved() {
		return colorBorderSolved;
	}

    /**
     * Sets color border solved.
     *
     * @param colorBorderSolved the color border solved
     */
    public void setColorBorderSolved(float[] colorBorderSolved) {
		this.colorBorderSolved = colorBorderSolved;
	}

    /**
     * Get color float [ ].
     *
     * @return the float [ ]
     */
    public float[] getColor() {
		return color;
	}

    /**
     * Get color border float [ ].
     *
     * @return the float [ ]
     */
    public float[] getColorBorder() {
		return colorBorder;
	}

	/**
	 * malt den Button an die Stelle getX() + changeX und getY() + changeY hin
	 * @param changeX: Verschiebung in x-Richtung
	 * @param changeY: Verschiebung in y-Richtung
	 */
	public void render(GameScreen screen, int changeX, int changeY ) {
		if ( this.isVisible() ) {
			if (!this.isOnlyText()) {
				int rem = 0;
				if (getStroke() > 1) {
					rem = getStroke()/2;
				}
				if (color[3] < 1f) {
					Gdx.graphics.getGL20().glEnable(GL20.GL_BLEND);
				}
				screen.getRenderer().begin(ShapeType.Filled);
				screen.getRenderer().setColor(color[0], color[1], color[2], color[3]);
				screen.getRenderer().roundedRect(this.getX() + rem + changeX, this.getY() + rem + changeY, getWidth(), getHeight(), 3);
				if (this.getMouseOver() != null && this.isBOver()) {
					Constants.glyphLayout.setText(getFont(), getMouseOver());
					screen.getRenderer().roundedRect(this.getX() + getWidth()/2 - Constants.glyphLayout.width/2 - 5 + rem + changeX, this.getY() + rem + changeY + getHeight() + 3, Constants.glyphLayout.width + 10, getHeight(), 3);
				}
				screen.getRenderer().end();
				if (color[3] < 1f) {
					Gdx.graphics.getGL20().glDisable(GL20.GL_BLEND);
				}
				
				Gdx.gl20.glLineWidth(getStroke());
				screen.getRenderer().begin(ShapeType.Line);
				screen.getRenderer().setColor(colorBorder[0], colorBorder[1], colorBorder[2], 1f);
				if (( this.isBPressed() ) || (isSelect())) {
					screen.getRenderer().setColor(1.0f, 0f / 255.0f, 0f / 255.0f, 1f);
				} else if ( this.isBOver() ) {
					screen.getRenderer().setColor(1.0f, 1.0f, 0f / 255.0f, 1f);
				}
				screen.getRenderer().roundedRectLine(this.getX() + rem + changeX, this.getY() + rem + changeY, getWidth(), getHeight(), 3);
				screen.getRenderer().end();
				Gdx.gl20.glLineWidth(1f);
				
				if (this.isSolved()) {
					if (getSolvedImage() != null) {
						screen.spriteBatch.begin();
						screen.spriteBatch.enableBlending();
						renderSolvedImage(screen, changeX, changeY);
						screen.spriteBatch.end();
						changeY -= getHeight()/6;
					}
				}
				
				if (getImage() != null) {
					screen.spriteBatch.begin();
					screen.spriteBatch.enableBlending();
					renderImage(screen, changeX, changeY);
					screen.spriteBatch.end();
				} else {
					if (isSolved()) {
						drawString(screen, changeX, changeY - 9, colorBorderSolved);
					} else {
						drawString(screen, changeX, changeY - 2, colorBorder);
					}
				}
			}
		}
	}

    /**
     * Render solved image.
     *
     * @param screen  the screen
     * @param changeX the change x
     * @param changeY the change y
     */
    public void renderSolvedImage(GameScreen screen, int changeX, int changeY) {
		float width = getWidth()/3;
		float height = getHeight()/3;
		screen.spriteBatch.draw(this.getSolvedImage(), this.getX() + getWidth()/2 - width/2 + changeX, this.getY() + getHeight() - height - 2 + changeY, width, height);
	}

	public void drawString(GameScreen screen, int changeX, int changeY, float[] color) {
		this.drawString(screen, changeX, changeY, color, DrawString.MIDDLE);
	}

	public void drawString(GameScreen screen, int changeX, int changeY, float[] color, DrawString drawPosition) {
		String text = getText();
		if (this.getId() != null && this.getId().length() > 0) {
			text = Localization.getInstance().getCommon().get(this.getId());
		}
		screen.spriteBatch.begin();
		Constants.glyphLayout.setText(getFont(), text);
		float h = Constants.glyphLayout.height;
		if (( this.isBPressed() )) {
			screen.drawString(text, this.getX() + changeX + this.getWidth()/2, this.getY() + changeY + this.getHeight()/2 - h/2, Constants.COLOR_RED, getFont(), drawPosition, false, false);
		} else if (this.isBOver()) {
			screen.drawString(text, this.getX() + changeX + this.getWidth()/2, this.getY() + changeY + this.getHeight()/2 - h/2, Constants.COLOR_YELLOW, getFont(), drawPosition, false, false);

			if (this.getMouseOver() != null) {
				screen.drawString(this.getMouseOver(), this.getX() + changeX + this.getWidth()/2, this.getY() + changeY + this.getHeight() + 10, Constants.COLOR_YELLOW, getFont(), drawPosition, false, false);
			}
		} else {
			screen.drawString(text, this.getX() + changeX + this.getWidth()/2, this.getY() + changeY + this.getHeight()/2 - h/2, color, getFont(), drawPosition, false, false);
		}
		screen.spriteBatch.end();
	}
}
