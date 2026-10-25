/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.agilityscript;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ScriptState
{
	IDLE("Idle"),
	RUNNING_OBSTACLE("Traversing Obstacle"),
	CLIMBING_START("Starting Course"),
	RETURNING_TO_START("Returning to Start"),
	PICKING_MARK("Looting Mark of Grace"),
	EATING("Eating Food"),
	OUT_OF_RANGE("Out of Course Area");

	private final String status;
}
