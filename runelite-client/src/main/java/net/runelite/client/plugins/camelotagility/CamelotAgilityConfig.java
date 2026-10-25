/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.camelotagility;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("camelotagility")
public interface CamelotAgilityConfig extends Config
{
	@ConfigSection(
		name = "General",
		description = "Camelot / Seers' Village Agility automation settings",
		position = 0
	)
	String generalSection = "general";

	@ConfigItem(
		keyName = "startScript",
		name = "Start Script",
		description = "Enable or stop the Camelot Agility automation script",
		position = 1,
		section = generalSection
	)
	default boolean startScript()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pickupMarksOfGrace",
		name = "Pick up Marks of Grace (11849)",
		description = "Automatically collect Marks of Grace from the rooftops",
		position = 2,
		section = generalSection
	)
	default boolean pickupMarksOfGrace()
	{
		return true;
	}

	@ConfigItem(
		keyName = "antiLogout",
		name = "Anti-Logout (Anti-AFK)",
		description = "Estää automaattisen uloskirjautumisen pitkien agility-sessioiden aikana",
		position = 3,
		section = generalSection
	)
	default boolean antiLogout()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useCamelotTeleport",
		name = "Use Camelot Teleport Tab",
		description = "Käyttää Camelot-teleporttia (tabi) suoraan Seersin pankin eteen kierroksen päätyttyä (Kandarin Hard Diary)",
		position = 4,
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
		position = 5,
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
		position = 6,
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
		position = 7,
		section = generalSection
	)
	default String foodNames()
	{
		return "Shark";
	}
}
