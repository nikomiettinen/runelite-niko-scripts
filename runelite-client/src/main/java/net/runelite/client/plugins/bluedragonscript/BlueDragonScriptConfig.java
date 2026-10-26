/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.bluedragonscript;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("bluedragonscript")
public interface BlueDragonScriptConfig extends Config
{
	enum PotionChoice
	{
		AUTO("Auto (Super combat or Atk+Str)"),
		SUPER_COMBAT("Super Combat"),
		SUPER_ATTACK_AND_STRENGTH("Super Attack + Strength");

		private final String name;

		PotionChoice(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	@ConfigSection(
		name = "General",
		description = "General settings",
		position = 0
	)
	String generalSection = "general";

	@ConfigItem(
		keyName = "startTeleport",
		name = "Start Bot Sequence",
		description = "Teleports to Falador, banks supplies, runs to Taverley dungeon, drinks potions, fights Blue Dragons and repeats",
		position = 1,
		section = generalSection
	)
	default boolean startTeleport()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show Overlay",
		description = "Show status overlay",
		position = 2,
		section = generalSection
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigSection(
		name = "Restock Settings",
		description = "Bank restock quantities and items",
		position = 10
	)
	String restockSection = "restock";

	@ConfigItem(
		keyName = "potionChoice",
		name = "Combat Potion",
		description = "Choose which boost potion to use. AUTO uses Super combat if available, or Super attack + strength (supports 1, 2, 3, 4 doses)",
		position = 11,
		section = restockSection
	)
	default PotionChoice potionChoice()
	{
		return PotionChoice.AUTO;
	}

	@ConfigItem(
		keyName = "superCombatQuantity",
		name = "Super Combat Quantity",
		description = "Quantity of Super Combat potions to withdraw (if using Super Combat)",
		position = 12,
		section = restockSection
	)
	default int superCombatQuantity()
	{
		return 1;
	}

	@ConfigItem(
		keyName = "antifireQuantity",
		name = "Antifire Quantity",
		description = "Quantity of Antifire potions to withdraw (supports 1, 2, 3, 4 doses)",
		position = 13,
		section = restockSection
	)
	default int antifireQuantity()
	{
		return 1;
	}

	@ConfigItem(
		keyName = "sharkQuantity",
		name = "Food Quantity",
		description = "Quantity of Food/Sharks to withdraw",
		position = 14,
		section = restockSection
	)
	default int sharkQuantity()
	{
		return 8;
	}

	@ConfigItem(
		keyName = "eatToFullHpAtBank",
		name = "Eat to Full HP at Bank",
		description = "Eats food at the bank to reach full Hitpoints before departing",
		position = 15,
		section = restockSection
	)
	default boolean eatToFullHpAtBank()
	{
		return true;
	}

	@ConfigSection(
		name = "Combat Settings",
		description = "Blue Dragon combat and loot settings",
		position = 20
	)
	String combatSection = "combat";

	@ConfigItem(
		keyName = "eatHpPercent",
		name = "Eat at HP %",
		description = "Eat food when HP drops below this percentage",
		position = 21,
		section = combatSection
	)
	default int eatHpPercent()
	{
		return 65;
	}

	@ConfigItem(
		keyName = "emergencyFoodCount",
		name = "Teleport When Food <=",
		description = "Teleport back to Falador when food remaining is at or below this number",
		position = 22,
		section = combatSection
	)
	default int emergencyFoodCount()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "lootDragonBones",
		name = "Loot Dragon Bones",
		description = "Pick up Dragon Bones (ID: 536) from the ground",
		position = 23,
		section = combatSection
	)
	default boolean lootDragonBones()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lootDragonhide",
		name = "Loot Blue Dragonhide",
		description = "Pick up Blue Dragonhide (ID: 1751) from the ground",
		position = 24,
		section = combatSection
	)
	default boolean lootDragonhide()
	{
		return true;
	}
}
