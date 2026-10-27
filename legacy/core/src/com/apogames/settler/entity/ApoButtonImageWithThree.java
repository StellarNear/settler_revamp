package com.apogames.settler.entity;

import com.apogames.settler.Constants;
import com.apogames.settler.asset.AssetLoader;
import com.apogames.settler.backend.DrawString;
import com.apogames.settler.backend.GameScreen;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * The type Apo button image.
 */
public class ApoButtonImageWithThree extends ApoButton {

	private TextureRegion[] images;

	private int rotate = 0;
	private TextureRegion mouseOverTextureRegion;
	private String mouseOverText;

	private boolean mouseOverTextBottom = false;

	public ApoButtonImageWithThree(int x, int y, int width, int height, String function, String text, TextureRegion[] images) {
		super(x, y, width, height, function, text);

		this.images = images;
		this.mouseOverText = "";
	}

	public int getRotate() {
		return rotate;
	}

	public void setRotate(int rotate) {
		this.rotate = rotate;
	}

	public TextureRegion[] getImages() {
		return images;
	}

	public void setMouseOverText(TextureRegion mouseOverTextureRegion, String mouseOverText) {
		this.setMouseOverText(mouseOverTextureRegion, mouseOverText, false);
	}

	public void setMouseOverText(TextureRegion mouseOverTextureRegion, String mouseOverText, boolean mouseOverTextBottom) {
		this.mouseOverTextureRegion = mouseOverTextureRegion;
		this.mouseOverText = mouseOverText;
		this.mouseOverTextBottom = mouseOverTextBottom;
	}

	public void render(GameScreen screen, int changeX, int changeY ) {
		render(screen, changeX, changeY, true);
	}

	public void render(GameScreen screen, int changeX, int changeY, boolean needNewSpriteBatch) {
		if (this.isVisible()) {
			if (needNewSpriteBatch) {
				screen.spriteBatch.begin();
				//screen.spriteBatch.enableBlending();
			}
			renderImage(screen, changeX, changeY);
			if (this.getText() != null && !this.getText().isEmpty()) {
				float[] color = Constants.COLOR_BLACK;
				if (this.isSelect()) {
					color = Constants.COLOR_RED_DARK;
				}
				drawString(screen, changeX, changeY, color);
			}
			if (!this.mouseOverText.isEmpty() && this.isBOver()) {
				screen.getGlyphLayout().setText(this.getFont(), this.mouseOverText);
				int width = (int)(screen.getGlyphLayout().width);
				int height = 30;
				int x = (int)(this.getXMiddle() + changeX - width/2 - 10);
				int y = (int)(this.getY() + changeY - 3 - height);
				if (x < 5) {
					x = 5;
				} else if (x + width + 20 > Constants.GAME_WIDTH) {
					x = Constants.GAME_WIDTH - width - 20;
				}
				if (y < 10 || this.mouseOverTextBottom) {
					y = (int)(this.getY() + changeY + 3 + this.getHeight());
				}
				screen.spriteBatch.draw(this.mouseOverTextureRegion, x, y, width + 20, height);
//				screen.drawString(this.mouseOverText, x + 11 + width/2f, y + 1, Constants.COLOR_WHITE, this.getFont(), DrawString.MIDDLE, false, false);
				screen.drawString(this.mouseOverText, x + 10 + width/2f, (int)(this.getY() + changeY - height/2 - 3), Constants.COLOR_BLACK, this.getFont(), DrawString.MIDDLE, true, false);
			}
			if (needNewSpriteBatch) {
				screen.spriteBatch.end();
			}
		}
	}

	public void drawString(GameScreen screen, int changeX, int changeY, float[] color) {
		Constants.glyphLayout.setText(getFont(), getText());
		float h = Constants.glyphLayout.height;
		if (( this.isBPressed() )) {
			screen.drawString(getText(), this.getX() + changeX + this.getWidth()/2, this.getY() + changeY + this.getHeight()/2 - h/2, Constants.COLOR_RED, getFont(), DrawString.MIDDLE, false, false);
		} else if ( this.isBOver() ) {
			screen.drawString(getText(), this.getX() + changeX + this.getWidth()/2, this.getY() + changeY + this.getHeight()/2 - h/2, Constants.COLOR_YELLOW, getFont(), DrawString.MIDDLE, false, false);
		} else {
			screen.drawString(getText(), this.getX() + changeX + this.getWidth()/2, this.getY() + changeY + this.getHeight()/2 - h/2, color, getFont(), DrawString.MIDDLE, false, false);
		}
	}


	public void renderOutline(GameScreen screen, int changeX, int changeY) {
	}

	public void renderImage(GameScreen screen, int changeX, int changeY) {
		if (this.rotate == 0) {
			if (this.isBPressed() || this.isSelect()) {
				screen.spriteBatch.draw(this.images[2], this.getX() + changeX, this.getY() + changeY, getWidth(), getHeight());
			} else if (this.isBOver()) {
				screen.spriteBatch.draw(this.images[1], this.getX() + changeX, this.getY() + changeY, getWidth(), getHeight());
			} else {
				screen.spriteBatch.draw(this.images[0], this.getX() + changeX, this.getY() + changeY, getWidth(), getHeight());
			}
		} else {
			if (this.isBPressed() || this.isSelect()) {
				screen.spriteBatch.draw(this.images[2], this.getX() + changeX, this.getY() + changeY, getWidth()/2, getHeight()/2, getWidth(), getHeight(), 1f, 1f, this.rotate);
			} else if (this.isBOver()) {
				screen.spriteBatch.draw(this.images[1], this.getX() + changeX, this.getY() + changeY, getWidth()/2, getHeight()/2, getWidth(), getHeight(), 1f, 1f, this.rotate);
			} else {
				screen.spriteBatch.draw(this.images[0], this.getX() + changeX, this.getY() + changeY, getWidth()/2, getHeight()/2, getWidth(), getHeight(), 1f, 1f, this.rotate);
			}
		}
	}
}
