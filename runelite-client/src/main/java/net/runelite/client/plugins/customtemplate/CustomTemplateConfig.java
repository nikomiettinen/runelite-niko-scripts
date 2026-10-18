/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.customtemplate;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("customtemplate")
public interface CustomTemplateConfig extends Config
{
	@ConfigItem(
		keyName = "startScript",
		name = "Start Script",
		description = "Kytkee automaation päälle tai pois päältä",
		position = 1
	)
	default boolean startScript()
	{
		return false;
	}

	@ConfigItem(
		keyName = "exampleSetting",
		name = "Esimerkkiasetus",
		description = "Esimerkki numeerisesta asetuksesta",
		position = 2
	)
	default int exampleSetting()
	{
		return 10;
	}
}
