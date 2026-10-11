package com.runepixels.quests;

import java.util.List;
import lombok.Value;

@Value
class QuestsPayload
{
	long accountHash;
	List<Entry> quests;

	@Value
	static class Entry
	{
		String name;
		int status;
		int type;
		int difficulty;
		boolean members;
		int questPoints;
	}
}
