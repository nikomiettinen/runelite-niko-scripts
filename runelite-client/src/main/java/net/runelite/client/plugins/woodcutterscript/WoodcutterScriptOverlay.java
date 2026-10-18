/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.woodcutterscript;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class WoodcutterScriptOverlay extends OverlayPanel
{
	private final WoodcutterScriptPlugin plugin;
	private final WoodcutterScriptConfig config;

	@Inject
	public WoodcutterScriptOverlay(WoodcutterScriptPlugin plugin, WoodcutterScriptConfig config)
	{
		super(plugin);
		setPosition(OverlayPosition.TOP_LEFT);
		this.plugin = plugin;
		this.config = config;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Woodcutter Script")
			.color(Color.GREEN)
			.build());

		boolean running = config.startScript();
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Status:")
			.right(running ? "RUNNING" : "STOPPED")
			.rightColor(running ? Color.GREEN : Color.RED)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("State:")
			.right(plugin.getStatusLabel())
			.rightColor(Color.WHITE)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Target Tree:")
			.right(config.treeType().getLabel())
			.rightColor(Color.YELLOW)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Inventory:")
			.right(plugin.getCurrentInventoryLogs() + " / 28")
			.rightColor(plugin.getCurrentInventoryLogs() >= 28 ? Color.GREEN : Color.YELLOW)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Logs Cut:")
			.right(String.valueOf(plugin.getLogsCut()))
			.rightColor(Color.LIGHT_GRAY)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("XP Gained:")
			.right(String.format("%,d", plugin.getWoodcuttingXpGained()))
			.rightColor(Color.CYAN)
			.build());

		if (running)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Uptime:")
				.right(plugin.getFormattedUptime())
				.rightColor(Color.WHITE)
				.build());
		}

		return super.render(graphics);
	}
}
