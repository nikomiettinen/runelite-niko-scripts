/*
 * Copyright (c) 2026, RuneLite
 * All rights reserved.
 */
package net.runelite.client.plugins.woodcutterscript;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

@Singleton
public class WoodcutterScriptPanel extends PluginPanel
{
	private final JLabel statusLabel = new JLabel("Status: Stopped");
	private final JLabel uptimeLabel = new JLabel("Uptime: 00:00:00");
	private final JLabel logsCutLabel = new JLabel("Logs cut: 0");
	private final JLabel xpGainedLabel = new JLabel("XP gained: 0");

	@Inject
	WoodcutterScriptPanel()
	{
	}

	void init(WoodcutterScriptPlugin plugin)
	{
		removeAll();
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Woodcutter Script");
		title.setForeground(ColorScheme.BRAND_ORANGE);
		title.setAlignmentX(LEFT_ALIGNMENT);
		content.add(title);
		content.add(Box.createRigidArea(new Dimension(0, 12)));

		JLabel treeLabel = new JLabel("Tree and location");
		treeLabel.setForeground(ColorScheme.TEXT_COLOR);
		treeLabel.setAlignmentX(LEFT_ALIGNMENT);
		content.add(treeLabel);

		JComboBox<WoodcutterScriptPlugin.TreeType> treeSelector =
			new JComboBox<>(WoodcutterScriptPlugin.TreeType.values());
		treeSelector.setSelectedItem(plugin.getTreeType());
		treeSelector.setAlignmentX(LEFT_ALIGNMENT);
		treeSelector.addActionListener(event ->
			plugin.setTreeType((WoodcutterScriptPlugin.TreeType) treeSelector.getSelectedItem()));
		content.add(treeSelector);
		content.add(Box.createRigidArea(new Dimension(0, 12)));

		statusLabel.setForeground(ColorScheme.TEXT_COLOR);
		statusLabel.setAlignmentX(LEFT_ALIGNMENT);
		content.add(statusLabel);
		content.add(Box.createRigidArea(new Dimension(0, 12)));

		for (JLabel metricLabel : new JLabel[]{uptimeLabel, logsCutLabel, xpGainedLabel})
		{
			metricLabel.setForeground(ColorScheme.TEXT_COLOR);
			metricLabel.setAlignmentX(LEFT_ALIGNMENT);
			content.add(metricLabel);
		}
		content.add(Box.createRigidArea(new Dimension(0, 12)));

		JButton startButton = new JButton("Start Script");
		startButton.setAlignmentX(LEFT_ALIGNMENT);
		startButton.addActionListener(event -> plugin.startScript());
		content.add(startButton);
		content.add(Box.createRigidArea(new Dimension(0, 6)));

		JButton stopButton = new JButton("Stop Script");
		stopButton.setAlignmentX(LEFT_ALIGNMENT);
		stopButton.addActionListener(event -> plugin.stopScript());
		content.add(stopButton);

		add(content, BorderLayout.NORTH);
		revalidate();
		repaint();
	}

	public void setStatus(String status)
	{
		SwingUtilities.invokeLater(() -> statusLabel.setText("Status: " + status));
	}

	public void setMetrics(String uptime, int logsCut, long xpGained)
	{
		SwingUtilities.invokeLater(() ->
		{
			uptimeLabel.setText("Uptime: " + uptime);
			logsCutLabel.setText("Logs cut: " + String.format("%,d", logsCut));
			xpGainedLabel.setText("XP gained: " + String.format("%,d", xpGained));
		});
	}
}
