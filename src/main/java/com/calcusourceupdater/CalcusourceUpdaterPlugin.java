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

	private long lastAccount = -1L;
	private String lastDisplayName;
	private boolean fetchXp = true;
	private long lastXp;
	private static final long MAX_XP = 4_800_000_000L;

	HashSet<WorldType> unsupportedWorldTypes;

	@Override
	protected void startUp() throws Exception
	{
		unsupportedWorldTypes = new HashSet<>();
		unsupportedWorldTypes.add(WorldType.SEASONAL);
		unsupportedWorldTypes.add(WorldType.DEADMAN);
		unsupportedWorldTypes.add(WorldType.NOSAVE_MODE);
		unsupportedWorldTypes.add(WorldType.FRESH_START_WORLD);

		fetchXp = true;
		lastAccount = -1L;
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
			return;
		}

		if (state != GameState.LOGIN_SCREEN && state != GameState.HOPPING)
		{
			return;
		}

		long totalXp = client.getOverallExperience();

		// Must have captured name previously (like official plugin behavior)
		if (lastDisplayName == null)
		{
			return;
		}

		boolean maxXp = totalXp >= MAX_XP;
		boolean jacobs = "jacobs".equalsIgnoreCase(lastDisplayName);
		boolean gainedXp = Math.abs(totalXp - lastXp) >= config.minimumUpdatableXP();

		if (maxXp || jacobs || gainedXp)
		{
			if (maxXp)
			{
				log.debug("Submitting MAX XP update for {}", lastDisplayName);
			}
			else if (jacobs)
			{
				log.debug("Submitting JACOBS override update for {}", lastDisplayName);
			}
			else
			{
				log.debug("Submitting XP delta update for {}", lastDisplayName);
			}

			update(lastDisplayName);
			lastXp = totalXp;
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		if (fetchXp)
		{
			lastXp = client.getOverallExperience();

			Player local = client.getLocalPlayer();
			if (local != null)
			{
				lastDisplayName = local.getName();
			}

			fetchXp = false;
		}
	}

	private void update(String username)
	{
		updateCalcusource(username, client.getWorldType());
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
