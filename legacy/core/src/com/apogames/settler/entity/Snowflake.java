package com.apogames.settler.entity;

import com.apogames.settler.Constants;
import com.apogames.settler.backend.GameScreen;

public class Snowflake extends ApoEntity {

	private final int maxWidth;
	private final int maxHeight;
	
	public Snowflake(final int maxWidth, final int maxHeight) {
		super(0, 0, 2, 2);
		
		this.maxHeight = maxHeight;
		this.maxWidth = maxWidth;
		
		resetSnowflake(true);
	}
	
	public void resetSnowflake(boolean bNewHeight) {
		this.setX((float)(Math.random() * maxWidth));
		this.setY((float)(Math.random() * maxHeight));
		if (!bNewHeight) {
			this.setY(-2);
		}
		setNewSpeed();
	}
	
	public void setNewSpeed() {
		this.setVecX((float)(Math.random() * 0.01f) - 0.005f);
		this.setVecY((float)(Math.random() * 0.1f) + 0.05f);
	}
	
	public void think(int delta) {
		this.setX(getX() + getVecX());
		this.setY(getY() + getVecY());
	}
	
	public void render(GameScreen screen, int x, int y) {
        if (this.isVisible()) {
            screen.getRenderer().setColor(Constants.COLOR_WHITE[0], Constants.COLOR_WHITE[1], Constants.COLOR_WHITE[2], 1f);
            screen.getRenderer().rect(this.getX() + x, this.getY() + y, this.getWidth() - 1, this.getHeight() - 1);
        }
    }
	
}
