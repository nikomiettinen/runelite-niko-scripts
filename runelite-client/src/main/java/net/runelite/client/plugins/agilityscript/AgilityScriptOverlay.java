/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.agilityscript;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class AgilityScriptOverlay extends OverlayPanel
{
	private final AgilityScriptPlugin plugin;
	private final AgilityScriptConfig config;

	@Inject
	public AgilityScriptOverlay(AgilityScriptPlugin plugin, AgilityScriptConfig config)
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
			.text("Agility Script")
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

		AgilityCourse activeCourse = plugin.getActiveCourse();
		String courseText = activeCourse != null ? activeCourse.getCourseName() : "Detecting...";
		if (config.selectedCourse() == AgilityCourse.AUTO && activeCourse != null)
		{
			courseText += " (Auto)";
		}

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Course:")
			.right(courseText)
			.rightColor(Color.WHITE)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("State:")
			.right(plugin.getCurrentState().getStatus())
			.rightColor(Color.WHITE)
			.build());

		if (plugin.getCurrentObstacleIndex() > 0 && activeCourse != null && !activeCourse.getObstacleIds().isEmpty())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Obstacle Step:")
				.right(plugin.getCurrentObstacleIndex() + " / " + (activeCourse.getObstacleIds().size() - 1))
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
