/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.faladoragility;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class FaladorAgilityOverlay extends OverlayPanel
{
	private final FaladorAgilityPlugin plugin;
	private final FaladorAgilityConfig config;

	@Inject
	public FaladorAgilityOverlay(FaladorAgilityPlugin plugin, FaladorAgilityConfig config)
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
			.text("Falador Agility")
			.color(Color.CYAN)
			.build());

		boolean running = config.startScript();
		boolean inCourseArea = plugin.isInCourseArea();

		String statusText;
		Color statusColor;
		if (!running)
		{
			statusText = "STOPPED";
			statusColor = Color.RED;
		}
		else if (!inCourseArea)
		{
			statusText = "NOT IN AREA";
			statusColor = Color.ORANGE;
		}
		else
		{
			statusText = "RUNNING";
			statusColor = Color.GREEN;
		}

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Status:")
			.right(statusText)
			.rightColor(statusColor)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Course:")
			.right("Falador")
			.rightColor(Color.WHITE)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("State:")
			.right(plugin.getCurrentState().getDescription())
			.rightColor(Color.WHITE)
			.build());

		if (plugin.getCurrentObstacleIndex() > 0)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Obstacle Step:")
				.right(plugin.getCurrentObstacleIndex() + " / " + (FaladorAgilityPlugin.OBSTACLE_IDS.size() - 1))
				.rightColor(Color.CYAN)
				.build());
		}

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Laps Completed:")
			.right(String.valueOf(plugin.getLapsCompleted()))
			.rightColor(Color.GREEN)
			.build());

		if (config.pickupMarksOfGrace())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Marks Picked Up:")
				.right(String.valueOf(plugin.getMarksCollected()))
				.rightColor(Color.ORANGE)
				.build());

			panelComponent.getChildren().add(LineComponent.builder()
				.left("Marks on Roofs:")
				.right(String.valueOf(plugin.getMarksOnCourse()))
				.rightColor(plugin.getMarksOnCourse() > 0 ? Color.YELLOW : Color.GRAY)
				.build());
		}

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Run Energy:")
			.right(plugin.getRunEnergy() + "%")
			.rightColor(plugin.getRunEnergy() > 20 ? Color.GREEN : Color.YELLOW)
			.build());

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
