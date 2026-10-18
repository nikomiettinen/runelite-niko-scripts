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
		if (!config.showOverlay())
		{
			return null;
		}

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Woodcutter Script")
			.color(Color.GREEN)
			.build());

		boolean running = plugin.isRunning();
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Tila:")
			.right(running ? "PÄÄLLÄ" : "POIS")
			.rightColor(running ? Color.GREEN : Color.RED)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Status:")
			.right(plugin.getStatusLabel())
			.rightColor(Color.WHITE)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Kohde:")
			.right(plugin.getTreeType().getLabel())
			.rightColor(Color.YELLOW)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Halot kaadettu:")
			.right(String.valueOf(plugin.getLogsCut()))
			.rightColor(Color.LIGHT_GRAY)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("XP saatu:")
			.right(String.format("%,d", plugin.getWoodcuttingXpGained()))
			.rightColor(Color.CYAN)
			.build());

		if (running)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Aika:")
				.right(plugin.getFormattedUptime())
				.rightColor(Color.LIGHT_GRAY)
				.build());
		}

		return super.render(graphics);
	}
}
