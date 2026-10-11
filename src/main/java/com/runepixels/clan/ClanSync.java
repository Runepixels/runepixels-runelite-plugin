package com.runepixels.clan;

import com.runepixels.RunepixelsApi;
import com.runepixels.RunepixelsConfig;
import com.runepixels.Sync;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;
import net.runelite.client.util.Text;

public class ClanSync extends Sync
{
	@Inject
	private Client client;

	@Inject
	private RunepixelsApi api;

	@Inject
	private RunepixelsConfig config;

	private boolean manualRequested;

	public String requestManualSync()
	{
		if (!isEnabled())
		{
			return "Enable the Clan option in the Runepixels plugin settings first.";
		}

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return "Log in to sync your clan.";
		}

		ClanSettings settings = client.getClanSettings();
		Player player = client.getLocalPlayer();
		if (settings == null || player == null || player.getName() == null)
		{
			return "You are not in a clan.";
		}

		ClanMember self = settings.findMember(player.getName());
		if (self == null || self.getRank() == null || self.getRank().getRank() < ClanRank.DEPUTY_OWNER.getRank())
		{
			return "Only clan owners and deputy owners can sync the clan.";
		}

		manualRequested = true;
		request();
		return "Clan sync sent to Runepixels.";
	}

	@Override
	public boolean isEnabled()
	{
		return config.syncClan();
	}

	@Override
	public String getConfigKey()
	{
		return "syncClan";
	}

	@Override
	public String getPath()
	{
		return "clan";
	}

	@Override
	public String getDisplayName()
	{
		return "clan members";
	}

	@Override
	public void sync(long accountHash)
	{
		if (!manualRequested)
		{
			return;
		}

		manualRequested = false;

		ClanSettings settings = client.getClanSettings();
		if (settings == null || settings.getName() == null || settings.getMembers() == null)
		{
			return;
		}

		List<ClanPayload.Member> members = new ArrayList<>();
		for (ClanMember member : settings.getMembers())
		{
			if (member == null || member.getName() == null || member.getRank() == null || member.getRank().getRank() < 0)
			{
				continue;
			}

			members.add(new ClanPayload.Member(Text.sanitize(member.getName()), member.getRank().getRank()));
		}

		if (members.isEmpty())
		{
			return;
		}

		members.sort(Comparator.comparing(ClanPayload.Member::getName, String.CASE_INSENSITIVE_ORDER));
		api.post(getPath(), new ClanPayload(accountHash, Text.sanitize(settings.getName()), members));
	}

	@Override
	public void reset()
	{
		super.reset();
		manualRequested = false;
	}
}
