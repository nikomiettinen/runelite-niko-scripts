/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.faladoragility;

import lombok.Getter;

public enum ScriptState
{
	IDLE("Idle / Paused"),
	RUNNING_OBSTACLE("Running Obstacle"),
	CLIMBING_START("Climbing Start"),
	RETURNING_TO_START("Returning to Start"),
	PICKING_MARK("Picking Mark of Grace"),
	OUT_OF_RANGE("Not in Course Area");

	@Getter
	private final String description;

	ScriptState(String description)
	{
		this.description = description;
	}
}
