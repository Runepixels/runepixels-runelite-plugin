package com.runepixels;

import com.google.gson.Gson;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
@Singleton
public class RunepixelsApi
{
	private static final HttpUrl API_URL = HttpUrl.get(System.getProperty("runepixels.api", "https://api.runepixels.com"));
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	public void post(String path, Object body)
	{
		send("POST", path, body, null);
	}

	public void post(String path, Object body, Runnable onSuccess)
	{
		send("POST", path, body, onSuccess);
	}

	private void send(String method, String path, Object body, Runnable onSuccess)
	{
		Request request = new Request.Builder()
			.url(API_URL.newBuilder().addPathSegments("api/v1/oldschool/runelite").addPathSegment(path).build())
			.method(method, RequestBody.create(JSON, gson.toJson(body)))
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Failed to sync {} with Runepixels", path, e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (response)
				{
					if (!response.isSuccessful())
					{
						log.debug("Runepixels {} sync returned {}", path, response.code());
					}
					else if (onSuccess != null)
					{
						onSuccess.run();
					}
				}
			}
		});
	}
}
