/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.draynoragility;

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
	name = "Draynor Agility",
	description = "Automates Draynor Village rooftop agility course with auto-recovery and Mark of Grace pickup",
	tags = {"agility", "draynor", "rooftop", "bot"}
)
public class DraynorAgilityPlugin extends Plugin
{
	private static final int DEFAULT_LOGOUT_TIMEOUT = 500;
	private static final int EXTENDED_LOGOUT_TIMEOUT = 14000;

	public static final List<Integer> REGION_IDS = Arrays.asList(12338, 12339);
	public static final WorldPoint START_POINT = new WorldPoint(3103, 3279, 0);
	public static final WorldPoint FINISH_POINT = new WorldPoint(3103, 3261, 0);
	public static final List<Integer> OBSTACLE_IDS = Arrays.asList(
		ObjectID.ROOFTOPS_DRAYNOR_WALLCLIMB,      // 11404
		ObjectID.ROOFTOPS_DRAYNOR_TIGHTROPE_1,    // 11405
		ObjectID.ROOFTOPS_DRAYNOR_TIGHTROPE_2,    // 11406
		ObjectID.ROOFTOPS_DRAYNOR_WALLCROSSING,   // 11430
		ObjectID.ROOFTOPS_DRAYNOR_WALLSCRAMBLE,   // 11630
		ObjectID.ROOFTOPS_DRAYNOR_LEAPDOWN,       // 11631
		ObjectID.ROOFTOPS_DRAYNOR_CRATE           // 11632
	);
	public static final List<WorldPoint> RETURN_WAYPOINTS = Arrays.asList(
		new WorldPoint(3103, 3268, 0),
		new WorldPoint(3103, 3279, 0)
	);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private DraynorAgilityOverlay overlay;

	@Inject
	private DraynorAgilityConfig config;

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

	private int nextAntiLogoutThreshold = 6_000 + (int) (Math.random() * 4_000);
	private long lastAntiLogoutTimeMs = 0;

	@Provides
	DraynorAgilityConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DraynorAgilityConfig.class);
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
					log.info("Draynor: Plugin startup mid-course on rooftop! Synced obstacle index to {}", currentObstacleIndex);
				}
			});
		}

		if (config.antiLogout())
		{
			client.setIdleTimeout(EXTENDED_LOGOUT_TIMEOUT);
		}
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
		stuckTicks = 0;
		previousPlayerLoc = null;
		previousPlane = -1;
		currentObstacleIndex = 0;
		lastInteractedObstacleId = -1;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("draynoragility"))
		{
			return;
		}

		if (event.getKey().equals("antiLogout"))
		{
			if (config.antiLogout())
			{
				client.setIdleTimeout(EXTENDED_LOGOUT_TIMEOUT);
			}
			else
			{
				client.setIdleTimeout(DEFAULT_LOGOUT_TIMEOUT);
			}
		}
		else if (event.getKey().equals("startScript"))
		{
			if (config.startScript())
			{
				lastAgilityXp = client.getSkillExperience(Skill.AGILITY);
				previousPlane = client.getPlane();
				lastInteractedObstacleId = -1;
				stuckTicks = 0;
				cooldownTicks = 0;
				clientThread.invokeLater(() -> {
					scanScene();
					Player player = client.getLocalPlayer();
					if (player != null && client.getPlane() > 0)
					{
						wasOnRooftop = true;
						currentObstacleIndex = detectRooftopObstacleIndex(player);
						log.info("Draynor Agility started mid-course on rooftop! Detected step: {}", currentObstacleIndex);
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
				currentState = ScriptState.IDLE;
				stuckTicks = 0;
				cooldownTicks = 0;
				currentObstacleIndex = 0;
				lastInteractedObstacleId = -1;
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
			clientThread.invokeLater(() -> {
				scanScene();
				Player localPlayer = client.getLocalPlayer();
				if (localPlayer != null && client.getPlane() > 0)
				{
					wasOnRooftop = true;
					currentObstacleIndex = detectRooftopObstacleIndex(localPlayer);
					log.info("Draynor: Logged in mid-course on rooftop! Synced obstacle index to {}", currentObstacleIndex);
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

			if (currentObstacleIndex >= totalSteps - 1)
			{
				lapsCompleted++;
				wasOnRooftop = false;
				currentObstacleIndex = 0;
				lastInteractedObstacleId = -1;
				log.info("Draynor lap completed! Total laps: {}", lapsCompleted);
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

			if (currentObstacleIndex < 1)
			{
				currentObstacleIndex = detectRooftopObstacleIndex(localPlayer);
			}

			// If stuck for 10+ ticks stationary on a roof, re-detect obstacle on current roof
			if (stuckTicks >= 10)
			{
				int resyncedIdx = detectRooftopObstacleIndex(localPlayer);
				if (resyncedIdx != currentObstacleIndex)
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
				if (resyncedIdx != currentObstacleIndex)
				{
					log.info("Target obstacle not in reach: advancing index from {} to {}", currentObstacleIndex, resyncedIdx);
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
		WorldPoint playerPos = localPlayer.getWorldLocation();

		// If start obstacle is in scene and nearby (within 24 tiles), climb it directly!
		TileObject startObstacle = findStartObstacle();
		if (startObstacle != null)
		{
			int dist = getDistanceToObstacle(localPlayer, startObstacle);
			if (dist <= 24)
			{
				currentState = ScriptState.CLIMBING_START;
				log.info("Climbing Draynor start obstacle {} (dist: {} tiles)", startObstacle.getId(), dist);
				interactObstacle(startObstacle);
				cooldownTicks = 2;
				return;
			}
		}

		// Otherwise, walk along recovery waypoints back to start
		currentState = ScriptState.RETURNING_TO_START;
		int distToStart = playerPos.distanceTo2D(START_POINT);
		WorldPoint targetWaypoint = RETURN_WAYPOINTS.stream()
			.filter(wp -> playerPos.distanceTo2D(wp) > 3)
			.filter(wp -> wp.distanceTo2D(START_POINT) < distToStart)
			.min(Comparator.comparingInt(wp -> wp.distanceTo2D(START_POINT)))
			.orElse(START_POINT);

		log.debug("Walking towards Draynor start waypoint {}", targetWaypoint);
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

		if (currentObstacleIndex < OBSTACLE_IDS.size())
		{
			int targetId = OBSTACLE_IDS.get(currentObstacleIndex);
			List<TileObject> matching = obstacles.stream()
				.filter(obj -> obj != null
					&& obj.getId() == targetId
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

	private int detectRooftopObstacleIndex(Player localPlayer)
	{
		if (obstacles.isEmpty())
		{
			scanScene();
		}

		// 0. If current target obstacle is already in reach, do not change index
		if (currentObstacleIndex >= 1 && currentObstacleIndex < OBSTACLE_IDS.size())
		{
			int targetId = OBSTACLE_IDS.get(currentObstacleIndex);
			boolean targetInReach = obstacles.stream()
				.anyMatch(obj -> obj != null
					&& obj.getId() == targetId
					&& getDistanceToObstacle(localPlayer, obj) <= 15);
			if (targetInReach)
			{
				return currentObstacleIndex;
			}
		}

		// 1. Search for departure obstacles on current roof (within 8 tiles)
		List<TileObject> immediate = obstacles.stream()
			.filter(obj -> obj != null)
			.filter(obj -> {
				int idx = OBSTACLE_IDS.indexOf(obj.getId());
				return idx >= 1 && idx < OBSTACLE_IDS.size() && hasInteractAction(obj);
			})
			.filter(obj -> getDistanceToObstacle(localPlayer, obj) <= 8)
			.collect(Collectors.toList());

		if (!immediate.isEmpty())
		{
			TileObject best = immediate.stream()
				.min(Comparator.comparingInt(obj -> OBSTACLE_IDS.indexOf(obj.getId())))
				.orElse(null);

			if (best != null)
			{
				int idx = OBSTACLE_IDS.indexOf(best.getId());
				log.info("Detected departure rooftop obstacle (radius 8): {} (index {})", best.getId(), idx);
				return idx;
			}
		}

		// 2. If no obstacle within 8 tiles, pick closest obstacle up to 15 tiles
		TileObject closest = obstacles.stream()
			.filter(obj -> obj != null)
			.filter(obj -> {
				int idx = OBSTACLE_IDS.indexOf(obj.getId());
				return idx >= 1 && idx < OBSTACLE_IDS.size() && hasInteractAction(obj);
			})
			.filter(obj -> getDistanceToObstacle(localPlayer, obj) <= 15)
			.min(Comparator.comparingInt(obj -> getDistanceToObstacle(localPlayer, obj)))
			.orElse(null);

		if (closest != null)
		{
			int idx = OBSTACLE_IDS.indexOf(closest.getId());
			log.info("Detected closest rooftop obstacle (radius 15): {} (index {})", closest.getId(), idx);
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
		LocalPoint pLoc = localPlayer.getLocalLocation();

		Tile markTile = marksOfGrace.stream()
			.filter(t -> t.getPlane() == currentPlane)
			.filter(t -> t.getLocalLocation() != null && pLoc.distanceTo(t.getLocalLocation()) <= 14 * Perspective.LOCAL_TILE_SIZE)
			.min(Comparator.comparingInt(t -> pLoc.distanceTo(t.getLocalLocation())))
			.orElse(null);

		if (markTile != null)
		{
			currentState = ScriptState.PICKING_MARK;
			log.info("Taking Mark of Grace at ({}, {})", markTile.getSceneLocation().getX(), markTile.getSceneLocation().getY());
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

		log.info("Interacting with Draynor obstacle {} option '{}' target '{}' at ({}, {})", identifier, actualOption, actualTarget, param0, param1);
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

					if (tile.getGroundItems() != null)
					{
						for (TileItem item : tile.getGroundItems())
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
		log.debug("Draynor scene scanned: {} obstacles, {} marks of grace found", obstacles.size(), marksOfGrace.size());
	}

	private void checkAddObstacle(TileObject object)
	{
		if (object == null)
		{
			return;
		}
		if (OBSTACLE_IDS.contains(object.getId()))
		{
			obstacles.add(object);
		}
	}

	private void handleAntiLogout()
	{
		long now = System.currentTimeMillis();
		if (now - lastAntiLogoutTimeMs >= nextAntiLogoutThreshold)
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
