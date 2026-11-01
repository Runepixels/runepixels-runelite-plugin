package com.runepixels.clan;

import com.runepixels.RunepixelsApi;
import com.runepixels.RunepixelsConfig;
import com.runepixels.Sync;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

public class ClanSync extends Sync
{
	@Inject
	private Client client;

	@Inject
	private RunepixelsApi api;

	@Inject
	private RunepixelsConfig config;

	private ClanPayload lastSynced;

	@Subscribe
	public void onClanChannelChanged(ClanChannelChanged event)
	{
		if (!event.isGuest() && event.getClanChannel() != null)
		{
			request();
		}
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
		ClanSettings settings = client.getClanSettings();
		Player player = client.getLocalPlayer();
		if (settings == null || settings.getName() == null || settings.getMembers() == null || player == null || player.getName() == null)
		{
			return;
		}

		ClanMember self = settings.findMember(player.getName());
		if (self == null || self.getRank() == null || self.getRank().getRank() < ClanRank.DEPUTY_OWNER.getRank())
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
		ClanPayload payload = new ClanPayload(accountHash, Text.sanitize(settings.getName()), members);
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
