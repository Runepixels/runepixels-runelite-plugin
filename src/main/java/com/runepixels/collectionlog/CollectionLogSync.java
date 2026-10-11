package com.runepixels.collectionlog;

import com.runepixels.RunepixelsApi;
import com.runepixels.RunepixelsConfig;
import com.runepixels.Sync;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.ScriptID;
import net.runelite.api.StructComposition;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.util.Text;
import net.runelite.http.api.loottracker.LootRecordType;

public class CollectionLogSync extends Sync
{
	private static final Pattern MEMBERS_SUFFIX = Pattern.compile("\\s*\\(Members\\)$");
	private static final int COLLECTION_SEARCH_ITEM_SCRIPT = 4100;
	private static final int COLLECTION_TABS_ENUM = 2102;
	private static final int TAB_NAME_PARAM = 682;
	private static final int TAB_PAGES_PARAM = 683;
	private static final int PAGE_NAME_PARAM = 689;
	private static final int PAGE_ITEMS_PARAM = 690;

	@Inject
	private Client client;

	@Inject
	private ItemManager itemManager;

	@Inject
	private RunepixelsApi api;

	@Inject
	private RunepixelsConfig config;

	private final Map<Integer, CollectionLogPayload.Item> pendingItems = new LinkedHashMap<>();
	private final Map<Integer, CollectionLogPayload.Drop> pendingDrops = new LinkedHashMap<>();
	private final Map<Integer, Integer> lastSynced = new HashMap<>();
	private List<CollectionLogPayload.Page> catalog;
	private Set<Integer> catalogItemIds;
	private boolean catalogSent;

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() != ScriptID.COLLECTION_DRAW_LIST || !isEnabled())
		{
			return;
		}

		Widget items = client.getWidget(InterfaceID.Collection.ITEMS);
		if (items == null || items.getDynamicChildren() == null)
		{
			return;
		}

		for (Widget item : items.getDynamicChildren())
		{
			int itemId = item.getItemId();
			if (itemId <= 0 || item.getOpacity() != 0)
			{
				continue;
			}

			addItem(itemId, Math.max(item.getItemQuantity(), 1));
		}
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() != COLLECTION_SEARCH_ITEM_SCRIPT || event.getScriptEvent() == null || !isEnabled())
		{
			return;
		}

		Object[] args = event.getScriptEvent().getArguments();
		if (args == null || args.length < 3 || !(args[1] instanceof Integer) || !(args[2] instanceof Integer))
		{
			return;
		}

		int itemId = (Integer) args[1];
		int count = (Integer) args[2];
		if (itemId > 0 && count > 0)
		{
			addItem(itemId, count);
		}
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		addDrops(event.getItems());
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (event.getType() == LootRecordType.NPC || event.getType() == LootRecordType.PLAYER)
		{
			return;
		}

		addDrops(event.getItems());
	}

	@Override
	public boolean isEnabled()
	{
		return config.syncCollectionLog();
	}

	@Override
	public String getConfigKey()
	{
		return "syncCollectionLog";
	}

	@Override
	public String getPath()
	{
		return "collection-log";
	}

	@Override
	public String getDisplayName()
	{
		return "collection log";
	}

	@Override
	public void sync(long accountHash)
	{
		if (pendingItems.isEmpty() && pendingDrops.isEmpty())
		{
			return;
		}

		List<CollectionLogPayload.Item> items = new ArrayList<>(pendingItems.values());
		List<CollectionLogPayload.Drop> drops = new ArrayList<>(pendingDrops.values());
		pendingItems.clear();
		pendingDrops.clear();

		for (CollectionLogPayload.Item item : items)
		{
			lastSynced.put(item.getItemId(), item.getCount());
		}

		for (CollectionLogPayload.Drop drop : drops)
		{
			lastSynced.computeIfPresent(drop.getItemId(), (id, count) -> count + drop.getQuantity());
		}

		List<CollectionLogPayload.Page> pages = null;
		if (!catalogSent)
		{
			pages = loadCatalog();
			catalogSent = true;
		}

		api.post(getPath(), new CollectionLogPayload(accountHash, items, drops, pages));
	}

	@Override
	public void reset()
	{
		super.reset();
		pendingItems.clear();
		pendingDrops.clear();
		lastSynced.clear();
		catalogSent = false;
	}

	private void addItem(int itemId, int count)
	{
		Integer synced = lastSynced.get(itemId);
		if (synced != null && synced == count)
		{
			return;
		}

		pendingItems.put(itemId, new CollectionLogPayload.Item(itemId, itemName(itemId), count));
		request();
	}

	private void addDrops(Collection<ItemStack> items)
	{
		if (!isEnabled() || items == null || items.isEmpty())
		{
			return;
		}

		loadCatalog();

		for (ItemStack stack : items)
		{
			int itemId = itemManager.canonicalize(stack.getId());
			if (!catalogItemIds.contains(itemId) || stack.getQuantity() <= 0)
			{
				continue;
			}

			CollectionLogPayload.Drop current = pendingDrops.get(itemId);
			int quantity = stack.getQuantity() + (current == null ? 0 : current.getQuantity());
			pendingDrops.put(itemId, new CollectionLogPayload.Drop(itemId, itemName(itemId), quantity));
		}

		if (!pendingDrops.isEmpty())
		{
			request();
		}
	}

	private List<CollectionLogPayload.Page> loadCatalog()
	{
		if (catalog != null)
		{
			return catalog;
		}

		List<CollectionLogPayload.Page> pages = new ArrayList<>();
		Set<Integer> itemIds = new HashSet<>();
		EnumComposition tabs = client.getEnum(COLLECTION_TABS_ENUM);

		if (tabs != null)
		{
			for (int tabStructId : tabs.getIntVals())
			{
				StructComposition tab = client.getStructComposition(tabStructId);
				EnumComposition tabPages = client.getEnum(tab.getIntValue(TAB_PAGES_PARAM));
				if (tabPages == null)
				{
					continue;
				}

				String category = tab.getStringValue(TAB_NAME_PARAM);
				for (int pageStructId : tabPages.getIntVals())
				{
					StructComposition page = client.getStructComposition(pageStructId);
					EnumComposition pageItems = client.getEnum(page.getIntValue(PAGE_ITEMS_PARAM));
					if (pageItems == null)
					{
						continue;
					}

					List<CollectionLogPayload.PageItem> items = new ArrayList<>();
					for (int itemId : pageItems.getIntVals())
					{
						itemIds.add(itemId);
						items.add(new CollectionLogPayload.PageItem(itemId, itemName(itemId)));
					}

					pages.add(new CollectionLogPayload.Page(category, page.getStringValue(PAGE_NAME_PARAM), items));
				}
			}
		}

		catalog = pages;
		catalogItemIds = itemIds;
		return catalog;
	}

	private String itemName(int itemId)
	{
		return cleanName(client.getItemDefinition(itemId).getName());
	}

	private static String cleanName(String name)
	{
		return MEMBERS_SUFFIX.matcher(Text.removeTags(name)).replaceAll("").trim();
	}
}
