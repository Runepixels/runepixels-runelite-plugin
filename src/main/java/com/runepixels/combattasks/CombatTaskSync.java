package com.runepixels.combattasks;

import com.runepixels.RunepixelsApi;
import com.runepixels.RunepixelsConfig;
import com.runepixels.Sync;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;

public class CombatTaskSync extends Sync
{
	private static final int[] TASK_VARPS = {
		VarPlayerID.CA_TASK_COMPLETED_0, VarPlayerID.CA_TASK_COMPLETED_1, VarPlayerID.CA_TASK_COMPLETED_2,
		VarPlayerID.CA_TASK_COMPLETED_3, VarPlayerID.CA_TASK_COMPLETED_4, VarPlayerID.CA_TASK_COMPLETED_5,
		VarPlayerID.CA_TASK_COMPLETED_6, VarPlayerID.CA_TASK_COMPLETED_7, VarPlayerID.CA_TASK_COMPLETED_8,
		VarPlayerID.CA_TASK_COMPLETED_9, VarPlayerID.CA_TASK_COMPLETED_10, VarPlayerID.CA_TASK_COMPLETED_11,
		VarPlayerID.CA_TASK_COMPLETED_12, VarPlayerID.CA_TASK_COMPLETED_13, VarPlayerID.CA_TASK_COMPLETED_14,
		VarPlayerID.CA_TASK_COMPLETED_15, VarPlayerID.CA_TASK_COMPLETED_16, VarPlayerID.CA_TASK_COMPLETED_17,
		VarPlayerID.CA_TASK_COMPLETED_18, VarPlayerID.CA_TASK_COMPLETED_19, VarPlayerID.CA_TASK_COMPLETED_20,
	};

	private static final int[] TIER_THRESHOLD_VARBITS = {
		VarbitID.CA_THRESHOLD_EASY, VarbitID.CA_THRESHOLD_MEDIUM, VarbitID.CA_THRESHOLD_HARD,
		VarbitID.CA_THRESHOLD_ELITE, VarbitID.CA_THRESHOLD_MASTER, VarbitID.CA_THRESHOLD_GRANDMASTER,
	};

	private static final Set<Integer> VARPS = new HashSet<>();

	static
	{
		for (int varp : TASK_VARPS)
		{
			VARPS.add(varp);
		}
	}

	@Inject
	private Client client;

	@Inject
	private RunepixelsApi api;

	@Inject
	private RunepixelsConfig config;

	private CombatTasksPayload lastSynced;

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (VARPS.contains(event.getVarpId()))
		{
			request();
		}
	}

	@Override
	public boolean isEnabled()
	{
		return config.syncCombatTasks();
	}

	@Override
	public String getConfigKey()
	{
		return "syncCombatTasks";
	}

	@Override
	public String getPath()
	{
		return "combat-tasks";
	}

	@Override
	public String getDisplayName()
	{
		return "combat achievement progress";
	}

	@Override
	public void sync(long accountHash)
	{
		Map<Integer, Integer> varps = new TreeMap<>();
		for (int varp : TASK_VARPS)
		{
			varps.put(varp, client.getVarpValue(varp));
		}

		List<Integer> thresholds = new ArrayList<>();
		for (int varbit : TIER_THRESHOLD_VARBITS)
		{
			thresholds.add(client.getVarbitValue(varbit));
		}

		CombatTasksPayload payload = new CombatTasksPayload(accountHash, varps, thresholds);
		if (payload.equals(lastSynced))
		{
			return;
		}

		lastSynced = payload;
		api.post(getPath(), payload);
	}

	@Override
	public void reset()
	{
		super.reset();
		lastSynced = null;
	}
}
