package com.runepixels;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.function.Consumer;
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
import okhttp3.ResponseBody;

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
		post(path, body, null);
	}

	public void post(String path, Object body, Runnable onSuccess)
	{
		send(new Request.Builder()
			.url(runeliteUrl(path))
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build(), null, result -> run(onSuccess), error -> log.debug("Failed to sync {} with Runepixels: {}", path, error));
	}

	public <T> void get(HttpUrl url, Class<T> type, Consumer<T> onSuccess, Consumer<String> onError)
	{
		send(new Request.Builder().url(url).get().build(), type, onSuccess, onError);
	}

	public <T> void post(HttpUrl url, Object body, Class<T> type, Consumer<T> onSuccess, Consumer<String> onError)
	{
		send(new Request.Builder().url(url).post(RequestBody.create(JSON, gson.toJson(body))).build(), type, onSuccess, onError);
	}

	public HttpUrl runeliteUrl(String path)
	{
		return API_URL.newBuilder().addPathSegments("api/v1/oldschool/runelite").addPathSegments(path).build();
	}

	public HttpUrl.Builder playersUrl()
	{
		return API_URL.newBuilder().addPathSegments("api/v1/oldschool/players");
	}

	private <T> void send(Request request, Class<T> type, Consumer<T> onSuccess, Consumer<String> onError)
	{
		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Runepixels request {} failed", request.url(), e);
				onError.accept("Could not reach Runepixels.");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (response)
				{
					ResponseBody body = response.body();
					String text = body == null ? "" : body.string();
					if (!response.isSuccessful())
					{
						log.debug("Runepixels request {} returned {}", request.url(), response.code());
						onError.accept(errorMessage(text, response.code()));
						return;
					}

					onSuccess.accept(type == null || text.isEmpty() ? null : gson.fromJson(text, type));
				}
				catch (IOException | RuntimeException e)
				{
					log.debug("Runepixels request {} could not be read", request.url(), e);
					onError.accept("Invalid response from Runepixels.");
				}
			}
		});
	}

	private String errorMessage(String text, int code)
	{
		try
		{
			JsonObject json = gson.fromJson(text, JsonObject.class);
			if (json != null && json.has("message"))
			{
				return json.get("message").getAsString();
			}
		}
		catch (RuntimeException ignored)
		{
		}
		return "Runepixels returned " + code + ".";
	}

	private static void run(Runnable runnable)
	{
		if (runnable != null)
		{
			runnable.run();
		}
	}
}
