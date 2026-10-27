package com.apogames.settler.entity;

import com.apogames.settler.asset.AssetLoader;
import com.apogames.settler.backend.GameScreen;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.GridPoint2;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Textfield extends ApoButton {

	private final Pattern VALID_EMAIL_ADDRESS_REGEX = Pattern.compile("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,6}$");
	
	private final float[] COLOR_BLACK = new float[] {0f, 0f, 0f, 1f};
	private final float[] COLOR_RED = new float[] {1f, 0f, 0f, 1f};	
	
	private final float[] MOUSE_OVER_COLOR = new float[] {255/255f, 255/255f, 100/255f, 1f};
	private final float[] SELECTED_COLOR = new float[]{0/255f, 150/255f, 255/255f, 1f};
	
	private final int MAX_LENGTH = 100;
	
	private final int TIME_LINE = 700;
	
	private String curString;

	private int position;
	
	private int time;
	
	private boolean bLineOn;
	
	private BitmapFont myFont;
	
	private boolean bEnabled;
	
	private int maxLength;
	
	private GridPoint2 selectedPosition;
	
	private static GlyphLayout glyphLayout = new GlyphLayout();
	
	private boolean bCorrectString;
	
	public Textfield(float x, float y, float width, float height, BitmapFont myFont) {
		super((int)x, (int)y, (int)width, (int)height, "", "");
		
		this.myFont = myFont;
		this.bEnabled = true;
	}

	public void init() {
		super.init();
		this.curString = "dirk.aporius@gmail.com";
		this.position = this.curString.length();
		this.bLineOn = true;
		this.bEnabled = true;
		this.time = 0;
		if (this.myFont == null) {
			this.myFont = AssetLoader.font25;
		}
		if (this.maxLength <= 0) {
			this.maxLength = this.MAX_LENGTH;
		}
		this.selectedPosition = new GridPoint2(-1, -1);
		this.bCorrectString = true;
	}

	public boolean isCorrectString() {
		return bCorrectString;
	}

	public void setCorrectString(boolean bCorrectString) {
		this.bCorrectString = bCorrectString;
	}

	public boolean isStringAValidEMailAdress() {
		Matcher matcher = VALID_EMAIL_ADDRESS_REGEX .matcher(this.curString);
		return matcher.find();
	}
	
	public int getMaxLength() {
		return this.maxLength;
	}

	public void setMaxLength(int maxLength) {
		this.maxLength = maxLength;
		if (this.maxLength <= 0) {
			this.maxLength = this.MAX_LENGTH;
		}
	}

	public boolean isBEnabled() {
		return this.bEnabled;
	}

	public void setBEnabled(boolean bEnabled) {
		this.bEnabled = bEnabled;
	}

	public BitmapFont getFont() {
		return this.myFont;
	}

	public void setFont(BitmapFont myFont) {
		this.myFont = myFont;
	}

	public boolean mouseDragged(int x, int y) {
		if (!this.bEnabled) {
			return false;
		}
		if (this.selectedPosition.x < 0) {
			this.selectedPosition.x = this.getPosition();
		}
		if (this.selectedPosition.x >= 0) {
			if (this.getRec().contains(x, y)) {
				int position = this.getThisPosition(x, y);
				if (position != -1) {
					this.setSelectedPosition(position);
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean mousePressed(int x, int y) {
		if (!this.bEnabled) {
			return false;
		}
		if (this.getPressed(x, y)) {
			if (this.selectedPosition.y >= 0) {
				this.selectedPosition.x = -1;
				this.selectedPosition.y = -1;
			}
			int position = this.getThisPosition(x, y);
			if (position != -1) {
				this.selectedPosition.x = position;
			}
			return true;
		} else {
			this.selectedPosition.x = -1;
			this.selectedPosition.y = -1;
		}
		return false;
	}
	
	public boolean mouseReleased(int x, int y) {
		if (!this.bEnabled) {
			return false;
		}
		if (super.getReleased(x, y)) {
			int position = this.getThisPosition(x, y);
			if (position != -1) {
				this.setPosition(position);
				this.setSelectedPosition(position);
			}
			return true;
		} else {
			this.selectedPosition.x = -1;
			this.selectedPosition.y = -1;
		}
		return false;
	}
	
	public void nextSelectedPosition(int position) {
		if (this.selectedPosition.x < 0) {
			this.selectedPosition.x = this.getPosition();
		}
		if (position > this.curString.length()) {
			position = this.curString.length();
		} else if (position < 0) {
			position = 0;
		}
		this.setSelectedPosition(position);
	}
	
	private void setSelectedPosition(int position) {
		if (this.selectedPosition.x != position) {
			this.setPosition(position);
			if (this.selectedPosition.x > position) {
				if (this.selectedPosition.y < this.selectedPosition.x) {
					this.selectedPosition.y = this.selectedPosition.x;
				}
				this.selectedPosition.x = position;
			} else {
				this.selectedPosition.y = this.getPosition();
			}
		}
	}
	
	private int getThisPosition(int x, int y) {
		String s = this.curString;
		glyphLayout.setText(myFont, s);
		float w = glyphLayout.width;
		if (x > w + 5 + this.getX()) {
			return s.length();
		} else if (x < this.getX() + 5) {
			return 0;
		} else {
			for (int i = 0; i < this.curString.length(); i++) {
				String old = this.curString.substring(0, i);
				String next = this.curString.substring(0, i + 1);
				glyphLayout.setText(myFont, old);
				float wOld = glyphLayout.width;
				glyphLayout.setText(myFont, next);
				float wNext = glyphLayout.width;
				if ((x > wOld + 5 + this.getX()) && (x < wNext + 5 + this.getX())) {
					return i;
				}
			}
		}
		return -1;
	}

	public String getCurString() {
		return this.curString;
	}

	public String getSelectedString() {
		String selected = null;
		if ((this.selectedPosition.x >= 0) && (this.selectedPosition.y >= 0)) {
			return this.curString.substring(this.selectedPosition.x, this.selectedPosition.y);
		}
		return selected;
	}
	
	public void deleteSelectedString() {
		String s = this.getSelectedString();
		if ((s != null) && (s.length() > 0)) {
			this.setPosition(this.selectedPosition.x);
			this.curString = this.curString.substring(0, this.selectedPosition.x) + this.curString.substring(this.selectedPosition.y);
			this.selectedPosition.x = -1;
			this.selectedPosition.y = -1;
		}
	}
	
	public void removeCurStringAndSetCurString(String curString) {
		if (!curString.equals(this.curString)) {
			this.position = 0;
			this.setCurString(curString);
		}
	}
	
	public void setCurString(String curString) {
		this.curString = curString;
		if (!this.isSelect()) {
			this.position = this.curString.length();
		}
		this.myFont = this.getCorrectFontforFunction();
	}

	public int getPosition() {
		return this.position;
	}

	public void setPosition(int position) {
		this.position = position;
		if (this.position < 0) {
			this.position = 0;
		} else if (this.position > this.curString.length()) {
			this.position = this.curString.length();
		}
		this.showLine();
	}

	private void deleteTextBackspace() {
		if (!this.bEnabled) {
			return;
		}
		if ((this.curString.length() > 0) && (this.position != 0)) {
			this.curString = this.curString.substring(0, position - 1) + this.curString.substring(this.position, this.curString.length());
			this.setPosition(this.getPosition() - 1);
		}
		this.showLine();
	}

	private void deleteTextDelete() {
		if (!this.bEnabled) {
			return;
		}
		if (this.curString.length() != this.position) {
			this.curString = this.curString.substring(0, this.getPosition()) + this.curString.substring(this.getPosition() + 1, this.curString.length());
		}
		this.showLine();
	}

	public void addCharacter(int button, char character) {
		if (!this.bEnabled) {
			return;
		}
		if (this.isSelect()) {
			System.out.println(button);
			if (button == 67) {
				String s = this.getSelectedString();
				if ((s != null) && (s.length() > 0)) {
					this.deleteSelectedString();
				} else {
					this.deleteTextBackspace();
				}
				this.selectedPosition.x = -1;
				this.selectedPosition.y = -1;
			} else if (button == 112) {
				String s = this.getSelectedString();
				if ((s != null) && (s.length() > 0)) {
					this.deleteSelectedString();
				} else {
					this.deleteTextDelete();
				}
				this.selectedPosition.x = -1;
				this.selectedPosition.y = -1;
			} else if (button == 2) {
				this.curString = "";
				this.setPosition(0);
				this.selectedPosition.x = -1;
				this.selectedPosition.y = -1;
			} else if (button == 3) {
				this.setPosition(0);
				this.selectedPosition.x = -1;
				this.selectedPosition.y = -1;
			} else if (button == 132) {
				this.setPosition(curString.length());
				this.selectedPosition.x = -1;
				this.selectedPosition.y = -1;
			} else if (button == 21) {
				this.setPosition(this.getPosition() - 1);
				this.selectedPosition.x = -1;
				this.selectedPosition.y = -1;
			} else if (button == 22) {
				this.setPosition(this.getPosition() + 1);
				this.selectedPosition.x = -1;
				this.selectedPosition.y = -1;
			} else if (this.curString.length() < this.maxLength) {
				if (((button >= 29) && (button <= 54)) || (button == 56) || (button == Input.Keys.AT)) {
					this.curString = this.curString.substring(0, this.position) + Input.Keys.toString(button).toLowerCase() + this.curString.substring(this.position, this.curString.length());
					this.position	+= 1;
					this.showLine();
					this.selectedPosition.x = -1;
					this.selectedPosition.y = -1;
				}
			} else {
				return;
			}
			this.myFont = this.getCorrectFontforFunction();
		}
	}
	
	private void showLine() {
		if (this.isSelect()) {
			this.bLineOn = true;
			this.time = 0;
		}
	}

	public void think(int delta) {
		this.time += delta;
		if (this.time > this.TIME_LINE) {
			this.time = 0;
			this.bLineOn = !this.bLineOn;
		}
	}
	
	public void render(GameScreen screen, int changeX, int changeY) {
		try {
			screen.getRenderer().begin(ShapeType.Filled);
			screen.getRenderer().setColor(1f, 1f, 1f, 1f);
			if (!this.bEnabled) {
				screen.getRenderer().setColor(0.5f, 0.5f, 0.5f, 1f);				
			}
			screen.getRenderer().rect((this.getX() + changeX), (this.getY() + changeY), (this.getWidth()), (this.getHeight()));
	        screen.getRenderer().end();
			
	        screen.getRenderer().begin(ShapeType.Line);
			if ((this.isBOver()) && (this.bEnabled)) {
				screen.getRenderer().setColor(MOUSE_OVER_COLOR[0], MOUSE_OVER_COLOR[1], MOUSE_OVER_COLOR[2], 1f);
		        Gdx.gl20.glLineWidth(2f);
			} else if ((!this.isSelect()) || (!this.bEnabled)) {
				screen.getRenderer().setColor(0f, 0f, 0f, 1f);
			} else {
				screen.getRenderer().setColor(SELECTED_COLOR[0], SELECTED_COLOR[1], SELECTED_COLOR[2], 1f);
		        Gdx.gl20.glLineWidth(2f);
			}
			screen.getRenderer().rect((this.getX() + changeX), (this.getY() + changeY), (this.getWidth() - 1), (this.getHeight() - 1));
			screen.getRenderer().end();

	        Gdx.gl20.glLineWidth(1f);
			if (this.curString != null) {
				if ((this.selectedPosition.x > -1) && (this.selectedPosition.y > -1)) {
					screen.getRenderer().begin(ShapeType.Line);
					screen.getRenderer().setColor(0.5f, 0.5f, 0.5f, 1f);
					String s = this.curString.substring(0, this.selectedPosition.x);
					glyphLayout.setText(myFont, s);
					float x = glyphLayout.width + (int)(this.getX() + 5 + changeX);
					s = this.curString.substring(this.selectedPosition.x, this.selectedPosition.y);
					glyphLayout.setText(myFont, s);
					float width = glyphLayout.width;
					screen.getRenderer().rect(x, this.getY() + changeY + 1, (width), (this.getHeight() - 2));
					screen.getRenderer().end();
				}
				
				float[] color = COLOR_BLACK;
				if (!this.bCorrectString) {
					color = COLOR_RED;	
				}
				String s = this.curString;
				glyphLayout.setText(myFont, s);
				screen.drawString(this.curString, this.getX() + 5 + changeX, this.getY() - glyphLayout.height/2 + this.getHeight()/2 + changeY - 5, color, myFont, false);
				if ((this.isSelect()) && (this.bLineOn) && (this.bEnabled)) {
					try {
						s = this.curString.substring(0, this.position);
						glyphLayout.setText(myFont, s);
						float w = glyphLayout.width;
						Gdx.gl20.glLineWidth(3f);
						screen.getRenderer().begin(ShapeType.Line);
						screen.getRenderer().setColor(color[0], color[1], color[2], 1f);
						screen.getRenderer().line((this.getX() + 5 + w + changeX), (this.getY() + this.getHeight()/2 - glyphLayout.height/2 + changeY), (this.getX() + 5 + w + changeX), (this.getY() + glyphLayout.height/2 + this.getHeight()/2 + changeY));
						screen.getRenderer().end();
						Gdx.gl20.glLineWidth(1f);
					} catch (StringIndexOutOfBoundsException ex) {
					}
				}
			}
		} catch (Exception ex) {
		}
	}
	
	private BitmapFont getCorrectFontforFunction() {
		BitmapFont font = AssetLoader.font25;
			
		glyphLayout.setText(font, curString);
		float w = glyphLayout.width;
		
		if (w > this.getWidth() - 10) { 
			font = AssetLoader.font20;
			glyphLayout.setText(myFont, curString);
			w = glyphLayout.width;
			if (w > this.getWidth() - 10) {
				font = AssetLoader.font15;
			}
		}
		
		return font;
	}

}
