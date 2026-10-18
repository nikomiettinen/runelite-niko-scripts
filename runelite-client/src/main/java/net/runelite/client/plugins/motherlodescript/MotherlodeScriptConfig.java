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
		name = "Mine Upstairs (72+ Mining)",
		description = "Climb the ladder and mine veins on the upper floor",
		position = 2,
		section = generalSection
	)
	default boolean upperFloor()
	{
		return false;
	}

	@ConfigItem(
		keyName = "biggerSack",
		name = "Bigger Sack Unlocked",
		description = "Deposit up to 7 inventories before emptying the sack (instead of 4)",
		position = 3,
		section = generalSection
	)
	default boolean biggerSack()
	{
		return false;
	}

	@Range(min = 20, max = 28)
	@ConfigItem(
		keyName = "minPayDirt",
		name = "Pay-dirt Target",
		description = "Amount of pay-dirt to collect before depositing into hopper",
		position = 4,
		section = generalSection
	)
	default int minPayDirt()
	{
		return 26;
	}

	@ConfigSection(
		name = "Behavior",
		description = "Automation actions",
		position = 10
	)
	String behaviorSection = "behavior";

	@ConfigItem(
		keyName = "dropGems",
		name = "Drop Uncut Gems",
		description = "Automatically drop uncut sapphires, emeralds, rubies, and diamonds",
		position = 11,
		section = behaviorSection
	)
	default boolean dropGems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "clearRockfalls",
		name = "Clear Rockfalls",
		description = "Automatically mine rockfalls blocking tunnels (IDs 26679, 26680)",
		position = 12,
		section = behaviorSection
	)
	default boolean clearRockfalls()
	{
		return true;
	}

	@ConfigItem(
		keyName = "repairStruts",
		name = "Repair Water Wheels",
		description = "Fetch hammer from crate (ID 357) if needed and repair broken struts (ID 26670)",
		position = 13,
		section = behaviorSection
	)
	default boolean repairStruts()
	{
		return true;
	}
}
