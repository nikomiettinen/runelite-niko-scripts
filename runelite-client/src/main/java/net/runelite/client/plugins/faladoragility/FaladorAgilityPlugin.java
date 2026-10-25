/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.faladoragility;

import com.google.inject.Provides;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Falador Agility",
	description = "Automates Falador rooftop agility course with auto-recovery and Mark of Grace pickup",
	tags = {"agility", "falador", "rooftop", "bot"}
)
public class FaladorAgilityPlugin extends Plugin
{
	private static final int DEFAULT_LOGOUT_TIMEOUT = 500;
	private static final int EXTENDED_LOGOUT_TIMEOUT = 14000;

	public static final List<Integer> REGION_IDS = Arrays.asList(12084, 11828, 12083);
	public static final WorldPoint START_POINT = new WorldPoint(3036, 3341, 0);
	public static final WorldPoint FINISH_POINT = new WorldPoint(3029, 3333, 0);
	public static final List<Integer> OBSTACLE_IDS = Arrays.asList(
		ObjectID.ROOFTOPS_FALADOR_WALLCLIMB,       // 14898 (Rough wall, Climb)
		ObjectID.ROOFTOPS_FALADOR_TIGHTROPE_1,     // 14899 (Tightrope, Cross)
		ObjectID.ROOFTOPS_FALADOR_HANDHOLDS_START, // 14901 (Hand holds, Cross)
		ObjectID.ROOFTOPS_FALADOR_GAP_1,           // 14903 (Gap, Jump)
		ObjectID.ROOFTOPS_FALADOR_GAP_2,           // 14904 (Gap, Jump)
		ObjectID.ROOFTOPS_FALADOR_TIGHTROPE_2,     // 14905 (Tightrope, Cross)
		ObjectID.ROOFTOPS_FALADOR_TIGHTROPE_3,     // 14911 (Tightrope, Cross)
		ObjectID.ROOFTOPS_FALADOR_GAP_3,           // 14919 (Gap, Jump)
		ObjectID.ROOFTOPS_FALADOR_LEDGE_1,         // 14920 (Ledge, Jump)
		ObjectID.ROOFTOPS_FALADOR_LEDGE_2,         // 14921 (Ledge, Jump)
		ObjectID.ROOFTOPS_FALADOR_LEDGE_3A,        // 14922 (Ledge, Jump)
		ObjectID.ROOFTOPS_FALADOR_LEDGE_4,         // 14924 (Ledge, Jump)
		ObjectID.ROOFTOPS_FALADOR_EDGE             // 14925 (Edge, Jump down)
	);
	public static final List<WorldPoint> RETURN_WAYPOINTS = Arrays.asList(
		new WorldPoint(3029, 3333, 0),
		new WorldPoint(3036, 3336, 0),
		new WorldPoint(3036, 3341, 0),
		new WorldPoint(3030, 3350, 0),
		new WorldPoint(3045, 3350, 0),
		new WorldPoint(3040, 3340, 0)
	);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private FaladorAgilityOverlay overlay;

	@Inject
	private FaladorAgilityConfig config;

	@Getter
	private ScriptState currentState = ScriptState.IDLE;

	@Getter
	private boolean inCourseArea = false;

	@Getter
	private int lapsCompleted = 0;

	@Getter
	private int marksCollected = 0;

	@Getter
	private int currentObstacleIndex = 0;

	private int lastInteractedObstacleId = -1;

	private final Set<TileObject> obstacles = new HashSet<>();
	private final Set<Tile> marksOfGrace = new HashSet<>();

	private int cooldownTicks = 0;
	private boolean wasOnRooftop = false;
	private int lastAgilityXp = -1;
	private LocalPoint previousPlayerLoc = null;
	private int stuckTicks = 0;
	private int previousPlane = -1;

	private int markAttempts = 0;
	private WorldPoint lastMarkLocation = null;

	private int nextAntiLogoutThreshold = 6_000 + (int) (Math.random() * 4_000);
	private long lastAntiLogoutTimeMs = 0;

	@Provides
	FaladorAgilityConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FaladorAgilityConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		lapsCompleted = 0;
		marksCollected = 0;
		wasOnRooftop = false;
		stuckTicks = 0;
		previousPlayerLoc = null;
		previousPlane = -1;
		currentObstacleIndex = 0;
		lastInteractedObstacleId = -1;
		markAttempts = 0;
		lastMarkLocation = null;

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			lastAgilityXp = client.getSkillExperience(Skill.AGILITY);
			previousPlane = client.getPlane();
			clientThread.invokeLater(() -> {
				scanScene();
				Player localPlayer = client.getLocalPlayer();
				if (localPlayer != null && client.getPlane() > 0)
				{
					wasOnRooftop = true;
					currentObstacleIndex = detectRooftopObstacleIndex(localPlayer);
					log.info("Falador: Plugin startup mid-course on rooftop! Synced obstacle index to {}", currentObstacleIndex);
				}
			});
		}

		if (config.antiLogout())
		{
			client.setIdleTimeout(EXTENDED_LOGOUT_TIMEOUT);
		}
		log.info("Falador Agility plugin started");
	}

	@Override
	protected void shutDown()
	{
		client.setIdleTimeout(DEFAULT_LOGOUT_TIMEOUT);
		overlayManager.remove(overlay);
		obstacles.clear();
		marksOfGrace.clear();
		currentState = ScriptState.IDLE;
		inCourseArea = false;
		lastInteractedObstacleId = -1;
		markAttempts = 0;
		lastMarkLocation = null;
		log.info("Falador Agility plugin stopped");
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("faladoragility"))
		{
			return;
		}

		if (event.getKey().equals("startScript"))
		{
			if (config.startScript())
			{
				log.info("Falador Agility script started by user");
				currentState = ScriptState.IDLE;
				lastInteractedObstacleId = -1;
				stuckTicks = 0;
				cooldownTicks = 0;
				markAttempts = 0;
				lastMarkLocation = null;
				lastAgilityXp = client.getSkillExperience(Skill.AGILITY);
				previousPlane = client.getPlane();
				clientThread.invokeLater(() -> {
					scanScene();
					Player localPlayer = client.getLocalPlayer();
					if (localPlayer != null && client.getPlane() > 0)
					{
						wasOnRooftop = true;
						currentObstacleIndex = detectRooftopObstacleIndex(localPlayer);
						log.info("Falador: Script started mid-course on rooftop! Synced obstacle index to {}", currentObstacleIndex);
					}
					else
					{
						wasOnRooftop = false;
						currentObstacleIndex = 0;
					}
				});
			}
			else
			{
				log.info("Falador Agility script stopped by user");
				currentState = ScriptState.IDLE;
				stuckTicks = 0;
				cooldownTicks = 0;
				currentObstacleIndex = 0;
				lastInteractedObstacleId = -1;
				markAttempts = 0;
				lastMarkLocation = null;
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			lastAgilityXp = client.getSkillExperience(Skill.AGILITY);
			previousPlane = client.getPlane();
			lastInteractedObstacleId = -1;
			stuckTicks = 0;
			cooldownTicks = 0;
			markAttempts = 0;
			lastMarkLocation = null;
			clientThread.invokeLater(() -> {
				scanScene();
				Player localPlayer = client.getLocalPlayer();
				if (localPlayer != null && client.getPlane() > 0)
				{
					wasOnRooftop = true;
					currentObstacleIndex = detectRooftopObstacleIndex(localPlayer);
					log.info("Falador: Logged in mid-course on rooftop! Synced obstacle index to {}", currentObstacleIndex);
				}
				else
				{
					wasOnRooftop = false;
					currentObstacleIndex = 0;
				}
			});
		}
		else if (event.getGameState() == GameState.LOADING)
		{
			obstacles.clear();
			marksOfGrace.clear();
			previousPlane = client.getPlane();
			clientThread.invokeLater(this::scanScene);
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			obstacles.clear();
			marksOfGrace.clear();
			currentState = ScriptState.IDLE;
			inCourseArea = false;
			previousPlane = -1;
			currentObstacleIndex = 0;
			lastInteractedObstacleId = -1;
			markAttempts = 0;
			lastMarkLocation = null;
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged statChanged)
	{
		if (statChanged.getSkill() != Skill.AGILITY)
		{
			return;
		}

		int currentXp = statChanged.getXp();
		if (lastAgilityXp != -1 && currentXp > lastAgilityXp)
		{
			int totalSteps = OBSTACLE_IDS.size();
			lastInteractedObstacleId = -1;
			cooldownTicks = 0;
			markAttempts = 0;
			lastMarkLocation = null;

			if (currentObstacleIndex >= totalSteps - 1)
			{
				lapsCompleted++;
				wasOnRooftop = false;
				currentObstacleIndex = 0;
				lastInteractedObstacleId = -1;
				log.info("Falador lap completed! Total laps: {}", lapsCompleted);
			}
			else
			{
				currentObstacleIndex++;
				lastInteractedObstacleId = -1;
				if (currentObstacleIndex >= totalSteps)
				{
					currentObstacleIndex = totalSteps - 1;
				}
				log.info("Agility XP gained! Advanced to obstacle index {}", currentObstacleIndex);
			}
		}
		lastAgilityXp = currentXp;
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned itemSpawned)
	{
		TileItem item = itemSpawned.getItem();
		if (item != null && item.getId() == ItemID.MARK_OF_GRACE)
		{
			marksOfGrace.add(itemSpawned.getTile());
			log.debug("Mark of grace spawned at tile: {}", itemSpawned.getTile().getWorldLocation());
		}
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned itemDespawned)
	{
		TileItem item = itemDespawned.getItem();
		if (item != null && item.getId() == ItemID.MARK_OF_GRACE)
		{
			if (marksOfGrace.remove(itemDespawned.getTile()))
			{
				markAttempts = 0;
				lastMarkLocation = null;
				if (currentState == ScriptState.PICKING_MARK)
				{
					marksCollected++;
					log.info("Mark of Grace collected! Total marks: {}", marksCollected);
				}
			}
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		checkAddObstacle(event.getGameObject());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		obstacles.remove(event.getGameObject());
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned event)
	{
		checkAddObstacle(event.getWallObject());
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned event)
	{
		obstacles.remove(event.getWallObject());
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
	{
		checkAddObstacle(event.getDecorativeObject());
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
	{
		obstacles.remove(event.getDecorativeObject());
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		checkAddObstacle(event.getGroundObject());
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned event)
	{
		obstacles.remove(event.getGroundObject());
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		log.info("MenuOptionClicked: op='{}' targ='{}' action={} id={} p0={} p1={} itemId={}",
			event.getMenuOption(), event.getMenuTarget(), event.getMenuAction(), event.getId(),
			event.getParam0(), event.getParam1(), event.getItemId());
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (config.antiLogout())
		{
			handleAntiLogout();
		}

		if (!config.startScript())
		{
			currentState = ScriptState.IDLE;
			stuckTicks = 0;
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		inCourseArea = checkInCourseArea(localPlayer);
		if (!inCourseArea)
		{
			currentState = ScriptState.OUT_OF_RANGE;
			return;
		}

		int currentPlane = client.getPlane();
		if (previousPlane != currentPlane)
		{
			log.info("Plane changed from {} to {}, rescanning scene", previousPlane, currentPlane);
			scanScene();
			previousPlane = currentPlane;
		}

		// Ensure objects are populated if missed during load
		if (obstacles.isEmpty())
		{
			scanScene();
		}

		// Automatically toggle run if energy > 20 and run is off
		handleRunEnergy();

		// Track movement and stuck state
		LocalPoint currentLoc = localPlayer.getLocalLocation();
		boolean isMoving = previousPlayerLoc != null && !previousPlayerLoc.equals(currentLoc);
		if (localPlayer.getAnimation() != -1 || isMoving)
		{
			stuckTicks = 0;
		}
		else
		{
			stuckTicks++;
		}
		previousPlayerLoc = currentLoc;

		// If player is actively animating across an obstacle, wait
		if (localPlayer.getAnimation() != -1)
		{
			return;
		}

		// If player is actively moving towards the clicked obstacle, wait
		if (isMoving && lastInteractedObstacleId != -1)
		{
			return;
		}

		// If stuck for 6+ ticks without animating or moving, allow re-trying interaction
		if (stuckTicks >= 6)
		{
			lastInteractedObstacleId = -1;
		}

		if (cooldownTicks > 0)
		{
			cooldownTicks--;
			return;
		}

		// 1. Player is on the rooftops (plane > 0)
		if (currentPlane > 0)
		{
			wasOnRooftop = true;

			// Synchronize obstacle index with the specific roof the player is on
			syncObstacleIndexWithLocation(localPlayer);

			if (currentObstacleIndex < 1)
			{
				currentObstacleIndex = detectRooftopObstacleIndex(localPlayer);
			}

			// If stuck for 10+ ticks stationary on a roof, re-detect obstacle on current roof
			if (stuckTicks >= 10)
			{
				int resyncedIdx = detectRooftopObstacleIndex(localPlayer);
				if (resyncedIdx > currentObstacleIndex)
				{
					log.info("Stationary on roof: updating obstacle index from {} to {}", currentObstacleIndex, resyncedIdx);
					currentObstacleIndex = resyncedIdx;
				}
				stuckTicks = 0;
			}

			// A. Collect Mark of Grace if available on current roof
			if (config.pickupMarksOfGrace() && handleMarkOfGrace(localPlayer))
			{
				return;
			}

			// B. Find and traverse the next obstacle on the rooftop
			TileObject nextObstacle = findNextObstacle(localPlayer);
			if (nextObstacle == null)
			{
				int resyncedIdx = detectRooftopObstacleIndex(localPlayer);
				if (resyncedIdx > currentObstacleIndex)
				{
					log.info("Target obstacle not in reach: updating index from {} to {}", currentObstacleIndex, resyncedIdx);
					currentObstacleIndex = resyncedIdx;
					nextObstacle = findNextObstacle(localPlayer);
				}
			}

			if (nextObstacle != null)
			{
				currentState = ScriptState.RUNNING_OBSTACLE;
				interactObstacle(nextObstacle);
				cooldownTicks = 2;
				return;
			}
		}
		// 2. Player is on the ground (plane == 0): either finished lap, started script, or fell down
		else
		{
			if (wasOnRooftop)
			{
				if (localPlayer.getWorldLocation().distanceTo2D(FINISH_POINT) <= 8)
				{
					lapsCompleted++;
					log.info("Lap finished! Total laps completed: {}", lapsCompleted);
				}
				wasOnRooftop = false;
			}

			currentObstacleIndex = 0;
			lastInteractedObstacleId = -1;
			handleGroundRecovery(localPlayer);
		}
	}

	private void handleGroundRecovery(Player localPlayer)
	{
		wasOnRooftop = false;
		currentObstacleIndex = 0;
		lastInteractedObstacleId = -1;
		markAttempts = 0;
		lastMarkLocation = null;
		WorldPoint playerPos = localPlayer.getWorldLocation();

		// If start obstacle is in scene and nearby (within 24 tiles), climb it directly!
		TileObject startObstacle = findStartObstacle();
		if (startObstacle != null)
		{
			int dist = getDistanceToObstacle(localPlayer, startObstacle);
			if (dist <= 24)
			{
				currentState = ScriptState.CLIMBING_START;
				log.info("Climbing Falador rough wall {} (dist: {} tiles)", startObstacle.getId(), dist);
				interactObstacle(startObstacle);
				cooldownTicks = 2;
				return;
			}
		}

		// Otherwise, walk along recovery waypoints back to start
		currentState = ScriptState.RETURNING_TO_START;
		int distToStart = playerPos.distanceTo2D(START_POINT);
		WorldPoint targetWaypoint = RETURN_WAYPOINTS.stream()
			.filter(wp -> playerPos.distanceTo2D(wp) > 2)
			.filter(wp -> wp.distanceTo2D(START_POINT) < distToStart)
			.min(Comparator.comparingInt(wp -> wp.distanceTo2D(START_POINT)))
			.orElse(START_POINT);

		log.debug("Walking towards Falador start waypoint {}", targetWaypoint);
		walkToWorldPoint(targetWaypoint);
		cooldownTicks = 2;
	}

	private TileObject findStartObstacle()
	{
		int startId = OBSTACLE_IDS.get(0);
		TileObject found = obstacles.stream()
			.filter(o -> o != null && o.getId() == startId)
			.findFirst()
			.orElse(null);

		if (found != null)
		{
			return found;
		}

		// Fallback: search at course start location in scene
		LocalPoint lp = LocalPoint.fromWorld(client, START_POINT);
		if (lp != null)
		{
			int sceneX = lp.getSceneX();
			int sceneY = lp.getSceneY();
			Tile[][][] tiles = client.getScene().getTiles();
			if (tiles != null && sceneX >= 0 && sceneX < Constants.SCENE_SIZE && sceneY >= 0 && sceneY < Constants.SCENE_SIZE)
			{
				Tile tile = tiles[0][sceneX][sceneY];
				if (tile != null)
				{
					if (tile.getWallObject() != null && tile.getWallObject().getId() == startId)
					{
						return tile.getWallObject();
					}
					if (tile.getDecorativeObject() != null && tile.getDecorativeObject().getId() == startId)
					{
						return tile.getDecorativeObject();
					}
					if (tile.getGroundObject() != null && tile.getGroundObject().getId() == startId)
					{
						return tile.getGroundObject();
					}
					if (tile.getGameObjects() != null)
					{
						for (GameObject go : tile.getGameObjects())
						{
							if (go != null && go.getId() == startId)
							{
								return go;
							}
						}
					}
				}
			}
		}

		return null;
	}

	private TileObject findNextObstacle(Player localPlayer)
	{
		if (currentObstacleIndex < 1)
		{
			currentObstacleIndex = 1;
		}

		// Look specifically for OBSTACLE_IDS.get(currentObstacleIndex) within 25 tiles
		if (currentObstacleIndex < OBSTACLE_IDS.size())
		{
			int targetId = OBSTACLE_IDS.get(currentObstacleIndex);
			List<TileObject> matching = obstacles.stream()
				.filter(obj -> obj != null
					&& isMatchingObstacle(obj.getId(), targetId)
					&& getDistanceToObstacle(localPlayer, obj) <= 25)
				.collect(Collectors.toList());

			if (!matching.isEmpty())
			{
				TileObject withAction = matching.stream()
					.filter(this::hasInteractAction)
					.min(Comparator.comparingInt(obj -> getDistanceToObstacle(localPlayer, obj)))
					.orElse(null);

				if (withAction != null)
				{
					return withAction;
				}

				return matching.stream()
					.min(Comparator.comparingInt(obj -> getDistanceToObstacle(localPlayer, obj)))
					.orElse(null);
			}
		}

		return null;
	}

	private boolean isMatchingObstacle(int objId, int targetId)
	{
		if (objId == targetId)
		{
			return true;
		}
		if (targetId == ObjectID.ROOFTOPS_FALADOR_HANDHOLDS_START && objId == ObjectID.ROOFTOPS_FALADOR_HANDHOLDS)
		{
			return true;
		}
		if (targetId == ObjectID.ROOFTOPS_FALADOR_LEDGE_3A && objId == ObjectID.ROOFTOPS_FALADOR_LEDGE_3B)
		{
			return true;
		}
		if (targetId == ObjectID.ROOFTOPS_FALADOR_TIGHTROPE_2 && objId == ObjectID.ROOFTOPS_FALADOR_TIGHTROPE_FORDIAGONAL)
		{
			return true;
		}
		return false;
	}

	public int getObstacleIndex(int objectId)
	{
		if (objectId == ObjectID.ROOFTOPS_FALADOR_HANDHOLDS)
		{
			return OBSTACLE_IDS.indexOf(ObjectID.ROOFTOPS_FALADOR_HANDHOLDS_START);
		}
		if (objectId == ObjectID.ROOFTOPS_FALADOR_LEDGE_3B)
		{
			return OBSTACLE_IDS.indexOf(ObjectID.ROOFTOPS_FALADOR_LEDGE_3A);
		}
		if (objectId == ObjectID.ROOFTOPS_FALADOR_TIGHTROPE_FORDIAGONAL)
		{
			return OBSTACLE_IDS.indexOf(ObjectID.ROOFTOPS_FALADOR_TIGHTROPE_2);
		}
		return OBSTACLE_IDS.indexOf(objectId);
	}

	private boolean hasInteractAction(TileObject object)
	{
		if (object == null)
		{
			return false;
		}
		try
		{
			ObjectComposition def = client.getObjectDefinition(object.getId());
			if (def != null)
			{
				if (def.getImpostorIds() != null)
				{
					def = def.getImpostor();
				}
				if (def != null && def.getActions() != null)
				{
					for (String act : def.getActions())
					{
						if (act != null && !act.isEmpty() && !act.equalsIgnoreCase("Examine"))
						{
							return true;
						}
					}
				}
			}
		}
		catch (Exception ignored)
		{
		}
		return false;
	}

	public int getRoofIndex(WorldPoint wp, int plane)
	{
		if (wp == null || plane == 0)
		{
			return -1;
		}

		int x = wp.getX();
		int y = wp.getY();

		// Roof 12 -> Edge (14925)
		if (x >= 3016 && x <= 3026 && y >= 3331 && y <= 3337)
		{
			return 12;
		}
		// Roof 11 -> Ledge 4 (14924)
		if (x >= 3012 && x <= 3018 && y >= 3331 && y <= 3335)
		{
			return 11;
		}
		// Roof 10 -> Ledge 3 (14922)
		if (x >= 3012 && x <= 3019 && y >= 3334 && y <= 3339)
		{
			return 10;
		}
		// Roof 9 -> Ledge 2 (14921)
		if (x >= 3014 && x <= 3022 && y >= 3338 && y <= 3344)
		{
			return 9;
		}
		// Roof 8 -> Ledge 1 (14920)
		if (x >= 3016 && x <= 3023 && y >= 3343 && y <= 3349)
		{
			return 8;
		}
		// Roof 7 -> Gap 3 (14919)
		if (x >= 3016 && x <= 3024 && y >= 3350 && y <= 3357)
		{
			return 7;
		}
		// Roof 6 -> Tightrope 3 (14911)
		if (x >= 3025 && x <= 3033 && y >= 3348 && y <= 3355)
		{
			return 6;
		}
		// Roof 5 -> Tightrope 2 (14905)
		if (x >= 3034 && x <= 3042 && y >= 3352 && y <= 3358)
		{
			return 5;
		}
		// Roof 4 -> Gap 2 (14904)
		if (x >= 3034 && x <= 3043 && y >= 3359 && y <= 3366)
		{
			return 4;
		}
		// Roof 3 -> Gap 1 (14903)
		if (x >= 3044 && x <= 3052 && y >= 3353 && y <= 3363)
		{
			return 3;
		}
		// Roof 2 -> Handholds (14901)
		if (x >= 3044 && x <= 3052 && y >= 3347 && y <= 3352)
		{
			return 2;
		}
		// Roof 1 -> Tightrope 1 (14899)
		if (x >= 3036 && x <= 3043 && y >= 3342 && y <= 3346)
		{
			return 1;
		}

		return -1;
	}

	public int getObstacleIndexFromLocation(Player localPlayer)
	{
		if (localPlayer == null)
		{
			return -1;
		}
		return getRoofIndex(localPlayer.getWorldLocation(), client.getPlane());
	}

	private void syncObstacleIndexWithLocation(Player localPlayer)
	{
		int detected = getObstacleIndexFromLocation(localPlayer);
		if (detected > currentObstacleIndex)
		{
			markAttempts = 0;
			lastMarkLocation = null;
			log.info("Falador rooftop position indicates Roof {}: advancing obstacle index from {} to {}",
				detected, currentObstacleIndex, detected);
			currentObstacleIndex = detected;
		}
	}

	private int detectRooftopObstacleIndex(Player localPlayer)
	{
		if (obstacles.isEmpty())
		{
			scanScene();
		}

		// 1. Check coordinates first for 100% precision
		int locIndex = getObstacleIndexFromLocation(localPlayer);
		if (locIndex >= 1)
		{
			log.info("Falador: Detected rooftop obstacle from coordinates: Roof {}", locIndex);
			return locIndex;
		}

		int minIdx = Math.max(1, currentObstacleIndex);

		// 2. Search for departure obstacles on current roof (within 8 tiles)
		TileObject immediate = obstacles.stream()
			.filter(obj -> obj != null)
			.filter(obj -> {
				int idx = getObstacleIndex(obj.getId());
				return idx >= minIdx && idx < OBSTACLE_IDS.size() && hasInteractAction(obj);
			})
			.filter(obj -> getDistanceToObstacle(localPlayer, obj) <= 8)
			.min(Comparator.comparingInt(obj -> getDistanceToObstacle(localPlayer, obj)))
			.orElse(null);

		if (immediate != null)
		{
			int idx = getObstacleIndex(immediate.getId());
			log.info("Falador: Detected departure rooftop obstacle (radius 8): {} (index {})", immediate.getId(), idx);
			return idx;
		}

		// 3. Fallback: closest reachable rooftop obstacle within 15 tiles
		TileObject closest = obstacles.stream()
			.filter(obj -> obj != null)
			.filter(obj -> {
				int idx = getObstacleIndex(obj.getId());
				return idx >= minIdx && idx < OBSTACLE_IDS.size() && hasInteractAction(obj);
			})
			.filter(obj -> getDistanceToObstacle(localPlayer, obj) <= 15)
			.min(Comparator.comparingInt(obj -> getDistanceToObstacle(localPlayer, obj)))
			.orElse(null);

		if (closest != null)
		{
			int idx = getObstacleIndex(closest.getId());
			log.info("Falador: Detected closest rooftop obstacle (radius 15): {} (index {})", closest.getId(), idx);
			return idx;
		}

		return Math.max(1, currentObstacleIndex);
	}

	private int getDistanceToObstacle(Player player, TileObject object)
	{
		if (player == null || object == null)
		{
			return Integer.MAX_VALUE;
		}

		LocalPoint pLoc = player.getLocalLocation();
		if (pLoc == null)
		{
			return Integer.MAX_VALUE;
		}

		int pSceneX = pLoc.getSceneX();
		int pSceneY = pLoc.getSceneY();

		if (object instanceof GameObject)
		{
			GameObject go = (GameObject) object;
			Point min = go.getSceneMinLocation();
			Point max = go.getSceneMaxLocation();
			if (min != null && max != null)
			{
				int closestSceneX = Math.max(min.getX(), Math.min(max.getX(), pSceneX));
				int closestSceneY = Math.max(min.getY(), Math.min(max.getY(), pSceneY));
				int dx = pSceneX - closestSceneX;
				int dy = pSceneY - closestSceneY;
				return (int) Math.hypot(dx, dy);
			}
		}

		LocalPoint objLoc = object.getLocalLocation();
		if (objLoc != null)
		{
			int dx = pSceneX - objLoc.getSceneX();
			int dy = pSceneY - objLoc.getSceneY();
			return (int) Math.hypot(dx, dy);
		}
		return Integer.MAX_VALUE;
	}

	private boolean handleMarkOfGrace(Player localPlayer)
	{
		if (marksOfGrace.isEmpty())
		{
			markAttempts = 0;
			return false;
		}

		ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
		if (inv == null)
		{
			return false;
		}

		boolean canPickup = inv.count(ItemID.MARK_OF_GRACE) > 0 || inv.count() < 28;
		if (!canPickup)
		{
			return false;
		}

		int currentPlane = client.getPlane();
		int playerRoof = getObstacleIndexFromLocation(localPlayer);
		LocalPoint pLoc = localPlayer.getLocalLocation();

		// Strictly only pick up Mark of Grace that is on the EXACT SAME ROOF as the player!
		Tile markTile = marksOfGrace.stream()
			.filter(t -> t.getPlane() == currentPlane)
			.filter(t -> {
				int markRoof = getRoofIndex(t.getWorldLocation(), t.getPlane());
				if (playerRoof != -1 && markRoof != -1)
				{
					return markRoof == playerRoof;
				}
				return t.getLocalLocation() != null && pLoc.distanceTo(t.getLocalLocation()) <= 4 * Perspective.LOCAL_TILE_SIZE;
			})
			.min(Comparator.comparingInt(t -> pLoc.distanceTo(t.getLocalLocation())))
			.orElse(null);

		if (markTile != null)
		{
			WorldPoint markLoc = markTile.getWorldLocation();
			if (markLoc != null && markLoc.equals(lastMarkLocation))
			{
				markAttempts++;
				if (markAttempts > 3)
				{
					log.warn("Failed to reach Mark of Grace at {} after 3 attempts, continuing course", markLoc);
					return false;
				}
			}
			else
			{
				lastMarkLocation = markLoc;
				markAttempts = 1;
			}

			currentState = ScriptState.PICKING_MARK;
			log.info("Taking Mark of Grace at ({}, {}) on Roof {} (attempt {})",
				markTile.getSceneLocation().getX(), markTile.getSceneLocation().getY(), playerRoof, markAttempts);
			client.menuAction(
				markTile.getSceneLocation().getX(),
				markTile.getSceneLocation().getY(),
				MenuAction.GROUND_ITEM_THIRD_OPTION,
				ItemID.MARK_OF_GRACE,
				0,
				"Take",
				"Mark of grace"
			);
			cooldownTicks = 3;
			return true;
		}

		markAttempts = 0;
		return false;
	}

	private void interactObstacle(TileObject object)
	{
		if (object == null)
		{
			return;
		}
		LocalPoint loc = object.getLocalLocation();
		if (loc == null)
		{
			return;
		}

		lastInteractedObstacleId = object.getId();
		stuckTicks = 0;

		int param0 = loc.getSceneX();
		int param1 = loc.getSceneY();
		if (object instanceof GameObject)
		{
			Point sceneMin = ((GameObject) object).getSceneMinLocation();
			if (sceneMin != null)
			{
				param0 = sceneMin.getX();
				param1 = sceneMin.getY();
			}
		}

		int identifier = object.getId();
		MenuAction menuAction = MenuAction.GAME_OBJECT_FIRST_OPTION;
		String actualOption = "Climb";
		String actualTarget = "Obstacle";

		try
		{
			ObjectComposition def = client.getObjectDefinition(object.getId());
			if (def != null)
			{
				if (def.getImpostorIds() != null)
				{
					def = def.getImpostor();
				}
				if (def != null)
				{
					if (def.getName() != null && !def.getName().isEmpty())
					{
						actualTarget = def.getName();
					}
					String[] actions = def.getActions();
					if (actions != null)
					{
						for (int i = 0; i < actions.length; i++)
						{
							if (actions[i] != null && !actions[i].isEmpty() && !actions[i].equalsIgnoreCase("Examine"))
							{
								actualOption = actions[i];
								switch (i)
								{
									case 0: menuAction = MenuAction.GAME_OBJECT_FIRST_OPTION; break;
									case 1: menuAction = MenuAction.GAME_OBJECT_SECOND_OPTION; break;
									case 2: menuAction = MenuAction.GAME_OBJECT_THIRD_OPTION; break;
									case 3: menuAction = MenuAction.GAME_OBJECT_FOURTH_OPTION; break;
									case 4: menuAction = MenuAction.GAME_OBJECT_FIFTH_OPTION; break;
								}
								break;
							}
						}
					}
				}
			}
		}
		catch (Exception ignored)
		{
		}

		log.info("Interacting with Falador obstacle {} option '{}' target '{}' at ({}, {})", identifier, actualOption, actualTarget, param0, param1);
		client.menuAction(
			param0,
			param1,
			menuAction,
			identifier,
			0,
			actualOption,
			actualTarget
		);
	}

	private void walkToWorldPoint(WorldPoint wp)
	{
		if (wp == null)
		{
			return;
		}
		LocalPoint lp = LocalPoint.fromWorld(client, wp);
		if (lp != null)
		{
			client.menuAction(
				lp.getX(),
				lp.getY(),
				MenuAction.WALK,
				0,
				0,
				"Walk here",
				""
			);
		}
	}

	private void handleRunEnergy()
	{
		int energy = getRunEnergy();
		int runMode = client.getVarpValue(VarPlayerID.OPTION_RUN);
		if (energy > 20 && runMode == 0)
		{
			client.menuAction(1, InterfaceID.Orbs.RUNBUTTON, MenuAction.CC_OP, 1, -1, "Toggle Run", "");
		}
	}

	private boolean checkInCourseArea(Player player)
	{
		if (player == null)
		{
			return false;
		}
		int region = player.getWorldLocation().getRegionID();
		return REGION_IDS.contains(region);
	}

	private void scanScene()
	{
		obstacles.clear();
		marksOfGrace.clear();

		Tile[][][] tiles = client.getScene().getTiles();
		if (tiles == null)
		{
			return;
		}

		for (int z = 0; z < Constants.MAX_Z; z++)
		{
			for (int x = 0; x < Constants.SCENE_SIZE; x++)
			{
				for (int y = 0; y < Constants.SCENE_SIZE; y++)
				{
					Tile tile = tiles[z][x][y];
					if (tile == null)
					{
						continue;
					}

					checkAddObstacle(tile.getWallObject());
					checkAddObstacle(tile.getDecorativeObject());
					checkAddObstacle(tile.getGroundObject());

					GameObject[] gameObjects = tile.getGameObjects();
					if (gameObjects != null)
					{
						for (GameObject go : gameObjects)
						{
							checkAddObstacle(go);
						}
					}

					List<TileItem> groundItems = tile.getGroundItems();
					if (groundItems != null)
					{
						for (TileItem item : groundItems)
						{
							if (item != null && item.getId() == ItemID.MARK_OF_GRACE)
							{
								marksOfGrace.add(tile);
							}
						}
					}
				}
			}
		}
	}

	private void checkAddObstacle(TileObject object)
	{
		if (object != null && (OBSTACLE_IDS.contains(object.getId()) || getObstacleIndex(object.getId()) != -1))
		{
			obstacles.add(object);
		}
	}

	private void handleAntiLogout()
	{
		long now = System.currentTimeMillis();
		if (now - lastAntiLogoutTimeMs > nextAntiLogoutThreshold)
		{
			client.setIdleTimeout(EXTENDED_LOGOUT_TIMEOUT);

			Canvas canvas = client.getCanvas();
			if (canvas != null)
			{
				SwingUtilities.invokeLater(() ->
				{
					try
					{
						int fakeX = canvas.getWidth() / 2 + (int) ((Math.random() - 0.5) * 40);
						int fakeY = canvas.getHeight() / 2 + (int) ((Math.random() - 0.5) * 40);

						MouseEvent moveEvent = new MouseEvent(
							canvas,
							MouseEvent.MOUSE_MOVED,
							System.currentTimeMillis(),
							0,
							fakeX,
							fakeY,
							0,
							false
						);
						canvas.dispatchEvent(moveEvent);

						KeyEvent keyEvent = new KeyEvent(
							canvas,
							KeyEvent.KEY_PRESSED,
							System.currentTimeMillis(),
							0,
							KeyEvent.VK_UNDEFINED,
							KeyEvent.CHAR_UNDEFINED
						);
						canvas.dispatchEvent(keyEvent);
					}
					catch (Exception ignored)
					{
					}
				});
			}

			lastAntiLogoutTimeMs = now;
			nextAntiLogoutThreshold = 5_000 + (int) (Math.random() * 4_000);
		}
	}

	public int getRunEnergy()
	{
		try
		{
			return client.getEnergy() / 100;
		}
		catch (Exception e)
		{
			return 100;
		}
	}

	public int getMarksOnCourse()
	{
		return marksOfGrace.size();
	}
}
