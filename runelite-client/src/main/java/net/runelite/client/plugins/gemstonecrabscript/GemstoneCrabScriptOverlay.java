/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.gemstonecrabscript;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class GemstoneCrabScriptOverlay extends OverlayPanel
{
	private final GemstoneCrabScriptPlugin plugin;
	private final GemstoneCrabScriptConfig config;

	@Inject
	public GemstoneCrabScriptOverlay(GemstoneCrabScriptPlugin plugin, GemstoneCrabScriptConfig config)
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
			.text("Gemstone Crab Script")
			.color(Color.CYAN)
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

		boolean crabAlive = plugin.isCrabAlive();
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Crab:")
			.right(crabAlive ? "ALIVE" : "BURROWED / DEAD")
			.rightColor(crabAlive ? Color.GREEN : Color.RED)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Crabs Defeated:")
			.right(String.valueOf(plugin.getCrabsDefeated()))
			.rightColor(Color.LIGHT_GRAY)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Caves Entered:")
			.right(String.valueOf(plugin.getCavesEntered()))
			.rightColor(Color.LIGHT_GRAY)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Combat XP:")
			.right(String.format("%,d", plugin.getTotalCombatXpGained()))
			.rightColor(Color.CYAN)
			.build());

		if (config.antiAfk())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Anti-AFK:")
				.right("Active")
				.rightColor(Color.GREEN)
				.build());
		}

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
