/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.agilityscript;

import com.google.inject.Provides;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
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
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
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
	name = "Agility Script",
	description = "All-in-one Agility automation script supporting Draynor, Varrock, Canifis, Falador, and Camelot courses",
	tags = {"agility", "script", "bot", "rooftop", "all-in-one"}
)
public class AgilityScriptPlugin extends Plugin
{
	private static final int DEFAULT_LOGOUT_TIMEOUT = 500;
	private static final int EXTENDED_LOGOUT_TIMEOUT = 14000;
	private static final int VARROCK_FLOORBOARD_OVERHANG_ALT = 524;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private AgilityScriptOverlay overlay;

	@Inject
	private AgilityScriptConfig config;

	@Getter
	private ScriptState currentState = ScriptState.IDLE;

	@Getter
	private boolean inCourseArea = false;

	@Getter
	private AgilityCourse activeCourse = null;

	@Getter
	private int lapsCompleted = 0;

	@Getter
	private int marksCollected = 0;

	@Getter
	private int foodEaten = 0;

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
	AgilityScriptConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(AgilityScriptConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		obstacles.clear();
		marksOfGrace.clear();
		currentState = ScriptState.IDLE;
		inCourseArea = false;
		wasOnRooftop = false;
		stuckTicks = 0;
		previousPlayerLoc = null;
		previousPlane = -1;
		currentObstacleIndex = 0;
		lastInteractedObstacleId = -1;
		markAttempts = 0;
		lastMarkLocation = null;
		foodEaten = 0;

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			lastAgilityXp = client.getSkillExperience(Skill.AGILITY);
			previousPlane = client.getPlane();
			clientThread.invokeLater(() -> {
				resolveActiveCourse();
				scanScene();
				Player localPlayer = client.getLocalPlayer();
				if (localPlayer != null && client.getPlane() > 0)
				{
					wasOnRooftop = true;
					currentObstacleIndex = detectRooftopObstacleIndex(localPlayer);
					log.info("Agility Script: Startup mid-course on rooftop! Synced obstacle index to {}", currentObstacleIndex);
				}
			});
		}

		if (config.antiLogout())
		{
			client.setIdleTimeout(EXTENDED_LOGOUT_TIMEOUT);
		}
		log.info("Agility Script plugin started");
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
		foodEaten = 0;
		log.info("Agility Script plugin stopped");
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("agilityscript"))
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
				log.info("Agility Script started by user");
				currentState = ScriptState.IDLE;
				lastAgilityXp = client.getSkillExperience(Skill.AGILITY);
				previousPlane = client.getPlane();
				lastInteractedObstacleId = -1;
				stuckTicks = 0;
				cooldownTicks = 0;
				markAttempts = 0;
				lastMarkLocation = null;
				clientThread.invokeLater(() -> {
					resolveActiveCourse();
					scanScene();
					Player player = client.getLocalPlayer();
					if (player != null && client.getPlane() > 0)
					{
						wasOnRooftop = true;
						currentObstacleIndex = detectRooftopObstacleIndex(player);
						log.info("Agility Script: Started mid-course on rooftop! Detected step: {}", currentObstacleIndex);
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
				log.info("Agility Script stopped by user");
				currentState = ScriptState.IDLE;
			}
		}
		else if (event.getKey().equals("selectedCourse"))
		{
			clientThread.invokeLater(() -> {
				resolveActiveCourse();
				scanScene();
				currentObstacleIndex = 0;
				lastInteractedObstacleId = -1;
			});
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			lastAgilityXp = client.getSkillExperience(Skill.AGILITY);
			previousPlane = client.getPlane();
			if (config.antiLogout())
			{
				client.setIdleTimeout(EXTENDED_LOGOUT_TIMEOUT);
			}
			clientThread.invokeLater(() -> {
				resolveActiveCourse();
				scanScene();
				Player localPlayer = client.getLocalPlayer();
				if (localPlayer != null && client.getPlane() > 0)
				{
					wasOnRooftop = true;
					currentObstacleIndex = detectRooftopObstacleIndex(localPlayer);
					log.info("Agility Script: Resynced obstacle index to {} after login", currentObstacleIndex);
				}
				else
				{
					wasOnRooftop = false;
					currentObstacleIndex = 0;
				}
			});
		}
		else if (event.getGameState() == GameState.HOPPING || event.getGameState() == GameState.LOGIN_SCREEN)
		{
			obstacles.clear();
			marksOfGrace.clear();
			wasOnRooftop = false;
			previousPlayerLoc = null;
			previousPlane = -1;
			lastInteractedObstacleId = -1;
			markAttempts = 0;
			lastMarkLocation = null;
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		GameObject obj = event.getGameObject();
		if (obj != null && isRelevantObstacle(obj.getId()))
		{
			obstacles.add(obj);
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
		if (event.getWallObject() != null && isRelevantObstacle(event.getWallObject().getId()))
		{
			obstacles.add(event.getWallObject());
		}
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned event)
	{
		obstacles.remove(event.getWallObject());
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		if (event.getGroundObject() != null && isRelevantObstacle(event.getGroundObject().getId()))
		{
			obstacles.add(event.getGroundObject());
		}
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned event)
	{
		obstacles.remove(event.getGroundObject());
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
	{
		if (event.getDecorativeObject() != null && isRelevantObstacle(event.getDecorativeObject().getId()))
		{
			obstacles.add(event.getDecorativeObject());
		}
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
	{
		obstacles.remove(event.getDecorativeObject());
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

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (event.getMenuAction() == MenuAction.GROUND_ITEM_THIRD_OPTION
			&& event.getId() == ItemID.MARK_OF_GRACE)
		{
			marksCollected++;
			log.info("Picked up Mark of Grace! Total collected: {}", marksCollected);
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() != Skill.AGILITY)
		{
			return;
		}

		int currentXp = event.getXp();
		if (lastAgilityXp != -1 && currentXp > lastAgilityXp)
		{
			stuckTicks = 0;
			int totalSteps = activeCourse != null ? activeCourse.getObstacleIds().size() : 7;
			if (currentObstacleIndex >= totalSteps - 1)
			{
				lapsCompleted++;
				currentObstacleIndex = 0;
				lastInteractedObstacleId = -1;
				log.info("{} lap completed! Total laps: {}", activeCourse != null ? activeCourse.getCourseName() : "Course", lapsCompleted);
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

		resolveActiveCourse();
		if (activeCourse == null)
		{
			inCourseArea = false;
			currentState = ScriptState.OUT_OF_RANGE;
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

		handleAntiLogout();
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

		// If player is actively animating across an obstacle, wait
		if (isAnimating)
		{
			return;
		}

		// If player is actively moving towards the clicked obstacle, wait
		if (isMoving && lastInteractedObstacleId != -1)
		{
			return;
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

		// 1. Player is on the rooftops (plane >= 2, or plane 1 for courses where roof is plane 1/2)
		boolean onRooftopLevel = isRooftopLevel(currentPlane);
		if (onRooftopLevel)
		{
			wasOnRooftop = true;

			if (currentObstacleIndex < 1)
			{
				currentObstacleIndex = detectRooftopObstacleIndex(localPlayer);
			}

			// Special case for Varrock church roof: keep index at 3 until obstacle 14832 finishes
			if (activeCourse == AgilityCourse.VARROCK && isVarrockChurchRoof(localPlayer.getWorldLocation()))
			{
				if (currentObstacleIndex != 3)
				{
					log.info("On Varrock church roof: forcing obstacle index to 3");
					currentObstacleIndex = 3;
				}
			}

			// If stationary for 5+ ticks, re-detect obstacle on current roof
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

			// B. Find and traverse next obstacle
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
		// 2. Mid-climb animation ascending wall/tree (plane 1 for courses with roof >= 2)
		else if (currentPlane == 1 && activeCourse == AgilityCourse.CAMELOT)
		{
			// Ascending wall climb
			return;
		}
		// 3. Ground recovery (plane == 0): finished lap, started script, or fell down
		else
		{
			if (wasOnRooftop)
			{
				if (activeCourse.getFinishPoint() != null && localPlayer.getWorldLocation().distanceTo2D(activeCourse.getFinishPoint()) <= 8)
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
				if (activeCourse == AgilityCourse.CAMELOT && config.useCamelotTeleport() && handleCamelotTeleport())
				{
					return;
				}
			}

			TileObject startObstacle = findStartObstacle();
			int startDistLimit = (activeCourse == AgilityCourse.CAMELOT) ? 26 : 24;
			int distToStart = startObstacle != null ? getDistanceToObstacle(localPlayer, startObstacle) : (activeCourse.getStartPoint() != null ? localPlayer.getWorldLocation().distanceTo2D(activeCourse.getStartPoint()) : 999);

			// A. If start obstacle is nearby, climb it!
			if (startObstacle != null && distToStart <= startDistLimit)
			{
				if (isMoving && currentState == ScriptState.CLIMBING_START)
				{
					return;
				}
				currentState = ScriptState.CLIMBING_START;
				log.info("Climbing {} start obstacle {} (dist: {} tiles)", activeCourse.getCourseName(), startObstacle.getId(), distToStart);
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
				log.info("Walking towards {} waypoint {} (dist to start: {})", activeCourse.getCourseName(), nextWaypoint, distToStart);
				walkToWorldPoint(nextWaypoint);
				cooldownTicks = 2;
			}
		}
	}

	private boolean isRooftopLevel(int plane)
	{
		if (activeCourse == null || plane == 0)
		{
			return false;
		}

		if (activeCourse == AgilityCourse.CAMELOT)
		{
			return plane >= 2;
		}
		if (activeCourse == AgilityCourse.CANIFIS)
		{
			return plane >= 2;
		}
		// Draynor, Varrock, Falador rooftop sections can span plane 1, 2, or 3
		return plane >= 1;
	}

	private void resolveActiveCourse()
	{
		if (config.selectedCourse() != AgilityCourse.AUTO)
		{
			activeCourse = config.selectedCourse();
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer != null)
		{
			AgilityCourse detected = AgilityCourse.detectCourse(localPlayer.getWorldLocation());
			if (detected != null && detected != activeCourse)
			{
				log.info("Agility Script: Auto-detected course: {}", detected.getCourseName());
				activeCourse = detected;
				currentObstacleIndex = 0;
				lastInteractedObstacleId = -1;
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

	private boolean isVarrockChurchRoof(WorldPoint wp)
	{
		return wp != null
			&& wp.getX() >= 3190 && wp.getX() <= 3200
			&& wp.getY() >= 3400 && wp.getY() <= 3421;
	}

	private TileObject findStartObstacle()
	{
		if (activeCourse == null || activeCourse.getObstacleIds().isEmpty())
		{
			return null;
		}

		int startObstacleId = activeCourse.getObstacleIds().get(0);
		Player localPlayer = client.getLocalPlayer();
		TileObject found = obstacles.stream()
			.filter(obj -> obj != null && isMatchingObstacle(obj.getId(), startObstacleId))
			.min(Comparator.comparingInt(obj -> getDistanceToObstacle(localPlayer, obj)))
			.orElse(null);

		if (found != null)
		{
			return found;
		}

		// Fallback: search at course start location in scene
		WorldPoint startPoint = activeCourse.getStartPoint();
		if (startPoint == null)
		{
			return null;
		}

		for (int dx = -2; dx <= 2; dx++)
		{
			for (int dy = -2; dy <= 2; dy++)
			{
				WorldPoint wp = new WorldPoint(startPoint.getX() + dx, startPoint.getY() + dy, 0);
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
		if (activeCourse == null || activeCourse.getReturnWaypoints().isEmpty())
		{
			return null;
		}

		WorldPoint playerLoc = localPlayer.getWorldLocation();
		if (playerLoc == null)
		{
			return activeCourse.getReturnWaypoints().get(0);
		}

		// Special handling for Camelot church finish: force move to (2721, 3468, 0)
		if (activeCourse == AgilityCourse.CAMELOT)
		{
			if (playerLoc.distanceTo2D(activeCourse.getFinishPoint()) <= 12 || playerLoc.getX() < 2718)
			{
				return new WorldPoint(2721, 3468, 0);
			}
		}

		WorldPoint startPoint = activeCourse.getStartPoint();
		if (startPoint == null)
		{
			return activeCourse.getReturnWaypoints().get(0);
		}

		int distToStart = playerLoc.distanceTo2D(startPoint);

		return activeCourse.getReturnWaypoints().stream()
			.filter(wp -> playerLoc.distanceTo2D(wp) >= 2)
			.filter(wp -> wp.distanceTo2D(startPoint) < distToStart)
			.min(Comparator.comparingInt(wp -> playerLoc.distanceTo2D(wp)))
			.orElse(activeCourse.getReturnWaypoints().get(0));
	}

	private TileObject findNextObstacle(Player localPlayer)
	{
		if (activeCourse == null || activeCourse.getObstacleIds().isEmpty())
		{
			return null;
		}

		if (currentObstacleIndex < 1)
		{
			currentObstacleIndex = 1;
		}

		List<Integer> obstacleIds = activeCourse.getObstacleIds();
		if (currentObstacleIndex < obstacleIds.size())
		{
			int targetId = obstacleIds.get(currentObstacleIndex);
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
		if (objectId == targetId)
		{
			return true;
		}
		// Varrock wall swing alternate ID
		if (activeCourse == AgilityCourse.VARROCK && targetId == ObjectID.ROOFTOPS_VARROCK_WALLSWING)
		{
			return objectId == VARROCK_FLOORBOARD_OVERHANG_ALT;
		}
		return false;
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

	private int detectRooftopObstacleIndex(Player localPlayer)
	{
		if (activeCourse == null || activeCourse.getObstacleIds().isEmpty())
		{
			return 1;
		}

		int detectedRoof = activeCourse.getRoofIndex(localPlayer.getWorldLocation(), client.getPlane());
		if (detectedRoof >= 1)
		{
			return detectedRoof;
		}

		// Special case for Varrock church roof
		if (activeCourse == AgilityCourse.VARROCK && isVarrockChurchRoof(localPlayer.getWorldLocation()))
		{
			return Math.max(3, currentObstacleIndex);
		}

		int minIdx = Math.max(1, currentObstacleIndex);
		List<Integer> obstacleIds = activeCourse.getObstacleIds();

		// 1. If current target obstacle is already in reach, keep it
		if (currentObstacleIndex >= minIdx && currentObstacleIndex < obstacleIds.size())
		{
			int targetId = obstacleIds.get(currentObstacleIndex);
			boolean targetInReach = obstacles.stream()
				.anyMatch(obj -> obj != null
					&& isMatchingObstacle(obj.getId(), targetId)
					&& getDistanceToObstacle(localPlayer, obj) <= 15
					&& hasInteractAction(obj));
			if (targetInReach)
			{
				return currentObstacleIndex;
			}
		}

		// 2. Search for immediate departure obstacle on current roof within 8 tiles
		TileObject immediate = obstacles.stream()
			.filter(obj -> obj != null)
			.filter(obj -> {
				int idx = getObstacleIndex(obj.getId());
				return idx >= minIdx && idx < obstacleIds.size() && hasInteractAction(obj);
			})
			.filter(obj -> getDistanceToObstacle(localPlayer, obj) <= 8)
			.min(Comparator.comparingInt(obj -> getObstacleIndex(obj.getId())))
			.orElse(null);

		if (immediate != null)
		{
			int idx = getObstacleIndex(immediate.getId());
			log.info("Agility Script: Detected departure rooftop obstacle (radius 8): {} (index {})", immediate.getId(), idx);
			return idx;
		}

		// 3. Fallback: closest reachable rooftop obstacle within 15 tiles
		TileObject closest = obstacles.stream()
			.filter(obj -> obj != null)
			.filter(obj -> {
				int idx = getObstacleIndex(obj.getId());
				return idx >= minIdx && idx < obstacleIds.size() && hasInteractAction(obj);
			})
			.filter(obj -> getDistanceToObstacle(localPlayer, obj) <= 15)
			.min(Comparator.comparingInt(obj -> getDistanceToObstacle(localPlayer, obj)))
			.orElse(null);

		if (closest != null)
		{
			int idx = getObstacleIndex(closest.getId());
			log.info("Agility Script: Detected closest rooftop obstacle (radius 15): {} (index {})", closest.getId(), idx);
			return idx;
		}

		return Math.max(1, currentObstacleIndex);
	}

	public int getObstacleIndex(int objectId)
	{
		if (activeCourse == null)
		{
			return -1;
		}
		if (activeCourse == AgilityCourse.VARROCK && objectId == VARROCK_FLOORBOARD_OVERHANG_ALT)
		{
			return activeCourse.getObstacleIds().indexOf(ObjectID.ROOFTOPS_VARROCK_WALLSWING);
		}
		return activeCourse.getObstacleIds().indexOf(objectId);
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
		if (marksOfGrace.isEmpty() || activeCourse == null)
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
		int playerRoof = activeCourse.getRoofIndex(localPlayer.getWorldLocation(), currentPlane);
		LocalPoint pLoc = localPlayer.getLocalLocation();

		Tile markTile = marksOfGrace.stream()
			.filter(t -> t.getPlane() == currentPlane)
			.filter(t -> {
				int markRoof = activeCourse.getRoofIndex(t.getWorldLocation(), t.getPlane());
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

		log.info("Interacting with {} obstacle {} option '{}' target '{}' at ({}, {})",
			activeCourse != null ? activeCourse.getCourseName() : "obstacle", identifier, actualOption, actualTarget, param0, param1);
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
		if (player == null || activeCourse == null)
		{
			return false;
		}
		int region = player.getWorldLocation().getRegionID();
		return activeCourse.getRegionIds().contains(region);
	}

	private void scanScene()
	{
		obstacles.clear();
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

					if (tile.getWallObject() != null && isRelevantObstacle(tile.getWallObject().getId()))
					{
						obstacles.add(tile.getWallObject());
					}
					if (tile.getDecorativeObject() != null && isRelevantObstacle(tile.getDecorativeObject().getId()))
					{
						obstacles.add(tile.getDecorativeObject());
					}
					if (tile.getGroundObject() != null && isRelevantObstacle(tile.getGroundObject().getId()))
					{
						obstacles.add(tile.getGroundObject());
					}
					if (tile.getGameObjects() != null)
					{
						for (GameObject go : tile.getGameObjects())
						{
							if (go != null && isRelevantObstacle(go.getId()))
							{
								obstacles.add(go);
							}
						}
					}
				}
			}
		}
		log.info("Agility Script: Scene scanned! Found {} relevant course obstacles", obstacles.size());
	}

	private boolean isRelevantObstacle(int objectId)
	{
		if (activeCourse != null)
		{
			return activeCourse.getObstacleIds().contains(objectId)
				|| (activeCourse == AgilityCourse.VARROCK && objectId == VARROCK_FLOORBOARD_OVERHANG_ALT);
		}

		// If course not resolved yet, check against all courses
		for (AgilityCourse c : AgilityCourse.values())
		{
			if (c != AgilityCourse.AUTO)
			{
				if (c.getObstacleIds().contains(objectId)
					|| (c == AgilityCourse.VARROCK && objectId == VARROCK_FLOORBOARD_OVERHANG_ALT))
				{
					return true;
				}
			}
		}
		return false;
	}

	private int getRunEnergy()
	{
		return client.getEnergy() / 100;
	}

	private void handleAntiLogout()
	{
		if (!config.antiLogout())
		{
			return;
		}

		long now = System.currentTimeMillis();
		int idleTicks = client.getKeyboardIdleTicks();
		int mouseIdleTicks = client.getMouseIdleTicks();

		if (idleTicks > nextAntiLogoutThreshold || mouseIdleTicks > nextAntiLogoutThreshold)
		{
			if (now - lastAntiLogoutTimeMs > 10_000)
			{
				lastAntiLogoutTimeMs = now;
				nextAntiLogoutThreshold = 6_000 + (int) (Math.random() * 4_000);
				log.debug("Agility Script: Triggering anti-logout input event");

				SwingUtilities.invokeLater(() -> {
					try
					{
						Canvas canvas = client.getCanvas();
						if (canvas != null)
						{
							int width = canvas.getWidth();
							int height = canvas.getHeight();
							Point mousePos = client.getMouseCanvasPosition();
							int x = (mousePos != null && mousePos.getX() > 0) ? mousePos.getX() : width / 2;
							int y = (mousePos != null && mousePos.getY() > 0) ? mousePos.getY() : height / 2;
							x = Math.max(2, Math.min(x, width - 2));
							y = Math.max(2, Math.min(y, height - 2));
							int jitterX = (x > width / 2) ? x - 1 : x + 1;

							long time = System.currentTimeMillis();
							MouseEvent mouseMoveEvent = new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, time, 0, jitterX, y, 0, false);
							canvas.dispatchEvent(mouseMoveEvent);

							KeyEvent press = new KeyEvent(canvas, KeyEvent.KEY_PRESSED, time, 0, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED);
							KeyEvent release = new KeyEvent(canvas, KeyEvent.KEY_RELEASED, time + 15, 0, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED);
							canvas.dispatchEvent(press);
							canvas.dispatchEvent(release);
						}
					}
					catch (Exception e)
					{
						log.warn("Error triggering anti-logout input", e);
					}
				});
			}
		}
	}
}
