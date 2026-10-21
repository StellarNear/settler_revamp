/*
 * Copyright (c) 2005-2013 Dirk Aporius <dirk.aporius@gmail.com>
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

package com.apogames.settler.game;

import com.apogames.settler.Constants;
import com.apogames.settler.asset.AssetLoader;
import com.apogames.settler.entity.ApoButton;
import com.apogames.settler.entity.ApoButtonColor;
import com.apogames.settler.entity.ApoButtonImageThree;
import com.apogames.settler.entity.ApoButtonImageWithThree;
import com.apogames.settler.entity.HintBubble;
import com.apogames.settler.game.algorithm.Algorithm;
import com.apogames.settler.game.leaderboard.Leaderboard;
import com.apogames.settler.game.leaderboard.PlayerSelection;
import com.apogames.settler.game.leaderboard.PlayerSelectionForEdit;
import com.apogames.settler.game.menu.Menu;
import com.apogames.settler.game.tiwanaku.Tiwanaku;
import com.badlogic.gdx.graphics.g2d.BitmapFont;

public class ButtonProvider {

	private final MainPanel game;

	public ButtonProvider(MainPanel game) {
		this.game = game;
	}

	public void init() {
		if ((this.game.getButtons() == null) || (this.game.getButtons().size() <= 0)) {
			this.game.getButtons().clear();

			BitmapFont font = AssetLoader.font25;
			String text = "";
			String function = Menu.FUNCTION_BACK;
			int width = 64;
			int height = 64;
			int x = Constants.GAME_WIDTH - width - 15;
			int y = Constants.GAME_HEIGHT - height - 5;
			ApoButton button = new ApoButtonImageWithThree(x, y, width, height, function, text, AssetLoader.buttonXTextureRegion);
			//ApoButtonColor(x, y, width, height, function, text, Constants.COLOR_BACKGROUND, Constants.COLOR_WHITE);
			button.setStroke(1);
			button.setFont(AssetLoader.font40);
			this.game.getButtons().add(button);

			text = "";
			function = Tiwanaku.FUNCTION_TIWANAKU_BACK;
			width = 64;
			height = 64;
			x = Constants.GAME_WIDTH - width - 15;
			y = Constants.GAME_HEIGHT - height - 5;
			button = new ApoButtonImageWithThree(x, y, width, height, function, text, AssetLoader.buttonXTextureRegion);
			//ApoButtonColor(x, y, width, height, function, text, Constants.COLOR_BACKGROUND, Constants.COLOR_WHITE);
			button.setStroke(1);
			button.setFont(AssetLoader.font15);
			((ApoButtonImageWithThree)(button)).setMouseOverText(AssetLoader.buttonBlancoTextureRegion[0], "Menu");
			this.game.getButtons().add(button);

			text = "Play";
			function = Menu.FUNCTION_PLAY;
			width = 164;
			height = 80;
			x = Constants.GAME_WIDTH/2 - width/2;
			y = Constants.GAME_HEIGHT/2 + 215;
			button = new ApoButtonImageThree(x, y, width, height, function, text, 0, 0, width, height, Constants.COLOR_CLEAR, "button_play");
			//ApoButtonColor(x, y, width, height, function, text, Constants.COLOR_BACKGROUND, Constants.COLOR_WHITE);
			button.setStroke(1);
			button.setFont(AssetLoader.font30);
			this.game.getButtons().add(button);

		text = "Algorithm";
		function = Menu.FUNCTION_ALGORITHM;
		width = 164;
		height = 80;
		x = Constants.GAME_WIDTH - width - 5;
		y = 5;
		button = new ApoButtonImageThree(x, y, width, height, function, text, 0, 0, width, height, Constants.COLOR_CLEAR, "button_algorithm");
		button.setStroke(1);
		button.setFont(AssetLoader.font30);
		this.game.getButtons().add(button);

		text = "Leaderboard";
		function = Menu.FUNCTION_LEADERBOARD;
		width = 164;
		height = 80;
		x = Constants.GAME_WIDTH - width - 5;
		y = 5 + 80 + 10;
		button = new ApoButtonImageThree(x, y, width, height, function, text, 0, 0, width, height, Constants.COLOR_CLEAR, "button_leaderboard");
		button.setStroke(1);
		button.setFont(AssetLoader.font30);
		this.game.getButtons().add(button);

		text = "de";
		function = Menu.FUNCTION_LANGUAGE;
		width = 64;
		height = 64;
			x = 15;
			y = Constants.GAME_HEIGHT - height - 5;
			button = new ApoButtonImageThree(x, y, width, height, function, text, 0, 0, width, height, Constants.COLOR_CLEAR, "button_language_de");
			button.setFont(AssetLoader.font30);
			this.game.getButtons().add(button);

			int addValue = 100;

			text = "";
			function = Menu.FUNCTION_LEVEL_LEFT;
			width = 64;
			height = 64;
			x = Constants.GAME_WIDTH/2 - width - addValue;
			y = Constants.GAME_HEIGHT/2 - 150;
			button = new ApoButtonImageWithThree(x, y, width, height, function, text, AssetLoader.buttonLeftTextureRegion);
			button.setFont(AssetLoader.font30);
			this.game.getButtons().add(button);

			text = "";
			function = Menu.FUNCTION_LEVEL_RIGHT;
			width = 64;
			height = 64;
			x = Constants.GAME_WIDTH/2 + addValue;
			y = Constants.GAME_HEIGHT/2 - 150;
			button = new ApoButtonImageWithThree(x, y, width, height, function, text, AssetLoader.buttonRightTextureRegion);
			button.setFont(AssetLoader.font30);
			this.game.getButtons().add(button);


			text = "";
			function = Menu.FUNCTION_DIFFICULTY_LEFT;
			width = 64;
			height = 64;
			x = Constants.GAME_WIDTH/2 - width - addValue;
			y = Constants.GAME_HEIGHT/2;
			button = new ApoButtonImageWithThree(x, y, width, height, function, text, AssetLoader.buttonLeftTextureRegion);
			button.setFont(AssetLoader.font30);
			this.game.getButtons().add(button);

			text = "";
			function = Menu.FUNCTION_DIFFICULTY_RIGHT;
			width = 64;
			height = 64;
			x = Constants.GAME_WIDTH/2 + addValue;
			y = Constants.GAME_HEIGHT/2;
			button = new ApoButtonImageWithThree(x, y, width, height, function, text, AssetLoader.buttonRightTextureRegion);
			button.setFont(AssetLoader.font30);
			this.game.getButtons().add(button);


			String[] regionFunctions = {Menu.FUNCTION_REGION_LEFT, Menu.FUNCTION_REGION_RIGHT};
			for (int side = 0; side < regionFunctions.length; side++) {
				x = side == 0 ? Constants.GAME_WIDTH/2 - 64 - addValue : Constants.GAME_WIDTH/2 + addValue;
				y = Constants.GAME_HEIGHT/2 + 110;
				button = new ApoButtonImageWithThree(x, y, 64, 64, regionFunctions[side], "",
						side == 0 ? AssetLoader.buttonLeftTextureRegion : AssetLoader.buttonRightTextureRegion);
				button.setFont(AssetLoader.font30);
				this.game.getButtons().add(button);
			}

			text = "new Level";
			function = Tiwanaku.FUNCTION_NEW_LEVEL;
			width = 180;
			height = 64;
			x = Constants.GAME_WIDTH - width - 30;
			y = Constants.GAME_HEIGHT - (height + 5) * 3;
			button = new ApoButtonImageThree(x, y, width, height, function, text, 0, 0, width, height, Constants.COLOR_CLEAR, "button_newlevel");
			button.setFont(AssetLoader.font30);
			this.game.getButtons().add(button);

			text = "";
			function = Tiwanaku.FUNCTION_RESTART;
			width = 64;
			height = 64;
			x = Constants.GAME_WIDTH - AssetLoader.hudRightTextureRegion.getRegionWidth() + 5;
			y = Constants.GAME_HEIGHT - (height + 5);
			button = new ApoButtonImageWithThree(x, y, width, height, function, text, AssetLoader.buttonRestartTextureRegion);
			((ApoButtonImageWithThree)(button)).setMouseOverText(AssetLoader.buttonBlancoTextureRegion[0], "Restart");
			button.setFont(AssetLoader.font15);
			this.game.getButtons().add(button);

			text = "new Level";
			function = Tiwanaku.FUNCTION_FINISH_NEW_LEVEL;
			width = 180;
			height = 64;
			x = Constants.GAME_WIDTH/2 - AssetLoader.hudRightTextureRegion.getRegionWidth()/2 - width/2;
			y = Constants.GAME_HEIGHT/2 + AssetLoader.wonTextureRegion.getRegionHeight()/2 - (height + 50);
			button = new ApoButtonImageThree(x, y, width, height, function, text, 0, 0, width, height, Constants.COLOR_CLEAR, "button_newlevel");
			button.setFont(AssetLoader.font30);
			this.game.getButtons().add(button);

			text = "";
			function = Tiwanaku.FUNCTION_FINISH_RESTART;
			width = 64;
			height = 64;
			x = Constants.GAME_WIDTH/2 - AssetLoader.hudRightTextureRegion.getRegionWidth()/2 - AssetLoader.wonTextureRegion.getRegionWidth()/2 + 55;
			y = Constants.GAME_HEIGHT/2 + AssetLoader.wonTextureRegion.getRegionHeight()/2 - (height + 50);
			button = new ApoButtonImageWithThree(x, y, width, height, function, text, AssetLoader.buttonRestartTextureRegion);
			((ApoButtonImageWithThree)(button)).setMouseOverText(AssetLoader.buttonBlancoTextureRegion[0], "Restart");
			button.setFont(AssetLoader.font15);
			this.game.getButtons().add(button);

			text = "";
			function = Tiwanaku.FUNCTION_FINISH_BACK;
			width = 64;
			height = 64;
			x = Constants.GAME_WIDTH/2 - AssetLoader.hudRightTextureRegion.getRegionWidth()/2 + AssetLoader.wonTextureRegion.getRegionWidth()/2 - 55 - width;
			y = Constants.GAME_HEIGHT/2 + AssetLoader.wonTextureRegion.getRegionHeight()/2 - (height + 50);
			button = new ApoButtonImageWithThree(x, y, width, height, function, text, AssetLoader.buttonXTextureRegion);
			button.setFont(AssetLoader.font30);
			this.game.getButtons().add(button);

			text = "";
			function = Tiwanaku.FUNCTION_FIX;
			width = 64;
			height = 64;
			x = Constants.GAME_WIDTH - AssetLoader.hudRightTextureRegion.getRegionWidth() + 30;
			y = 50;
			button = new ApoButtonImageWithThree(x, y, width, height, function, text, AssetLoader.buttonFixTextureRegion);
			button.setFont(AssetLoader.font15);
			((ApoButtonImageWithThree)(button)).setMouseOverText(AssetLoader.buttonBlancoTextureRegion[0], "Hints");
			this.game.getButtons().add(button);

			text = "";
			function = Tiwanaku.FUNCTION_HELP;
			width = 64;
			height = 64;
			x = Constants.GAME_WIDTH - width - 30;
			y = 50;
			button = new ApoButtonImageWithThree(x, y, width, height, function, text, AssetLoader.buttonHelpTextureRegion);
			button.setFont(AssetLoader.font15);
			((ApoButtonImageWithThree)(button)).setMouseOverText(AssetLoader.buttonBlancoTextureRegion[0], "Help");
			this.game.getButtons().add(button);

			int hudStartX = Constants.GAME_WIDTH - 5 - AssetLoader.hudRightTextureRegion.getRegionWidth();
			int bubbleX = hudStartX + (AssetLoader.hudRightTextureRegion.getRegionWidth() - Tiwanaku.HUD_BUBBLE_SIZE) / 2;
			for (int v = 1; v <= Tiwanaku.HINT_VALUE_COUNT; v++) {
				int bubbleY = Tiwanaku.HUD_BUBBLE_START_Y + (v - 1) * (Tiwanaku.HUD_BUBBLE_SIZE + Tiwanaku.HUD_BUBBLE_SPACING);
				HintBubble bubble = new HintBubble(bubbleX, bubbleY, Tiwanaku.HUD_BUBBLE_SIZE, v, Tiwanaku.FUNCTION_HINT[v - 1], this.game);
				this.game.getButtons().add(bubble);
			}

			text = "X";
			function = Algorithm.FUNCTION_BACK;
			width = 64;
			height = 64;
			x = Constants.GAME_WIDTH - width - 15;
			y = Constants.GAME_HEIGHT - height - 5;
		button = new ApoButtonColor(x, y, width, height, function, text, Constants.COLOR_PURPLE, Constants.COLOR_CLEAR);
		button.setStroke(1);
		button.setFont(AssetLoader.font40);
		this.game.getButtons().add(button);

			text = "Reset";
			function = Leaderboard.FUNCTION_RESET;
			width = 128;
			height = 64;
			x = Constants.GAME_WIDTH - width - 15 - 64 - 10;
			y = Constants.GAME_HEIGHT - height - 5;
			button = new ApoButtonColor(x, y, width, height, function, text, Constants.COLOR_PURPLE, Constants.COLOR_CLEAR);
			button.setStroke(1);
			button.setFont(AssetLoader.font30);
			this.game.getButtons().add(button);

			text = "Confirm";
			function = PlayerSelectionForEdit.FUNCTION_CONFIRM;
			width = 140;
			height = 48;
			x = Constants.GAME_WIDTH / 2 - width - 10;
			y = 30;
			button = new ApoButtonColor(x, y, width, height, function, text, Constants.COLOR_PURPLE, Constants.COLOR_CLEAR);
			button.setStroke(1);
			button.setFont(AssetLoader.font30);
			this.game.getButtons().add(button);

			text = "+";
			function = PlayerSelectionForEdit.FUNCTION_ADD_PLAYER;
			width = 60;
			height = 48;
			x = Constants.GAME_WIDTH / 2 + 10;
			y = 30;
			button = new ApoButtonColor(x, y, width, height, function, text, Constants.COLOR_PURPLE, Constants.COLOR_CLEAR);
			button.setStroke(1);
			button.setFont(AssetLoader.font40);
			this.game.getButtons().add(button);

		text = "Confirm";
		function = PlayerSelection.FUNCTION_CONFIRM;
		width = 140;
		height = 48;
		x = Constants.GAME_WIDTH / 2 - width - 10;
		y = 30;
		button = new ApoButtonColor(x, y, width, height, function, text, Constants.COLOR_PURPLE, Constants.COLOR_CLEAR);
		button.setStroke(1);
		button.setFont(AssetLoader.font30);
		this.game.getButtons().add(button);

		text = "+";
		function = PlayerSelection.FUNCTION_ADD_PLAYER;
		width = 60;
		height = 48;
		x = Constants.GAME_WIDTH / 2 + 10;
		y = 30;
		button = new ApoButtonColor(x, y, width, height, function, text, Constants.COLOR_PURPLE, Constants.COLOR_CLEAR);
		button.setStroke(1);
		button.setFont(AssetLoader.font40);
		this.game.getButtons().add(button);


		text = "Step";
			function = Algorithm.FUNCTION_STEP;
			width = 128;
			height = 64;
			x = Constants.GAME_WIDTH / 2 - width - 5;
			y = Constants.GAME_HEIGHT - height - 5;
			button = new ApoButtonColor(x, y, width, height, function, text, Constants.COLOR_PURPLE, Constants.COLOR_CLEAR);
			//ApoButtonColor(x, y, width, height, function, text, Constants.COLOR_BACKGROUND, Constants.COLOR_WHITE);
			button.setStroke(1);
			button.setFont(AssetLoader.font40);
			this.game.getButtons().add(button);

			text = "Restart";
			function = Algorithm.FUNCTION_RESTART;
			width = 128;
			height = 64;
			x = Constants.GAME_WIDTH / 2 + 5;
			y = Constants.GAME_HEIGHT - height - 5;
			button = new ApoButtonColor(x, y, width, height, function, text, Constants.COLOR_PURPLE, Constants.COLOR_CLEAR);
			//ApoButtonColor(x, y, width, height, function, text, Constants.COLOR_BACKGROUND, Constants.COLOR_WHITE);
			button.setStroke(1);
			button.setFont(AssetLoader.font30);
			this.game.getButtons().add(button);

			text = "New";
			function = Algorithm.FUNCTION_NEW_LEVEL;
			width = 128;
			height = 64;
			x = Constants.GAME_WIDTH / 2 - width * 2 - 5 - 10;
			y = Constants.GAME_HEIGHT - height - 5;
			button = new ApoButtonColor(x, y, width, height, function, text, Constants.COLOR_PURPLE, Constants.COLOR_CLEAR);
			//ApoButtonColor(x, y, width, height, function, text, Constants.COLOR_BACKGROUND, Constants.COLOR_WHITE);
			button.setStroke(1);
			button.setFont(AssetLoader.font40);
			this.game.getButtons().add(button);

			for (int i = 0; i < this.game.getButtons().size(); i++) {
				this.game.getButtons().get(i).setBOpaque(false);
			}
		}
	}
}
