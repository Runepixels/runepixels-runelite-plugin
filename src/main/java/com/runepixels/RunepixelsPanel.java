package com.runepixels;

import com.runepixels.clan.ClanSync;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

class RunepixelsPanel extends PluginPanel
{
	private final JLabel status = new JLabel();

	RunepixelsPanel(ClientThread clientThread, ClanSync clanSync)
	{
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Clan");
		title.setForeground(ColorScheme.BRAND_ORANGE);

		JButton sync = new JButton("Sync clan");
		sync.addActionListener(e ->
		{
			sync.setEnabled(false);
			clientThread.invoke(() ->
			{
				String message = clanSync.requestManualSync();
				SwingUtilities.invokeLater(() ->
				{
					setStatus(message);
					sync.setEnabled(true);
				});
			});
		});

		status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JPanel content = new JPanel(new GridLayout(0, 1, 0, 8));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.add(title);
		content.add(sync);
		content.add(status);

		add(content, BorderLayout.NORTH);
	}

	private void setStatus(String message)
	{
		status.setText("<html>" + message + "</html>");
	}
}
