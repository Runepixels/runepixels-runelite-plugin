package com.runepixels;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(RunepixelsConfig.GROUP)
public interface RunepixelsConfig extends Config
{
	String GROUP = "runepixels";

	@ConfigSection(
		name = "Sync",
		description = "Choose what is sent to Runepixels",
		position = 0
	)
	String syncSection = "sync";

	@ConfigItem(
		keyName = "syncQuests",
		name = "Quests",
		description = "Send your quest progress to Runepixels",
		section = syncSection,
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		position = 0
	)
	default boolean syncQuests()
	{
		return false;
	}

	@ConfigItem(
		keyName = "syncDiaries",
		name = "Achievement diaries",
		description = "Send your achievement diary progress to Runepixels",
		section = syncSection,
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		position = 1
	)
	default boolean syncDiaries()
	{
		return false;
	}

	@ConfigItem(
		keyName = "syncCombatTasks",
		name = "Combat achievements",
		description = "Send your combat achievement progress to Runepixels",
		section = syncSection,
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		position = 2
	)
	default boolean syncCombatTasks()
	{
		return false;
	}

	@ConfigItem(
		keyName = "syncCollectionLog",
		name = "Collection log",
		description = "Send your collection log to Runepixels. Open the collection log and click Search to sync all items; "
			+ "collection log drops are recorded as you receive them",
		section = syncSection,
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		position = 3
	)
	default boolean syncCollectionLog()
	{
		return false;
	}

	@ConfigItem(
		keyName = "syncClan",
		name = "Clan",
		description = "Allow clan owners and deputy owners to send the clan name, members and ranks to Runepixels from the Runepixels panel",
		section = syncSection,
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		position = 4
	)
	default boolean syncClan()
	{
		return false;
	}

	@ConfigItem(
		keyName = "syncBossKills",
		name = "Boss kill times",
		description = "Send your boss kill times to Runepixels. Kills are recorded as you complete them, "
			+ "only when the game shows a fight duration",
		section = syncSection,
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		position = 5
	)
	default boolean syncBossKills()
	{
		return false;
	}

	@ConfigSection(
		name = "Tools",
		description = "Lookups from the Runepixels panel",
		position = 1
	)
	String toolsSection = "tools";

	@ConfigItem(
		keyName = "lookupPlayers",
		name = "Player lookup",
		description = "Adds Runepixels lookup to the right-click menu of players and enables the Lookup tab. "
			+ "The looked-up name is sent to Runepixels. Hidden in the Wilderness and on PvP worlds",
		section = toolsSection,
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		position = 0
	)
	default boolean lookupPlayers()
	{
		return false;
	}
}
