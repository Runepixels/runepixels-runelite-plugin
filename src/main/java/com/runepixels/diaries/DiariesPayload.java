package com.runepixels.diaries;

import java.util.Map;
import lombok.Value;

@Value
class DiariesPayload
{
	long accountHash;
	Map<Integer, Integer> varps;
	Map<Integer, Integer> varbits;
}
