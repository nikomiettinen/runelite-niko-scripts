/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.motherlodescript;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("motherlodescript")
public interface MotherlodeScriptConfig extends Config
{
	@ConfigSection(
		name = "General",
		description = "General script settings",
		position = 0
	)
	String generalSection = "general";

	@ConfigItem(
		keyName = "startScript",
		name = "Start Script",
		description = "Enable or pause the MLM automation script",
		position = 1,
		section = generalSection
	)
	default boolean startScript()
	{
		return false;
	}

	@ConfigItem(
		keyName = "upperFloor",
		name = "Mine Upstairs (Upper Level)",
		description = "Climb the ladder and mine veins on the upper floor",
		position = 2,
		section = generalSection
	)
	default boolean upperFloor()
	{
		return false;
	}

	@ConfigItem(
		keyName = "upperHopperUnlocked",
		name = "Upstairs Hopper (50 Nuggets)",
		description = "Use the hopper on the upper floor (requires 50 golden nuggets). If disabled, climbs down to deposit in lower hopper.",
		position = 3,
		section = generalSection
	)
	default boolean upperHopperUnlocked()
	{
		return false;
	}

	@ConfigItem(
		keyName = "biggerSack",
		name = "Bigger Sack Unlocked",
		description = "Deposit up to 7 inventories before emptying the sack (instead of 4)",
		position = 4,
		section = generalSection
	)
	default boolean biggerSack()
	{
		return false;
	}

	@Range(min = 20, max = 28)
	@ConfigItem(
		keyName = "minPayDirt",
		name = "Deposit At Pay-dirt Count",
		description = "Deposit to hopper when inventory has at least this much pay-dirt (or when full)",
		position = 5,
		section = generalSection
	)
	default int minPayDirt()
	{
		return 26;
	}

	@ConfigItem(
		keyName = "repairStruts",
		name = "Repair Water Wheels",
		description = "Repair broken water wheel struts when near the hopper (requires hammer)",
		position = 6,
		section = generalSection
	)
	default boolean repairStruts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "clearRockfalls",
		name = "Clear Blocking Rockfalls",
		description = "Mine rockfalls blocking the tunnel between the hopper/sack and the mining area",
		position = 7,
		section = generalSection
	)
	default boolean clearRockfalls()
	{
		return true;
	}

	@ConfigItem(
		keyName = "dropGems",
		name = "Drop Uncut Gems",
		description = "Automatically drop uncut gems found while mining pay-dirt to save inventory space",
		position = 8,
		section = generalSection
	)
	default boolean dropGems()
	{
		return false;
	}
}
