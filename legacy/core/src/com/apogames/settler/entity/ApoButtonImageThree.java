package com.apogames.settler.entity;

import com.apogames.settler.Constants;
import com.apogames.settler.asset.AssetLoader;
import com.apogames.settler.backend.DrawString;
import com.apogames.settler.backend.GameScreen;
import com.apogames.settler.common.Localization;

/**
 * The type Apo button image three.
 */
public class ApoButtonImageThree extends ApoButton {

	private int assetX = 0;
	private int assetY = 0;

	private int picWidth = 0;
	private int picHeight = 0;
	private float[] color;

    /**
     * Instantiates a new Apo button image three.
     *
     * @param x         the x
     * @param y         the y
     * @param width     the width
     * @param height    the height
     * @param function  the function
     * @param text      the text
     */
	public ApoButtonImageThree(int x, int y, int width, int height, String function, String text, float[] color) {
		this(x, y, width, height, function, text, 0, 0, AssetLoader.buttonBlancoTextureRegion[0].getRegionWidth(), AssetLoader.buttonBlancoTextureRegion[0].getRegionHeight(), color, null);
	}

	public ApoButtonImageThree(int x, int y, int width, int height, String function, String text, int assetX, int assetY, int picWidth, int picHeight, float[] color, String id) {
		super(x, y, width, height, function, text);

		this.assetX = assetX;
		this.assetY = assetY;

		this.picWidth = picWidth;
		this.picHeight = picHeight;

		this.color = color;

		this.setId(id);
	}

	public float[] getColor() {
		return color;
	}

	public void setColor(float[] color) {
		this.color = color;
	}

	/**
     * Gets asset x.
     *
     * @return the asset x
     */
    public int getAssetX() {
		return assetX;
	}

    /**
     * Sets asset x.
     *
     * @param assetX the asset x
     */
    public void setAssetX(int assetX) {
		this.assetX = assetX;
	}

    /**
     * Gets asset y.
     *
     * @return the asset y
     */
    public int getAssetY() {
		return assetY;
	}

    /**
     * Sets asset y.
     *
     * @param assetY the asset y
     */
    public void setAssetY(int assetY) {
		this.assetY = assetY;
	}

	public void render(GameScreen screen, int changeX, int changeY, boolean bShowTextOnly ) {
		if ( this.isVisible() ) {
			screen.spriteBatch.begin();
			renderImage(screen, changeX, changeY);
			if (this.getText() != null && this.getText().length() > 0) {
				drawString(screen,changeX, changeY, color);
			}
			screen.spriteBatch.end();
		}
	}

	public void drawString(GameScreen screen, int changeX, int changeY, float[] color) {
		String text = getText();
		if (this.getId() != null && this.getId().length() > 0) {
			text = Localization.getInstance().getCommon().get(this.getId());
		}
		Constants.glyphLayout.setText(getFont(), text);
		float h = Constants.glyphLayout.height;
		if (( this.isBPressed() )) {
			screen.drawString(text, this.getX() + changeX + this.getWidth()/2, this.getY() + changeY + this.getHeight()/2 - h/2, Constants.COLOR_RED, getFont(), DrawString.MIDDLE, false, false);
		} else if ( this.isBOver() ) {
			screen.drawString(text, this.getX() + changeX + this.getWidth()/2, this.getY() + changeY + this.getHeight()/2 - h/2, Constants.COLOR_YELLOW, getFont(), DrawString.MIDDLE, false, false);
		} else {
			screen.drawString(text, this.getX() + changeX + this.getWidth()/2, this.getY() + changeY + this.getHeight()/2 - h/2, color, getFont(), DrawString.MIDDLE, false, false);
		}
	}

	public void renderImage(GameScreen screen, int changeX, int changeY) {
		int currentX = assetX;
		if (this.isBOver()) {
			currentX = assetX + 1;
		}
		if (this.isBPressed()) {
			currentX = assetX + 2;
		}
		float x = this.getX() + changeX;
		if (picWidth != getWidth()) {
			x = getXMiddle() - picWidth/2f + changeX;
		}
		float y = this.getY() + changeY;
		if (picHeight != getHeight()) {
			y = getY() + getHeight()/2 - picHeight/2f + changeY;
		}
		screen.spriteBatch.draw(AssetLoader.buttonBlancoTextureRegion[currentX], x, y, picWidth, picHeight);
	}
}
