/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.woodcutterscript;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Duration;
import java.time.Instant;
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
			.left("Tila:")
			.right(running ? "PÄÄLLÄ" : "POIS")
			.rightColor(running ? Color.GREEN : Color.RED)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Toiminta:")
			.right(plugin.getCurrentState().getStatusName())
			.rightColor(Color.WHITE)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Puu:")
			.right(config.treeType().getDisplayName())
			.rightColor(Color.YELLOW)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Moodi:")
			.right(config.mode().getName())
			.rightColor(Color.CYAN)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Halot kaadettu:")
			.right(String.valueOf(plugin.getLogsCut()))
			.rightColor(Color.LIGHT_GRAY)
			.build());

		if (running && plugin.getStartedAt() != null)
		{
			Duration duration = Duration.between(plugin.getStartedAt(), Instant.now());
			long s = duration.getSeconds();
			String timeStr = String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, (s % 60));
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Aika:")
				.right(timeStr)
				.rightColor(Color.LIGHT_GRAY)
				.build());
		}

		return super.render(graphics);
	}
}
