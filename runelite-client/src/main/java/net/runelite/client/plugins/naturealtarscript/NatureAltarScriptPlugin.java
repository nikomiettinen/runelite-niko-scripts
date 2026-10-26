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
import net.runelite.api.ItemID;
import net.runelite.api.MenuAction;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.Varbits;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.events.ConfigChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Nature Altar Script",
	description = "Runs back and forth between Karamja General Store and Nature Altar without minimap clicks",
	tags = {"nature", "altar", "karamja", "runecraft", "runner", "store"}
)
public class NatureAltarScriptPlugin extends Plugin
{
	// Endpoint destinations specified by user
	public static final WorldPoint STORE_LOCATION = new WorldPoint(2766, 3120, 0);
	public static final WorldPoint NATURE_ALTAR = new WorldPoint(2869, 3016, 0);

	// Full path connecting General Store and Nature Altar without obstacle collisions
	private static final WorldPoint[] POSITIONS = {
		new WorldPoint(2766, 3120, 0), // 0: Karamja General Store (Jiminua)
		new WorldPoint(2774, 3113, 0), // 1: Path exiting store area south-east
		new WorldPoint(2792, 3105, 0), // 2: Iron/coal mine bypass
		new WorldPoint(2811, 3097, 0), // 3: North of village fence
		new WorldPoint(2828, 3087, 0), // 4: Jungle clearing corridor
		new WorldPoint(2845, 3075, 0), // 5: Approaching coast treeline
		new WorldPoint(2856, 3056, 0), // 6: Treeline south
		new WorldPoint(2862, 3038, 0), // 7: South towards altar clearing
		new WorldPoint(2869, 3016, 0)  // 8: Nature Altar ruins
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

	private int pauseTicks = 0;
	private int stuckTicks = 0;
	private int actionCooldownTicks = 0;
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

			case PAUSED_AT_ENDPOINT:
				// End pause, continue in opposite direction
				break;
		}
	}

	private void handleRunningToAltar(WorldPoint playerLoc)
	{
		WorldPoint targetWp = POSITIONS[currentWaypointIndex];
		distanceToCurrentWaypoint = playerLoc.distanceTo(targetWp);

		// Check if arrived at overall destination (Nature Altar)
		if (playerLoc.distanceTo(NATURE_ALTAR) <= 4 || (currentWaypointIndex == POSITIONS.length - 1 && distanceToCurrentWaypoint <= 3))
		{
			lapsCompleted++;
			log.info("Arrived at Nature Altar (2869, 3016)! Trips completed: {}", lapsCompleted);
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
		if (playerLoc.distanceTo(STORE_LOCATION) <= 4 || (currentWaypointIndex == 0 && distanceToCurrentWaypoint <= 3))
		{
			lapsCompleted++;
			log.info("Arrived at Karamja General Store (2766, 3120)! Trips completed: {}", lapsCompleted);
			sendGameMessage(String.format("Arrived at General Store! Turning around to Nature Altar. (Trips: %d)", lapsCompleted));
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
		net.runelite.api.ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
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
