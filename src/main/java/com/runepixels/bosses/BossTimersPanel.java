package com.runepixels.bosses;

import com.runepixels.RunepixelsApi;
import com.runepixels.RunepixelsConfig;
import com.runepixels.TimeFormat;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

public class BossTimersPanel extends JPanel
{
	private static final String[] PERIODS = {"Today", "Yesterday", "Week", "Month", "Year"};

	private final RunepixelsApi api;
	private final RunepixelsConfig config;
	private final BossKillSync kills;

	private final JComboBox<String> bossCombo = new JComboBox<>();
	private final DefaultListModel<String> myModel = new DefaultListModel<>();
	private final JList<String> myList = new JList<>(myModel)
	{
		@Override
		public String getToolTipText(MouseEvent event)
		{
			int index = locationToIndex(event.getPoint());
			if (index < 0 || index >= rows.size() || rows.get(index).getDrops().isEmpty())
			{
				return null;
			}
			return "<html>" + String.join("<br>", rows.get(index).getDrops()) + "</html>";
		}
	};
	private final JButton remove = new JButton("Remove");
	private final JButton clear = new JButton("Clear");
	private final JComboBox<String> period = new JComboBox<>(PERIODS);
	private final JButton refresh = new JButton("Refresh");
	private final JButton share = new JButton("Share all");
	private final DefaultListModel<String> globalModel = new DefaultListModel<>();

	private final List<String> bosses = new ArrayList<>();
	private List<String> sharedBosses = new ArrayList<>();
	private List<BossKill> rows = new ArrayList<>();
	private String currentBoss;
	private boolean updating;
	private int request;

	public BossTimersPanel(RunepixelsApi api, RunepixelsConfig config, BossKillSync kills)
	{
		this.api = api;
		this.config = config;
		this.kills = kills;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		bossCombo.addActionListener(e ->
		{
			int index = bossCombo.getSelectedIndex();
			if (updating || index < 0 || index >= bosses.size() || bosses.get(index).equals(currentBoss))
			{
				return;
			}
			currentBoss = bosses.get(index);
			fillMine();
			fillGlobal();
		});

		myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		myList.addListSelectionListener(e -> updateButtons());
		myList.setToolTipText("");

		remove.addActionListener(e ->
		{
			int index = myList.getSelectedIndex();
			if (index >= 0 && index < rows.size())
			{
				kills.remove(rows.get(index));
			}
		});
		clear.addActionListener(e ->
		{
			if (currentBoss != null)
			{
				kills.clear(currentBoss);
			}
		});
		period.addActionListener(e -> fillGlobal());
		refresh.addActionListener(e -> fillGlobal());
		share.addActionListener(e -> kills.toggleShare());

		JList<String> globalList = new JList<>(globalModel);
		globalList.setFocusable(false);

		add(wrap(bossCombo));
		add(wrap(scroll(myList)));
		add(wrap(grid(remove, clear)));
		add(wrap(share));
		add(wrap(grid(period, refresh)));
		add(wrap(scroll(globalList)));

		refresh();
	}

	public void refresh()
	{
		fillBosses();
		fillMine();
		fillGlobal();
	}

	public void onKillsChanged(String killedBoss)
	{
		if (killedBoss != null && !killedBoss.equals(currentBoss))
		{
			currentBoss = killedBoss;
			fillBosses();
			fillMine();
			fillGlobal();
			return;
		}

		fillBosses();
		fillMine();
		if (!kills.isSharePending())
		{
			fillGlobal();
		}
	}

	private void fillBosses()
	{
		Map<String, Integer> counts = kills.timedBosses();
		TreeMap<String, String> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (String boss : counts.keySet())
		{
			sorted.put(boss, boss);
		}
		for (String boss : sharedBosses)
		{
			sorted.putIfAbsent(boss, boss);
		}

		bosses.clear();
		bosses.addAll(sorted.values());
		if (currentBoss == null || !bosses.contains(currentBoss))
		{
			currentBoss = bosses.isEmpty() ? null : bosses.get(0);
		}

		updating = true;
		bossCombo.removeAllItems();
		if (bosses.isEmpty())
		{
			bossCombo.addItem("No shared times or kills");
		}
		for (String boss : bosses)
		{
			int count = counts.getOrDefault(boss, 0);
			bossCombo.addItem(count > 0 ? boss + " (" + count + ")" : boss);
		}
		bossCombo.setSelectedIndex(currentBoss == null ? 0 : bosses.indexOf(currentBoss));
		updating = false;
	}

	private void fillMine()
	{
		rows = currentBoss == null ? new ArrayList<>() : kills.killsFor(currentBoss);
		myModel.clear();
		for (BossKill kill : rows)
		{
			myModel.addElement(TimeFormat.ticks(kill.getTimeTicks()) + "  KC " + kill.getKillCount());
		}
		updateButtons();
	}

	private void fillGlobal()
	{
		int mine = ++request;
		if (!config.syncBossKills())
		{
			setGlobal("Enable Boss kill times in settings");
			return;
		}

		setGlobal("Loading...");
		String boss = currentBoss;
		String selectedPeriod = ((String) Objects.requireNonNull(period.getSelectedItem())).toLowerCase();
		api.get(api.runeliteUrl("boss-kills/leaderboard").newBuilder()
				.addQueryParameter("boss", boss == null ? "" : boss)
				.addQueryParameter("period", selectedPeriod)
				.build(), BossLeaderboard.class,
			(BossLeaderboard leaderboard) -> SwingUtilities.invokeLater(() -> showGlobal(mine, boss, leaderboard)),
			error -> SwingUtilities.invokeLater(() ->
			{
				if (mine == request)
				{
					setGlobal("Not available");
				}
			}));
	}

	private void showGlobal(int mine, String boss, BossLeaderboard leaderboard)
	{
		if (mine != request)
		{
			return;
		}

		List<String> keys = leaderboard == null || leaderboard.getBosses() == null ? new ArrayList<>() : leaderboard.getBosses();
		if (!keys.equals(sharedBosses))
		{
			sharedBosses = keys;
			fillBosses();
			if (!Objects.equals(currentBoss, boss))
			{
				fillMine();
				fillGlobal();
				return;
			}
		}

		List<BossLeaderboard.Entry> entries = leaderboard == null || leaderboard.getEntries() == null ? new ArrayList<>() : leaderboard.getEntries();
		globalModel.clear();
		if (boss == null)
		{
			return;
		}
		if (entries.isEmpty())
		{
			globalModel.addElement("No shared times yet");
		}
		int rank = 1;
		for (BossLeaderboard.Entry entry : entries)
		{
			globalModel.addElement(rank++ + ". " + entry.getName() + "  " + TimeFormat.ticks(entry.getTimeTicks()));
		}
	}

	private void updateButtons()
	{
		boolean shared = kills.isShared();
		share.setText(kills.isSharePending() ? "Loading..." : shared ? "Sharing" : "Share all");
		remove.setText(myList.getSelectedIndex() >= 0 ? "X Remove" : "Remove");
		remove.setEnabled(myList.getSelectedIndex() >= 0);
		clear.setText(shared ? "Clear (keep best)" : "Clear");
		clear.setEnabled(!rows.isEmpty());
	}

	private void setGlobal(String message)
	{
		globalModel.clear();
		globalModel.addElement(message);
	}

	private static JScrollPane scroll(JList<String> list)
	{
		list.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		list.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		JScrollPane pane = new JScrollPane(list);
		pane.setPreferredSize(new Dimension(0, 180));
		return pane;
	}

	private static JPanel grid(JComponent... components)
	{
		JPanel panel = new JPanel(new GridLayout(1, components.length, 4, 0));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		for (JComponent component : components)
		{
			panel.add(component);
		}
		return panel;
	}

	private static Component wrap(JComponent component)
	{
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.setBorder(new EmptyBorder(3, 0, 3, 0));
		wrapper.add(component, BorderLayout.CENTER);
		wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
		return wrapper;
	}
}
