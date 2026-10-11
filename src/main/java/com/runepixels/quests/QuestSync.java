package com.runepixels.quests;

import com.runepixels.RunepixelsApi;
import com.runepixels.RunepixelsConfig;
import com.runepixels.Sync;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.eventbus.Subscribe;

public class QuestSync extends Sync
{
	private static final int CHECK_TICKS = 500;

	@Inject
	private Client client;

	@Inject
	private RunepixelsApi api;

	@Inject
	private RunepixelsConfig config;

	private List<QuestsPayload.Entry> lastSynced;
	private int ticksUntilCheck = CHECK_TICKS;

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (--ticksUntilCheck <= 0)
		{
			ticksUntilCheck = CHECK_TICKS;
			request();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.QUESTSCROLL)
		{
			request();
		}
	}

	@Override
	public boolean isEnabled()
	{
		return config.syncQuests();
	}

	@Override
	public String getConfigKey()
	{
		return "syncQuests";
	}

	@Override
	public String getPath()
	{
		return "quests";
	}

	@Override
	public String getDisplayName()
	{
		return "quest progress";
	}

	@Override
	public void sync(long accountHash)
	{
		List<QuestsPayload.Entry> quests = new ArrayList<>();
		for (Quest quest : Quest.values())
		{
			int row = quest.getId();
			quests.add(new QuestsPayload.Entry(
				quest.getName(),
				QuestStatus.of(quest.getState(client)),
				intField(row, DBTableID.Quest.COL_TYPE),
				intField(row, DBTableID.Quest.COL_DIFFICULTY),
				intField(row, DBTableID.Quest.COL_MEMBERS) != 0,
				intField(row, DBTableID.Quest.COL_QUESTPOINTS)
			));
		}

		if (quests.equals(lastSynced))
		{
			return;
		}

		lastSynced = quests;
		api.post(getPath(), new QuestsPayload(accountHash, quests));
	}

	@Override
	public void reset()
	{
		super.reset();
		lastSynced = null;
		ticksUntilCheck = CHECK_TICKS;
	}

	private int intField(int row, int column)
	{
		Object[] values = client.getDBTableField(row, column, 0);
		if (values == null || values.length == 0 || values[0] == null)
		{
			return 0;
		}

		Object value = values[0];
		if (value instanceof Boolean)
		{
			return (Boolean) value ? 1 : 0;
		}
		return ((Number) value).intValue();
	}
}
