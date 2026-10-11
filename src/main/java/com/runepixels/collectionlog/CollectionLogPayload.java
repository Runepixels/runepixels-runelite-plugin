package com.runepixels.collectionlog;

import java.util.List;
import lombok.Value;

@Value
class CollectionLogPayload
{
	long accountHash;
	List<Item> items;
	List<Drop> drops;
	List<Page> pages;

	@Value
	static class Item
	{
		int itemId;
		String name;
		int count;
	}

	@Value
	static class Drop
	{
		int itemId;
		String name;
		int quantity;
	}

	@Value
	static class Page
	{
		String category;
		String name;
		List<PageItem> items;
	}

	@Value
	static class PageItem
	{
		int itemId;
		String name;
	}
}
