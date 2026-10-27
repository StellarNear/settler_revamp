package com.apogames.settler;

import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.apogames.settler.Settler;

public class AndroidLauncher extends AndroidApplication {
	@Override
	protected void onCreate (Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Constants.IS_ANDROID = true;
		Constants.IS_MOBILE = true;
		AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
		config.useWakelock = true;
		initialize(new Settler(), config);
	}
}
