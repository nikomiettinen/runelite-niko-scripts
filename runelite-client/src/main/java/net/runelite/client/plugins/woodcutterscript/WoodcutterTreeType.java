/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.woodcutterscript;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum WoodcutterTreeType
{
	ANY("Kaikki puut (Any)", new String[]{"tree", "oak", "willow", "maple", "yew", "magic", "redwood", "teak", "mahogany"}),
	NORMAL("Normal Tree", new String[]{"tree", "evergreen", "dead tree"}),
	OAK("Oak", new String[]{"oak"}),
	WILLOW("Willow", new String[]{"willow"}),
	MAPLE("Maple", new String[]{"maple"}),
	YEW("Yew", new String[]{"yew"}),
	MAGIC("Magic", new String[]{"magic tree"}),
	REDWOOD("Redwood", new String[]{"redwood"}),
	TEAK("Teak", new String[]{"teak"}),
	MAHOGANY("Mahogany", new String[]{"mahogany"});

	private final String displayName;
	private final String[] keywords;

	@Override
	public String toString()
	{
		return displayName;
	}

	public boolean matches(String objectName)
	{
		if (objectName == null)
		{
			return false;
		}
		String lower = objectName.toLowerCase();
		if (lower.contains("stump"))
		{
			return false;
		}
		for (String kw : keywords)
		{
			if (lower.contains(kw))
			{
				return true;
			}
		}
		return false;
	}
}
