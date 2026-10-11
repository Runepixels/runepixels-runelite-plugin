package com.runepixels.quests;

import net.runelite.api.QuestState;

final class QuestStatus
{
	static final int COMPLETED = 0;
	static final int IN_PROGRESS = 1;
	static final int NOT_STARTED = 2;

	private QuestStatus()
	{
	}

	static int of(QuestState state)
	{
		switch (state)
		{
			case FINISHED:
				return COMPLETED;
			case IN_PROGRESS:
				return IN_PROGRESS;
			default:
				return NOT_STARTED;
		}
	}
}
