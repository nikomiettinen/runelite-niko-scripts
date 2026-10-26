/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.naturealtarscript;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class NatureAltarScriptOverlay extends OverlayPanel
{
	private final NatureAltarScriptPlugin plugin;
	private final NatureAltarScriptConfig config;

	@Inject
	public NatureAltarScriptOverlay(NatureAltarScriptPlugin plugin, NatureAltarScriptConfig config)
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
			.text("Nature Altar Runner")
			.color(new Color(0, 220, 100))
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Status:")
			.right(plugin.getStatus())
			.rightColor(plugin.getStatusColor())
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Waypoint:")
			.right((plugin.getCurrentWaypointIndex() + 1) + " / " + plugin.getTotalWaypoints())
			.rightColor(Color.CYAN)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Dist to Target:")
			.right(plugin.getDistanceToCurrentWaypoint() + " tiles")
			.rightColor(Color.YELLOW)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Essence (Unnoted):")
			.right(String.valueOf(plugin.getUnnotedEssenceCount()))
			.rightColor(plugin.getUnnotedEssenceCount() > 0 ? Color.GREEN : Color.ORANGE)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Trips Done:")
			.right(String.valueOf(plugin.getLapsCompleted()))
			.rightColor(Color.MAGENTA)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Energy:")
			.right(plugin.getEnergyPercent() + "%")
			.rightColor(plugin.getEnergyPercent() > 20 ? Color.GREEN : Color.RED)
			.build());

		return super.render(graphics);
	}
}
