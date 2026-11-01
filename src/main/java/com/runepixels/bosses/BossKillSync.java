package com.runepixels.bosses;

import com.google.gson.Gson;
import com.runepixels.RunepixelsApi;
import com.runepixels.RunepixelsConfig;
import com.runepixels.Sync;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.util.Text;
import net.runelite.http.api.loottracker.LootRecordType;

@Slf4j
public class BossKillSync extends Sync
{
	static final String STORE_GROUP = "runepixelstimers";
	private static final String KILLS_KEY = "kills";
	private static final String SHARED_KEY = "shared";
	private static final String DURATION_LABEL = "(?:(?:Fight |Lap |Challenge |Corrupted challenge )?duration:|Subdued in|(?<!total )completion time:)";
	private static final String TIME = "([0-9]+(?::[0-9]+)+(?:\\.[0-9]+)?)";
	private static final Pattern KILL_COUNT_PATTERN = Pattern.compile(
		"Your (?:completion count for |subdued |completed )?(.+?) (?:(?:kill|harvest|lap|completion) )?(?:count )?is: ([0-9,]+)");
	private static final Pattern NEW_PB_PATTERN = Pattern.compile(
		"(?i)" + DURATION_LABEL + " " + TIME + " \\(new personal best\\)");
	private static final Pattern DURATION_PATTERN = Pattern.compile(
		"(?i)" + DURATION_LABEL + " " + TIME + "\\. Personal best: " + TIME);
	private static final int MAX_TICK_GAP = 1;
	private static final int DROP_WINDOW_TICKS = 15;
	private static final int MAX_KILLS_PER_BOSS = 100;
	private static final int MAX_ATTEMPTS = 5;
	private static final int RETRY_TICKS = 50;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private RunepixelsApi api;

	@Inject
	private RunepixelsConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private Gson gson;

	private final List<BossKill> kills = new ArrayList<>();
	private final Deque<Outgoing> outbox = new ArrayDeque<>();
	private long loadedAccount = -1;
	private boolean shared;
	private boolean sharePending;
	private boolean sending;
	private int nextAttemptTick;
	private BossKill lastKill;
	private int lastKillTick;
	private String lastBoss;
	private int lastKillCount;
	private int lastBossTick = -1;
	private int lastTimeTicks = -1;
	private int lastTimeTick = -1;
	private int lastPersonalBestTicks = -1;
	private Consumer<String> onChange;

	@Subscribe
	public void onGameTick(GameTick event)
	{
		long accountHash = client.getAccountHash();
		if (accountHash != -1 && accountHash != loadedAccount)
		{
			load(accountHash);
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!isEnabled() || loadedAccount == -1 || (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM))
		{
			return;
		}

		String message = Text.removeTags(event.getMessage().replace("<br>", " "));
		int tick = client.getTickCount();

		Matcher killCount = KILL_COUNT_PATTERN.matcher(message);
		if (killCount.find())
		{
			lastBoss = killCount.group(1).trim();
			lastKillCount = Integer.parseInt(killCount.group(2).replace(",", ""));
			lastBossTick = tick;
			match(tick);
			return;
		}

		Matcher duration = NEW_PB_PATTERN.matcher(message);
		if (duration.find())
		{
			lastTimeTicks = toTicks(duration.group(1));
			lastPersonalBestTicks = lastTimeTicks;
		}
		else
		{
			duration = DURATION_PATTERN.matcher(message);
			if (!duration.find())
			{
				return;
			}
			lastTimeTicks = toTicks(duration.group(1));
			lastPersonalBestTicks = toTicks(duration.group(2));
		}

		lastTimeTick = tick;
		match(tick);
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		addDrops(event.getItems());
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (event.getType() != LootRecordType.NPC && event.getType() != LootRecordType.PLAYER)
		{
			addDrops(event.getItems());
		}
	}

	@Override
	public boolean isEnabled()
	{
		return config.syncBossKills();
	}

	@Override
	public String getConfigKey()
	{
		return "syncBossKills";
	}

	@Override
	public String getPath()
	{
		return "boss-kills";
	}

	@Override
	public String getDisplayName()
	{
		return "boss kill times";
	}

	@Override
	public void sync(long accountHash)
	{
		if (sending || outbox.isEmpty())
		{
			return;
		}

		if (client.getTickCount() < nextAttemptTick)
		{
			request();
			return;
		}

		Outgoing item = outbox.peek();
		sending = true;
		api.post(api.runeliteUrl(item.path), item.payload, null,
			result -> clientThread.invoke(() -> finish(item, true)),
			error -> clientThread.invoke(() ->
			{
				log.debug("Runepixels {} failed: {}", item.path, error);
				finish(item, ++item.attempts >= item.maxAttempts);
			}));
	}

	@Override
	public void reset()
	{
		super.reset();
		sending = false;
		clearLast();
	}

	public void accountLinked()
	{
		if (isEnabled())
		{
			enqueue("boss-kills/share", Map.of("accountHash", client.getAccountHash(), "shared", isShared()), 1, null);
		}
	}

	public void setOnChange(Consumer<String> onChange)
	{
		this.onChange = onChange;
	}

	public synchronized boolean isShared()
	{
		return shared;
	}

	public synchronized boolean isSharePending()
	{
		return sharePending;
	}

	public synchronized Map<String, Integer> timedBosses()
	{
		Map<String, Integer> bosses = new LinkedHashMap<>();
		for (BossKill kill : kills)
		{
			bosses.merge(kill.getBoss(), 1, Integer::sum);
		}
		return bosses;
	}

	public synchronized List<BossKill> killsFor(String boss)
	{
		List<BossKill> result = new ArrayList<>();
		for (BossKill kill : kills)
		{
			if (kill.getBoss().equals(boss))
			{
				result.add(kill);
			}
		}
		return result;
	}

	public void remove(BossKill kill)
	{
		clientThread.invoke(() ->
		{
			synchronized (this)
			{
				if (!kills.remove(kill))
				{
					return;
				}
				if (lastKill == kill)
				{
					lastKill = null;
				}
			}
			save();
			enqueue("boss-kills/delete", Map.of("accountHash", client.getAccountHash(), "boss", kill.getBoss(), "killCount", kill.getKillCount()), MAX_ATTEMPTS, null);
			notifyChange(null);
		});
	}

	public void clear(String boss)
	{
		clientThread.invoke(() ->
		{
			boolean keepBest;
			synchronized (this)
			{
				keepBest = shared;
				BossKill best = null;
				for (BossKill kill : kills)
				{
					if (kill.getBoss().equals(boss) && (best == null || kill.getTimeTicks() < best.getTimeTicks()))
					{
						best = kill;
					}
				}

				BossKill keep = keepBest ? best : null;
				kills.removeIf(kill -> kill.getBoss().equals(boss) && kill != keep);
				if (lastKill != null && lastKill != keep && lastKill.getBoss().equals(boss))
				{
					lastKill = null;
				}
			}
			save();
			enqueue("boss-kills/clear", Map.of("accountHash", client.getAccountHash(), "boss", boss, "keepBest", keepBest), MAX_ATTEMPTS, null);
			notifyChange(null);
		});
	}

	public void toggleShare()
	{
		clientThread.invoke(() ->
		{
			boolean wanted;
			synchronized (this)
			{
				if (sharePending || loadedAccount == -1)
				{
					return;
				}
				wanted = !shared;
				sharePending = true;
			}

			enqueue("boss-kills/share", Map.of("accountHash", client.getAccountHash(), "shared", wanted), 1, ok ->
			{
				synchronized (this)
				{
					sharePending = false;
					if (ok)
					{
						shared = wanted;
					}
				}
				if (ok)
				{
					configManager.setRSProfileConfiguration(STORE_GROUP, SHARED_KEY, wanted);
				}
				notifyChange(null);
			});
			notifyChange(null);
		});
	}

	private void match(int tick)
	{
		if (lastBoss == null || lastTimeTicks <= 0
			|| tick - lastBossTick > MAX_TICK_GAP || tick - lastTimeTick > MAX_TICK_GAP)
		{
			return;
		}

		BossKill kill = new BossKill();
		kill.setBoss(lastBoss);
		kill.setKillCount(lastKillCount);
		kill.setTimeTicks(lastTimeTicks);
		kill.setDate(System.currentTimeMillis());
		Integer personalBest = lastPersonalBestTicks > 0 ? lastPersonalBestTicks : null;
		clearLast();
		store(kill);
		lastKill = kill;
		lastKillTick = tick;

		BossKillsPayload.Kill sent = new BossKillsPayload.Kill(kill.getBoss(), kill.getKillCount(), kill.getTimeTicks(), personalBest);
		enqueue(getPath(), new BossKillsPayload(client.getAccountHash(), List.of(sent)), MAX_ATTEMPTS, null);
		notifyChange(kill.getBoss());
	}

	private void store(BossKill kill)
	{
		synchronized (this)
		{
			kills.add(0, kill);
			int count = 0;
			for (int i = 0; i < kills.size(); i++)
			{
				if (kills.get(i).getBoss().equals(kill.getBoss()) && ++count > MAX_KILLS_PER_BOSS)
				{
					kills.remove(i--);
				}
			}
		}
		save();
	}

	private void addDrops(Collection<ItemStack> items)
	{
		if (lastKill == null || items == null || items.isEmpty() || client.getTickCount() - lastKillTick > DROP_WINDOW_TICKS)
		{
			return;
		}

		synchronized (this)
		{
			for (ItemStack item : items)
			{
				String name = itemManager.getItemComposition(item.getId()).getName();
				lastKill.getDrops().add(item.getQuantity() > 1 ? item.getQuantity() + "x " + name : name);
			}
		}
		save();
		notifyChange(null);
	}

	private void enqueue(String path, Object payload, int maxAttempts, Consumer<Boolean> onDone)
	{
		outbox.add(new Outgoing(path, payload, maxAttempts, onDone));
		request();
	}

	private void finish(Outgoing item, boolean done)
	{
		sending = false;
		if (done)
		{
			outbox.remove(item);
			if (item.onDone != null)
			{
				item.onDone.accept(item.attempts < item.maxAttempts);
			}
			nextAttemptTick = 0;
		}
		else
		{
			nextAttemptTick = client.getTickCount() + RETRY_TICKS * item.attempts;
		}

		if (!outbox.isEmpty())
		{
			request();
		}
	}

	private void load(long accountHash)
	{
		String json = configManager.getRSProfileConfiguration(STORE_GROUP, KILLS_KEY);
		Boolean savedShare = configManager.getRSProfileConfiguration(STORE_GROUP, SHARED_KEY, Boolean.class);
		BossKill[] saved = null;
		try
		{
			saved = json == null ? null : gson.fromJson(json, BossKill[].class);
		}
		catch (RuntimeException e)
		{
			log.debug("Unable to read saved boss kills", e);
		}

		synchronized (this)
		{
			kills.clear();
			if (saved != null)
			{
				kills.addAll(Arrays.asList(saved));
			}
			shared = Boolean.TRUE.equals(savedShare);
			sharePending = false;
			loadedAccount = accountHash;
		}
		lastKill = null;
		notifyChange(null);
	}

	private void save()
	{
		String json;
		synchronized (this)
		{
			json = gson.toJson(kills);
		}
		configManager.setRSProfileConfiguration(STORE_GROUP, KILLS_KEY, json);
	}

	private void notifyChange(String killedBoss)
	{
		Consumer<String> listener = onChange;
		if (listener != null)
		{
			listener.accept(killedBoss);
		}
	}

	private void clearLast()
	{
		lastBoss = null;
		lastKillCount = 0;
		lastBossTick = -1;
		lastTimeTicks = -1;
		lastTimeTick = -1;
		lastPersonalBestTicks = -1;
	}

	private static int toTicks(String time)
	{
		String[] parts = time.split(":");
		double seconds = 0;
		for (String part : parts)
		{
			seconds = seconds * 60 + Double.parseDouble(part);
		}
		return (int) Math.round(seconds / 0.6);
	}

	private static class Outgoing
	{
		private final String path;
		private final Object payload;
		private final int maxAttempts;
		private final Consumer<Boolean> onDone;
		private int attempts;

		Outgoing(String path, Object payload, int maxAttempts, Consumer<Boolean> onDone)
		{
			this.path = path;
			this.payload = payload;
			this.maxAttempts = maxAttempts;
			this.onDone = onDone;
		}
	}
}
