package com.apogames.settler.game.tiwanaku;

import com.apogames.settler.backend.GameProperties;
import com.apogames.settler.backend.SequentiallyThinkingScreenModel;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

public class TiwanakuPreferences extends GameProperties {

    public TiwanakuPreferences(SequentiallyThinkingScreenModel mainPanel) {
        super(mainPanel);
    }

    @Override
    public Preferences getPreferences() {
        return Gdx.app.getPreferences("TiwanakuGamePreferences");
    }

    public void writeLevel() {

        getPref().flush();
    }

    public void readLevel() {
    }

}
