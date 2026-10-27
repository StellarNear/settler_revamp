package com.apogames.settler.game.menu;

import com.apogames.settler.backend.GameProperties;
import com.apogames.settler.backend.SequentiallyThinkingScreenModel;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

public class MenuPreferences extends GameProperties {

    public MenuPreferences(SequentiallyThinkingScreenModel mainPanel) {
        super(mainPanel);
    }

    @Override
    public Preferences getPreferences() {
        return Gdx.app.getPreferences("TiwanakuMenuPreferences");
    }

    public void writeLevel() {

        getPref().flush();
    }

    public void readLevel() {
    }

}
