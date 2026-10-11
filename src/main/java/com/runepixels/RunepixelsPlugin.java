package com.runepixels;

import com.google.inject.Provides;
import com.runepixels.clan.ClanSync;
import com.runepixels.collectionlog.CollectionLogSync;
import com.runepixels.combattasks.CombatTaskSync;
import com.runepixels.diaries.DiarySync;
import com.runepixels.quests.QuestSync;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Runepixels"
)
public class RunepixelsPlugin extends Plugin
{
	private static final EnumSet<WorldType> UNTRACKED_WORLDS = EnumSet.of(
		WorldType.SEASONAL,
		WorldType.DEADMAN,
		WorldType.BETA_WORLD,
		WorldType.TOURNAMENT_WORLD,
		WorldType.NOSAVE_MODE,
		WorldType.FRESH_START_WORLD,
		WorldType.QUEST_SPEEDRUNNING,
		WorldType.LAST_MAN_STANDING
	);

	@Inject
	private Client client;

	@Inject
	private EventBus eventBus;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private RunepixelsApi api;

	@Inject
	private QuestSync questSync;

	@Inject
	private DiarySync diarySync;

	@Inject
	private CombatTaskSync combatTaskSync;

	@Inject
	private CollectionLogSync collectionLogSync;

	@Inject
	private ClanSync clanSync;

	@Inject
	private ClientToolbar clientToolbar;

	private List<Sync> syncs;
	private NavigationButton navigationButton;
	private boolean pendingLogin;
	private long lastSyncedAccountHash = -1;
	private final Set<String> revertingKeys = ConcurrentHashMap.newKeySet();

	@Override
	protected void startUp() throws Exception
	{
		syncs = List.of(questSync, diarySync, combatTaskSync, collectionLogSync, clanSync);
		syncs.forEach(eventBus::register);
		pendingLogin = client.getGameState() == GameState.LOGGED_IN;

		navigationButton = NavigationButton.builder()
			.tooltip("Runepixels")
			.icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
			.priority(10)
			.panel(new RunepixelsPanel(clientThread, clanSync))
			.build();
		clientToolbar.addNavigation(navigationButton);
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navigationButton);
		syncs.forEach(eventBus::unregister);
		pendingLogin = false;
		resetSyncs();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGGED_IN:
				pendingLogin = true;
				break;
			case LOGIN_SCREEN:
				resetSyncs();
				break;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals(RunepixelsConfig.GROUP))
		{
			return;
		}

		if (!revertingKeys.remove(event.getKey()))
		{
			for (Sync sync : syncs)
			{
				if (!sync.getConfigKey().equals(event.getKey()))
				{
					continue;
				}

				if ("true".equals(event.getNewValue()))
				{
					confirmEnable(sync);
				}
				else if ("true".equals(event.getOldValue()))
				{
					confirmDisable(sync);
				}
			}
		}

		resetSyncs();
		pendingLogin = client.getGameState() == GameState.LOGGED_IN;
	}

	private void confirmEnable(Sync sync)
	{
		SwingUtilities.invokeLater(() ->
		{
			int result = JOptionPane.showOptionDialog(null,
				"Your " + sync.getDisplayName() + " will be sent to Runepixels and will be visible to other Runepixels users.",
				"Runepixels", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
				null, new String[]{"Enable", "Cancel"}, "Cancel");

			if (result != 0)
			{
				revert(sync, false);
			}
		});
	}

	private void confirmDisable(Sync sync)
	{
		SwingUtilities.invokeLater(() ->
		{
			int result = JOptionPane.showOptionDialog(null,
				"Your " + sync.getDisplayName() + " will no longer be sent to Runepixels.",
				"Runepixels", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
				null, new String[]{"Disable", "Cancel"}, "Disable");

			if (result != 0)
			{
				revert(sync, true);
			}
		});
	}

	private void revert(Sync sync, boolean enabled)
	{
		revertingKeys.add(sync.getConfigKey());
		configManager.setConfiguration(RunepixelsConfig.GROUP, sync.getConfigKey(), enabled);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!anySyncEnabled())
		{
			return;
		}

		if (pendingLogin)
		{
			Player player = client.getLocalPlayer();
			if (player == null || player.getName() == null)
			{
				return;
			}

			pendingLogin = false;
			syncAccount(player.getName());
		}

		if (lastSyncedAccountHash != client.getAccountHash())
		{
			return;
		}

		for (Sync sync : syncs)
		{
			if (sync.consumeRequest() && sync.isEnabled() && isTrackable())
			{
				sync.sync(client.getAccountHash());
			}
		}
	}

	private void syncAccount(String name)
	{
		long accountHash = client.getAccountHash();
		if (accountHash == lastSyncedAccountHash)
		{
			syncs.forEach(Sync::request);
			return;
		}

		if (!isTrackable())
		{
			return;
		}

		api.post("account", new AccountPayload(accountHash, Text.sanitize(name)), () -> clientThread.invoke(() ->
		{
			if (client.getAccountHash() == accountHash)
			{
				lastSyncedAccountHash = accountHash;
				syncs.forEach(Sync::request);
			}
		}));
	}

	private boolean anySyncEnabled()
	{
		for (Sync sync : syncs)
		{
			if (sync.isEnabled())
			{
				return true;
			}
		}
		return false;
	}

	private void resetSyncs()
	{
		lastSyncedAccountHash = -1;
		syncs.forEach(Sync::reset);
	}

	private boolean isTrackable()
	{
		if (client.getAccountHash() == -1)
		{
			return false;
		}

		for (WorldType type : client.getWorldType())
		{
			if (UNTRACKED_WORLDS.contains(type))
			{
				return false;
			}
		}
		return true;
	}

	@Provides
	RunepixelsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RunepixelsConfig.class);
	}
}
