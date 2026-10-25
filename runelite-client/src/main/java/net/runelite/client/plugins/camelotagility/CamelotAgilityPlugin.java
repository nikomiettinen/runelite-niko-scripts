/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.camelotagility;

import com.google.inject.Provides;
import java.awt.Canvas;
import java.awt.Rectangle;
import java.util.ArrayList;
import net.runelite.api.widgets.Widget;
import java.awt.event.MouseEvent;
import javax.swing.SwingUtilities;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.MenuAction;
import net.runelite.api.ItemComposition;
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
	name = "Camelot Agility",
	description = "Automates Camelot / Seers' Village rooftop agility course with auto-recovery and Mark of Grace pickup",
	tags = {"agility", "camelot", "seers", "rooftop", "bot"}
)
public class CamelotAgilityPlugin extends Plugin
{
	private static final int DEFAULT_LOGOUT_TIMEOUT = 500;
	private static final int EXTENDED_LOGOUT_TIMEOUT = 14000;

	public static final List<Integer> REGION_IDS = Arrays.asList(10806, 10550, 10805, 11062);
	public static final WorldPoint START_POINT = new WorldPoint(2729, 3488, 0);
	public static final WorldPoint FINISH_POINT = new WorldPoint(2704, 3464, 0);

	public static final List<Integer> OBSTACLE_IDS = Arrays.asList(
		ObjectID.ROOFTOPS_SEERS_WALLCLIMB, // 14927 (Wall climb)
		ObjectID.ROOFTOPS_SEERS_JUMP,      // 14928 (Jump gap 1)
		ObjectID.ROOFTOPS_SEERS_TIGHTROPE, // 14932 (Cross tightrope)
		ObjectID.ROOFTOPS_SEERS_JUMP_1,    // 14929 (Jump gap 2)
		ObjectID.ROOFTOPS_SEERS_JUMP_2,    // 14930 (Jump gap 3)
		ObjectID.ROOFTOPS_SEERS_LEAPDOWN   // 14931 (Jump edge)
	);

	public static final List<WorldPoint> RETURN_WAYPOINTS = Arrays.asList(
		new WorldPoint(2704, 3464, 0), // Finish landing tile
		new WorldPoint(2721, 3468, 0), // User requested street waypoint (2721, 3468)
		new WorldPoint(2725, 3478, 0), // Mid-street waypoint
		new WorldPoint(2729, 3488, 0)  // Bank climbable wall
	);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private CamelotAgilityOverlay overlay;

	@Inject
	private CamelotAgilityConfig config;

	@Getter
	private ScriptState currentState = ScriptState.IDLE;

	@Getter
	private boolean inCourseArea = false;

	@Getter
	private int lapsCompleted = 0;

	@Getter
	private int marksCollected = 0;

	@Getter
	private int foodEaten = 0;

	@Getter
	private int currentObstacleIndex = 0;

	private int lastInteractedObstacleId = -1;
	private boolean loggedInFromLoginScreen = false;

	private final Set<TileObject> obstacles = new HashSet<>();
	private final Set<Tile> marksOfGrace = new HashSet<>();

	private int cooldownTicks = 0;
	private boolean wasOnRooftop = false;
	private int lastAgilityXp = -1;
	private LocalPoint previousPlayerLoc = null;
	private int previousPlane = -1;
	private int stuckTicks = 0;

	private WorldPoint lastMarkLocation = null;
	private int markAttempts = 0;

	@Provides
	CamelotAgilityConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CamelotAgilityConfig.class);
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
		loggedInFromLoginScreen = false;
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
					log.info("Camelot: Plugin startup mid-course on rooftop! Synced obstacle index to {}", currentObstacleIndex);
				}
			});
		}

		if (config.antiLogout())
		{
			client.setIdleTimeout(EXTENDED_LOGOUT_TIMEOUT);
		}
		log.info("Camelot Agility plugin started");
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
		markAttempts = 0;
		lastMarkLocation = null;
		log.info("Camelot Agility plugin stopped");
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("camelotagility"))
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
				log.info("Camelot Agility script started by user");
				currentState = ScriptState.IDLE;
				lastAgilityXp = client.getSkillExperience(Skill.AGILITY);
				previousPlane = client.getPlane();
				lastInteractedObstacleId = -1;
				stuckTicks = 0;
				cooldownTicks = 0;
				markAttempts = 0;
				lastMarkLocation = null;
				clientThread.invokeLater(() -> {
					scanScene();
					Player player = client.getLocalPlayer();
					if (player != null && client.getPlane() > 0)
					{
						wasOnRooftop = true;
						currentObstacleIndex = detectRooftopObstacleIndex(player);
						log.info("Camelot Agility started mid-course on rooftop! Detected step: {}", currentObstacleIndex);
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
				log.info("Camelot Agility script stopped by user");
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
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.LOGGING_IN)
		{
			loggedInFromLoginScreen = true;
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
		else if (event.getGameState() == GameState.LOADING)
		{
			obstacles.clear();
			marksOfGrace.clear();
			previousPlane = client.getPlane();
			clientThread.invokeLater(this::scanScene);
		}
		else if (event.getGameState() == GameState.LOGGED_IN)
		{
			lastAgilityXp = client.getSkillExperience(Skill.AGILITY);
			previousPlane = client.getPlane();
			lastInteractedObstacleId = -1;
			stuckTicks = 0;
			cooldownTicks = 0;
			markAttempts = 0;
			lastMarkLocation = null;

			boolean fromLogin = loggedInFromLoginScreen;
			loggedInFromLoginScreen = false;

			clientThread.invokeLater(() -> {
				scanScene();
				if (fromLogin)
				{
					Player localPlayer = client.getLocalPlayer();
					if (localPlayer != null && client.getPlane() > 0)
					{
						wasOnRooftop = true;
						currentObstacleIndex = detectRooftopObstacleIndex(localPlayer);
						log.info("Camelot: Logged in mid-course on rooftop! Synced obstacle index to {}", currentObstacleIndex);
					}
					else
					{
						wasOnRooftop = false;
						currentObstacleIndex = 0;
					}
				}
			});
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		log.info("Camelot MenuOptionClicked: op='{}' targ='{}' action={} id={} p0={} p1={} itemId={}",
			event.getMenuOption(), event.getMenuTarget(), event.getMenuAction(),
			event.getId(), event.getParam0(), event.getParam1(), event.getItemId());
	}

	@Subscribe
	public void onStatChanged(StatChanged statChanged)
	{
		if (statChanged.getSkill() != Skill.AGILITY)
		{
			return;
		}

		int currentXp = statChanged.getXp();
		if (lastAgilityXp > 0 && currentXp > lastAgilityXp)
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
				log.info("Camelot lap completed! Total laps: {}", lapsCompleted);
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
	public void onGameTick(GameTick event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		inCourseArea = checkInCourseArea(localPlayer);
		if (!inCourseArea)
		{
			currentState = ScriptState.OUT_OF_RANGE;
			return;
		}

		if (!config.startScript())
		{
			currentState = ScriptState.IDLE;
			return;
		}

		int currentPlane = client.getPlane();
		if (previousPlane != -1 && previousPlane != currentPlane)
		{
			log.info("Plane changed from {} to {}, rescanning scene", previousPlane, currentPlane);
			scanScene();
			lastInteractedObstacleId = -1;
			cooldownTicks = 1;
		}
		previousPlane = currentPlane;

		handleRunEnergy();

		if (config.autoEat() && handleEating())
		{
			return;
		}

		LocalPoint currentLoc = localPlayer.getLocalLocation();
		int anim = localPlayer.getAnimation();
		boolean isAnimating = anim != -1;
		boolean isMoving = previousPlayerLoc != null && !previousPlayerLoc.equals(currentLoc);
		previousPlayerLoc = currentLoc;

		if (isAnimating || isMoving)
		{
			stuckTicks = 0;
		}
		else
		{
			stuckTicks++;
		}

		if (stuckTicks >= 6)
		{
			lastInteractedObstacleId = -1;
		}

		if (cooldownTicks > 0)
		{
			cooldownTicks--;
			return;
		}

		// 1. Player is on the rooftops (plane >= 2)
		if (currentPlane >= 2)
		{
			wasOnRooftop = true;

			int currentRoof = getRoofIndex(localPlayer.getWorldLocation(), currentPlane);
			if (currentRoof >= 1)
			{
				if (currentObstacleIndex != currentRoof)
				{
					log.info("On Roof {}: syncing obstacle index from {} to {}", currentRoof, currentObstacleIndex, currentRoof);
					currentObstacleIndex = currentRoof;
				}
			}
			else if (currentObstacleIndex < 1)
			{
				currentObstacleIndex = detectRooftopObstacleIndex(localPlayer);
			}

			// If stuck for 5+ ticks stationary on a roof, re-detect obstacle on current roof
			if (stuckTicks >= 5)
			{
				int resyncedIdx = detectRooftopObstacleIndex(localPlayer);
				if (resyncedIdx >= 1 && resyncedIdx != currentObstacleIndex)
				{
					log.info("Stationary on roof: resyncing obstacle index from {} to {}", currentObstacleIndex, resyncedIdx);
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
				if (resyncedIdx >= 1 && resyncedIdx != currentObstacleIndex)
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
		// 2. Mid-climb animation ascending bank wall (plane 1)
		else if (currentPlane == 1)
		{
			// Wait for wall climb completion to reach plane 3 (bank roof)
			return;
		}
		// 3. Player is on the ground (plane == 0): either finished lap, started script, or fell down
		else
		{
			if (wasOnRooftop)
			{
				if (localPlayer.getWorldLocation().distanceTo2D(FINISH_POINT) <= 8)
				{
					lapsCompleted++;
					log.info("Lap finished! Total laps completed: {}", lapsCompleted);
				}
				else
				{
					log.warn("Fell down or reached ground mid-course! Recovering...");
				}
				wasOnRooftop = false;
				currentObstacleIndex = 0;
				lastInteractedObstacleId = -1;
				markAttempts = 0;
				lastMarkLocation = null;

				// Optional Camelot Teleport tab
				if (config.useCamelotTeleport() && handleCamelotTeleport())
				{
					return;
				}
			}

			TileObject startObstacle = findStartObstacle();
			int distToStart = startObstacle != null ? getDistanceToObstacle(localPlayer, startObstacle) : localPlayer.getWorldLocation().distanceTo2D(START_POINT);

			// A. If start obstacle is nearby (<= 24 tiles), climb it!
			if (startObstacle != null && distToStart <= 26)
			{
				if (isMoving && currentState == ScriptState.CLIMBING_START)
				{
					return;
				}
				currentState = ScriptState.CLIMBING_START;
				log.info("Climbing Camelot start wall {} (dist: {} tiles)", startObstacle.getId(), distToStart);
				interactObstacle(startObstacle);
				cooldownTicks = 3;
				return;
			}

			// B. If player is actively moving towards a waypoint, wait
			if (isMoving && currentState == ScriptState.RETURNING_TO_START)
			{
				return;
			}

			// C. Otherwise, walk along recovery waypoints back to start
			currentState = ScriptState.RETURNING_TO_START;
			WorldPoint nextWaypoint = findNextWaypoint(localPlayer);
			if (nextWaypoint != null)
			{
				log.info("Walking towards Camelot waypoint {} (dist to start: {})", nextWaypoint, distToStart);
				walkToWorldPoint(nextWaypoint);
				cooldownTicks = 2;
			}
		}
	}

	private boolean handleEating()
	{
		if (!config.autoEat())
		{
			return false;
		}

		int currentHp = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int maxHp = client.getRealSkillLevel(Skill.HITPOINTS);
		if (currentHp <= 0 || maxHp <= 0)
		{
			return false;
		}

		int threshold = config.eatAtHp();
		if (maxHp <= threshold)
		{
			threshold = Math.max(1, (int) (maxHp * 0.70));
		}

		if (currentHp > threshold)
		{
			return false;
		}

		ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
		if (inv == null)
		{
			return false;
		}

		// Parse configured food names
		String configuredFoods = config.foodNames();
		List<String> foodFilter = new ArrayList<>();
		if (configuredFoods != null && !configuredFoods.trim().isEmpty())
		{
			for (String f : configuredFoods.split(","))
			{
				String trimmed = f.trim().toLowerCase();
				if (!trimmed.isEmpty())
				{
					foodFilter.add(trimmed);
				}
			}
		}

		Item[] items = inv.getItems();
		for (int slot = 0; slot < items.length; slot++)
		{
			Item item = items[slot];
			if (item == null || item.getId() <= 0)
			{
				continue;
			}

			ItemComposition def = client.getItemDefinition(item.getId());
			if (def == null)
			{
				continue;
			}

			String itemName = def.getName();
			if (itemName == null)
			{
				continue;
			}

			// If specific foods are configured, verify item name matches at least one
			if (!foodFilter.isEmpty())
			{
				String lowerName = itemName.toLowerCase();
				boolean matched = false;
				for (String food : foodFilter)
				{
					if (lowerName.contains(food))
					{
						matched = true;
						break;
					}
				}
				if (!matched)
				{
					continue;
				}
			}

			String[] actions = def.getInventoryActions();
			if (actions == null || actions.length == 0)
			{
				continue;
			}

			int eatActionIdx = -1;
			for (int i = 0; i < actions.length; i++)
			{
				String act = actions[i];
				if (act != null && (act.equalsIgnoreCase("Eat") || (act.equalsIgnoreCase("Drink") && (itemName.toLowerCase().contains("pot") || itemName.toLowerCase().contains("brew") || itemName.toLowerCase().contains("wine")))))
				{
					eatActionIdx = i;
					break;
				}
			}

			// If no explicit Eat/Drink action found but user explicitly named this item in config, check for any valid action
			if (eatActionIdx == -1 && !foodFilter.isEmpty())
			{
				for (int i = 0; i < actions.length; i++)
				{
					String act = actions[i];
					if (act != null && !act.isEmpty() && !act.equalsIgnoreCase("Drop") && !act.equalsIgnoreCase("Examine"))
					{
						eatActionIdx = i;
						break;
					}
				}
			}

			if (eatActionIdx != -1)
			{
				String opName = actions[eatActionIdx] != null ? actions[eatActionIdx] : "Eat";
				// In OSRS inventory container widget (InterfaceID.Inventory.ITEMS):
				// Op 2 corresponds to actions[0] (e.g. Eat, Drink, Wield)
				// Op 3 corresponds to actions[1]
				// Op 4 corresponds to actions[2]
				// Op 5 corresponds to actions[3]
				// Op 6 corresponds to actions[4]
				// Op 7 is Drop
				int opIdentifier = eatActionIdx + 2;
				MenuAction menuAction = opIdentifier >= 6 ? MenuAction.CC_OP_LOW_PRIORITY : MenuAction.CC_OP;

				log.info("Eating {} from slot {} at HP {}/{} (threshold: {}) using op {} ({})",
					itemName, slot, currentHp, maxHp, threshold, opIdentifier, opName);
				currentState = ScriptState.EATING;
				foodEaten++;

				// Invoke modern widget menu action on InterfaceID.Inventory.ITEMS with Op 2 ("Eat")
				client.menuAction(
					slot,
					InterfaceID.Inventory.ITEMS,
					menuAction,
					opIdentifier,
					item.getId(),
					opName,
					itemName
				);

				cooldownTicks = 1;
				return true;
			}
		}

		return false;
	}

	private boolean handleCamelotTeleport()
	{
		ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
		if (inv == null)
		{
			return false;
		}

		Item[] items = inv.getItems();
		for (int slot = 0; slot < items.length; slot++)
		{
			Item item = items[slot];
			if (item != null && item.getId() == ItemID.CAMELOT_TELEPORT)
			{
				log.info("Using Camelot teleport tab from inventory slot {}", slot);
				client.menuAction(
					slot,
					InterfaceID.Inventory.ITEMS,
					MenuAction.CC_OP,
					2,
					ItemID.CAMELOT_TELEPORT,
					"Break",
					"Camelot teleport"
				);
				cooldownTicks = 4;
				return true;
			}
		}
		return false;
	}

	private TileObject findStartObstacle()
	{
		int startObstacleId = OBSTACLE_IDS.get(0);
		Player localPlayer = client.getLocalPlayer();
		TileObject found = obstacles.stream()
			.filter(obj -> obj != null && isMatchingObstacle(obj.getId(), startObstacleId))
			.min(Comparator.comparingInt(obj -> getDistanceToObstacle(localPlayer, obj)))
			.orElse(null);

		if (found != null)
		{
			return found;
		}

		// Fallback: search at course start location and surrounding tiles in scene
		for (int dx = -2; dx <= 2; dx++)
		{
			for (int dy = -2; dy <= 2; dy++)
			{
				WorldPoint wp = new WorldPoint(START_POINT.getX() + dx, START_POINT.getY() + dy, 0);
				LocalPoint lp = LocalPoint.fromWorld(client, wp);
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
							if (tile.getWallObject() != null && isMatchingObstacle(tile.getWallObject().getId(), startObstacleId))
							{
								return tile.getWallObject();
							}
							if (tile.getDecorativeObject() != null && isMatchingObstacle(tile.getDecorativeObject().getId(), startObstacleId))
							{
								return tile.getDecorativeObject();
							}
							if (tile.getGroundObject() != null && isMatchingObstacle(tile.getGroundObject().getId(), startObstacleId))
							{
								return tile.getGroundObject();
							}
							if (tile.getGameObjects() != null)
							{
								for (GameObject go : tile.getGameObjects())
								{
									if (go != null && isMatchingObstacle(go.getId(), startObstacleId))
									{
										return go;
									}
								}
							}
						}
					}
				}
			}
		}

		return null;
	}

	private WorldPoint findNextWaypoint(Player localPlayer)
	{
		WorldPoint playerLoc = localPlayer.getWorldLocation();
		if (playerLoc == null)
		{
			return new WorldPoint(2721, 3468, 0);
		}

		// If near finish point (church landing) or west of street, directly click the user's requested waypoint (2721, 3468)
		if (playerLoc.distanceTo2D(FINISH_POINT) <= 12 || playerLoc.getX() < 2718)
		{
			return new WorldPoint(2721, 3468, 0);
		}

		int distToStart = playerLoc.distanceTo2D(START_POINT);

		return RETURN_WAYPOINTS.stream()
			.filter(wp -> playerLoc.distanceTo2D(wp) >= 2)
			.filter(wp -> wp.distanceTo2D(START_POINT) < distToStart)
			.min(Comparator.comparingInt(wp -> playerLoc.distanceTo2D(wp)))
			.orElse(new WorldPoint(2721, 3468, 0));
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
					&& isMatchingObstacle(obj.getId(), targetId)
					&& getDistanceToObstacle(localPlayer, obj) <= 25)
				.collect(Collectors.toList());

			if (!matching.isEmpty())
			{
				TileObject samePlane = matching.stream()
					.filter(obj -> obj.getPlane() == client.getPlane())
					.filter(this::hasInteractAction)
					.min(Comparator.comparingInt(obj -> getDistanceToObstacle(localPlayer, obj)))
					.orElse(null);

				if (samePlane != null)
				{
					return samePlane;
				}

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

	private boolean isMatchingObstacle(int objectId, int targetId)
	{
		return objectId == targetId;
	}

	private boolean hasInteractAction(TileObject obj)
	{
		if (obj == null)
		{
			return false;
		}
		try
		{
			ObjectComposition def = client.getObjectDefinition(obj.getId());
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

		// Roof 5 -> Edge jump 14931 (Church roof)
		if (x >= 2698 && x <= 2706 && y >= 3460 && y <= 3467)
		{
			return 5;
		}
		// Roof 4 -> Gap 3 jump 14930 (Poison shop roof)
		if (x >= 2698 && x <= 2716 && y >= 3468 && y <= 3476)
		{
			return 4;
		}
		// Roof 3 -> Gap 2 jump 14929 (South building after tightrope)
		if (x >= 2708 && x <= 2716 && y >= 3477 && y <= 3483)
		{
			return 3;
		}
		// Roof 2 -> Tightrope cross 14932 (West building)
		if (x >= 2705 && x <= 2716 && y >= 3488 && y <= 3500)
		{
			return 2;
		}
		// Roof 1 -> Gap 1 jump 14928 (Bank roof)
		if (x >= 2720 && x <= 2731 && y >= 3490 && y <= 3498)
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
			log.info("Camelot: Detected rooftop obstacle from coordinates: Roof {}", locIndex);
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
			.min(Comparator.comparingInt(obj -> getObstacleIndex(obj.getId())))
			.orElse(null);

		if (immediate != null)
		{
			int idx = getObstacleIndex(immediate.getId());
			log.info("Camelot: Detected departure rooftop obstacle (radius 8): {} (index {})", immediate.getId(), idx);
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
			log.info("Camelot: Detected closest rooftop obstacle (radius 15): {} (index {})", closest.getId(), idx);
			return idx;
		}

		return Math.max(1, currentObstacleIndex);
	}

	public int getObstacleIndex(int objectId)
	{
		return OBSTACLE_IDS.indexOf(objectId);
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

		log.info("Interacting with Camelot obstacle {} option '{}' target '{}' at ({}, {})", identifier, actualOption, actualTarget, param0, param1);
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
		if (lp == null)
		{
			return;
		}

		// 1. Try minimap projection (minimap click)
		// Minimap clicks are the most reliable way in OSRS to walk directly to a world tile
		// without camera occlusion, pitch/yaw issues, or obstacles in 3D perspective.
		Point miniPoint = Perspective.localToMinimap(client, lp);
		if (miniPoint != null)
		{
			log.info("Walking via minimap canvas click to {} at ({}, {})", wp, miniPoint.getX(), miniPoint.getY());
			dispatchCanvasClick(miniPoint);
			return;
		}

		// 2. Try viewport canvas projection (normal ground click)
		Point canvasPoint = Perspective.localToCanvas(client, lp, client.getPlane());
		if (canvasPoint != null)
		{
			int x = canvasPoint.getX();
			int y = canvasPoint.getY();
			int cw = client.getCanvasWidth();
			int ch = client.getCanvasHeight();
			if (x >= 5 && x < cw - 5 && y >= 5 && y < ch - 5)
			{
				log.info("Walking via viewport canvas click to {} at ({}, {})", wp, x, y);
				dispatchCanvasClick(canvasPoint);
				return;
			}
		}

		// 3. Fallback: scene coordinates menu action
		log.info("Walking via scene coords fallback to {} at ({}, {})", wp, lp.getSceneX(), lp.getSceneY());
		client.menuAction(
			lp.getSceneX(),
			lp.getSceneY(),
			MenuAction.WALK,
			0,
			0,
			"Walk here",
			""
		);
	}

	private void dispatchCanvasClick(Point p)
	{
		if (p == null)
		{
			return;
		}
		Canvas canvas = client.getCanvas();
		if (canvas == null)
		{
			return;
		}

		int x = p.getX();
		int y = p.getY();
		long now = System.currentTimeMillis();

		MouseEvent move = new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, now, 0, x, y, 0, false);
		MouseEvent press = new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, now, MouseEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1);
		MouseEvent release = new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, now + 15, 0, x, y, 1, false, MouseEvent.BUTTON1);
		MouseEvent click = new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, now + 15, 0, x, y, 1, false, MouseEvent.BUTTON1);

		try
		{
			canvas.dispatchEvent(move);
			canvas.dispatchEvent(press);
			canvas.dispatchEvent(release);
			canvas.dispatchEvent(click);
		}
		catch (Exception ignored)
		{
		}

		SwingUtilities.invokeLater(() -> {
			try
			{
				canvas.dispatchEvent(move);
				canvas.dispatchEvent(press);
				canvas.dispatchEvent(release);
				canvas.dispatchEvent(click);
			}
			catch (Exception ignored)
			{
			}
		});
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

	private int getRunEnergy()
	{
		return client.getEnergy() / 100;
	}

	public void scanScene()
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

					GameObject[] gameObjects = tile.getGameObjects();
					if (gameObjects != null)
					{
						for (GameObject go : gameObjects)
						{
							if (go != null && isCourseObstacle(go.getId()))
							{
								obstacles.add(go);
							}
						}
					}

					TileObject wall = tile.getWallObject();
					if (wall != null && isCourseObstacle(wall.getId()))
					{
						obstacles.add(wall);
					}

					TileObject dec = tile.getDecorativeObject();
					if (dec != null && isCourseObstacle(dec.getId()))
					{
						obstacles.add(dec);
					}

					TileObject ground = tile.getGroundObject();
					if (ground != null && isCourseObstacle(ground.getId()))
					{
						obstacles.add(ground);
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

	private boolean isCourseObstacle(int objectId)
	{
		return OBSTACLE_IDS.contains(objectId);
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		GameObject go = event.getGameObject();
		if (go != null && isCourseObstacle(go.getId()))
		{
			obstacles.add(go);
		}
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		obstacles.remove(event.getGameObject());
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned event)
	{
		TileObject to = event.getWallObject();
		if (to != null && isCourseObstacle(to.getId()))
		{
			obstacles.add(to);
		}
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned event)
	{
		obstacles.remove(event.getWallObject());
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
	{
		TileObject to = event.getDecorativeObject();
		if (to != null && isCourseObstacle(to.getId()))
		{
			obstacles.add(to);
		}
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
	{
		obstacles.remove(event.getDecorativeObject());
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		TileObject to = event.getGroundObject();
		if (to != null && isCourseObstacle(to.getId()))
		{
			obstacles.add(to);
		}
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned event)
	{
		obstacles.remove(event.getGroundObject());
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		TileItem item = event.getItem();
		if (item != null && item.getId() == ItemID.MARK_OF_GRACE)
		{
			marksOfGrace.add(event.getTile());
		}
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		TileItem item = event.getItem();
		if (item != null && item.getId() == ItemID.MARK_OF_GRACE)
		{
			marksOfGrace.remove(event.getTile());
		}
	}
}
