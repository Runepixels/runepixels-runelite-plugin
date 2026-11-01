package com.runepixels.bosses;

import java.util.List;
import lombok.Value;

@Value
class BossKillsPayload
{
	long accountHash;
	List<Kill> kills;

	@Value
	static class Kill
	{
		String boss;
		int killCount;
		int timeTicks;
		Integer personalBestTicks;
	}
}
