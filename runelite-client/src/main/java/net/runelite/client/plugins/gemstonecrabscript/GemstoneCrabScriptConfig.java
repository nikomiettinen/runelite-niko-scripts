/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.gemstonecrabscript;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("gemstonecrabscript")
public interface GemstoneCrabScriptConfig extends Config
{
	@ConfigSection(
		name = "General",
		description = "Yleiset asetukset",
		position = 0
	)
	String generalSection = "general";

	@ConfigItem(
		keyName = "startScript",
		name = "Start Script",
		description = "Käynnistä tai pysäytä Gemstone Crab -skripti",
		position = 1,
		section = generalSection
	)
	default boolean startScript()
	{
		return false;
	}

	@ConfigItem(
		keyName = "enterCaveWhenDead",
		name = "Enter Cave When Dead",
		description = "Kulje luolasta automaattisesti seuraavaan monttuun kun rapu kuolee tai kaivautuu",
		position = 2,
		section = generalSection
	)
	default boolean enterCaveWhenDead()
	{
		return true;
	}

	@ConfigItem(
		keyName = "mineShell",
		name = "Mine Shell When Burrowed",
		description = "Louhi ravun jättämä kuori ennen luolaan menemistä (jos hakku mukana)",
		position = 3,
		section = generalSection
	)
	default boolean mineShell()
	{
		return false;
	}

	@ConfigItem(
		keyName = "antiAfk",
		name = "Anti-AFK",
		description = "Estää automaattisen uloskirjautumisen ja simuloi satunnaista aktiivisuutta",
		position = 4,
		section = generalSection
	)
	default boolean antiAfk()
	{
		return true;
	}
}
