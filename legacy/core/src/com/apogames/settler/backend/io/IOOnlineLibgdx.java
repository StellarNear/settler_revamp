package com.apogames.settler.backend.io;

import com.apogames.settler.Constants;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net.HttpMethods;
import com.badlogic.gdx.Net.HttpRequest;
import com.badlogic.gdx.Net.HttpResponse;
import com.badlogic.gdx.Net.HttpResponseListener;
import com.badlogic.gdx.net.HttpRequestBuilder;

/**
 * The type Io online libgdx.
 */
public class IOOnlineLibgdx {

	public IOOnlineLibgdx() {
	}

	public String save(final String email, final String solution) {
		return "";
	}

	public boolean load() {
		try {
			HttpRequestBuilder requestBuilder = new HttpRequestBuilder();
			HttpRequest httpRequest = requestBuilder.newRequest().method(HttpMethods.GET).url(Constants.USERLEVELS_GETPHP).build();

			Gdx.net.sendHttpRequest(httpRequest, new HttpResponseListener() {
				@Override
				public void handleHttpResponse(HttpResponse httpResponse) {
					String resultAsString = httpResponse.getResultAsString();
					String[] split = resultAsString.split("\n", -1);

				}

				@Override
				public void failed(Throwable t) {
					Gdx.app.log("Failed ", t.getMessage());
				}

				@Override
				public void cancelled() {
					Gdx.app.log("Cancelled", "Load cancelled");
				}
			});

			return true;
		} catch (Exception me) {
			System.err.println("Exception: " + me);
		}
		return false;
	}

}