/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.woodcutterscript;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("woodcutterscript")
public interface WoodcutterScriptConfig extends Config
{
	@ConfigItem(
		keyName = "startScript",
		name = "Käynnistä Woodcutter",
		description = "Käynnistää / pysäyttää puiden automaattisen hakkauksen",
		position = 1
	)
	default boolean startScript()
	{
		return false;
	}

	@ConfigItem(
		keyName = "treeType",
		name = "Puutyyppi",
		description = "Valitse mitä puita hakataan lähistöltä",
		position = 2
	)
	default WoodcutterTreeType treeType()
	{
		return WoodcutterTreeType.OAK;
	}

	@ConfigItem(
		keyName = "mode",
		name = "Toimintatila",
		description = "Drop = pudottaa halot maahan (Powerchop), Bank = vie halot lähimpään pankkiin",
		position = 3
	)
	default WoodcutterMode mode()
	{
		return WoodcutterMode.DROP;
	}

	@Range(min = 3, max = 30)
	@ConfigItem(
		keyName = "maxDistance",
		name = "Maksimietäisyys (laattaa)",
		description = "Kuinka kaukaa hahmot etsivät uusia puita",
		position = 4
	)
	default int maxDistance()
	{
		return 15;
	}
}
