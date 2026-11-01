package com.runepixels.lookup;

import com.runepixels.RunepixelsApi;
import com.runepixels.RunepixelsConfig;
import com.runepixels.TimeFormat;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import okhttp3.HttpUrl;

public class LookupPanel extends JPanel
{
	private static final String[] PERIODS = {"Today", "Yesterday", "Week", "Month", "Year"};
	private static final String[] CATEGORIES = {"Bosses", "Raids", "Clues", "Minigames", "Other"};

	private final RunepixelsApi api;
	private final ItemManager itemManager;
	private final RunepixelsConfig config;

	private final JTextField search = new JTextField();
	private final JLabel player = new JLabel();
	private final JLabel status = new JLabel();
	private final JPanel activity = listPanel();
	private final JComboBox<String> period = new JComboBox<>(PERIODS);
	private final JPanel drops = listPanel();
	private final JComboBox<String> page = new JComboBox<>();
	private final JLabel pageSummary = new JLabel();
	private final JPanel pageItems = new JPanel(new GridLayout(0, 5, 2, 2));

	private LookupData.Profile profile;
	private List<LookupData.Page> pages = new ArrayList<>();
	private final Map<String, Integer> counts = new HashMap<>();
	private final Map<String, Integer> bestTimes = new HashMap<>();
	private boolean updating;
	private int generation;

	public LookupPanel(RunepixelsApi api, ItemManager itemManager, RunepixelsConfig config, Runnable lookupSelf)
	{
		this.api = api;
		this.itemManager = itemManager;
		this.config = config;

		setLayout(new BorderLayout(0, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JButton go = new JButton("Search");
		go.addActionListener(e -> lookup(search.getText()));
		search.addActionListener(e -> lookup(search.getText()));

		JButton me = new JButton("My lookup");
		me.addActionListener(e -> lookupSelf.run());

		JPanel searchRow = new JPanel(new BorderLayout(4, 0));
		searchRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		searchRow.add(search, BorderLayout.CENTER);
		searchRow.add(go, BorderLayout.EAST);

		player.setForeground(ColorScheme.BRAND_ORANGE);
		player.setFont(FontManager.getRunescapeBoldFont());
		status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JPanel header = new JPanel(new GridLayout(0, 1, 0, 4));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.add(searchRow);
		header.add(me);
		header.add(player);
		header.add(status);

		period.addActionListener(e -> loadDrops());
		page.addActionListener(e ->
		{
			if (!updating)
			{
				renderPage();
			}
		});

		pageSummary.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		pageItems.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel dropsTab = column(period, drops);
		JPanel logTab = column(page, pageSummary, pageItems);

		JPanel display = new JPanel(new BorderLayout());
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);
		MaterialTabGroup tabs = new MaterialTabGroup(display);
		MaterialTab activityTab = new MaterialTab("Activity", tabs, column(activity));
		tabs.addTab(activityTab);
		tabs.addTab(new MaterialTab("Drops", tabs, dropsTab));
		tabs.addTab(new MaterialTab("Log", tabs, logTab));
		tabs.select(activityTab);

		JPanel body = new JPanel(new BorderLayout(0, 6));
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);
		body.add(tabs, BorderLayout.NORTH);
		body.add(display, BorderLayout.CENTER);

		add(header, BorderLayout.NORTH);
		add(body, BorderLayout.CENTER);
	}

	public void lookup(String name)
	{
		String trimmed = name == null ? "" : name.replace(' ', ' ').trim();
		if (trimmed.isEmpty())
		{
			return;
		}

		if (!config.lookupPlayers())
		{
			setStatus("Enable Player lookup in the Runepixels plugin settings.");
			return;
		}

		int current = ++generation;
		search.setText(trimmed);
		player.setText(trimmed);
		setStatus("Loading...");
		profile = null;
		clear(activity);
		clear(drops);
		pages = new ArrayList<>();
		counts.clear();
		bestTimes.clear();
		setItems(page, new ArrayList<>());
		renderPage();

		HttpUrl track = api.playersUrl().addPathSegment(trimmed).addPathSegment("track").build();
		HttpUrl get = api.playersUrl().addPathSegment(trimmed).build();
		api.post(track, Map.of(), LookupData.Profile.class,
			(LookupData.Profile p) -> SwingUtilities.invokeLater(() -> showProfile(current, p)),
			error -> api.get(get, LookupData.Profile.class,
				(LookupData.Profile p) -> SwingUtilities.invokeLater(() -> showProfile(current, p)),
				notFound -> SwingUtilities.invokeLater(() -> fail(current, notFound))));
	}

	private void showProfile(int current, LookupData.Profile p)
	{
		if (current != generation)
		{
			return;
		}

		if (p == null || p.getId() == null)
		{
			fail(current, "Player not found on Runepixels.");
			return;
		}

		profile = p;
		player.setText(p.getName());
		setStatus("");

		api.post(api.playersUrl().addPathSegment(p.getId()).addPathSegment("activity").build(), Map.of("limit", 30, "offset", 0), LookupData.Activity[].class,
			(LookupData.Activity[] list) -> SwingUtilities.invokeLater(() -> showActivity(current, list)),
			error -> SwingUtilities.invokeLater(() -> fail(current, error)));

		loadDrops();

		api.get(api.playersUrl().addPathSegment(p.getId()).addPathSegment("collection-log").build(), LookupData.CollectionLog.class,
			(LookupData.CollectionLog log) -> SwingUtilities.invokeLater(() -> showLog(current, log)),
			error -> SwingUtilities.invokeLater(() -> fail(current, error)));

		api.get(api.runeliteUrl("boss-kills/best").newBuilder().addQueryParameter("name", p.getName()).build(), LookupData.BestTime[].class,
			(LookupData.BestTime[] times) -> SwingUtilities.invokeLater(() -> showBestTimes(current, times)),
			error -> { });
	}

	private void showActivity(int current, LookupData.Activity[] list)
	{
		if (current != generation)
		{
			return;
		}

		clear(activity);
		if (list == null || list.length == 0)
		{
			activity.add(muted("No activity yet."));
		}
		else
		{
			for (LookupData.Activity a : list)
			{
				JLabel label = new JLabel("<html>" + escape(a.getText()) + "<br><font color='#888888'>" + TimeFormat.date(a.getDate()) + "</font></html>");
				label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				activity.add(row(label));
			}
		}
		refresh(activity);
	}

	private void loadDrops()
	{
		if (profile == null)
		{
			return;
		}

		int current = generation;
		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		LocalDate from;
		LocalDate to = today.plusDays(1);
		switch (Objects.requireNonNull((String) period.getSelectedItem()))
		{
			case "Yesterday":
				from = today.minusDays(1);
				to = today;
				break;
			case "Week":
				from = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
				break;
			case "Month":
				from = today.withDayOfMonth(1);
				break;
			case "Year":
				from = today.withDayOfYear(1);
				break;
			default:
				from = today;
		}

		clear(drops);
		drops.add(muted("Loading..."));
		refresh(drops);

		api.get(api.playersUrl().addPathSegment(profile.getId()).addPathSegment("drops")
				.addQueryParameter("from", from.atStartOfDay(ZoneOffset.UTC).toInstant().toString())
				.addQueryParameter("to", to.atStartOfDay(ZoneOffset.UTC).toInstant().toString())
				.addQueryParameter("limit", "2000")
				.build(), LookupData.Drop[].class,
			(LookupData.Drop[] list) -> SwingUtilities.invokeLater(() -> showDrops(current, list)),
			error -> SwingUtilities.invokeLater(() -> fail(current, error)));
	}

	private void showDrops(int current, LookupData.Drop[] list)
	{
		if (current != generation)
		{
			return;
		}

		Map<String, Integer> totals = new LinkedHashMap<>();
		Map<String, Integer> itemIds = new HashMap<>();
		if (list != null)
		{
			for (LookupData.Drop drop : list)
			{
				totals.merge(drop.getText(), 1, Integer::sum);
				itemIds.put(drop.getText(), parseInt(drop.getData()));
			}
		}

		clear(drops);
		if (totals.isEmpty())
		{
			drops.add(muted("No drops in this period."));
		}

		for (Map.Entry<String, Integer> entry : totals.entrySet())
		{
			JLabel label = new JLabel(entry.getValue() + " x " + entry.getKey());
			label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			icon(label, itemIds.get(entry.getKey()), true);
			drops.add(row(label));
		}
		refresh(drops);
	}

	private void showLog(int current, LookupData.CollectionLog log)
	{
		if (current != generation || log == null)
		{
			return;
		}

		counts.clear();
		if (log.getItems() != null)
		{
			for (LookupData.Entry item : log.getItems())
			{
				counts.put(item.getName().toLowerCase(), item.getCount());
			}
		}

		pages = log.getPages() == null ? new ArrayList<>() : log.getPages();
		List<String> names = new ArrayList<>();
		for (LookupData.Page p : pages)
		{
			names.add(pageLabel(p));
		}
		setItems(page, names);
		renderPage();
	}

	private void showBestTimes(int current, LookupData.BestTime[] times)
	{
		if (current != generation || times == null)
		{
			return;
		}

		for (LookupData.BestTime t : times)
		{
			bestTimes.put(t.getBoss().toLowerCase(), t.getTimeTicks());
		}
		renderPage();
	}

	private void renderPage()
	{
		pageItems.removeAll();
		int index = page.getSelectedIndex();
		if (index < 0 || index >= pages.size())
		{
			pageSummary.setText(profile == null ? "" : "No collection log synced.");
			refresh(pageItems);
			return;
		}

		LookupData.Page selected = pages.get(index);
		int received = 0;
		for (LookupData.Entry item : selected.getItems())
		{
			int count = counts.getOrDefault(item.getName().toLowerCase(), 0);
			if (count > 0)
			{
				received++;
			}

			JLabel cell = new JLabel();
			cell.setHorizontalAlignment(SwingConstants.CENTER);
			cell.setPreferredSize(new Dimension(36, 32));
			cell.setToolTipText(item.getName() + (count > 1 ? " x " + count : ""));
			icon(cell, item.getItemId(), count > 0);
			pageItems.add(cell);
		}

		Integer best = bestTimes.get(selected.getName().toLowerCase());
		pageSummary.setText("<html>" + received + "/" + selected.getItems().size() + " obtained"
			+ (best == null ? "" : "<br>Best time: <font color='#ff981f'>" + TimeFormat.ticks(best) + "</font>") + "</html>");
		refresh(pageItems);
	}

	private void icon(JLabel label, Integer itemId, boolean obtained)
	{
		if (itemId == null || itemId <= 0)
		{
			return;
		}

		AsyncBufferedImage image = itemManager.getImage(itemId);
		Runnable apply = () -> label.setIcon(new ImageIcon(obtained ? image : ImageUtil.alphaOffset(image, 0.25f)));
		image.onLoaded(() -> SwingUtilities.invokeLater(apply));
		apply.run();
	}

	private void fail(int current, String error)
	{
		if (current == generation)
		{
			setStatus(error);
		}
	}

	private void setItems(JComboBox<String> combo, List<String> items)
	{
		updating = true;
		combo.removeAllItems();
		items.forEach(combo::addItem);
		updating = false;
	}

	private void setStatus(String message)
	{
		status.setText(message.isEmpty() ? "" : "<html>" + message + "</html>");
	}

	private static String pageLabel(LookupData.Page p)
	{
		String category = p.getCategory() >= 0 && p.getCategory() < CATEGORIES.length ? CATEGORIES[p.getCategory()] : "";
		return category.isEmpty() ? p.getName() : category + " - " + p.getName();
	}

	private static Integer parseInt(String value)
	{
		try
		{
			return value == null || value.isEmpty() ? null : Integer.parseInt(value);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	private static String escape(String text)
	{
		return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static JPanel listPanel()
	{
		JPanel panel = new JPanel(new GridLayout(0, 1, 0, 2));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		return panel;
	}

	private static JPanel column(JComponent... components)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		for (JComponent component : components)
		{
			JPanel wrapper = new JPanel(new BorderLayout());
			wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
			wrapper.setBorder(new EmptyBorder(2, 0, 2, 0));
			wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
			wrapper.add(component, BorderLayout.CENTER);
			panel.add(wrapper);
		}
		return panel;
	}

	private static JPanel row(JComponent content)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		row.add(content, BorderLayout.CENTER);
		return row;
	}

	private static JLabel muted(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		return label;
	}

	private static void clear(JPanel panel)
	{
		panel.removeAll();
		refresh(panel);
	}

	private static void refresh(JPanel panel)
	{
		panel.revalidate();
		panel.repaint();
	}
}
