/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.woodcutterscript;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum WoodcutterMode
{
	DROP("Drop logs (Powerchop)"),
	BANK("Bank logs");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
