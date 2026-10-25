/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.agilityscript;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("agilityscript")
public interface AgilityScriptConfig extends Config
{
	@ConfigSection(
		name = "General Settings",
		description = "General configuration for Agility Script",
		position = 0
	)
	String generalSection = "general";

	@ConfigItem(
		keyName = "startScript",
		name = "Start Agility Script",
		description = "Toggle to start or stop the agility automation script",
		position = 1,
		section = generalSection
	)
	default boolean startScript()
	{
		return false;
	}

	@ConfigItem(
		keyName = "selectedCourse",
		name = "Course",
		description = "Select course or Auto-detect to automatically run the course corresponding to your location",
		position = 2,
		section = generalSection
	)
	default AgilityCourse selectedCourse()
	{
		return AgilityCourse.AUTO;
	}

	@ConfigItem(
		keyName = "pickupMarksOfGrace",
		name = "Pickup Marks of Grace",
		description = "Automatically collect Marks of Grace when spawned on the current roof",
		position = 3,
		section = generalSection
	)
	default boolean pickupMarksOfGrace()
	{
		return true;
	}

	@ConfigItem(
		keyName = "antiLogout",
		name = "Anti-Logout & Run",
		description = "Prevent logout timeout and auto-activate run energy",
		position = 4,
		section = generalSection
	)
	default boolean antiLogout()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useCamelotTeleport",
		name = "Use Camelot Teleport",
		description = "When doing Camelot course, use Camelot teleport tabs from inventory upon lap completion",
		position = 5,
		section = generalSection
	)
	default boolean useCamelotTeleport()
	{
		return false;
	}

	@ConfigItem(
		keyName = "autoEat",
		name = "Syö ruokaa automaattisesti (Auto Eat)",
		description = "Syö automaattisesti ruokaa repusta kun Hitpointit laskevat",
		position = 6,
		section = generalSection
	)
	default boolean autoEat()
	{
		return true;
	}

	@ConfigItem(
		keyName = "eatAtHp",
		name = "Syö kun HP alle (Eat at HP)",
		description = "Syö ruokaa kun nykyinen HP on alle tai yhtä suuri kuin tämä arvo (tai vastaava prosentti)",
		position = 7,
		section = generalSection
	)
	default int eatAtHp()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "foodNames",
		name = "Ruuan nimi / lista (Food names)",
		description = "Kirjoita haluamasi ruokien nimet pilkulla erotettuna (esim. Shark, Summer pie, Monkfish)",
		position = 8,
		section = generalSection
	)
	default String foodNames()
	{
		return "Shark";
	}
}
