package com.calcusourceupdater;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.inject.Inject;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashSet;

@Slf4j
@PluginDescriptor(
	name = "Calcusource Updater",
	description = "Automatically updates your stats on calcusource when you log out",
	tags = {"calcusource", "ccs", "tracker", "updater"},
	enabledByDefault = true
)
public class CalcusourceUpdaterPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private CalcusourceUpdaterConfig config;

	@Inject
	private OkHttpClient okHttpClient;

	private long lastAccount;
	private boolean fetchXp;
	private long lastXp;

	HashSet<WorldType> unsupportedWorldTypes;

	@Override
	protected void startUp() throws Exception
	{
		unsupportedWorldTypes = new HashSet<>();
		unsupportedWorldTypes.add(WorldType.SEASONAL);
		unsupportedWorldTypes.add(WorldType.DEADMAN);
		unsupportedWorldTypes.add(WorldType.NOSAVE_MODE);
		unsupportedWorldTypes.add(WorldType.FRESH_START_WORLD);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		GameState state = gameStateChanged.getGameState();

		if (state == GameState.LOGGED_IN)
		{
			if (lastAccount != client.getAccountHash())
			{
				lastAccount = client.getAccountHash();
				fetchXp = true;
			}
		}
		else if (state == GameState.LOGIN_SCREEN ||  state == GameState.HOPPING)
		{
			Player local = client.getLocalPlayer();

			if (local == null)
			{
				return;
			}

			long totalXp = client.getOverallExperience();

			int minimumUpdatableXP = config.minimumUpdatableXP();

			// Don't submit update unless xp threshold is reached
			if (Math.abs(totalXp - lastXp) > minimumUpdatableXP)
			{
				log.debug("Submitting update for {}", local.getName());
				update(local.getName());
				lastXp = totalXp;
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		if (fetchXp)
		{
			lastXp = client.getOverallExperience();
			fetchXp = false;
		}
	}

	private void update(String username)
	{
		EnumSet<WorldType> worldTypes = client.getWorldType();
		updateCalcusource(username, worldTypes);
	}

	private void updateCalcusource(String username, EnumSet<WorldType> worldTypes)
	{
		if (!worldTypes.contains(WorldType.SEASONAL)
				&& !worldTypes.contains(WorldType.DEADMAN)
				&& !worldTypes.contains(WorldType.NOSAVE_MODE)
				&& !worldTypes.contains(WorldType.FRESH_START_WORLD))
		{
			HttpUrl url = new HttpUrl.Builder()
					.scheme("https")
					.host("calcusource.com")
					.addPathSegment("tracker")
					.addPathSegment("update")
					.addQueryParameter("player", username)
					.build();

			Request request = new Request.Builder()
					.header("User-Agent", "RuneLite")
					.addHeader("RUNELITE_ACCOUNT_HASH", String.valueOf(client.getAccountHash()))
					.header("RUNELITE_ACCOUNT_HASH", String.valueOf(client.getAccountHash()))
					.url(url)
					.build();

			sendRequest("Calcusource", request);
		}
	}

	private void sendRequest(String platform, Request request)
	{
		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Error submitting {} update, caused by {}.", platform, e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				response.close();
			}
		});
	}

	@Provides
	CalcusourceUpdaterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CalcusourceUpdaterConfig.class);
	}
}
