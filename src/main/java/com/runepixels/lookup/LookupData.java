package com.runepixels.lookup;

import java.util.List;
import lombok.Data;

final class LookupData
{
	private LookupData()
	{
	}

	@Data
	static class Profile
	{
		private String id;
		private String name;
	}

	@Data
	static class Activity
	{
		private String text;
		private String date;
	}

	@Data
	static class Drop
	{
		private String text;
		private String data;
	}

	@Data
	static class CollectionLog
	{
		private List<Page> pages;
		private List<Entry> items;
	}

	@Data
	static class Page
	{
		private int category;
		private String name;
		private List<Entry> items;
	}

	@Data
	static class Entry
	{
		private String name;
		private Integer itemId;
		private int count;
	}

	@Data
	static class BestTime
	{
		private String boss;
		private int timeTicks;
	}
}
