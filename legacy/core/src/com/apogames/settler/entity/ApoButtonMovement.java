package com.apogames.settler.entity;

import com.apogames.settler.backend.GameScreen;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;

public class ApoButtonMovement extends ApoButtonColor {
	
	public static enum MOVEMENT {LEFT, RIGHT, UP};
	
	private MOVEMENT movement;
	
	public ApoButtonMovement(int x, int y, int width, int height, String function, float[] color, float[] colorBorder) {
		super(x, y, width, height, function, "", color, colorBorder);
		
		this.movement = MOVEMENT.UP;
	}

	public MOVEMENT getMovement() {
		return movement;
	}

	public void setMovement(MOVEMENT movement) {
		this.movement = movement;
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
				Gdx.graphics.getGL20().glEnable(GL20.GL_BLEND);
				screen.getRenderer().begin(ShapeType.Filled);
				screen.getRenderer().setColor(getColor()[0], getColor()[1], getColor()[2], getColor()[3]);
				screen.getRenderer().roundedRect(this.getX() + rem + changeX, this.getY() + rem + changeY, getWidth(), getHeight(), 3);
				screen.getRenderer().end();
				Gdx.graphics.getGL20().glDisable(GL20.GL_BLEND);
				
				Gdx.gl20.glLineWidth(getStroke());
				screen.getRenderer().begin(ShapeType.Line);
				screen.getRenderer().setColor(getColorBorder()[0], getColorBorder()[1], getColorBorder()[2], 1f);
				
				if (movement.equals(MOVEMENT.UP)) {
					float x = this.getX() + rem + changeX + getWidth() / 4;
					float y = this.getY() + rem + changeY + getHeight() * 3 / 4;
					
					screen.getRenderer().line(x, y, x + getWidth()/4, y - getWidth()/2);
					screen.getRenderer().line(x + getWidth()/2, y, x + getWidth()/4, y - getWidth()/2);
				} else if (movement.equals(MOVEMENT.RIGHT)) {
					float x = this.getX() + rem + changeX + getWidth() / 4;
					float y = this.getY() + rem + changeY + getHeight() / 4;
					
					screen.getRenderer().line(x, y, x + getWidth()/2, y + getWidth()/4);
					screen.getRenderer().line(x, y + getWidth()/2, x + getWidth()/2, y + getWidth()/4);
				} else {
					float x = this.getX() + rem + changeX + getWidth() * 3 / 4;
					float y = this.getY() + rem + changeY + getHeight() / 4;
					
					screen.getRenderer().line(x, y, x - getWidth()/2, y + getWidth()/4);
					screen.getRenderer().line(x, y + getWidth()/2, x - getWidth()/2, y + getWidth()/4);
				}
				
				if (( this.isBPressed() ) || (isSelect())) {
					screen.getRenderer().setColor(255f/ 255.0f, 0f / 255.0f, 0f / 255.0f, 1f);
				} else if ( this.isBOver() ) {
					screen.getRenderer().setColor(255f/ 255.0f, 255f / 255.0f, 0f / 255.0f, 1f);
				}
				screen.getRenderer().roundedRectLine(this.getX() + rem + changeX, this.getY() + rem + changeY, getWidth(), getHeight(), 3);
				screen.getRenderer().end();
				Gdx.gl20.glLineWidth(1f);
			}
		}
	}
}
