/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.woodcutterscript;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum WoodcutterScriptState
{
	IDLE("Odottaa..."),
	FINDING_TREE("Etsitään puuta"),
	MOVING_TO_TREE("Kävellään puulle"),
	CHOPPING("Hakataan puuta"),
	DROPPING_LOGS("Pudotetaan halkoja"),
	WALKING_TO_BANK("Kävellään pankille"),
	BANKING("Talletetaan pankkiin");

	private final String statusName;

	@Override
	public String toString()
	{
		return statusName;
	}
}
