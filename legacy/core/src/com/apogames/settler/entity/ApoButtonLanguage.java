package com.apogames.settler.entity;

public class ApoButtonLanguage extends ApoButtonColor {
	
	public static final String FUNCTION_GERMAN = "de";
	public static final String FUNCTION_ENGLISH = "en";
	
	private String functionOne = "";
	private String functionTwo = "";
	
	public ApoButtonLanguage(int x, int y, int width, int height, String function, float[] color, float[] colorBorder) {
		super(x, y, width, height, function, FUNCTION_ENGLISH, color, colorBorder);
		
		this.functionOne = FUNCTION_GERMAN;
		this.functionTwo = FUNCTION_ENGLISH;
	}

	public void changeText() {
		if (this.getText().equals(functionOne)) {
			this.setText(functionTwo);
		} else {
			this.setText(functionOne);
		}
	}
}
