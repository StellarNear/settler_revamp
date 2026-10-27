package com.apogames.settler.teavm;

import com.apogames.settler.Constants;
import com.apogames.settler.Settler;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplication;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration;
import org.teavm.jso.JSBody;

public class TeaVMLauncher {

    @JSBody(script = "return /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini|Mobile/i.test(navigator.userAgent);")
    public static native boolean isMobileBrowser();

    @JSBody(script = "if (window.hideLoadingScreen) { window.hideLoadingScreen(); }")
    public static native void hideLoadingScreen();

    public static void main(String[] args) {
        Constants.IS_HTML = true;
        Constants.IS_MOBILE = isMobileBrowser();
        WebApplicationConfiguration config = new WebApplicationConfiguration();
        config.width = 0;
        config.height = 0;
        config.antialiasing = true;
        config.showDownloadLogs = true;
        config.preloadListener = assetLoader -> {
            assetLoader.loadScript("freetype.js");
        };
        // Set callback to hide the loading screen once the game is ready
        Constants.onGameStart = () -> {
            hideLoadingScreen();
        };
        new WebApplication(new Settler(), config);
    }
}
