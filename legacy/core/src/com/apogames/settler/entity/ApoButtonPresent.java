package com.apogames.settler.entity;

import com.apogames.settler.Constants;
import com.apogames.settler.backend.GameScreen;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;

public class ApoButtonPresent extends ApoButton {

	private final float[] COLOR_GOLD = new float[] {182f/255f, 155f/255f, 84f/255f, 1f};
	private float[] color;
	
	public ApoButtonPresent(int x, int y, int width, int height, String function, String text, float[] color) {
		super(x, y, width, height, function, text);
		
		this.color = color;
	}

	public void render(GameScreen screen, int changeX, int changeY, boolean bShowTextOnly ) {
		if (this.isVisible()) {
			float rem = 0;
			if (getStroke() > 1) {
				rem = getStroke()/2f;
			}
			
			screen.getRenderer().begin(ShapeType.Filled);
			screen.getRenderer().setColor(color[0], color[1], color[2], 1f);
			screen.getRenderer().roundedRect(this.getX() + rem + changeX, this.getY() + rem + changeY, (this.getWidth() - 1 - rem*2), (this.getHeight() - 1 - rem*2), getRounded());
			
			float height = getHeight() * 0.2f;
			screen.getRenderer().setColor(COLOR_GOLD[0], COLOR_GOLD[1], COLOR_GOLD[2], 1f);
			screen.getRenderer().rect(this.getX() + rem + changeX, this.getY() + getHeight()/2 - height/2 + rem + changeY, (this.getWidth() - 1 - rem*2), (height - 1 - rem*2));
			screen.getRenderer().rect(this.getX() + getWidth()/2 - height/2 + rem + changeX, this.getY() + rem + changeY, (height - 1 - rem*2), (getHeight() - 1 - rem*2));
			screen.getRenderer().end();
			
			screen.getRenderer().begin(ShapeType.Line);
			screen.getRenderer().setColor(Constants.COLOR_WHITE[0], Constants.COLOR_WHITE[1], Constants.COLOR_WHITE[2], 1f);
			if (this.isBPressed()) {
				screen.getRenderer().setColor(255f/ 255.0f, 0f / 255.0f, 0f / 255.0f, 1f);
			} else if (this.isBOver()) {
				screen.getRenderer().setColor(255f/ 255.0f, 255f / 255.0f, 0f / 255.0f, 1f);
			}
			if (getStroke() > 1) {
				Gdx.gl20.glLineWidth(getStroke());
			}
			screen.getRenderer().roundedRectLine(this.getX() + rem + changeX, this.getY() + rem + changeY, (this.getWidth() - 1 - rem*2), (this.getHeight() - 1 - rem*2), getRounded());
			screen.getRenderer().end();
			Gdx.gl20.glLineWidth(1f);
			
			drawString(screen, changeX + 1, changeY + 1 - 10, Constants.COLOR_BLACK);
			drawString(screen, changeX, changeY - 10, Constants.COLOR_WHITE);
		}
	}
}
