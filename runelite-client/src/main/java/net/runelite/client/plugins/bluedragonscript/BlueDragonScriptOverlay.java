/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.bluedragonscript;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class BlueDragonScriptOverlay extends OverlayPanel
{
	private final BlueDragonScriptPlugin plugin;
	private final BlueDragonScriptConfig config;

	@Inject
	public BlueDragonScriptOverlay(BlueDragonScriptPlugin plugin, BlueDragonScriptConfig config)
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
			.text("Blue Dragon Script")
			.color(new Color(0, 180, 255))
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Status:")
			.right(plugin.getStatus())
			.rightColor(plugin.getStatusColor())
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("HP:")
			.right(plugin.getCurrentHp() + " / " + plugin.getMaxHp())
			.rightColor(plugin.getCurrentHp() <= (plugin.getMaxHp() * config.eatHpPercent() / 100) ? Color.RED : Color.GREEN)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Boost:")
			.right(plugin.getBoostPotionStatus())
			.rightColor((plugin.getCurrentSuperCombat() > 0 || (plugin.getCurrentSuperAttack() > 0 && plugin.getCurrentSuperStrength() > 0)) ? Color.GREEN : Color.YELLOW)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Antifire:")
			.right(plugin.getCurrentAntifire() + " / " + config.antifireQuantity())
			.rightColor(plugin.getCurrentAntifire() >= config.antifireQuantity() ? Color.GREEN : Color.YELLOW)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Food:")
			.right(plugin.getCurrentShark() + " / " + config.sharkQuantity())
			.rightColor(plugin.getCurrentShark() > config.emergencyFoodCount() ? Color.GREEN : Color.RED)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Bones Looted:")
			.right(String.valueOf(plugin.getBonesLooted()))
			.rightColor(Color.CYAN)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Hides Looted:")
			.right(String.valueOf(plugin.getHidesLooted()))
			.rightColor(Color.CYAN)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Trips Done:")
			.right(String.valueOf(plugin.getTripsCompleted()))
			.rightColor(Color.MAGENTA)
			.build());

		return super.render(graphics);
	}
}
