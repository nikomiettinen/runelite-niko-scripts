/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.canifisagility;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("canifisagility")
public interface CanifisAgilityConfig extends Config
{
	@ConfigSection(
		name = "General",
		description = "Canifis Agility automation settings",
		position = 0
	)
	String generalSection = "general";

	@ConfigItem(
		keyName = "startScript",
		name = "Start Script",
		description = "Enable or stop the Canifis Agility automation script",
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
		description = "Estää automaattisen uloskirjautumisen (5-25 min) pitkien agility-sessioiden aikana",
		position = 3,
		section = generalSection
	)
	default boolean antiLogout()
	{
		return true;
	}
}
