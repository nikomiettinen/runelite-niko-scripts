/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.naturealtarscript;

import com.google.inject.Provides;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Rectangle;
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
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Player;
import net.runelite.api.Varbits;
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
	description = "Runs back and forth between Karamja General Store and Nature Altar, un-noting pure essence with Aisles",
	tags = {"nature", "altar", "karamja", "runecraft", "runner", "store", "aisles", "essence"}
)
public class NatureAltarScriptPlugin extends Plugin
{
	// Endpoint destinations specified by user
	public static final WorldPoint STORE_LOCATION = new WorldPoint(2767, 3121, 0);
	public static final WorldPoint NATURE_ALTAR = new WorldPoint(2869, 3012, 0);

	// NPC Aisles at Karamja General store
	public static final int AISLES_NPC_ID = 2134;

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
		RUNNING_TO_STORE,
		UNNOTING_AT_STORE,
		PAUSED_AT_ENDPOINT
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

	private void initializeRun()
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		WorldPoint playerLoc = localPlayer.getWorldLocation();
		int distToStore = playerLoc.distanceTo(STORE_LOCATION);
		int distToAltar = playerLoc.distanceTo(NATURE_ALTAR);

		unnotedEssenceCount = countUnnotedEssence();

		// If closer to store and we have noted essence but no unnoted essence, un-note first
		if (distToStore <= distToAltar && config.unnoteEssence() && hasNotedEssence() && unnotedEssenceCount == 0)
		{
			state = State.UNNOTING_AT_STORE;
			status = "UN-NOTING ESSENCE WITH AISLES";
			statusColor = Color.YELLOW;
			unnoteAttempts = 0;
			actionCooldownTicks = 1;
			sendGameMessage("At General Store: un-noting Pure Essence with Aisles first...");
			return;
		}

		// Find closest waypoint along the predefined route
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

		// If closer to store than altar, head towards altar
		if (distToStore <= distToAltar)
		{
			state = State.RUNNING_TO_ALTAR;
			currentWaypointIndex = Math.min(POSITIONS.length - 1, closestIdx + (closestIdx == 0 ? 1 : 0));
			status = "RUNNING TO NATURE ALTAR";
			statusColor = Color.CYAN;
			sendGameMessage("Starting run to Nature Altar! Starting at WP " + (currentWaypointIndex + 1));
		}
		else
		{
			state = State.RUNNING_TO_STORE;
			currentWaypointIndex = Math.max(0, closestIdx - (closestIdx == POSITIONS.length - 1 ? 1 : 0));
			status = "RUNNING TO GENERAL STORE";
			statusColor = Color.CYAN;
			sendGameMessage("Starting run to General Store! Starting at WP " + (currentWaypointIndex + 1));
		}

		stuckTicks = 0;
		actionCooldownTicks = 0;
		walkToWorldPoint(POSITIONS[currentWaypointIndex]);
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

		// Check if stuck
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

		switch (state)
		{
			case RUNNING_TO_ALTAR:
				handleRunningToAltar(playerLoc);
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

	private void handleRunningToAltar(WorldPoint playerLoc)
	{
		WorldPoint targetWp = POSITIONS[currentWaypointIndex];
		distanceToCurrentWaypoint = playerLoc.distanceTo(targetWp);

		// Check if arrived at overall destination (Nature Altar)
		if (playerLoc.distanceTo(NATURE_ALTAR) <= 3 || (currentWaypointIndex == POSITIONS.length - 1 && distanceToCurrentWaypoint <= 3))
		{
			lapsCompleted++;
			log.info("Arrived at Nature Altar (2869, 3012)! Trips completed: {}", lapsCompleted);
			sendGameMessage(String.format("Arrived at Nature Altar! Turning around to General Store. (Trips: %d)", lapsCompleted));
			state = State.RUNNING_TO_STORE;
			currentWaypointIndex = POSITIONS.length - 2;
			status = "RUNNING TO GENERAL STORE";
			statusColor = Color.CYAN;
			pauseTicks = Math.max(0, config.delayAtDestination());
			walkToWorldPoint(POSITIONS[currentWaypointIndex]);
			return;
		}

		// Advance to next waypoint when within 4 tiles for smooth running curve
		if (distanceToCurrentWaypoint <= 4)
		{
			if (currentWaypointIndex < POSITIONS.length - 1)
			{
				currentWaypointIndex++;
				targetWp = POSITIONS[currentWaypointIndex];
				distanceToCurrentWaypoint = playerLoc.distanceTo(targetWp);
				log.info("Advancing to Altar waypoint {}/{} at {}", currentWaypointIndex + 1, POSITIONS.length, targetWp);
				walkToWorldPoint(targetWp);
				actionCooldownTicks = 2;
				return;
			}
		}

		// Periodic heartbeat walk or anti-stuck re-click
		if (actionCooldownTicks <= 0 || stuckTicks >= 3)
		{
			log.info("Walking towards waypoint {}/{} (dist: {}, stuckTicks: {})", currentWaypointIndex + 1, POSITIONS.length, distanceToCurrentWaypoint, stuckTicks);
			status = "TO ALTAR (WP " + (currentWaypointIndex + 1) + "/" + POSITIONS.length + ")";
			statusColor = Color.CYAN;
			walkToWorldPoint(targetWp);
			actionCooldownTicks = 3;
			if (stuckTicks >= 5)
			{
				stuckTicks = 0;
			}
		}
	}

	private void handleRunningToStore(WorldPoint playerLoc)
	{
		WorldPoint targetWp = POSITIONS[currentWaypointIndex];
		distanceToCurrentWaypoint = playerLoc.distanceTo(targetWp);

		// Check if arrived at overall destination (General Store)
		if (playerLoc.distanceTo(STORE_LOCATION) <= 3 || (currentWaypointIndex == 0 && distanceToCurrentWaypoint <= 3))
		{
			lapsCompleted++;
			log.info("Arrived at Karamja General Store (2767, 3121)! Trips completed: {}", lapsCompleted);
			sendGameMessage(String.format("Arrived at General Store! (Trips: %d)", lapsCompleted));

			// If configured to un-note essence, and we have noted essence but no unnoted essence
			if (config.unnoteEssence() && hasNotedEssence() && unnotedEssenceCount == 0)
			{
				state = State.UNNOTING_AT_STORE;
				status = "UN-NOTING ESSENCE WITH AISLES";
				statusColor = Color.YELLOW;
				unnoteAttempts = 0;
				actionCooldownTicks = 1;
				return;
			}

			// Otherwise turn around directly to altar
			state = State.RUNNING_TO_ALTAR;
			currentWaypointIndex = 1;
			status = "RUNNING TO NATURE ALTAR";
			statusColor = Color.CYAN;
			pauseTicks = Math.max(0, config.delayAtDestination());
			walkToWorldPoint(POSITIONS[currentWaypointIndex]);
			return;
		}

		// Advance to next waypoint towards store
		if (distanceToCurrentWaypoint <= 4)
		{
			if (currentWaypointIndex > 0)
			{
				currentWaypointIndex--;
				targetWp = POSITIONS[currentWaypointIndex];
				distanceToCurrentWaypoint = playerLoc.distanceTo(targetWp);
				log.info("Advancing to Store waypoint {}/{} at {}", currentWaypointIndex + 1, POSITIONS.length, targetWp);
				walkToWorldPoint(targetWp);
				actionCooldownTicks = 2;
				return;
			}
		}

		// Periodic heartbeat walk or anti-stuck re-click
		if (actionCooldownTicks <= 0 || stuckTicks >= 3)
		{
			log.info("Walking towards waypoint {}/{} (dist: {}, stuckTicks: {})", currentWaypointIndex + 1, POSITIONS.length, distanceToCurrentWaypoint, stuckTicks);
			status = "TO STORE (WP " + (currentWaypointIndex + 1) + "/" + POSITIONS.length + ")";
			statusColor = Color.CYAN;
			walkToWorldPoint(targetWp);
			actionCooldownTicks = 3;
			if (stuckTicks >= 5)
			{
				stuckTicks = 0;
			}
		}
	}

	private void handleUnnotingAtStore(WorldPoint playerLoc)
	{
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
		int minEnergy = config.minRunEnergy() * 100;
		if (client.getEnergy() >= minEnergy && client.getVarpValue(VarPlayerID.OPTION_RUN) == 0)
		{
			log.info("Toggling run ON (Energy: {}%)", energyPercent);
			client.menuAction(1, InterfaceID.Orbs.RUNBUTTON, MenuAction.CC_OP, 1, -1, "Toggle Run", "");
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
