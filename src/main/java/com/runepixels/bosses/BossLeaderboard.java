package com.runepixels.bosses;

import java.util.List;
import lombok.Data;

@Data
class BossLeaderboard
{
	private List<String> bosses;
	private List<Entry> entries;

	@Data
	static class Entry
	{
		private String name;
		private int killCount;
		private int timeTicks;
		private String date;
	}
}
