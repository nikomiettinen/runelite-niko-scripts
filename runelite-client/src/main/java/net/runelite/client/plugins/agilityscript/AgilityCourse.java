/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.agilityscript;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;

@Getter
@AllArgsConstructor
public enum AgilityCourse
{
	AUTO(
		"Auto-detect",
		Collections.emptyList(),
		null,
		null,
		Collections.emptyList(),
		Collections.emptyList()
	),

	DRAYNOR(
		"Draynor Village",
		Arrays.asList(12338, 12339),
		new WorldPoint(3103, 3279, 0),
		new WorldPoint(3103, 3261, 0),
		Arrays.asList(
			ObjectID.ROOFTOPS_DRAYNOR_WALLCLIMB,      // 11404
			ObjectID.ROOFTOPS_DRAYNOR_TIGHTROPE_1,    // 11405
			ObjectID.ROOFTOPS_DRAYNOR_TIGHTROPE_2,    // 11406
			ObjectID.ROOFTOPS_DRAYNOR_WALLCROSSING,   // 11430
			ObjectID.ROOFTOPS_DRAYNOR_WALLSCRAMBLE,   // 11630
			ObjectID.ROOFTOPS_DRAYNOR_LEAPDOWN,       // 11631
			ObjectID.ROOFTOPS_DRAYNOR_CRATE           // 11632
		),
		Arrays.asList(
			new WorldPoint(3103, 3268, 0),
			new WorldPoint(3103, 3279, 0)
		)
	),

	VARROCK(
		"Varrock",
		Arrays.asList(12853, 12852, 12597, 12596, 12854),
		new WorldPoint(3221, 3414, 0),
		new WorldPoint(3236, 3417, 0),
		Arrays.asList(
			ObjectID.ROOFTOPS_VARROCK_WALLCLIMB,     // 14412
			ObjectID.ROOFTOPS_VARROCK_CLOTHESLINE,   // 14413
			ObjectID.ROOFTOPS_VARROCK_LEAPTORUINS,   // 14414
			ObjectID.ROOFTOPS_VARROCK_WALLSWING,     // 14832
			ObjectID.ROOFTOPS_VARROCK_WALLSCRAMBLE,  // 14833
			ObjectID.ROOFTOPS_VARROCK_LEAPTOBALCONY, // 14834
			ObjectID.ROOFTOPS_VARROCK_LEAPDOWN,      // 14835
			ObjectID.ROOFTOPS_VARROCK_STEPUPROOF,    // 14836
			ObjectID.ROOFTOPS_VARROCK_FINISH         // 14841
		),
		Arrays.asList(
			new WorldPoint(3236, 3417, 0),
			new WorldPoint(3229, 3415, 0),
			new WorldPoint(3212, 3414, 0),
			new WorldPoint(3221, 3414, 0)
		)
	),

	CANIFIS(
		"Canifis",
		Arrays.asList(13878, 13622, 14134),
		new WorldPoint(3507, 3488, 0),
		new WorldPoint(3510, 3485, 0),
		Arrays.asList(
			ObjectID.ROOFTOPS_CANIFIS_START_TREE, // 14843
			ObjectID.ROOFTOPS_CANIFIS_JUMP,       // 14844
			ObjectID.ROOFTOPS_CANIFIS_JUMP_2,     // 14845
			ObjectID.ROOFTOPS_CANIFIS_JUMP_5,     // 14848
			ObjectID.ROOFTOPS_CANIFIS_JUMP_3,     // 14846
			ObjectID.ROOFTOPS_CANIFIS_POLEVAULT,  // 14894
			ObjectID.ROOFTOPS_CANIFIS_JUMP_4,     // 14847
			ObjectID.ROOFTOPS_CANIFIS_LEAPDOWN    // 14897
		),
		Arrays.asList(
			new WorldPoint(3510, 3485, 0),
			new WorldPoint(3498, 3492, 0),
			new WorldPoint(3503, 3488, 0),
			new WorldPoint(3507, 3488, 0)
		)
	),

	FALADOR(
		"Falador",
		Arrays.asList(12084, 11828, 12083),
		new WorldPoint(3036, 3341, 0),
		new WorldPoint(3029, 3333, 0),
		Arrays.asList(
			ObjectID.ROOFTOPS_FALADOR_WALLCLIMB,       // 14898
			ObjectID.ROOFTOPS_FALADOR_TIGHTROPE_1,     // 14899
			ObjectID.ROOFTOPS_FALADOR_HANDHOLDS_START, // 14901
			ObjectID.ROOFTOPS_FALADOR_GAP_1,           // 14903
			ObjectID.ROOFTOPS_FALADOR_GAP_2,           // 14904
			ObjectID.ROOFTOPS_FALADOR_TIGHTROPE_2,     // 14905
			ObjectID.ROOFTOPS_FALADOR_TIGHTROPE_3,     // 14911
			ObjectID.ROOFTOPS_FALADOR_GAP_3,           // 14919
			ObjectID.ROOFTOPS_FALADOR_LEDGE_1,         // 14920
			ObjectID.ROOFTOPS_FALADOR_LEDGE_2,         // 14921
			ObjectID.ROOFTOPS_FALADOR_LEDGE_3A,        // 14922
			ObjectID.ROOFTOPS_FALADOR_LEDGE_4,         // 14924
			ObjectID.ROOFTOPS_FALADOR_EDGE             // 14925
		),
		Arrays.asList(
			new WorldPoint(3029, 3333, 0),
			new WorldPoint(3036, 3336, 0),
			new WorldPoint(3036, 3341, 0),
			new WorldPoint(3030, 3350, 0),
			new WorldPoint(3045, 3350, 0),
			new WorldPoint(3040, 3340, 0)
		)
	),

	CAMELOT(
		"Camelot / Seers",
		Arrays.asList(10806, 10550, 10805, 11062),
		new WorldPoint(2729, 3488, 0),
		new WorldPoint(2704, 3464, 0),
		Arrays.asList(
			ObjectID.ROOFTOPS_SEERS_WALLCLIMB, // 14927
			ObjectID.ROOFTOPS_SEERS_JUMP,      // 14928
			ObjectID.ROOFTOPS_SEERS_TIGHTROPE, // 14932
			ObjectID.ROOFTOPS_SEERS_JUMP_1,    // 14929
			ObjectID.ROOFTOPS_SEERS_JUMP_2,    // 14930
			ObjectID.ROOFTOPS_SEERS_LEAPDOWN   // 14931
		),
		Arrays.asList(
			new WorldPoint(2704, 3464, 0),
			new WorldPoint(2721, 3468, 0),
			new WorldPoint(2725, 3478, 0),
			new WorldPoint(2729, 3488, 0)
		)
	);

	private final String courseName;
	private final List<Integer> regionIds;
	private final WorldPoint startPoint;
	private final WorldPoint finishPoint;
	private final List<Integer> obstacleIds;
	private final List<WorldPoint> returnWaypoints;

	@Override
	public String toString()
	{
		return courseName;
	}

	public static AgilityCourse detectCourse(WorldPoint wp)
	{
		if (wp == null)
		{
			return null;
		}

		int region = wp.getRegionID();
		for (AgilityCourse course : values())
		{
			if (course != AUTO && course.getRegionIds().contains(region))
			{
				return course;
			}
		}

		// Fallback check by distance to course start points (within 100 tiles)
		AgilityCourse closest = null;
		int minDist = 100;
		for (AgilityCourse course : values())
		{
			if (course != AUTO && course.getStartPoint() != null)
			{
				int dist = wp.distanceTo2D(course.getStartPoint());
				if (dist < minDist)
				{
					minDist = dist;
					closest = course;
				}
			}
		}

		return closest;
	}

	public int getRoofIndex(WorldPoint wp, int plane)
	{
		if (wp == null || plane == 0)
		{
			return -1;
		}

		int x = wp.getX();
		int y = wp.getY();

		switch (this)
		{
			case DRAYNOR:
				// Draynor doesn't use hardcoded bounding boxes; dynamic obstacle scanning is used
				return -1;

			case VARROCK:
				// Roof 8 (Finish edge 14841 - northern section of eastern building)
				if (x >= 3235 && x <= 3244 && y >= 3409 && y <= 3425) return 8;
				// Roof 7 (Step up roof 14836 - southern section of eastern building)
				if (x >= 3235 && x <= 3244 && y >= 3393 && y <= 3408) return 7;
				// Roof 6 (Leap down 14835)
				if (x >= 3216 && x <= 3234 && y >= 3393 && y <= 3408) return 6;
				// Roof 5 (Leap to balcony 14834)
				if (x >= 3200 && x <= 3215 && y >= 3393 && y <= 3408) return 5;
				// Roof 4 (Gap leap 14833)
				if (x >= 3190 && x <= 3199 && y >= 3395 && y <= 3409) return 4;
				// Roof 3 (Church roof & wall 14832)
				if (x >= 3190 && x <= 3200 && y >= 3410 && y <= 3421) return 3;
				// Roof 2 (Leap to ruins 14414)
				if (x >= 3201 && x <= 3211 && y >= 3410 && y <= 3421) return 2;
				// Roof 1 (Clothes line 14413)
				if (x >= 3211 && x <= 3226 && y >= 3410 && y <= 3422) return 1;
				return -1;

			case CANIFIS:
				if (plane == 2 && x >= 3508 && x <= 3519 && y >= 3474 && y <= 3487) return 7;
				if (plane == 3 && x >= 3495 && x <= 3508 && y >= 3470 && y <= 3482) return 6;
				if (plane == 2 && x >= 3475 && x <= 3489 && y >= 3480 && y <= 3488) return 5;
				if (plane == 3 && x >= 3474 && x <= 3486 && y >= 3486 && y <= 3502) return 4;
				if (plane == 2 && x >= 3485 && x <= 3495 && y >= 3496 && y <= 3507) return 3;
				if (plane == 2 && x >= 3495 && x <= 3507 && y >= 3501 && y <= 3513) return 2;
				if (plane == 2 && x >= 3504 && x <= 3516 && y >= 3490 && y <= 3500) return 1;
				return -1;

			case FALADOR:
				if (x >= 3016 && x <= 3026 && y >= 3331 && y <= 3337) return 12;
				if (x >= 3012 && x <= 3018 && y >= 3331 && y <= 3335) return 11;
				if (x >= 3012 && x <= 3019 && y >= 3334 && y <= 3339) return 10;
				if (x >= 3014 && x <= 3022 && y >= 3338 && y <= 3344) return 9;
				if (x >= 3016 && x <= 3023 && y >= 3343 && y <= 3349) return 8;
				if (x >= 3016 && x <= 3024 && y >= 3350 && y <= 3357) return 7;
				if (x >= 3025 && x <= 3033 && y >= 3348 && y <= 3355) return 6;
				if (x >= 3034 && x <= 3042 && y >= 3352 && y <= 3358) return 5;
				if (x >= 3034 && x <= 3043 && y >= 3359 && y <= 3366) return 4;
				if (x >= 3044 && x <= 3052 && y >= 3353 && y <= 3363) return 3;
				if (x >= 3044 && x <= 3052 && y >= 3347 && y <= 3352) return 2;
				if (x >= 3036 && x <= 3043 && y >= 3342 && y <= 3346) return 1;
				return -1;

			case CAMELOT:
				if (x >= 2698 && x <= 2706 && y >= 3460 && y <= 3467) return 5;
				if (x >= 2698 && x <= 2716 && y >= 3468 && y <= 3476) return 4;
				if (x >= 2708 && x <= 2716 && y >= 3477 && y <= 3483) return 3;
				if (x >= 2705 && x <= 2716 && y >= 3488 && y <= 3500) return 2;
				if (x >= 2720 && x <= 2731 && y >= 3490 && y <= 3498) return 1;
				return -1;

			default:
				return -1;
		}
	}
}
