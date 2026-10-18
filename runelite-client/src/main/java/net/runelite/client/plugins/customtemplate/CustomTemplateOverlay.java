/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.customtemplate;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class CustomTemplateOverlay extends OverlayPanel
{
	private final CustomTemplatePlugin plugin;
	private final CustomTemplateConfig config;

	@Inject
	CustomTemplateOverlay(CustomTemplatePlugin plugin, CustomTemplateConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.startScript())
		{
			return null;
		}

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Uusi Skripti")
			.color(Color.CYAN)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Tila:")
			.right(plugin.getStatus())
			.rightColor(Color.WHITE)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Suorituskerrat:")
			.right(String.valueOf(plugin.getActionCount()))
			.rightColor(Color.GREEN)
			.build());

		return super.render(graphics);
	}
}
