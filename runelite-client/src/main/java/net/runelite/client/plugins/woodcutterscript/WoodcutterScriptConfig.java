/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.woodcutterscript;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("woodcutterscript")
public interface WoodcutterScriptConfig extends Config
{
	@ConfigSection(
		name = "General",
		description = "Yleiset skriptin asetukset",
		position = 0
	)
	String generalSection = "general";

	@ConfigItem(
		keyName = "startScript",
		name = "Start Script",
		description = "Käynnistä tai pysäytä Woodcutter-skripti",
		position = 1,
		section = generalSection
	)
	default boolean startScript()
	{
		return false;
	}

	@ConfigItem(
		keyName = "treeType",
		name = "Tree and Location",
		description = "Valitse mitä puita hakataan (Normal = Powerchop ilman bankitusta)",
		position = 2,
		section = generalSection
	)
	default WoodcutterScriptPlugin.TreeType treeType()
	{
		return WoodcutterScriptPlugin.TreeType.NORMAL;
	}

	@ConfigItem(
		keyName = "dropMode",
		name = "Normal Logs Drop Mode",
		description = "Pudotetaanko normaalit halot heti vai vasta kun reppu on täynnä",
		position = 3,
		section = generalSection
	)
	default WoodcutterScriptPlugin.DropMode dropMode()
	{
		return WoodcutterScriptPlugin.DropMode.DROP_WHEN_FULL;
	}

	@ConfigItem(
		keyName = "pickupBirdNests",
		name = "Pick Up Bird Nests",
		description = "Poimii automaattisesti maahan putoavat linnunpesät",
		position = 4,
		section = generalSection
	)
	default boolean pickupBirdNests()
	{
		return true;
	}

	@ConfigItem(
		keyName = "antiAfk",
		name = "Anti-AFK",
		description = "Estää automaattisen uloskirjautumisen ja simuloi satunnaista aktiivisuutta",
		position = 5,
		section = generalSection
	)
	default boolean antiAfk()
	{
		return true;
	}
}
