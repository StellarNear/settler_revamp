/*
 * Copyright (c) 2005-2020 Dirk Aporius <dirk.aporius@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.apogames.settler.asset;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;

/**
 * The type Asset loader.
 */
public class AssetLoader {

	public static final String FONT_CHARACTERS = FreeTypeFontGenerator.DEFAULT_CHARS
			+ "\u00C4\u00D6\u00DC\u00E4\u00F6\u00FC\u00DF"
			+ "\u00C0\u00C1\u00C2\u00C3\u00C5\u00C6\u00C7\u00C8\u00C9\u00CA\u00CB\u00CC\u00CD\u00CE\u00CF"
			+ "\u00D0\u00D1\u00D2\u00D3\u00D4\u00D5\u00D7\u00D8\u00D9\u00DA\u00DB\u00DD\u00DE"
			+ "\u00E0\u00E1\u00E2\u00E3\u00E5\u00E6\u00E7\u00E8\u00E9\u00EA\u00EB\u00EC\u00ED\u00EE\u00EF"
			+ "\u00F0\u00F1\u00F2\u00F3\u00F4\u00F5\u00F7\u00F8\u00F9\u00FA\u00FB\u00FD\u00FE\u00FF"
			+ "\u2013\u2014\u2018\u2019\u201C\u201D\u201E\u2026\u20AC";

	private static Texture backgroundTexture;
	public static TextureRegion[] backgroundTextureRegion;

	private static Texture backgroundMainTexture;
	public static TextureRegion backgroundMainTextureRegion;

	private static Texture boardMainTexture;
	public static TextureRegion boardMainTextureRegion;

	private static Texture wonTexture;
	public static TextureRegion wonTextureRegion;
	private static Texture titleTexture;
	public static TextureRegion titleTextureRegion;
	private static Texture hudMenuTexture;
	public static TextureRegion hudMenuTextureRegion;

	private static Texture buttonXTexture;
	public static TextureRegion[] buttonXTextureRegion;
	private static Texture buttonHelpTexture;
	public static TextureRegion[] buttonHelpTextureRegion;
	private static Texture buttonFixTexture;
	public static TextureRegion[] buttonFixTextureRegion;
	private static Texture buttonRestartTexture;
	public static TextureRegion[] buttonRestartTextureRegion;
	private static Texture buttonRightTexture;
	public static TextureRegion[] buttonRightTextureRegion;
	private static Texture buttonLeftTexture;
	public static TextureRegion[] buttonLeftTextureRegion;
	private static Texture buttonBlancoTexture;
	public static TextureRegion[] buttonBlancoTextureRegion;
	private static Texture hudRightTexture;
	public static TextureRegion hudRightTextureRegion;
	private static Texture circlesTexture;
	public static TextureRegion[] circlesTextureRegion;
	private static Texture extraNumbersTexture;
	public static TextureRegion[] numberTextureRegion;
	public static BitmapFont font40;
	public static BitmapFont font20;
	public static BitmapFont font15;
	public static BitmapFont font25;
	public static BitmapFont font30;



	public static void load() {
		backgroundTexture = new Texture(Gdx.files.internal("images/background.png"));
		backgroundTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

		backgroundTextureRegion = new TextureRegion[5];
		for (int i = 0; i < backgroundTextureRegion.length; i++) {
			backgroundTextureRegion[i] = new TextureRegion(backgroundTexture, 128 * i, 0, 128, 192);
			backgroundTextureRegion[i].flip(false, true);
		}

		backgroundMainTexture = new Texture(Gdx.files.internal("images/background_main.png"));
		backgroundMainTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

		backgroundMainTextureRegion = new TextureRegion(backgroundMainTexture, 0, 0, 1422, 800);
		backgroundMainTextureRegion.flip(false, true);

		boardMainTexture = new Texture(Gdx.files.internal("images/board_main.png"));
		boardMainTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

		boardMainTextureRegion = new TextureRegion(boardMainTexture, 0, 0, 1200, 715);
		boardMainTextureRegion.flip(false, true);

		wonTexture = new Texture(Gdx.files.internal("images/won.png"));
		wonTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

		wonTextureRegion = new TextureRegion(wonTexture, 0, 0, 500, 300);
		wonTextureRegion.flip(false, true);

		titleTexture = new Texture(Gdx.files.internal("images/title.png"));
		titleTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

		titleTextureRegion = new TextureRegion(titleTexture, 0, 0, 400, 71);
		titleTextureRegion.flip(false, true);

		hudRightTexture = new Texture(Gdx.files.internal("images/hud_right.png"));
		hudRightTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

		hudRightTextureRegion = new TextureRegion(hudRightTexture, 0, 0, 230, 700);
		hudRightTextureRegion.flip(false, true);

		hudMenuTexture = new Texture(Gdx.files.internal("images/hud_menu.png"));
		hudMenuTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

		hudMenuTextureRegion = new TextureRegion(hudMenuTexture, 0, 0, 800, 700);
		hudMenuTextureRegion.flip(false, true);

		buttonXTexture = new Texture(Gdx.files.internal("images/buttons_x.png"));
		buttonXTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

		buttonXTextureRegion = new TextureRegion[3];
		for (int i = 0; i < buttonXTextureRegion.length; i++) {
			buttonXTextureRegion[i] = new TextureRegion(buttonXTexture, 70 * i, 0, 70, 70);
			buttonXTextureRegion[i].flip(false, true);
		}

		buttonHelpTexture = new Texture(Gdx.files.internal("images/buttons_help.png"));
		buttonHelpTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

		buttonHelpTextureRegion = new TextureRegion[3];
		for (int i = 0; i < buttonHelpTextureRegion.length; i++) {
			buttonHelpTextureRegion[i] = new TextureRegion(buttonHelpTexture, 70 * i, 0, 70, 70);
			buttonHelpTextureRegion[i].flip(false, true);
		}

		buttonFixTexture = new Texture(Gdx.files.internal("images/buttons_fix.png"));
		buttonFixTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

		buttonFixTextureRegion = new TextureRegion[3];
		for (int i = 0; i < buttonFixTextureRegion.length; i++) {
			buttonFixTextureRegion[i] = new TextureRegion(buttonFixTexture, 70 * i, 0, 70, 70);
			buttonFixTextureRegion[i].flip(false, true);
		}

		buttonRestartTexture = new Texture(Gdx.files.internal("images/buttons_restart.png"));
		buttonRestartTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

		buttonRestartTextureRegion = new TextureRegion[3];
		for (int i = 0; i < buttonRestartTextureRegion.length; i++) {
			buttonRestartTextureRegion[i] = new TextureRegion(buttonRestartTexture, 70 * i, 0, 70, 70);
			buttonRestartTextureRegion[i].flip(false, true);
		}

		buttonRightTexture = new Texture(Gdx.files.internal("images/buttons_right.png"));
		buttonRightTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

		buttonRightTextureRegion = new TextureRegion[3];
		for (int i = 0; i < buttonRightTextureRegion.length; i++) {
			buttonRightTextureRegion[i] = new TextureRegion(buttonRightTexture, 70 * i, 0, 70, 70);
			buttonRightTextureRegion[i].flip(false, true);
		}

		buttonLeftTexture = new Texture(Gdx.files.internal("images/buttons_left.png"));
		buttonLeftTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

		buttonLeftTextureRegion = new TextureRegion[3];
		for (int i = 0; i < buttonLeftTextureRegion.length; i++) {
			buttonLeftTextureRegion[i] = new TextureRegion(buttonLeftTexture, 70 * i, 0, 70, 70);
			buttonLeftTextureRegion[i].flip(false, true);
		}

		buttonBlancoTexture = new Texture(Gdx.files.internal("images/buttons_blanco.png"));
		buttonBlancoTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

		buttonBlancoTextureRegion = new TextureRegion[3];
		for (int i = 0; i < buttonBlancoTextureRegion.length; i++) {
			buttonBlancoTextureRegion[i] = new TextureRegion(buttonBlancoTexture, 300 * i, 0, 300, 70);
			buttonBlancoTextureRegion[i].flip(false, true);
		}

		circlesTexture = new Texture(Gdx.files.internal("images/circles.png"));
		circlesTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

		circlesTextureRegion = new TextureRegion[6];
		for (int i = 0; i < circlesTextureRegion.length; i++) {
			circlesTextureRegion[i] = new TextureRegion(circlesTexture, 64 * i, 0, 64, 64);
			circlesTextureRegion[i].flip(false, true);
		}

		FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/OpenSans.ttf"));
		loadNumberRegions();
		font15 = generateFont(generator, 15);
		font20 = generateFont(generator, 20);
		font25 = generateFont(generator, 25);
		font30 = generateFont(generator, 30);
		font40 = generateFont(generator, 40);
		generator.dispose();
	}

	private static void loadNumberRegions() {
		numberTextureRegion = new TextureRegion[9];
		System.arraycopy(circlesTextureRegion, 0, numberTextureRegion, 0, 5);
		Pixmap source = new Pixmap(Gdx.files.internal("images/circles.png"));
		Pixmap extra = new Pixmap(256, 64, Pixmap.Format.RGBA8888);
		extra.setBlending(Pixmap.Blending.None);
		int[] colors = {0x99DDEEFF, 0xFFC48CFF, 0xD9B3E6FF, 0xC8CDD2FF};
		for (int index = 0; index < colors.length; index++) {
			for (int row = 0; row < 64; row++) {
				for (int column = 0; column < 64; column++) {
					int pixel = source.getPixel(column, row);
					int red = ((pixel >>> 24) & 255) * ((colors[index] >>> 24) & 255) / 255;
					int green = ((pixel >>> 16) & 255) * ((colors[index] >>> 16) & 255) / 255;
					int blue = ((pixel >>> 8) & 255) * ((colors[index] >>> 8) & 255) / 255;
					extra.drawPixel(index * 64 + column, row, red << 24 | green << 16 | blue << 8 | (pixel & 255));
				}
			}
		}
		extraNumbersTexture = new Texture(extra);
		extraNumbersTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
		for (int index = 0; index < colors.length; index++) {
			numberTextureRegion[index + 5] = new TextureRegion(extraNumbersTexture, index * 64, 0, 64, 64);
			numberTextureRegion[index + 5].flip(false, true);
		}
		source.dispose();
		extra.dispose();
	}

	private static BitmapFont generateFont(FreeTypeFontGenerator generator, int size) {
		FreeTypeFontParameter param = new FreeTypeFontParameter();
		param.size = size;
		param.flip = true;
		param.characters = FONT_CHARACTERS;
		param.hinting = FreeTypeFontGenerator.Hinting.Full;
		param.minFilter = Texture.TextureFilter.Linear;
		param.magFilter = Texture.TextureFilter.Linear;
		param.genMipMaps = true;
		return generator.generateFont(param);
	}

	public static void dispose() {
		backgroundTexture.dispose();
		circlesTexture.dispose();
		extraNumbersTexture.dispose();
		backgroundMainTexture.dispose();
		boardMainTexture.dispose();
		hudRightTexture.dispose();
		buttonXTexture.dispose();
		buttonFixTexture.dispose();
		buttonHelpTexture.dispose();
		buttonRestartTexture.dispose();
		buttonRightTexture.dispose();
		buttonLeftTexture.dispose();
		buttonBlancoTexture.dispose();
		wonTexture.dispose();
		titleTexture.dispose();
		font40.dispose();
		font30.dispose();
		font25.dispose();
		font20.dispose();
		font15.dispose();
//        click.dispose();
	}

}

