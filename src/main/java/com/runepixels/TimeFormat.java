package com.runepixels;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class TimeFormat
{
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm").withZone(ZoneId.systemDefault());

	private TimeFormat()
	{
	}

	public static String ticks(int ticks)
	{
		long tenths = ticks * 6L;
		long seconds = tenths / 10;
		return String.format("%02d:%02d:%02d.%d", seconds / 3600, seconds / 60 % 60, seconds % 60, tenths % 10);
	}

	public static String date(String date)
	{
		try
		{
			return DATE.format(Instant.parse(date));
		}
		catch (RuntimeException e)
		{
			return "";
		}
	}
}
