/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.motherlodescript;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class MotherlodeScriptOverlay extends OverlayPanel
{
	private final MotherlodeScriptPlugin plugin;
	private final MotherlodeScriptConfig config;

	@Inject
	public MotherlodeScriptOverlay(MotherlodeScriptPlugin plugin, MotherlodeScriptConfig config)
	{
		super(plugin);
		setPosition(OverlayPosition.TOP_LEFT);
		this.plugin = plugin;
		this.config = config;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.isInMlm())
		{
			return null;
		}

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Motherlode Mine Script")
			.color(Color.ORANGE)
			.build());

		boolean running = config.startScript();
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Status:")
			.right(running ? "RUNNING" : "STOPPED")
			.rightColor(running ? Color.GREEN : Color.RED)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("State:")
			.right(plugin.getCurrentState().getDescription())
			.rightColor(Color.WHITE)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Pay-dirt:")
			.right(plugin.getCurrentPayDirt() + " / " + config.minPayDirt())
			.rightColor(plugin.getCurrentPayDirt() >= config.minPayDirt() ? Color.GREEN : Color.YELLOW)
			.build());

		boolean isBigger = config.biggerSack();
		int maxDeposits = isBigger ? 7 : 4;
		int maxCapacity = isBigger ? 189 : 108;

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Deposited Invs:")
			.right(plugin.getDepositedInventories() + " / " + maxDeposits)
			.rightColor(plugin.getDepositedInventories() >= maxDeposits ? Color.MAGENTA : Color.CYAN)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Sack Ore Count:")
			.right(plugin.getCurrentSackCount() + " / " + maxCapacity)
			.rightColor(Color.LIGHT_GRAY)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Target Floor:")
			.right(config.upperFloor() ? "Upper Level" : "Lower Level")
			.rightColor(Color.WHITE)
			.build());

		if (config.upperFloor())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Upper Hopper:")
				.right(config.upperHopperUnlocked() ? "Unlocked" : "Locked (use lower)")
				.rightColor(config.upperHopperUnlocked() ? Color.GREEN : Color.ORANGE)
				.build());
		}

		if (config.antiLogout())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Anti-Logout:")
				.right("Active")
				.rightColor(Color.GREEN)
				.build());
		}

		return super.render(graphics);
	}
}
