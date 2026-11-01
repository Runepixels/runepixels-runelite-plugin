package com.runepixels;

import com.runepixels.bosses.BossTimersPanel;
import com.runepixels.lookup.LookupPanel;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

class RunepixelsPanel extends PluginPanel
{
	private final MaterialTabGroup tabs;
	private final MaterialTab lookupTab;

	RunepixelsPanel(BossTimersPanel timersPanel, LookupPanel lookupPanel)
	{
		setLayout(new BorderLayout(0, 8));
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel display = new JPanel(new BorderLayout());
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);

		tabs = new MaterialTabGroup(display);
		MaterialTab timersTab = new MaterialTab("Timers", tabs, timersPanel);
		timersTab.setOnSelectEvent(() ->
		{
			timersPanel.refresh();
			return true;
		});
		lookupTab = new MaterialTab("Lookup", tabs, lookupPanel);
		tabs.addTab(timersTab);
		tabs.addTab(lookupTab);
		tabs.select(timersTab);

		add(tabs, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);
	}

	void showLookup()
	{
		tabs.select(lookupTab);
	}
}
