/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.camelotagility;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class CamelotAgilityOverlay extends OverlayPanel
{
	private final CamelotAgilityPlugin plugin;
	private final CamelotAgilityConfig config;

	@Inject
	public CamelotAgilityOverlay(CamelotAgilityPlugin plugin, CamelotAgilityConfig config)
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
			.text("Camelot Agility")
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
			.right("Camelot / Seers")
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
				.right(plugin.getCurrentObstacleIndex() + " / " + (CamelotAgilityPlugin.OBSTACLE_IDS.size() - 1))
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
		}

		if (config.autoEat() && plugin.getFoodEaten() > 0)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Food Eaten:")
				.right(String.valueOf(plugin.getFoodEaten()))
				.rightColor(Color.PINK)
				.build());
		}

		return super.render(graphics);
	}
}
