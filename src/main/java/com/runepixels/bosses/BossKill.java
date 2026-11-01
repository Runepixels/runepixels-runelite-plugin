package com.runepixels.bosses;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class BossKill
{
	private String boss;
	private int killCount;
	private int timeTicks;
	private long date;
	private List<String> drops = new ArrayList<>();
}
