package com.apogames.settler.backend;

import com.badlogic.gdx.Preferences;

/**
 * The type Game properties.
 */
public abstract class GameProperties {

	private final String LEVEL_SIZE = "size";

	private Preferences pref;

	private final SequentiallyThinkingScreenModel mainPanel;

	public GameProperties(final SequentiallyThinkingScreenModel mainPanel) {
		this.mainPanel = mainPanel;

		this.pref = getPreferences();
	}

	public SequentiallyThinkingScreenModel getMainPanel() {
		return mainPanel;
	}

	public Preferences getPref() {
		return pref;
	}

	public abstract Preferences getPreferences();

	public void writeLevel() {
		int size = getMainPanel().getSolvedLevels().size();
		getPref().putInteger(LEVEL_SIZE, size);
		for (int i = 0; i < size; i++) {
			getPref().putString(String.valueOf(i), getMainPanel().getSolvedLevels().get(i));
		}
		getPref().flush();
	}

	public void readLevel() {
		int size = getPref().getInteger(LEVEL_SIZE);
		for (int i = 0; i < size; i++) {
			getMainPanel().getSolvedLevels().add(getPref().getString(String.valueOf(i)));
		}
	}
}
