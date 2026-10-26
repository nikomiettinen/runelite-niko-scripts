/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.naturealtarscript;

import com.google.inject.Provides;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.GroundObject;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.Varbits;
import net.runelite.api.WallObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Nature Altar Script",
	description = "Runs back and forth between Karamja General Store and Nature Altar, un-noting pure essence with Aisles and entering ruins with Nature talisman",
	tags = {"nature", "altar", "karamja", "runecraft", "runner", "store", "aisles", "essence", "ruins"}
)
public class NatureAltarScriptPlugin extends Plugin
{
	// Endpoint destinations specified by user
	public static final WorldPoint STORE_LOCATION = new WorldPoint(2767, 3121, 0);
	public static final WorldPoint NATURE_ALTAR = new WorldPoint(2869, 3012, 0);

	// NPC Aisles at Karamja General store
	public static final int AISLES_NPC_ID = 2134;

	// Mysterious ruins object ID
	public static final int MYSTERIOUS_RUINS_ID = 34821;

	// Nature rune crafting altar inside temple
	public static final int NATURE_ALTAR_OBJECT_ID = 34768;

	// Exit portal inside temple
	public static final int NATURE_PORTAL_OBJECT_ID = 34756;

	// Exact route specified by user (27 waypoints)
	private static final WorldPoint[] POSITIONS = {
		new WorldPoint(2767, 3121, 0),
		new WorldPoint(2767, 3112, 0),
		new WorldPoint(2764, 3100, 0),
		new WorldPoint(2764, 3092, 0),
		new WorldPoint(2765, 3084, 0),
		new WorldPoint(2766, 3078, 0),
		new WorldPoint(2764, 3072, 0),
		new WorldPoint(2765, 3063, 0),
		new WorldPoint(2766, 3055, 0),
		new WorldPoint(2769, 3050, 0),
		new WorldPoint(2775, 3047, 0),
		new WorldPoint(2785, 3047, 0),
		new WorldPoint(2791, 3046, 0),
		new WorldPoint(2797, 3044, 0),
		new WorldPoint(2800, 3041, 0),
		new WorldPoint(2804, 3035, 0),
		new WorldPoint(2810, 3028, 0),
		new WorldPoint(2816, 3025, 0),
		new WorldPoint(2825, 3016, 0),
		new WorldPoint(2829, 3012, 0),
		new WorldPoint(2833, 3012, 0),
		new WorldPoint(2845, 3008, 0),
		new WorldPoint(2853, 3007, 0),
		new WorldPoint(2860, 3008, 0),
		new WorldPoint(2867, 3008, 0),
		new WorldPoint(2869, 3010, 0),
		new WorldPoint(2869, 3012, 0)
	};

	private static final Set<Integer> STAMINA_POTION_IDS = new HashSet<>(Arrays.asList(
		ItemID.STAMINA_POTION4, ItemID.STAMINA_POTION3, ItemID.STAMINA_POTION2, ItemID.STAMINA_POTION1
	));

	private static final Set<Integer> ENERGY_POTION_IDS = new HashSet<>(Arrays.asList(
		ItemID.ENERGY_POTION4, ItemID.ENERGY_POTION3, ItemID.ENERGY_POTION2, ItemID.ENERGY_POTION1,
		ItemID.SUPER_ENERGY4, ItemID.SUPER_ENERGY3, ItemID.SUPER_ENERGY2, ItemID.SUPER_ENERGY1
	));

	private enum State
	{
		IDLE,
		RUNNING_TO_ALTAR,
		ENTERING_RUINS,
		INSIDE_ALTAR,
		RUNNING_TO_STORE,
		UNNOTING_AT_STORE,
		PAUSED_AT_ENDPOINT
	}

	private enum RuinsStep
	{
		WAIT_ARRIVAL,
		ACTIVATE_TALISMAN,
		WAIT_TALISMAN_ACTIVE,
		CLICK_RUINS,
		WAIT_ENTRY
	}

	@Inject
	private Client client;

	@Inject
	private NatureAltarScriptConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private NatureAltarScriptOverlay overlay;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Getter
	private State state = State.IDLE;

	@Getter
	private String status = "READY";

	@Getter
	private Color statusColor = Color.GREEN;

	@Getter
	private int currentWaypointIndex = 0;

	@Getter
	private int totalWaypoints = POSITIONS.length;

	@Getter
	private int distanceToCurrentWaypoint = 0;

	@Getter
	private int lapsCompleted = 0;

	@Getter
	private int energyPercent = 100;

	@Getter
	private int unnotedEssenceCount = 0;

	private int pauseTicks = 0;
	private int stuckTicks = 0;
	private int actionCooldownTicks = 0;
	private int unnoteAttempts = 0;
	private boolean isTalismanActivated = false;
	private RuinsStep ruinsStep = RuinsStep.WAIT_ARRIVAL;
	private int talismanActivationAttempts = 0;
	private int runToggleCooldown = 0;
	private WorldPoint previousWorldLoc = null;

	@Provides
	NatureAltarScriptConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NatureAltarScriptConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		status = "READY";
		statusColor = Color.GREEN;
		stuckTicks = 0;
		actionCooldownTicks = 0;
		unnoteAttempts = 0;
		isTalismanActivated = false;
		ruinsStep = RuinsStep.WAIT_ARRIVAL;
		talismanActivationAttempts = 0;
		runToggleCooldown = 0;
		previousWorldLoc = null;
		log.info("Nature Altar Script started!");
		sendGameMessage("Plugin started. Toggle 'Start Running Script' to begin.");
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		state = State.IDLE;
		status = "STOPPED";
		statusColor = Color.RED;
		isTalismanActivated = false;
		ruinsStep = RuinsStep.WAIT_ARRIVAL;
		talismanActivationAttempts = 0;
		log.info("Nature Altar Script stopped.");
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("naturealtarscript"))
		{
			return;
		}

		if (event.getKey().equals("startScript"))
		{
			if (config.startScript())
			{
				initializeRun();
			}
			else
			{
				state = State.IDLE;
				status = "IDLE";
				statusColor = Color.YELLOW;
			}
		}
	}

	/**
	 * Intelligent state detection and recovery:
	 * Recognizes where the player is and whether pure essence is already unnoted.
	 */
	private void initializeRun()
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		WorldPoint playerLoc = localPlayer.getWorldLocation();
		unnotedEssenceCount = countUnnotedEssence();
		isTalismanActivated = false;

		// 1. If already inside Nature Altar temple
		if (isInsideNatureAltar(playerLoc))
		{
			state = State.INSIDE_ALTAR;
			status = "INSIDE NATURE ALTAR";
			statusColor = Color.GREEN;
			sendGameMessage("Sijainti hahmotettu: Sisällä Nature Altarilla!");
			return;
		}

		// 2. If player has unnoted Pure Essence in inventory (unnotedEssenceCount > 0)
		if (unnotedEssenceCount > 0)
		{
			// If already at Mysterious ruins (within 2 tiles)
			if (playerLoc.distanceTo(NATURE_ALTAR) <= 2)
			{
				log.info("Pure essence in inventory ({}) and at ruins -> Preparing to enter calmly!", unnotedEssenceCount);
				state = State.ENTERING_RUINS;
				ruinsStep = RuinsStep.WAIT_ARRIVAL;
		talismanActivationAttempts = 0;
				actionCooldownTicks = 2;
				status = "AT RUINS (WAITING)";
				statusColor = Color.YELLOW;
				sendGameMessage(String.format("Pure essencet (%d kpl) inventoryssä ja alttarilla! Valmistaudutaan menemään sisään rauhassa...", unnotedEssenceCount));
				return;
			}

			// Otherwise continue route towards Altar
			int closestIdx = findClosestWaypointIndex(playerLoc);
			state = State.RUNNING_TO_ALTAR;
			if (playerLoc.equals(POSITIONS[closestIdx]) && closestIdx < POSITIONS.length - 1)
			{
				currentWaypointIndex = closestIdx + 1;
			}
			else
			{
				currentWaypointIndex = closestIdx;
			}
			status = "RUNNING TO NATURE ALTAR";
			statusColor = Color.CYAN;
			sendGameMessage(String.format("Pure essencet (%d kpl) inventoryssä! Jatketaan kohti Alttaria (WP %d/%d).",
				unnotedEssenceCount, currentWaypointIndex + 1, POSITIONS.length));
			walkToWorldPoint(POSITIONS[currentWaypointIndex]);
			return;
		}

		// 3. Player does NOT have unnoted Pure Essence (unnotedEssenceCount == 0)
		// If near General Store and we have noted essence: un-note first
		if (playerLoc.distanceTo(STORE_LOCATION) <= 4 && config.unnoteEssence() && hasNotedEssence())
		{
			state = State.UNNOTING_AT_STORE;
			status = "UN-NOTING ESSENCE WITH AISLES";
			statusColor = Color.YELLOW;
			unnoteAttempts = 0;
			actionCooldownTicks = 1;
			sendGameMessage("General Storella: un-notetetaan Pure Essence Aislesin kanssa (Option 3)...");
			return;
		}

		// Otherwise run towards General Store
		int closestIdx = findClosestWaypointIndex(playerLoc);
		state = State.RUNNING_TO_STORE;
		if (playerLoc.equals(POSITIONS[closestIdx]) && closestIdx > 0)
		{
			currentWaypointIndex = closestIdx - 1;
		}
		else
		{
			currentWaypointIndex = closestIdx;
		}
		status = "RUNNING TO GENERAL STORE";
		statusColor = Color.CYAN;
		sendGameMessage(String.format("Ei unnotettua essenceä. Juostaan General Storelle (WP %d/%d).",
			currentWaypointIndex + 1, POSITIONS.length));
		walkToWorldPoint(POSITIONS[currentWaypointIndex]);
	}

	private int findClosestWaypointIndex(WorldPoint playerLoc)
	{
		int closestIdx = 0;
		int bestDist = Integer.MAX_VALUE;
		for (int i = 0; i < POSITIONS.length; i++)
		{
			int d = playerLoc.distanceTo(POSITIONS[i]);
			if (d < bestDist)
			{
				bestDist = d;
				closestIdx = i;
			}
		}
		return closestIdx;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		energyPercent = client.getEnergy() / 100;
		unnotedEssenceCount = countUnnotedEssence();
		handleRunEnergy();

		if (!config.startScript())
		{
			if (state != State.IDLE)
			{
				state = State.IDLE;
				status = "IDLE";
				statusColor = Color.YELLOW;
			}
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		WorldPoint playerLoc = localPlayer.getWorldLocation();

		// Check if stuck / not moving
		if (previousWorldLoc != null && previousWorldLoc.equals(playerLoc))
		{
			stuckTicks++;
		}
		else
		{
			stuckTicks = 0;
		}
		previousWorldLoc = playerLoc;

		if (actionCooldownTicks > 0)
		{
			actionCooldownTicks--;
		}

		if (pauseTicks > 0)
		{
			pauseTicks--;
			return;
		}

		if (state == State.IDLE)
		{
			initializeRun();
			return;
		}

		if (config.drinkStamina())
		{
			handlePotions();
		}

		// State correction check
		if (state == State.RUNNING_TO_STORE && unnotedEssenceCount > 0 && playerLoc.distanceTo(NATURE_ALTAR) < 14)
		{
			log.info("Detected unnoted essence near ruins while in RUNNING_TO_STORE, switching to ENTERING_RUINS");
			state = State.ENTERING_RUINS;
		}

		switch (state)
		{
			case RUNNING_TO_ALTAR:
				handleRunningToAltar(playerLoc);
				break;

			case ENTERING_RUINS:
				handleEnteringRuins(playerLoc);
				break;

			case INSIDE_ALTAR:
				handleInsideAltar(playerLoc);
				break;

			case RUNNING_TO_STORE:
				handleRunningToStore(playerLoc);
				break;

			case UNNOTING_AT_STORE:
				handleUnnotingAtStore(playerLoc);
				break;

			case PAUSED_AT_ENDPOINT:
				break;
		}
	}

	/**
	 * Strictly waits until the player arrives at the currently clicked waypoint tile before clicking the next!
	 * Once arrived at the last waypoint tile (2869, 3012, 0), proceeds to enter ruins with Nature talisman.
	 */
	private void handleRunningToAltar(WorldPoint playerLoc)
	{
		// Arrived at the final waypoint (2869, 3012, 0)
		boolean atFinalWaypoint = playerLoc.equals(NATURE_ALTAR) ||
			(currentWaypointIndex >= POSITIONS.length - 1 && playerLoc.distanceTo(NATURE_ALTAR) <= 1 && stuckTicks >= 1);

		if (atFinalWaypoint)
		{
			log.info("Arrived at final waypoint ({})! Preparing to enter ruins calmly...", NATURE_ALTAR);
			state = State.ENTERING_RUINS;
			ruinsStep = RuinsStep.WAIT_ARRIVAL;
		talismanActivationAttempts = 0;
			actionCooldownTicks = 2; // Calm 2-tick pause upon arrival
			status = "AT RUINS (WAITING)";
			statusColor = Color.YELLOW;
			return;
		}

		WorldPoint targetWp = POSITIONS[currentWaypointIndex];
		distanceToCurrentWaypoint = playerLoc.distanceTo(targetWp);

		// Wait until the player has arrived at this specific waypoint tile
		boolean arrivedAtWaypoint = playerLoc.equals(targetWp) || (distanceToCurrentWaypoint <= 1 && stuckTicks >= 3);

		if (arrivedAtWaypoint)
		{
			// Player reached this tile: advance to next waypoint tile
			if (currentWaypointIndex >= POSITIONS.length - 1)
			{
				log.info("Reached last tile ({})! Preparing to enter ruins calmly...", targetWp);
				state = State.ENTERING_RUINS;
				ruinsStep = RuinsStep.WAIT_ARRIVAL;
		talismanActivationAttempts = 0;
				actionCooldownTicks = 2;
				status = "AT RUINS (WAITING)";
				statusColor = Color.YELLOW;
				return;
			}

			currentWaypointIndex++;
			targetWp = POSITIONS[currentWaypointIndex];
			distanceToCurrentWaypoint = playerLoc.distanceTo(targetWp);
			log.info("Arrived at tile. Now advancing to next waypoint {}/{} at {}",
				currentWaypointIndex + 1, POSITIONS.length, targetWp);

			status = "TO ALTAR (WP " + (currentWaypointIndex + 1) + "/" + POSITIONS.length + ")";
			statusColor = Color.CYAN;
			walkToWorldPoint(targetWp);
			actionCooldownTicks = 1;
			stuckTicks = 0;
			return;
		}

		// Player has not yet reached the targeted tile: WAIT!
		if (stuckTicks >= 4)
		{
			log.info("Player stopped moving before reaching WP {}/{} (dist: {}), re-clicking...",
				currentWaypointIndex + 1, POSITIONS.length, distanceToCurrentWaypoint);
			walkToWorldPoint(targetWp);
			stuckTicks = 0;
		}
	}

	/**
	 * Entering Mysterious ruins (ID 34821) in two discrete user-requested steps:
	 * Step 1: Click Nature talisman (ID 1462) active
	 * Step 2: Click Mysterious ruins (ID 34821)
	 */
	private void handleEnteringRuins(WorldPoint playerLoc)
	{
		// 1. Check if already inside Nature Altar temple (Y > 4000)
		if (isInsideNatureAltar(playerLoc))
		{
			isTalismanActivated = false;
			ruinsStep = RuinsStep.WAIT_ARRIVAL;
		talismanActivationAttempts = 0;
			lapsCompleted++;
			log.info("Successfully entered Nature Altar interior at {}! Trips completed: {}", playerLoc, lapsCompleted);
			sendGameMessage(String.format("Päästiin sisälle Nature Altarille! (Kierrokset: %d)", lapsCompleted));
			state = State.INSIDE_ALTAR;
			status = "INSIDE NATURE ALTAR";
			statusColor = Color.GREEN;
			actionCooldownTicks = 3;
			return;
		}

		// 2. If no unnoted essence is left, return to store
		if (unnotedEssenceCount == 0 && !hasUnnotedEssence())
		{
			isTalismanActivated = false;
			ruinsStep = RuinsStep.WAIT_ARRIVAL;
		talismanActivationAttempts = 0;
			log.info("No unnoted essence in inventory. Heading back to General Store...");
			state = State.RUNNING_TO_STORE;
			currentWaypointIndex = POSITIONS.length - 1;
			walkToWorldPoint(POSITIONS[currentWaypointIndex]);
			return;
		}

		if (actionCooldownTicks > 0)
		{
			return;
		}

		// 3. Perform calm discrete entrance steps
		enterMysteriousRuins(playerLoc);
	}

	private void handleInsideAltar(WorldPoint playerLoc)
	{
		isTalismanActivated = false;

		// 1. If player leaves altar through portal back to surface (Y < 4000):
		if (!isInsideNatureAltar(playerLoc))
		{
			log.info("Exited Nature Altar back to surface at {}. Checking state...", playerLoc);
			if (unnotedEssenceCount > 0)
			{
				state = State.ENTERING_RUINS;
				ruinsStep = RuinsStep.WAIT_ARRIVAL;
				actionCooldownTicks = 2;
			}
			else
			{
				state = State.RUNNING_TO_STORE;
				int closestIdx = findClosestWaypointIndex(playerLoc);
				if (closestIdx >= POSITIONS.length - 1 && playerLoc.distanceTo(POSITIONS[closestIdx]) <= 1)
				{
					currentWaypointIndex = POSITIONS.length - 2;
				}
				else
				{
					currentWaypointIndex = Math.min(closestIdx, POSITIONS.length - 1);
				}
				status = "RUNNING TO GENERAL STORE";
				statusColor = Color.CYAN;
				sendGameMessage(String.format("Poistuttu portaalista. Juostaan takaisin General Storelle (WP %d/%d)...",
					currentWaypointIndex + 1, POSITIONS.length));
				walkToWorldPoint(POSITIONS[currentWaypointIndex]);
				actionCooldownTicks = 1;
			}
			return;
		}

		if (actionCooldownTicks > 0)
		{
			return;
		}

		// 2. Step 1: Craft nature runes if we have unnoted pure essence
		if (unnotedEssenceCount > 0 || hasUnnotedEssence())
		{
			Player localPlayer = client.getLocalPlayer();
			if (localPlayer != null && localPlayer.getPoseAnimation() != localPlayer.getIdlePoseAnimation())
			{
				return;
			}

			TileObject altar = findNatureAltar();
			if (altar == null)
			{
				log.warn("Nature altar (ID {}) not found in scene!", NATURE_ALTAR_OBJECT_ID);
				status = "SEARCHING FOR ALTAR (34768)";
				statusColor = Color.YELLOW;
				actionCooldownTicks = 1;
				return;
			}

			log.info("Crafting nature runes at Altar (ID {})! Unnoted essence: {}", NATURE_ALTAR_OBJECT_ID, unnotedEssenceCount);
			status = "CRAFTING NATURE RUNES (ALTAR 34768)";
			statusColor = Color.GREEN;
			sendGameMessage(String.format("Klikataan Nature Altaria (ID 34768) ja tehdään %d Pure Essencestä runet...", unnotedEssenceCount));

			interactWithGameObject(altar, NATURE_ALTAR_OBJECT_ID, "Craft-rune", "Altar");
			actionCooldownTicks = 3;
			return;
		}

		// 3. Step 2: Pure essence crafted! Exit through portal (ID 34756)
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer != null && localPlayer.getPoseAnimation() != localPlayer.getIdlePoseAnimation())
		{
			return;
		}

		TileObject portal = findPortal();
		if (portal == null)
		{
			log.warn("Exit portal (ID {}) not found in scene!", NATURE_PORTAL_OBJECT_ID);
			status = "SEARCHING FOR PORTAL (34756)";
			statusColor = Color.YELLOW;
			actionCooldownTicks = 1;
			return;
		}

		log.info("All essence crafted! Exiting via Portal (ID {})...", NATURE_PORTAL_OBJECT_ID);
		status = "EXITING VIA PORTAL (34756)";
		statusColor = Color.GREEN;
		sendGameMessage("Essencet tehty! Poistutaan portaalista (ID 34756)...");

		interactWithGameObject(portal, NATURE_PORTAL_OBJECT_ID, "Use", "Portal");
		actionCooldownTicks = 3;
	}

	private boolean isInsideNatureAltar(WorldPoint loc)
	{
		if (loc == null)
		{
			return false;
		}
		// Nature altar interior is in a separate plane/dungeon region (Y >= 4000)
		return loc.getY() >= 4000;
	}

	/**
	 * Strictly waits until player reaches each waypoint tile before clicking the next towards General Store.
	 */
	private void handleRunningToStore(WorldPoint playerLoc)
	{
		isTalismanActivated = false;

		// If player already has unnoted Pure Essence, turn around to Altar immediately!
		if (unnotedEssenceCount > 0)
		{
			log.info("Player already has {} unnoted essence! Correcting direction to Nature Altar...", unnotedEssenceCount);
			state = State.RUNNING_TO_ALTAR;
			return;
		}

		WorldPoint targetWp = POSITIONS[currentWaypointIndex];
		distanceToCurrentWaypoint = playerLoc.distanceTo(targetWp);

		// Check if arrived at overall destination (General Store)
		if (playerLoc.distanceTo(STORE_LOCATION) <= 2 || (currentWaypointIndex <= 0 && playerLoc.equals(STORE_LOCATION)))
		{
			log.info("Arrived at Karamja General Store (2767, 3121)! Trips completed: {}", lapsCompleted);

			if (config.unnoteEssence() && hasNotedEssence() && unnotedEssenceCount == 0)
			{
				state = State.UNNOTING_AT_STORE;
				status = "UN-NOTING ESSENCE WITH AISLES";
				statusColor = Color.YELLOW;
				unnoteAttempts = 0;
				actionCooldownTicks = 1;
				return;
			}

			// Direct turnaround if no un-noting needed
			state = State.RUNNING_TO_ALTAR;
			currentWaypointIndex = 1;
			status = "RUNNING TO NATURE ALTAR";
			statusColor = Color.CYAN;
			pauseTicks = Math.max(0, config.delayAtDestination());
			walkToWorldPoint(POSITIONS[currentWaypointIndex]);
			return;
		}

		// Wait until player arrives at this specific waypoint tile
		boolean arrivedAtWaypoint = playerLoc.equals(targetWp) || (distanceToCurrentWaypoint <= 1 && stuckTicks >= 3);

		if (arrivedAtWaypoint)
		{
			if (currentWaypointIndex <= 0)
			{
				if (config.unnoteEssence() && hasNotedEssence() && unnotedEssenceCount == 0)
				{
					state = State.UNNOTING_AT_STORE;
					status = "UN-NOTING ESSENCE WITH AISLES";
					statusColor = Color.YELLOW;
					unnoteAttempts = 0;
					actionCooldownTicks = 1;
					return;
				}
				state = State.RUNNING_TO_ALTAR;
				currentWaypointIndex = 1;
				walkToWorldPoint(POSITIONS[currentWaypointIndex]);
				return;
			}

			currentWaypointIndex--;
			targetWp = POSITIONS[currentWaypointIndex];
			distanceToCurrentWaypoint = playerLoc.distanceTo(targetWp);
			log.info("Arrived at tile. Now advancing to next waypoint {}/{} at {}",
				currentWaypointIndex + 1, POSITIONS.length, targetWp);

			status = "TO STORE (WP " + (currentWaypointIndex + 1) + "/" + POSITIONS.length + ")";
			statusColor = Color.CYAN;
			walkToWorldPoint(targetWp);
			actionCooldownTicks = 1;
			stuckTicks = 0;
			return;
		}

		// Player has not yet reached the targeted tile: WAIT!
		if (stuckTicks >= 4)
		{
			log.info("Player stopped moving before reaching WP {}/{} (dist: {}), re-clicking...",
				currentWaypointIndex + 1, POSITIONS.length, distanceToCurrentWaypoint);
			walkToWorldPoint(targetWp);
			stuckTicks = 0;
		}
	}

	private void handleUnnotingAtStore(WorldPoint playerLoc)
	{
		isTalismanActivated = false;

		// 1. If we already have unnoted essence in inventory, un-noting is complete!
		if (unnotedEssenceCount > 0)
		{
			log.info("Essence un-noted successfully! ({} unnoted in inventory). Resuming route to Nature Altar...", unnotedEssenceCount);
			sendGameMessage(String.format("Un-noted %d Pure Essence! Running to Nature Altar...", unnotedEssenceCount));
			state = State.RUNNING_TO_ALTAR;
			currentWaypointIndex = 1;
			status = "RUNNING TO NATURE ALTAR";
			statusColor = Color.CYAN;
			pauseTicks = Math.max(0, config.delayAtDestination());
			walkToWorldPoint(POSITIONS[currentWaypointIndex]);
			return;
		}

		if (actionCooldownTicks > 0)
		{
			return;
		}

		// 2. If dialogue options (Chatmenu 219) are open, select Option 3 ("Exchange all 100 coins.")
		if (isDialogueOptionsOpen())
		{
			handleAislesDialogue();
			actionCooldownTicks = 2;
			return;
		}

		// 3. Find NPC Aisles (ID 2134)
		NPC aisles = findAisles();
		if (aisles == null)
		{
			log.info("NPC Aisles not found yet, walking to store location {}", STORE_LOCATION);
			walkToWorldPoint(STORE_LOCATION);
			actionCooldownTicks = 2;
			return;
		}

		// If player is far from Aisles (> 4 tiles), walk closer
		if (playerLoc.distanceTo(aisles.getWorldLocation()) > 4)
		{
			log.info("Walking closer to Aisles at {}", aisles.getWorldLocation());
			walkToWorldPoint(aisles.getWorldLocation());
			actionCooldownTicks = 2;
			return;
		}

		// 4. Use noted Pure Essence on Aisles
		useEssenceOnAisles(aisles);
		actionCooldownTicks = 2;
	}

	/**
	 * Exact user requirement:
	 * 1. Klikkaa 1 kerran Nature talismania että sen reunat muuttuvat valkoisiksi (aktiivinen)
	 * 2. Varmista että talisman on aktiivinen (client.isWidgetSelected() == true)
	 * 3. Klikkaa 1 kerran Mysterious ruinsia sisään menemiseksi
	 */
	private void enterMysteriousRuins(WorldPoint playerLoc)
	{
		// Step 0: Ensure player is at ruins destination and stopped
		if (ruinsStep == RuinsStep.WAIT_ARRIVAL)
		{
			if (playerLoc.distanceTo(NATURE_ALTAR) > 2)
			{
				walkToWorldPoint(NATURE_ALTAR);
				actionCooldownTicks = 2;
				return;
			}
			talismanActivationAttempts = 0;
			ruinsStep = RuinsStep.ACTIVATE_TALISMAN;
			actionCooldownTicks = 1;
			return;
		}

		// Step 1: Click 1 time on Nature talisman in inventory
		if (ruinsStep == RuinsStep.ACTIVATE_TALISMAN)
		{
			// Check if already active (white border)
			if (client.isWidgetSelected())
			{
				log.info("Nature talisman on jo aktiivinen (reunat valkoiset)! Siirrytään klikkaamaan 1 kerran ruinsia.");
				ruinsStep = RuinsStep.CLICK_RUINS;
				actionCooldownTicks = 1;
				return;
			}

			int talismanSlot = findNatureTalismanSlot();
			if (talismanSlot == -1)
			{
				log.warn("Nature talisman not found in inventory!");
				status = "NO NATURE TALISMAN";
				statusColor = Color.RED;
				sendGameMessage("Huomio: Nature talisman puuttuu inventorystä!");
				actionCooldownTicks = 3;
				return;
			}

			log.info("Klikataan 1 kerran Nature talismania (slot {}) että reunat muuttuvat valkoisiksi...", talismanSlot);
			status = "KLIKATAAN TALISMANIA (1 KERRAN)";
			statusColor = Color.YELLOW;
			sendGameMessage("Klikataan 1 kerran Nature talismania (reunat valkoisiksi)...");

			Point slotCenter = getInventoryItemCenter(talismanSlot);
			if (slotCenter != null)
			{
				// Exactly 1 clean left click on the talisman slot!
				dispatchCanvasClick(slotCenter);
			}
			else
			{
				// Fallback only if bounds are unavailable
				client.menuAction(
					talismanSlot,
					InterfaceID.Inventory.ITEMS,
					MenuAction.WIDGET_TARGET,
					0,
					ItemID.NATURE_TALISMAN,
					"Use",
					"Nature talisman"
				);
			}

			talismanActivationAttempts++;
			isTalismanActivated = true;
			ruinsStep = RuinsStep.WAIT_TALISMAN_ACTIVE;
			actionCooldownTicks = 2; // Wait 2 ticks (1.2s) for white border to appear
			return;
		}

		// Step 2: Confirm Nature talisman is active (borders are white)
		if (ruinsStep == RuinsStep.WAIT_TALISMAN_ACTIVE)
		{
			if (client.isWidgetSelected())
			{
				log.info("Nature talismanin reunat ovat nyt valkoiset (aktiivinen)! Klikataan seuraavaksi 1 kerran Mysterious ruinsia.");
				status = "TALISMAN AKTIIVINEN (VALKOISET REUNAT)";
				statusColor = Color.GREEN;
				sendGameMessage("Talisman aktiivinen (reunat valkoiset)! Klikataan 1 kerran Mysterious ruinsia...");
				ruinsStep = RuinsStep.CLICK_RUINS;
				actionCooldownTicks = 1; // 1 tick calm pause before clicking ruins
				return;
			}
			else
			{
				log.info("Talismanin reunat eivät vielä valkoiset (attempts: {}). Odotetaan/yritetään uudelleen...", talismanActivationAttempts);
				if (talismanActivationAttempts < 2)
				{
					talismanActivationAttempts++;
					int talismanSlot = findNatureTalismanSlot();
					if (talismanSlot != -1)
					{
						client.menuAction(
							talismanSlot,
							InterfaceID.Inventory.ITEMS,
							MenuAction.WIDGET_TARGET,
							0,
							ItemID.NATURE_TALISMAN,
							"Use",
							"Nature talisman"
						);
					}
					actionCooldownTicks = 2;
					return;
				}
				else
				{
					ruinsStep = RuinsStep.ACTIVATE_TALISMAN;
					actionCooldownTicks = 1;
					return;
				}
			}
		}

		// Step 3: Talisman is active (white border)! Now click 1 time on Mysterious ruins (ID 34821)
		if (ruinsStep == RuinsStep.CLICK_RUINS)
		{
			// Verify talisman is still active before clicking ruins!
			if (!client.isWidgetSelected())
			{
				log.info("Talisman ei olekaan aktiivinen, palataan aktivoimaan 1 kerran...");
				ruinsStep = RuinsStep.ACTIVATE_TALISMAN;
				actionCooldownTicks = 1;
				return;
			}

			TileObject ruins = findMysteriousRuins();
			if (ruins == null)
			{
				log.info("Mysterious ruins (ID {}) ei löytynyt skenestä, kävellään lähemmäs {}", MYSTERIOUS_RUINS_ID, NATURE_ALTAR);
				walkToWorldPoint(NATURE_ALTAR);
				actionCooldownTicks = 2;
				return;
			}

			LocalPoint lp = ruins.getLocalLocation();
			if (lp == null)
			{
				return;
			}

			int param0 = lp.getSceneX();
			int param1 = lp.getSceneY();
			if (ruins instanceof GameObject)
			{
				Point sceneMin = ((GameObject) ruins).getSceneMinLocation();
				if (sceneMin != null)
				{
					param0 = sceneMin.getX();
					param1 = sceneMin.getY();
				}
			}

			int ruinsId = ruins.getId();
			String ruinsName = "Mysterious ruins";
			try
			{
				ObjectComposition def = client.getObjectDefinition(ruinsId);
				if (def != null && def.getName() != null && !def.getName().isEmpty())
				{
					ruinsName = def.getName();
				}
			}
			catch (Exception ignored)
			{
			}

			log.info("Talisman on aktiivinen! Klikataan 1 kerran Mysterious ruinsia (ID {})...", ruinsId);
			status = "KLIKATAAN RUINSIA (1 KERRAN)";
			statusColor = Color.GREEN;
			sendGameMessage("Klikataan 1 kerran Mysterious ruinsia...");

			// Find screen click point on Mysterious ruins
			Point clickPoint = getRuinsClickPoint(ruins);

			// Exactly 1 clean left click on Mysterious ruins!
			if (clickPoint != null)
			{
				dispatchCanvasClick(clickPoint);
			}
			else
			{
				client.menuAction(
					param0,
					param1,
					MenuAction.WIDGET_TARGET_ON_GAME_OBJECT,
					ruinsId,
					0,
					"Use",
					"Use Nature talisman -> " + ruinsName
				);
			}

			isTalismanActivated = false;
			ruinsStep = RuinsStep.WAIT_ENTRY;
			actionCooldownTicks = 3; // Wait 3 ticks (1.8s) for character to enter
			return;
		}

		// Step 4: Wait for entry confirmation
		if (ruinsStep == RuinsStep.WAIT_ENTRY)
		{
			if (isInsideNatureAltar(playerLoc))
			{
				return; // Handled by handleEnteringRuins
			}
			// If not yet entered:
			if (client.isWidgetSelected())
			{
				// Talisman still active, re-click ruins 1 time
				ruinsStep = RuinsStep.CLICK_RUINS;
				actionCooldownTicks = 1;
			}
			else
			{
				// Talisman not active, re-activate
				ruinsStep = RuinsStep.ACTIVATE_TALISMAN;
				actionCooldownTicks = 1;
			}
		}
	}

	private Point getInventoryItemCenter(int slot)
	{
		Widget invWidget = client.getWidget(InterfaceID.Inventory.ITEMS);
		if (invWidget != null)
		{
			Widget child = invWidget.getChild(slot);
			if (child != null)
			{
				Rectangle bounds = child.getBounds();
				if (bounds != null && bounds.width > 0 && bounds.height > 0)
				{
					return new Point((int) bounds.getCenterX(), (int) bounds.getCenterY());
				}
			}
		}
		return null;
	}

	private void interactWithGameObject(TileObject obj, int defaultId, String defaultOption, String defaultTarget)
	{
		if (obj == null)
		{
			return;
		}

		LocalPoint lp = obj.getLocalLocation();
		if (lp == null)
		{
			return;
		}

		int param0 = lp.getSceneX();
		int param1 = lp.getSceneY();
		if (obj instanceof GameObject)
		{
			Point sceneMin = ((GameObject) obj).getSceneMinLocation();
			if (sceneMin != null)
			{
				param0 = sceneMin.getX();
				param1 = sceneMin.getY();
			}
		}

		int identifier = obj.getId() > 0 ? obj.getId() : defaultId;
		MenuAction menuAction = MenuAction.GAME_OBJECT_FIRST_OPTION;
		String actualOption = defaultOption;
		String actualTarget = defaultTarget;

		try
		{
			ObjectComposition def = client.getObjectDefinition(obj.getId());
			if (def != null)
			{
				if (def.getImpostorIds() != null && def.getImpostor() != null)
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

		log.info("Interacting with GameObject '{}' (ID {}) via menuAction {} option '{}' at scene ({}, {})",
			actualTarget, identifier, menuAction, actualOption, param0, param1);

		// Directly invoke game object menu action (RED CLICK)
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

	private Point getObjectClickPoint(TileObject obj)
	{
		if (obj == null)
		{
			return null;
		}

		Shape clickbox = obj.getClickbox();
		if (clickbox != null && clickbox.getBounds() != null)
		{
			Rectangle bounds = clickbox.getBounds();
			if (bounds.width > 0 && bounds.height > 0)
			{
				int cx = (int) bounds.getCenterX();
				int cy = (int) bounds.getCenterY();
				int cw = client.getCanvasWidth();
				int ch = client.getCanvasHeight();
				if (cx >= 20 && cx < (cw - 220) && cy >= 20 && cy < (ch - 160))
				{
					return new Point(cx, cy);
				}
			}
		}

		LocalPoint lp = obj.getLocalLocation();
		if (lp != null)
		{
			Point p = Perspective.localToCanvas(client, lp, client.getPlane(), 50);
			if (p != null)
			{
				int cw = client.getCanvasWidth();
				int ch = client.getCanvasHeight();
				if (p.getX() >= 20 && p.getX() < (cw - 220) && p.getY() >= 20 && p.getY() < (ch - 160))
				{
					return p;
				}
			}
		}

		return null;
	}

	private Point getRuinsClickPoint(TileObject ruins)
	{
		return getObjectClickPoint(ruins);
	}

	private TileObject findNatureAltar()
	{
		TileObject obj = findGameObjectById(NATURE_ALTAR_OBJECT_ID);
		if (obj != null)
		{
			return obj;
		}
		return findGameObjectByName("Altar");
	}

	private TileObject findPortal()
	{
		TileObject obj = findGameObjectById(NATURE_PORTAL_OBJECT_ID);
		if (obj != null)
		{
			return obj;
		}
		return findGameObjectByName("Portal");
	}

	private TileObject findGameObjectByName(String matchName)
	{
		Tile[][][] tiles = client.getScene().getTiles();
		int plane = client.getPlane();
		if (tiles == null || plane >= tiles.length)
		{
			return null;
		}

		for (int x = 0; x < Constants.SCENE_SIZE; x++)
		{
			for (int y = 0; y < Constants.SCENE_SIZE; y++)
			{
				Tile tile = tiles[plane][x][y];
				if (tile == null)
				{
					continue;
				}

				GameObject[] gameObjects = tile.getGameObjects();
				if (gameObjects != null)
				{
					for (GameObject go : gameObjects)
					{
						if (go != null)
						{
							try
							{
								ObjectComposition def = client.getObjectDefinition(go.getId());
								if (def != null && def.getName() != null && def.getName().equalsIgnoreCase(matchName))
								{
									return go;
								}
							}
							catch (Exception ignored)
							{
							}
						}
					}
				}
			}
		}

		return null;
	}

	private TileObject findGameObjectById(int targetId)
	{
		Tile[][][] tiles = client.getScene().getTiles();
		int plane = client.getPlane();
		if (tiles == null || plane >= tiles.length)
		{
			return null;
		}

		Player localPlayer = client.getLocalPlayer();
		WorldPoint playerLoc = localPlayer != null ? localPlayer.getWorldLocation() : null;

		TileObject bestObj = null;
		int closestDist = Integer.MAX_VALUE;

		for (int x = 0; x < Constants.SCENE_SIZE; x++)
		{
			for (int y = 0; y < Constants.SCENE_SIZE; y++)
			{
				Tile tile = tiles[plane][x][y];
				if (tile == null)
				{
					continue;
				}

				GameObject[] gameObjects = tile.getGameObjects();
				if (gameObjects != null)
				{
					for (GameObject go : gameObjects)
					{
						if (go != null && matchesTargetId(go.getId(), targetId))
						{
							if (playerLoc == null)
							{
								return go;
							}
							int d = playerLoc.distanceTo(go.getWorldLocation());
							if (d < closestDist)
							{
								closestDist = d;
								bestObj = go;
							}
						}
					}
				}

				WallObject wo = tile.getWallObject();
				if (wo != null && matchesTargetId(wo.getId(), targetId))
				{
					return wo;
				}

				DecorativeObject deco = tile.getDecorativeObject();
				if (deco != null && matchesTargetId(deco.getId(), targetId))
				{
					return deco;
				}

				GroundObject gro = tile.getGroundObject();
				if (gro != null && matchesTargetId(gro.getId(), targetId))
				{
					return gro;
				}
			}
		}

		return bestObj;
	}

	private boolean matchesTargetId(int objId, int targetId)
	{
		if (objId == targetId)
		{
			return true;
		}
		try
		{
			ObjectComposition def = client.getObjectDefinition(objId);
			if (def != null)
			{
				if (def.getId() == targetId)
				{
					return true;
				}
				if (def.getImpostor() != null && def.getImpostor().getId() == targetId)
				{
					return true;
				}
				if (def.getImpostorIds() != null)
				{
					for (int impId : def.getImpostorIds())
					{
						if (impId == targetId)
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

	private TileObject findMysteriousRuins()
	{
		Tile[][][] tiles = client.getScene().getTiles();
		int plane = client.getPlane();
		if (tiles == null || plane >= tiles.length)
		{
			return null;
		}

		Player localPlayer = client.getLocalPlayer();
		WorldPoint playerLoc = localPlayer != null ? localPlayer.getWorldLocation() : null;

		// 1. First pass: Check for GameObject with exact ID 34821
		for (int x = 0; x < Constants.SCENE_SIZE; x++)
		{
			for (int y = 0; y < Constants.SCENE_SIZE; y++)
			{
				Tile tile = tiles[plane][x][y];
				if (tile == null)
				{
					continue;
				}

				GameObject[] gameObjects = tile.getGameObjects();
				if (gameObjects != null)
				{
					for (GameObject go : gameObjects)
					{
						if (go != null && (go.getId() == MYSTERIOUS_RUINS_ID || go.getId() == 34821))
						{
							return go;
						}
					}
				}
			}
		}

		// 2. Second pass: Check GameObjects strictly matching isMysteriousRuins (rejecting Rubble)
		GameObject bestRuins = null;
		int closestDist = Integer.MAX_VALUE;

		for (int x = 0; x < Constants.SCENE_SIZE; x++)
		{
			for (int y = 0; y < Constants.SCENE_SIZE; y++)
			{
				Tile tile = tiles[plane][x][y];
				if (tile == null)
				{
					continue;
				}

				GameObject[] gameObjects = tile.getGameObjects();
				if (gameObjects != null)
				{
					for (GameObject go : gameObjects)
					{
						if (go != null && isMysteriousRuins(go))
						{
							if (playerLoc == null)
							{
								return go;
							}
							int d = playerLoc.distanceTo(go.getWorldLocation());
							if (d < closestDist)
							{
								closestDist = d;
								bestRuins = go;
							}
						}
					}
				}
			}
		}

		return bestRuins;
	}

	private boolean isMysteriousRuins(TileObject obj)
	{
		if (obj == null)
		{
			return false;
		}

		int objId = obj.getId();

		// Check object definition first to strictly reject rubble
		try
		{
			ObjectComposition def = client.getObjectDefinition(objId);
			if (def != null)
			{
				String name = def.getName();
				if (name != null && name.toLowerCase().contains("rubble"))
				{
					return false;
				}

				if (def.getImpostor() != null)
				{
					String impName = def.getImpostor().getName();
					if (impName != null && impName.toLowerCase().contains("rubble"))
					{
						return false;
					}
					if (impName != null && impName.equalsIgnoreCase("Mysterious ruins"))
					{
						return true;
					}
					if (def.getImpostor().getId() == MYSTERIOUS_RUINS_ID || def.getImpostor().getId() == 34821)
					{
						return true;
					}
				}

				if (name != null && name.equalsIgnoreCase("Mysterious ruins"))
				{
					return true;
				}
				if (def.getId() == MYSTERIOUS_RUINS_ID || def.getId() == 34821)
				{
					return true;
				}
				if (def.getImpostorIds() != null)
				{
					for (int impId : def.getImpostorIds())
					{
						if (impId == MYSTERIOUS_RUINS_ID || impId == 34821)
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

		// Exact ID match check
		if (objId == MYSTERIOUS_RUINS_ID || objId == 34821)
		{
			return true;
		}

		return false;
	}

	private int findNatureTalismanSlot()
	{
		ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
		if (inv == null)
		{
			return -1;
		}
		Item[] items = inv.getItems();
		for (int i = 0; i < items.length; i++)
		{
			if (items[i] != null && items[i].getId() > 0)
			{
				int id = items[i].getId();
				if (id == ItemID.NATURE_TALISMAN || id == 1462)
				{
					return i;
				}
				try
				{
					ItemComposition comp = client.getItemDefinition(id);
					if (comp != null && comp.getName() != null && comp.getName().toLowerCase().contains("nature talisman"))
					{
						return i;
					}
				}
				catch (Exception ignored)
				{
				}
			}
		}
		return -1;
	}

	private NPC findAisles()
	{
		for (NPC npc : client.getNpcs())
		{
			if (npc != null)
			{
				if (npc.getId() == AISLES_NPC_ID)
				{
					return npc;
				}
				String name = npc.getName();
				if (name != null && name.equalsIgnoreCase("Aisles"))
				{
					return npc;
				}
			}
		}
		return null;
	}

	private boolean isDialogueOptionsOpen()
	{
		Widget chatMenu = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
		if (chatMenu != null && !chatMenu.isHidden())
		{
			return true;
		}
		Widget chatMenuFallback = client.getWidget(219, 1);
		return chatMenuFallback != null && !chatMenuFallback.isHidden();
	}

	private boolean handleAislesDialogue()
	{
		Widget chatMenu = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
		if (chatMenu == null || chatMenu.isHidden())
		{
			chatMenu = client.getWidget(219, 1);
		}

		if (chatMenu == null || chatMenu.isHidden())
		{
			return false;
		}

		Widget[] children = chatMenu.getChildren();
		if (children == null || children.length == 0)
		{
			children = chatMenu.getDynamicChildren();
		}

		int targetOptionIndex = 3; // Default option 3
		Widget targetWidget = null;

		if (children != null)
		{
			for (Widget child : children)
			{
				if (child != null && child.getText() != null)
				{
					String text = child.getText().toLowerCase();
					if (text.contains("exchange all") || (text.contains("exchange") && text.contains("100")))
					{
						targetWidget = child;
						targetOptionIndex = child.getIndex();
						log.info("Found exchange option text '{}' at index {}", child.getText(), targetOptionIndex);
						break;
					}
				}
			}

			if (targetWidget == null && children.length > 3 && children[3] != null)
			{
				targetWidget = children[3];
			}
		}

		log.info("Selecting dialogue Option 3 (Exchange all 100 coins.) via widget click & key press 3");
		status = "SELECTING OPTION 3 (EXCHANGE ALL)";
		statusColor = Color.YELLOW;
		sendGameMessage("Exchanging noted Pure Essence with Aisles (Option 3)...");

		// 1. Direct menuAction: WIDGET_CONTINUE on Chatmenu.OPTIONS
		client.menuAction(
			targetOptionIndex,
			InterfaceID.Chatmenu.OPTIONS,
			MenuAction.WIDGET_CONTINUE,
			targetOptionIndex,
			-1,
			"Continue",
			""
		);

		// 2. Dispatch canvas click on option if bounds available
		if (targetWidget != null)
		{
			Rectangle bounds = targetWidget.getBounds();
			if (bounds != null && bounds.width > 0 && bounds.height > 0)
			{
				dispatchCanvasClick(new Point((int) bounds.getCenterX(), (int) bounds.getCenterY()));
			}
		}

		// 3. Hotkey 3 (official OSRS dialogue hotkey)
		pressKey(KeyEvent.VK_3);
		pressKey(KeyEvent.VK_NUMPAD3);

		return true;
	}

	private void useEssenceOnAisles(NPC aisles)
	{
		int slot = findNotedEssenceSlot();
		if (slot == -1)
		{
			log.warn("No noted pure essence found in inventory to un-note!");
			return;
		}

		ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
		Item item = inv != null ? inv.getItem(slot) : null;
		int itemId = item != null ? item.getId() : 7937;
		String itemName = "Pure essence";
		try
		{
			ItemComposition comp = client.getItemDefinition(itemId);
			if (comp != null && comp.getName() != null)
			{
				itemName = comp.getName();
			}
		}
		catch (Exception ignored)
		{
		}

		unnoteAttempts++;
		log.info("Using noted essence {} (ID: {}) in slot {} on Aisles (attempt {})", itemName, itemId, slot, unnoteAttempts);
		status = "USING ESSENCE ON AISLES";
		statusColor = Color.YELLOW;

		// 1. Select "Use" on noted essence in inventory
		client.menuAction(
			slot,
			InterfaceID.Inventory.ITEMS,
			MenuAction.WIDGET_TARGET,
			0,
			itemId,
			"Use",
			itemName
		);

		// 2. Target NPC Aisles
		client.menuAction(
			0,
			0,
			MenuAction.WIDGET_TARGET_ON_NPC,
			aisles.getIndex(),
			0,
			"Use",
			"Use " + itemName + " -> " + aisles.getName()
		);

		// Fallback: If several attempts made, also try Talk-to
		if (unnoteAttempts % 3 == 0)
		{
			client.menuAction(
				0,
				0,
				MenuAction.NPC_FIRST_OPTION,
				aisles.getIndex(),
				0,
				"Talk-to",
				aisles.getName()
			);
		}
	}

	private int findNotedEssenceSlot()
	{
		ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
		if (inv == null)
		{
			return -1;
		}
		Item[] items = inv.getItems();
		for (int i = 0; i < items.length; i++)
		{
			if (items[i] != null && items[i].getId() > 0)
			{
				if (isNotedEssence(items[i].getId()))
				{
					return i;
				}
			}
		}
		return -1;
	}

	private int findUnnotedEssenceSlot()
	{
		ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
		if (inv == null)
		{
			return -1;
		}
		Item[] items = inv.getItems();
		for (int i = 0; i < items.length; i++)
		{
			if (items[i] != null && items[i].getId() > 0)
			{
				if (isUnnotedEssence(items[i].getId()))
				{
					return i;
				}
			}
		}
		return -1;
	}

	private boolean hasNotedEssence()
	{
		return findNotedEssenceSlot() != -1;
	}

	private boolean hasUnnotedEssence()
	{
		return findUnnotedEssenceSlot() != -1;
	}

	private int countUnnotedEssence()
	{
		ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
		if (inv == null)
		{
			return 0;
		}
		int count = 0;
		for (Item item : inv.getItems())
		{
			if (item != null && item.getId() > 0 && isUnnotedEssence(item.getId()))
			{
				count += item.getQuantity();
			}
		}
		return count;
	}

	private boolean isUnnotedEssence(int itemId)
	{
		if (itemId == ItemID.PURE_ESSENCE || itemId == 1436)
		{
			return true;
		}
		try
		{
			ItemComposition comp = client.getItemDefinition(itemId);
			if (comp != null && comp.getNote() == -1 && comp.getName() != null && comp.getName().toLowerCase().contains("essence"))
			{
				return true;
			}
		}
		catch (Exception ignored)
		{
		}
		return false;
	}

	private boolean isNotedEssence(int itemId)
	{
		if (itemId == 7937 || itemId == 1437)
		{
			return true;
		}
		try
		{
			ItemComposition comp = client.getItemDefinition(itemId);
			if (comp != null && comp.getNote() != -1 && comp.getName() != null && comp.getName().toLowerCase().contains("essence"))
			{
				return true;
			}
		}
		catch (Exception ignored)
		{
		}
		return false;
	}

	/**
	 * Strictly NO minimap clicks!
	 * Walks using 3D viewport canvas clicks or direct scene tile menuAction.
	 */
	private void walkToWorldPoint(WorldPoint wp)
	{
		if (wp == null)
		{
			return;
		}

		LocalPoint lp = LocalPoint.fromWorld(client, wp);
		if (lp == null)
		{
			Player localPlayer = client.getLocalPlayer();
			if (localPlayer != null)
			{
				WorldPoint playerLoc = localPlayer.getWorldLocation();
				int dx = Integer.compare(wp.getX(), playerLoc.getX());
				int dy = Integer.compare(wp.getY(), playerLoc.getY());
				WorldPoint intermediate = new WorldPoint(playerLoc.getX() + dx * 14, playerLoc.getY() + dy * 14, playerLoc.getPlane());
				lp = LocalPoint.fromWorld(client, intermediate);
			}
		}

		if (lp == null)
		{
			return;
		}

		// 1. Viewport canvas click (3D game screen ground click, strictly NEVER minimap)
		Point canvasPoint = Perspective.localToCanvas(client, lp, client.getPlane());
		if (canvasPoint != null)
		{
			int x = canvasPoint.getX();
			int y = canvasPoint.getY();
			int cw = client.getCanvasWidth();
			int ch = client.getCanvasHeight();
			// Ensure click is well inside 3D viewport and not overlapping chatbox or sidepanels
			if (x >= 20 && x < (cw - 220) && y >= 20 && y < (ch - 160))
			{
				dispatchCanvasClick(canvasPoint);
				return;
			}
		}

		// 2. Direct scene menu action (scene X & Y, NEVER touches minimap)
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
		MouseEvent release = new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, now + 25, 0, x, y, 1, false, MouseEvent.BUTTON1);
		MouseEvent click = new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, now + 25, 0, x, y, 1, false, MouseEvent.BUTTON1);

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

	private void pressKey(int keyCode)
	{
		Canvas canvas = client.getCanvas();
		if (canvas == null)
		{
			return;
		}

		long now = System.currentTimeMillis();
		KeyEvent press = new KeyEvent(canvas, KeyEvent.KEY_PRESSED, now, 0, keyCode, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_STANDARD);
		KeyEvent release = new KeyEvent(canvas, KeyEvent.KEY_RELEASED, now + 20, 0, keyCode, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_STANDARD);

		try
		{
			canvas.dispatchEvent(press);
			canvas.dispatchEvent(release);
		}
		catch (Exception ignored)
		{
		}

		SwingUtilities.invokeLater(() -> {
			try
			{
				canvas.dispatchEvent(press);
				canvas.dispatchEvent(release);
			}
			catch (Exception ignored)
			{
			}
		});
	}

	private void handleRunEnergy()
	{
		if (runToggleCooldown > 0)
		{
			runToggleCooldown--;
			return;
		}

		int minEnergy = Math.max(1, config.minRunEnergy()) * 100;
		int currentEnergy = client.getEnergy();
		int runMode = client.getVarpValue(VarPlayerID.OPTION_RUN);

		if (currentEnergy >= minEnergy && runMode == 0)
		{
			log.info("Toggling run ON (Energy: {}%, Min: {}%)", currentEnergy / 100, config.minRunEnergy());
			toggleRun();
			runToggleCooldown = 2; // Wait 2 ticks (1.2s) before checking again
		}
	}

	private Widget getRunOrbWidget()
	{
		int[] candidateIds = {
			InterfaceID.OrbsOsm.RUNBUTTON,      // 0x0381_0018 (Resizable Classic)
			InterfaceID.OrbsOsmNomap.RUNBUTTON, // 0x0382_0019 (Resizable Modern)
			InterfaceID.Orbs.RUNBUTTON,         // 0x00a0_001c (Fixed)
			InterfaceID.OrbsNomap.RUNBUTTON     // 0x037f_001a (OrbsNomap)
		};

		for (int id : candidateIds)
		{
			Widget w = client.getWidget(id);
			if (w != null && !w.isHidden() && w.getWidth() > 0 && w.getHeight() > 0)
			{
				return w;
			}
		}

		for (int id : candidateIds)
		{
			Widget w = client.getWidget(id);
			if (w != null && !w.isHidden())
			{
				return w;
			}
		}

		return null;
	}

	private void toggleRun()
	{
		Widget runOrb = getRunOrbWidget();
		if (runOrb != null)
		{
			int widgetId = runOrb.getId();
			log.info("Toggling run ON on widget 0x{}", Integer.toHexString(widgetId));

			// 1. Invoke menu action on active run orb widget
			client.menuAction(1, widgetId, MenuAction.CC_OP, 1, -1, "Toggle Run", "");
			client.menuAction(-1, widgetId, MenuAction.CC_OP, 1, -1, "Toggle Run", "");

			// 2. Dispatch canvas click on orb center
			Rectangle bounds = runOrb.getBounds();
			if (bounds != null && bounds.width > 0 && bounds.height > 0)
			{
				Point center = new Point((int) bounds.getCenterX(), (int) bounds.getCenterY());
				dispatchCanvasClick(center);
			}
		}
		else
		{
			// Fallback: invoke across all known run orb IDs
			client.menuAction(1, InterfaceID.OrbsOsm.RUNBUTTON, MenuAction.CC_OP, 1, -1, "Toggle Run", "");
			client.menuAction(1, InterfaceID.OrbsOsmNomap.RUNBUTTON, MenuAction.CC_OP, 1, -1, "Toggle Run", "");
			client.menuAction(1, InterfaceID.Orbs.RUNBUTTON, MenuAction.CC_OP, 1, -1, "Toggle Run", "");
			client.menuAction(1, InterfaceID.OrbsNomap.RUNBUTTON, MenuAction.CC_OP, 1, -1, "Toggle Run", "");
		}
	}

	private void handlePotions()
	{
		// If stamina buff is not active and energy is below 45%
		boolean staminaActive = client.getVarbitValue(Varbits.RUN_SLOWED_DEPLETION_ACTIVE) > 0;
		if (!staminaActive && energyPercent < 45)
		{
			if (drinkPotion(STAMINA_POTION_IDS, "Stamina potion"))
			{
				return;
			}
		}

		if (energyPercent < 30)
		{
			drinkPotion(ENERGY_POTION_IDS, "Energy potion");
		}
	}

	private boolean drinkPotion(Set<Integer> potionIds, String potionName)
	{
		int slot = findItemSlotInInventory(potionIds);
		if (slot == -1)
		{
			return false;
		}

		ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
		Item item = inv != null ? inv.getItem(slot) : null;
		int itemId = item != null ? item.getId() : -1;
		String itemName = potionName;
		try
		{
			ItemComposition def = client.getItemDefinition(itemId);
			if (def != null && def.getName() != null)
			{
				itemName = def.getName();
			}
		}
		catch (Exception ignored)
		{
		}

		log.info("Drinking {} in slot {}", itemName, slot);
		client.menuAction(
			slot,
			InterfaceID.Inventory.ITEMS,
			MenuAction.CC_OP,
			2,
			itemId,
			"Drink",
			itemName
		);
		sendGameMessage("Drank 1 dose of " + itemName + "!");
		return true;
	}

	private int findItemSlotInInventory(Set<Integer> validIds)
	{
		ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
		if (inv == null)
		{
			return -1;
		}
		Item[] items = inv.getItems();
		for (int i = 0; i < items.length; i++)
		{
			if (items[i] != null && validIds.contains(items[i].getId()))
			{
				return i;
			}
		}
		return -1;
	}

	private void sendGameMessage(String message)
	{
		String chatMessage = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append("[Nature Altar Script] ")
			.append(ChatColorType.NORMAL)
			.append(message)
			.build();

		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(chatMessage)
			.build());
	}
}
