package com.apogames.settler.game.algorithm;

import com.apogames.settler.backend.GameProperties;
import com.apogames.settler.backend.SequentiallyThinkingScreenModel;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

public class AlgorithmPreferences extends GameProperties {

    public AlgorithmPreferences(SequentiallyThinkingScreenModel mainPanel) {
        super(mainPanel);
    }

    @Override
    public Preferences getPreferences() {
        return Gdx.app.getPreferences("TiwanakuAlgorithmMenuPreferences");
    }

    public void writeLevel() {

        getPref().flush();
    }

    public void readLevel() {
    }

}
