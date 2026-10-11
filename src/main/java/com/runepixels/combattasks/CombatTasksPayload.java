package com.runepixels.combattasks;

import java.util.List;
import java.util.Map;
import lombok.Value;

@Value
class CombatTasksPayload
{
	long accountHash;
	Map<Integer, Integer> varps;
	List<Integer> thresholds;
}
