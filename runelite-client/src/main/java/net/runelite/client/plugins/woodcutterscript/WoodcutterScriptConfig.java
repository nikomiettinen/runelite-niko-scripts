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
		description = "Valitse mitä puita hakataan ja missä",
		position = 2,
		section = generalSection
	)
	default WoodcutterScriptPlugin.TreeType treeType()
	{
		return WoodcutterScriptPlugin.TreeType.OAK;
	}
}
