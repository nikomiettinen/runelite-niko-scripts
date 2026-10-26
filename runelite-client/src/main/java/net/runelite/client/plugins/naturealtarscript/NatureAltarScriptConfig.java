/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.naturealtarscript;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("naturealtarscript")
public interface NatureAltarScriptConfig extends Config
{
	@ConfigSection(
		name = "General",
		description = "General settings",
		position = 0
	)
	String generalSection = "general";

	@ConfigItem(
		keyName = "startScript",
		name = "Start Running Script",
		description = "Enables automated running back and forth between Karamja General Store and Nature Altar",
		position = 1,
		section = generalSection
	)
	default boolean startScript()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show Overlay",
		description = "Displays script progress and current target overlay",
		position = 2,
		section = generalSection
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "delayAtDestination",
		name = "Delay at Endpoints (Ticks)",
		description = "Number of game ticks to pause at Store or Altar before running back",
		position = 3,
		section = generalSection
	)
	default int delayAtDestination()
	{
		return 1;
	}

	@ConfigSection(
		name = "Movement",
		description = "Run energy and path settings",
		position = 10
	)
	String movementSection = "movement";

	@ConfigItem(
		keyName = "minRunEnergy",
		name = "Min Run Energy %",
		description = "Toggle run on when energy is at or above this percentage",
		position = 11,
		section = movementSection
	)
	default int minRunEnergy()
	{
		return 15;
	}

	@ConfigItem(
		keyName = "drinkStamina",
		name = "Drink Stamina / Energy Potions",
		description = "Automatically drink stamina or energy potions when in inventory and energy is low",
		position = 12,
		section = movementSection
	)
	default boolean drinkStamina()
	{
		return true;
	}
}
