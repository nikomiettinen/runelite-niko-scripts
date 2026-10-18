/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.woodcutterscript;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("woodcutterscript")
public interface WoodcutterScriptConfig extends Config
{
	@ConfigItem(
		keyName = "showOverlay",
		name = "Näytä tilapaneeli ruudulla",
		description = "Näyttää reaaliaikaisen tilapaneelin peliruudun vasemmassa yläkulmassa",
		position = 1
	)
	default boolean showOverlay()
	{
		return true;
	}
}
