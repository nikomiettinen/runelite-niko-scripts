/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.woodcutterscript;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WidgetNode;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Woodcutter Script",
	description = "Chops trees with banking support (Oak, Yew, Magic) or powerchopping for normal trees, with bird nest collection and anti-afk",
	tags = {"woodcutting", "tree", "oak", "yew", "magic", "powerlevel", "logs", "bank", "nest", "birds", "script", "anti-afk"},
	enabledByDefault = false
)
public class WoodcutterScriptPlugin extends Plugin
{
	private static final int BANKER_ID = 2897;
	private static final int INVENTORY_CAPACITY = 28;
	private static final int BANK_RADIUS = 35;
	private static final String CHOP_ACTION = "Chop down";
	private static final String BANK_ACTION = "Bank";
	private static final int DEPOSIT_ALL_OP = 8;
	private static final int BANK_APPROACH_DISTANCE = 8;
	private static final int NORMAL_TREE_MAX_SEARCH_RADIUS = 18;
	private static final int NEST_SEARCH_RADIUS = 15;

	// Anti-logout timeouts (25 minutes extended vs 5 minutes default)
	private static final int EXTENDED_LOGOUT_TIMEOUT = (int) Duration.ofMinutes(25).toMillis() / Constants.CLIENT_TICK_LENGTH;
	private static final int DEFAULT_LOGOUT_TIMEOUT = (int) Duration.ofMinutes(5).toMillis() / Constants.CLIENT_TICK_LENGTH;

	// Comprehensive set of all bird nests dropped from woodcutting in OSRS
	private static final Set<Integer> BIRD_NEST_IDS = ImmutableSet.of(
		// Standard bird nests
		ItemID.BIRD_NEST_EMPTY, // 5075
		ItemID.BIRD_NEST_RING, // 5074
		ItemID.BIRD_NEST_SEEDS, // 5073
		ItemID.BIRD_NEST_EGG_RED, // 5070
		ItemID.BIRD_NEST_EGG_GREEN, // 5071
		ItemID.BIRD_NEST_EGG_BLUE, // 5072
		ItemID.BIRD_NEST_CHEAPSEEDS, // 7413
		ItemID.BIRD_NEST_DECENTSEEDS, // 13653
		ItemID.BIRD_NEST_SEEDS_JAN2019, // 22798
		ItemID.BIRD_NEST_DECENTSEEDS_JAN2019, // 22800

		// Clue nests (all tiers)
		ItemID.WC_CLUE_NEST_BEGINNER, // 23127
		ItemID.WC_CLUE_NEST_EASY, // 19712
		ItemID.WC_CLUE_NEST_MEDIUM, // 19714
		ItemID.WC_CLUE_NEST_HARD, // 19716
		ItemID.WC_CLUE_NEST_ELITE, // 19718

		// Alternate/duplicate client cache item IDs
		13989, 13990, 13991, 13992, 13993, 13994, 13995,
		19713, 19715, 19717, 19719, 20783, 22799, 22801, 23128
	);

	@Getter
	public enum TreeType
	{
		NORMAL("Normal Tree", "Powerchop (No bank)", "", 0, ItemID.LOGS, Set.of(), null, true),
		OAK("Oak", "Varrock West", "Bank booth", 10820, ItemID.OAK_LOGS, Set.of(34810, 10583),
			new WorldPoint(3185, 3441, 0), false),
		YEW("Yew", "Woodcutting Guild", "Bank chest", ObjectID.YEWTREE, ItemID.YEW_LOGS,
			Set.of(ObjectID.WCGUILD_BANKCHEST), new WorldPoint(1592, 3475, 0), false),
		MAGIC("Magic", "Woodcutting Guild", "Bank chest", ObjectID.MAGICTREE, ItemID.MAGIC_LOGS,
			Set.of(ObjectID.WCGUILD_BANKCHEST), new WorldPoint(1592, 3475, 0), false);

		private final String treeName;
		private final String label;
		private final String bankTarget;
		private final int treeId;
		private final int logId;
		private final Set<Integer> bankObjectIds;
		private final WorldPoint bankLocation;
		private final boolean powerchopOnly;

		TreeType(String treeName, String locationName, String bankTarget, int treeId, int logId,
			Set<Integer> bankObjectIds, WorldPoint bankLocation, boolean powerchopOnly)
		{
			this.treeName = treeName;
			this.label = treeName + " - " + locationName;
			this.bankTarget = bankTarget;
			this.treeId = treeId;
			this.logId = logId;
			this.bankObjectIds = bankObjectIds;
			this.bankLocation = bankLocation;
			this.powerchopOnly = powerchopOnly;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@Getter
	public enum DropMode
	{
		DROP_WHEN_FULL("Drop When Full"),
		DROP_IMMEDIATELY("Drop Immediately");

		private final String label;

		DropMode(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@Getter
	public enum Status
	{
		STOPPED("STOPPED"),
		OUT_OF_AREA("Go to tree area"),
		CHOPPING("Chopping tree"),
		WAITING_FOR_TREE("Waiting for tree spawn"),
		PICKING_UP_NEST("Picking up bird nest"),
		DROPPING_LOGS("Dropping normal logs"),
		WALKING_TO_BANK("Walking to bank"),
		BANKING("Banking");

		private final String label;

		Status(String label)
		{
			this.label = label;
		}
	}

	private static class GroundBirdNest
	{
		private final Tile tile;
		private final int itemId;
		private final String name;

		GroundBirdNest(Tile tile, int itemId, String name)
		{
			this.tile = tile;
			this.itemId = itemId;
			this.name = name;
		}

		Tile getTile()
		{
			return tile;
		}

		int getItemId()
		{
			return itemId;
		}

		String getName()
		{
			return name;
		}
	}

	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private WoodcutterScriptOverlay overlay;

	@Inject
	private WoodcutterScriptConfig config;

	private int actionDelay;
	private boolean tryBoothNext = true;
	private long scriptStartedAt;
	private long elapsedNanos;
	private int startingWoodcuttingXp;

	@Getter
	private long woodcuttingXpGained;

	@Getter
	private int logsCut;

	@Getter
	private int nestsCollected;

	private int previousLogCount;
	private boolean xpTracking;
	private Status currentStatus = Status.STOPPED;

	private GameObject currentTreeTarget = null;
	private LocalPoint previousPlayerLoc = null;
	private boolean isDroppingLogs = false;

	// Ground bird nests tracking
	private final Set<Tile> trackedNestTiles = new HashSet<>();
	private GroundBirdNest currentNestTarget = null;

	// Anti-AFK state
	private long lastAntiLogoutTimeMs = 0;
	private int nextAntiLogoutThreshold = 5_000 + (int) (Math.random() * 4_500);

	@Provides
	WoodcutterScriptConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(WoodcutterScriptConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		logsCut = 0;
		nestsCollected = 0;
		woodcuttingXpGained = 0;
		currentStatus = Status.STOPPED;
		currentTreeTarget = null;
		previousPlayerLoc = null;
		isDroppingLogs = false;
		trackedNestTiles.clear();
		currentNestTarget = null;

		if (config.antiAfk())
		{
			client.setIdleTimeout(EXTENDED_LOGOUT_TIMEOUT);
		}

		if (config.startScript())
		{
			startSession();
		}
	}

	@Override
	protected void shutDown()
	{
		client.setIdleTimeout(DEFAULT_LOGOUT_TIMEOUT);
		overlayManager.remove(overlay);
		currentStatus = Status.STOPPED;
		currentTreeTarget = null;
		previousPlayerLoc = null;
		isDroppingLogs = false;
		trackedNestTiles.clear();
		currentNestTarget = null;
	}

	private void startSession()
	{
		actionDelay = 0;
		tryBoothNext = true;
		scriptStartedAt = System.nanoTime();
		elapsedNanos = 0;
		xpTracking = client.getGameState() == GameState.LOGGED_IN;
		startingWoodcuttingXp = xpTracking ? client.getSkillExperience(Skill.WOODCUTTING) : 0;
		woodcuttingXpGained = 0;
		logsCut = 0;
		nestsCollected = 0;
		previousLogCount = getInventoryLogCount(config.treeType());
		currentStatus = Status.CHOPPING;
		currentTreeTarget = null;
		previousPlayerLoc = null;
		isDroppingLogs = false;
		trackedNestTiles.clear();
		currentNestTarget = null;
		lastAntiLogoutTimeMs = System.currentTimeMillis();
		nextAntiLogoutThreshold = 5_000 + (int) (Math.random() * 4_500);

		if (config.antiAfk())
		{
			client.setIdleTimeout(EXTENDED_LOGOUT_TIMEOUT);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("woodcutterscript"))
		{
			return;
		}

		if ("startScript".equals(event.getKey()))
		{
			if (config.startScript())
			{
				startSession();
			}
			else
			{
				currentStatus = Status.STOPPED;
				currentTreeTarget = null;
				isDroppingLogs = false;
				currentNestTarget = null;
			}
		}
		else if ("treeType".equals(event.getKey()))
		{
			previousLogCount = getInventoryLogCount(config.treeType());
			actionDelay = 0;
			tryBoothNext = true;
			currentTreeTarget = null;
			isDroppingLogs = false;
			currentNestTarget = null;
			if (config.startScript())
			{
				currentStatus = Status.CHOPPING;
			}
		}
		else if ("antiAfk".equals(event.getKey()))
		{
			if (config.antiAfk())
			{
				client.setIdleTimeout(EXTENDED_LOGOUT_TIMEOUT);
			}
			else
			{
				client.setIdleTimeout(DEFAULT_LOGOUT_TIMEOUT);
			}
		}
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned itemSpawned)
	{
		TileItem item = itemSpawned.getItem();
		if (item != null && isBirdNest(item.getId()))
		{
			trackedNestTiles.add(itemSpawned.getTile());
			log.info("Bird nest spawned on ground at {} (ID: {})",
				itemSpawned.getTile().getWorldLocation(), item.getId());
		}
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned itemDespawned)
	{
		TileItem item = itemDespawned.getItem();
		if (item != null && isBirdNest(item.getId()))
		{
			if (trackedNestTiles.remove(itemDespawned.getTile()))
			{
				if (currentStatus == Status.PICKING_UP_NEST)
				{
					nestsCollected++;
					log.info("Bird nest collected! Total nests: {}", nestsCollected);
					currentNestTarget = null;
				}
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		handleAntiLogout();

		if (!config.startScript() || client.getGameState() != GameState.LOGGED_IN)
		{
			currentStatus = Status.STOPPED;
			return;
		}

		updateMetrics();

		if (actionDelay > 0)
		{
			actionDelay--;
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		TreeType treeType = config.treeType();
		WorldPoint playerLocation = localPlayer.getWorldLocation();

		// Track movement
		LocalPoint currentLoc = localPlayer.getLocalLocation();
		boolean isMoving = previousPlayerLoc != null && !previousPlayerLoc.equals(currentLoc);
		previousPlayerLoc = currentLoc;

		// ==========================================
		// 0. BIRD NEST PICKUP (HIGH PRIORITY)
		// ==========================================
		if (config.pickupBirdNests())
		{
			GroundBirdNest nearestNest = findNearestBirdNest(playerLocation);
			if (nearestNest != null)
			{
				if (isInventoryFull())
				{
					// Free up 1 inventory slot by dropping 1 log
					int logToDrop = treeType.getLogId();
					if (dropSingleLog(logToDrop))
					{
						currentStatus = Status.DROPPING_LOGS;
						actionDelay = 1;
						return;
					}
				}
				else
				{
					// If already walking to this nest, wait for pickup
					if (currentNestTarget != null && nearestNest.getTile().equals(currentNestTarget.getTile()) && isMoving)
					{
						currentStatus = Status.PICKING_UP_NEST;
						return;
					}

					currentStatus = Status.PICKING_UP_NEST;
					currentNestTarget = nearestNest;
					currentTreeTarget = null;
					takeGroundItem(nearestNest.getTile(), nearestNest.getItemId(), nearestNest.getName());
					actionDelay = 3;
					return;
				}
			}
			else
			{
				currentNestTarget = null;
			}
		}

		// ==========================================
		// 1. NORMAL TREES: POWERCHOPPING (NO BANKING)
		// ==========================================
		if (treeType.isPowerchopOnly())
		{
			int normalLogsInInv = getInventoryNormalLogCount();

			// Determine if dropping is required
			if (config.dropMode() == DropMode.DROP_WHEN_FULL)
			{
				if (isInventoryFull() && normalLogsInInv > 0)
				{
					isDroppingLogs = true;
				}
			}
			else if (config.dropMode() == DropMode.DROP_IMMEDIATELY)
			{
				if (normalLogsInInv > 0 && localPlayer.getAnimation() == -1)
				{
					isDroppingLogs = true;
				}
			}

			// Drop normal logs one by one
			if (isDroppingLogs)
			{
				if (normalLogsInInv > 0)
				{
					currentStatus = Status.DROPPING_LOGS;
					dropSingleNormalLog();
					actionDelay = 1;
					return;
				}
				else
				{
					isDroppingLogs = false;
					currentTreeTarget = null;
				}
			}

			boolean isAnimating = localPlayer.getAnimation() != -1;

			// Verify if current normal tree target is still alive
			if (currentTreeTarget != null && !isNormalTree(currentTreeTarget))
			{
				currentTreeTarget = null;
			}

			// If player is swinging axe or walking to the normal tree, wait
			if (currentTreeTarget != null)
			{
				if (isAnimating || isMoving)
				{
					currentStatus = Status.CHOPPING;
					return;
				}
			}

			// Find nearest normal tree
			GameObject nearestTree = findNearestNormalTree(playerLocation);
			if (nearestTree == null)
			{
				currentStatus = Status.WAITING_FOR_TREE;
				currentTreeTarget = null;
				return;
			}

			currentStatus = Status.CHOPPING;
			currentTreeTarget = nearestTree;
			interactWithObject(nearestTree, CHOP_ACTION, "Tree");
			actionDelay = 3;
			return;
		}

		// ==========================================
		// 2. OTHER TREES (OAK, YEW, MAGIC): BANKING
		// ==========================================
		if (treeType.getBankLocation() != null && playerLocation.distanceTo(treeType.getBankLocation()) > BANK_RADIUS)
		{
			currentStatus = Status.OUT_OF_AREA;
			return;
		}

		if (isBankOpen())
		{
			bankLogs();
			return;
		}

		if (isInventoryFull())
		{
			GameObject bankBooth = findNearestObject(treeType.getBankObjectIds(), playerLocation);
			NPC banker = client.getNpcs().stream()
				.filter(npc -> npc.getId() == BANKER_ID)
				.min(Comparator.comparingInt(npc -> npc.getWorldLocation().distanceTo(playerLocation)))
				.orElse(null);

			if (tryBoothNext && bankBooth != null)
			{
				currentStatus = Status.WALKING_TO_BANK;
				interactWithObject(bankBooth, BANK_ACTION, treeType.getBankTarget());
				tryBoothNext = false;
				actionDelay = 5;
				return;
			}

			if (banker != null)
			{
				currentStatus = Status.WALKING_TO_BANK;
				MenuAction bankAction = getBankerAction(banker);
				client.menuAction(banker.getIndex(), 0, bankAction,
					BANKER_ID, 0, BANK_ACTION, "Banker");
				tryBoothNext = true;
				actionDelay = 5;
				return;
			}

			if (bankBooth != null)
			{
				currentStatus = Status.WALKING_TO_BANK;
				interactWithObject(bankBooth, BANK_ACTION, treeType.getBankTarget());
				tryBoothNext = true;
				actionDelay = 5;
				return;
			}

			walkTowardBank(playerLocation);
			return;
		}

		if (localPlayer.getAnimation() != -1)
		{
			currentStatus = Status.CHOPPING;
			return;
		}

		GameObject tree = findNearestTree(playerLocation);
		if (tree == null)
		{
			currentStatus = Status.WAITING_FOR_TREE;
			return;
		}

		currentStatus = Status.CHOPPING;
		interactWithObject(tree, CHOP_ACTION, treeType.getTreeName());
		actionDelay = 4;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getItemContainer() != client.getItemContainer(InventoryID.INV))
		{
			return;
		}

		int currentLogCount = getInventoryLogCount(config.treeType(), event.getItemContainer());
		if (config.startScript())
		{
			if (currentLogCount > previousLogCount)
			{
				logsCut += (currentLogCount - previousLogCount);
				updateMetrics();
			}
		}
		previousLogCount = currentLogCount;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN && config.antiAfk())
		{
			client.setIdleTimeout(EXTENDED_LOGOUT_TIMEOUT);
		}

		if (event.getGameState() != GameState.LOGGED_IN && config.startScript())
		{
			updateMetrics();
			elapsedNanos = System.nanoTime() - scriptStartedAt;
			currentStatus = Status.STOPPED;
			currentTreeTarget = null;
			isDroppingLogs = false;
			trackedNestTiles.clear();
			currentNestTarget = null;
		}
	}

	/**
	 * Checks if an item ID corresponds to any known bird nest.
	 */
	private boolean isBirdNest(int itemId)
	{
		if (BIRD_NEST_IDS.contains(itemId))
		{
			return true;
		}
		try
		{
			ItemComposition def = client.getItemDefinition(itemId);
			if (def != null && def.getName() != null)
			{
				String name = def.getName().toLowerCase();
				return name.contains("bird nest") || name.contains("clue nest");
			}
		}
		catch (Exception ignored)
		{
		}
		return false;
	}

	/**
	 * Finds the nearest bird nest on the ground within NEST_SEARCH_RADIUS.
	 */
	private GroundBirdNest findNearestBirdNest(WorldPoint playerLocation)
	{
		if (playerLocation == null)
		{
			return null;
		}

		Scene scene = client.getScene();
		if (scene == null)
		{
			return null;
		}

		Tile[][][] tiles = scene.getTiles();
		int plane = client.getPlane();
		if (plane < 0 || plane >= tiles.length)
		{
			return null;
		}

		GroundBirdNest bestNest = null;
		int bestDistance = Integer.MAX_VALUE;

		// 1. Check known tracked nest tiles first
		for (Tile tile : new ArrayList<>(trackedNestTiles))
		{
			if (tile == null || tile.getPlane() != plane)
			{
				continue;
			}

			List<TileItem> groundItems = tile.getGroundItems();
			if (groundItems == null || groundItems.isEmpty())
			{
				trackedNestTiles.remove(tile);
				continue;
			}

			boolean hasNest = false;
			for (TileItem item : groundItems)
			{
				if (item != null && isBirdNest(item.getId()))
				{
					hasNest = true;
					int dist = tile.getWorldLocation().distanceTo(playerLocation);
					if (dist <= NEST_SEARCH_RADIUS && dist < bestDistance)
					{
						bestDistance = dist;
						String name = "Bird nest";
						try
						{
							ItemComposition def = client.getItemDefinition(item.getId());
							if (def != null && def.getName() != null)
							{
								name = def.getName();
							}
						}
						catch (Exception ignored)
						{
						}
						bestNest = new GroundBirdNest(tile, item.getId(), name);
					}
				}
			}
			if (!hasNest)
			{
				trackedNestTiles.remove(tile);
			}
		}

		if (bestNest != null)
		{
			return bestNest;
		}

		// 2. Comprehensive tile scan fallback (ensures pre-existing or missed nests are found)
		for (Tile[] row : tiles[plane])
		{
			if (row == null)
			{
				continue;
			}
			for (Tile tile : row)
			{
				if (tile == null)
				{
					continue;
				}

				List<TileItem> groundItems = tile.getGroundItems();
				if (groundItems == null || groundItems.isEmpty())
				{
					continue;
				}

				for (TileItem item : groundItems)
				{
					if (item != null && isBirdNest(item.getId()))
					{
						trackedNestTiles.add(tile);
						int dist = tile.getWorldLocation().distanceTo(playerLocation);
						if (dist <= NEST_SEARCH_RADIUS && dist < bestDistance)
						{
							bestDistance = dist;
							String name = "Bird nest";
							try
							{
								ItemComposition def = client.getItemDefinition(item.getId());
								if (def != null && def.getName() != null)
								{
									name = def.getName();
								}
							}
							catch (Exception ignored)
							{
							}
							bestNest = new GroundBirdNest(tile, item.getId(), name);
						}
					}
				}
			}
		}

		return bestNest;
	}

	/**
	 * Takes an item from the ground using MenuAction.GROUND_ITEM_THIRD_OPTION ("Take").
	 */
	private void takeGroundItem(Tile tile, int itemId, String itemName)
	{
		if (tile == null)
		{
			return;
		}

		LocalPoint lp = tile.getLocalLocation();
		if (lp == null)
		{
			return;
		}

		client.menuAction(
			lp.getSceneX(),
			lp.getSceneY(),
			MenuAction.GROUND_ITEM_THIRD_OPTION,
			itemId,
			0,
			"Take",
			itemName != null ? itemName : "Bird nest"
		);
	}

	/**
	 * Drops a single log of the specified itemId from inventory.
	 * Never drops axes or bird nests!
	 */
	private boolean dropSingleLog(int logId)
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return false;
		}

		Item[] items = inventory.getItems();
		for (int slot = 0; slot < items.length; slot++)
		{
			Item item = items[slot];
			if (item != null && item.getId() == logId)
			{
				client.menuAction(
					slot,
					InterfaceID.Inventory.ITEMS,
					MenuAction.CC_OP_LOW_PRIORITY,
					7, // Standard "Drop" action index
					logId,
					"Drop",
					"Logs"
				);
				return true;
			}
		}
		return false;
	}

	/**
	 * Drops a single ItemID.LOGS (Normal logs) from the inventory.
	 * Never drops any other items (axes, bird nests, etc.)!
	 */
	private boolean dropSingleNormalLog()
	{
		return dropSingleLog(ItemID.LOGS);
	}

	public int getCurrentInventoryLogs()
	{
		return getInventoryLogCount(config.treeType());
	}

	private int getInventoryNormalLogCount()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return 0;
		}
		return inventory.count(ItemID.LOGS);
	}

	private int getInventoryLogCount(TreeType type)
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		return inventory == null ? 0 : getInventoryLogCount(type, inventory);
	}

	private static int getInventoryLogCount(TreeType type, ItemContainer inventory)
	{
		int count = 0;
		for (Item item : inventory.getItems())
		{
			if (item != null && item.getId() == type.getLogId())
			{
				count += item.getQuantity();
			}
		}
		return count;
	}

	private boolean isInventoryFull()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return false;
		}

		Item[] items = inventory.getItems();
		if (items.length < INVENTORY_CAPACITY)
		{
			return false;
		}

		for (int slot = 0; slot < INVENTORY_CAPACITY; slot++)
		{
			if (items[slot] == null || items[slot].getId() == -1)
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Checks if an object is an alive normal tree (Tree, Dead tree, Evergreen) with a "Chop down" option.
	 * Stumps do not have the "Chop down" action.
	 */
	private boolean isNormalTree(GameObject object)
	{
		if (object == null)
		{
			return false;
		}

		try
		{
			ObjectComposition def = client.getObjectDefinition(object.getId());
			if (def == null)
			{
				return false;
			}
			if (def.getImpostorIds() != null)
			{
				def = def.getImpostor();
			}
			if (def == null)
			{
				return false;
			}

			String name = def.getName();
			if (name == null)
			{
				return false;
			}

			if (!name.equalsIgnoreCase("Tree") && !name.equalsIgnoreCase("Dead tree") && !name.equalsIgnoreCase("Evergreen"))
			{
				return false;
			}

			String[] actions = def.getActions();
			if (actions == null)
			{
				return false;
			}

			for (String action : actions)
			{
				if (action != null && action.equalsIgnoreCase(CHOP_ACTION))
				{
					return true;
				}
			}
		}
		catch (Exception ignored)
		{
		}

		return false;
	}

	/**
	 * Finds the nearest alive normal tree within search radius.
	 */
	private GameObject findNearestNormalTree(WorldPoint playerLocation)
	{
		Scene scene = client.getScene();
		if (scene == null || playerLocation == null)
		{
			return null;
		}

		Tile[][][] tiles = scene.getTiles();
		int plane = client.getPlane();
		if (plane < 0 || plane >= tiles.length)
		{
			return null;
		}

		GameObject nearest = null;
		int nearestDistance = Integer.MAX_VALUE;

		for (Tile[] row : tiles[plane])
		{
			if (row == null)
			{
				continue;
			}
			for (Tile tile : row)
			{
				if (tile == null)
				{
					continue;
				}

				GameObject[] objects = tile.getGameObjects();
				if (objects == null)
				{
					continue;
				}

				for (GameObject object : objects)
				{
					if (object == null)
					{
						continue;
					}

					if (isNormalTree(object))
					{
						int distance = object.getWorldLocation().distanceTo(playerLocation);
						if (distance <= NORMAL_TREE_MAX_SEARCH_RADIUS && distance < nearestDistance)
						{
							nearest = object;
							nearestDistance = distance;
						}
					}
				}
			}
		}

		return nearest;
	}

	private GameObject findNearestTree(WorldPoint playerLocation)
	{
		if (config.treeType().isPowerchopOnly())
		{
			return findNearestNormalTree(playerLocation);
		}
		return findNearestObject(Set.of(config.treeType().getTreeId()), playerLocation);
	}

	private GameObject findNearestObject(Set<Integer> objectIds, WorldPoint playerLocation)
	{
		Scene scene = client.getScene();
		if (scene == null)
		{
			return null;
		}

		Tile[][][] tiles = scene.getTiles();
		if (client.getPlane() < 0 || client.getPlane() >= tiles.length)
		{
			return null;
		}

		GameObject nearest = null;
		int nearestDistance = Integer.MAX_VALUE;
		for (Tile[] row : tiles[client.getPlane()])
		{
			for (Tile tile : row)
			{
				if (tile == null)
				{
					continue;
				}

				GameObject[] objects = tile.getGameObjects();
				if (objects == null)
				{
					continue;
				}

				for (GameObject object : objects)
				{
					if (object == null || !objectIds.contains(object.getId()))
					{
						continue;
					}

					int distance = object.getWorldLocation().distanceTo(playerLocation);
					if (distance < nearestDistance)
					{
						nearest = object;
						nearestDistance = distance;
					}
				}
			}
		}

		return nearest;
	}

	private void interactWithObject(GameObject object, String option, String target)
	{
		if (object == null)
		{
			return;
		}

		MenuAction menuAction = MenuAction.GAME_OBJECT_FIRST_OPTION;
		int param0 = object.getSceneMinLocation().getX();
		int param1 = object.getSceneMinLocation().getY();
		int identifier = object.getId();
		String actualOption = option;
		String actualTarget = target;

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
							if (actions[i] != null && actions[i].toLowerCase().contains(option.toLowerCase()))
							{
								switch (i)
								{
									case 0: menuAction = MenuAction.GAME_OBJECT_FIRST_OPTION; break;
									case 1: menuAction = MenuAction.GAME_OBJECT_SECOND_OPTION; break;
									case 2: menuAction = MenuAction.GAME_OBJECT_THIRD_OPTION; break;
									case 3: menuAction = MenuAction.GAME_OBJECT_FOURTH_OPTION; break;
									case 4: menuAction = MenuAction.GAME_OBJECT_FIFTH_OPTION; break;
								}
								actualOption = actions[i];
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

	private boolean isBankOpen()
	{
		Widget bankItemContainer = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER);
		return bankItemContainer != null && !bankItemContainer.isHidden();
	}

	private void bankLogs()
	{
		TreeType treeType = config.treeType();
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory != null)
		{
			Item[] items = inventory.getItems();

			// 1. First deposit all chopped logs
			for (int slot = 0; slot < items.length; slot++)
			{
				Item item = items[slot];
				if (item != null && item.getId() == treeType.getLogId())
				{
					currentStatus = Status.BANKING;
					client.menuAction(slot, WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER.getId(),
						MenuAction.CC_OP_LOW_PRIORITY, DEPOSIT_ALL_OP, treeType.getLogId(),
						"Deposit-All", treeType.getTreeName() + " logs");
					actionDelay = 2;
					return;
				}
			}

			// 2. Also deposit any collected bird nests so they don't fill inventory on banking runs
			for (int slot = 0; slot < items.length; slot++)
			{
				Item item = items[slot];
				if (item != null && isBirdNest(item.getId()))
				{
					currentStatus = Status.BANKING;
					client.menuAction(slot, WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER.getId(),
						MenuAction.CC_OP_LOW_PRIORITY, DEPOSIT_ALL_OP, item.getId(),
						"Deposit-All", "Bird nest");
					actionDelay = 2;
					return;
				}
			}
		}

		closeBank();
		actionDelay = 2;
		currentStatus = Status.CHOPPING;
	}

	private MenuAction getBankerAction(NPC banker)
	{
		String[] actions = client.getNpcDefinition(banker.getId()).getActions();
		if (actions != null)
		{
			for (int i = 0; i < Math.min(actions.length, 5); i++)
			{
				if (BANK_ACTION.equalsIgnoreCase(actions[i]))
				{
					return npcMenuAction(i);
				}
			}
		}
		return MenuAction.NPC_SECOND_OPTION;
	}

	private static MenuAction npcMenuAction(int index)
	{
		switch (index)
		{
			case 1:
				return MenuAction.NPC_SECOND_OPTION;
			case 2:
				return MenuAction.NPC_THIRD_OPTION;
			case 3:
				return MenuAction.NPC_FOURTH_OPTION;
			case 4:
				return MenuAction.NPC_FIFTH_OPTION;
			default:
				return MenuAction.NPC_FIRST_OPTION;
		}
	}

	private void walkTowardBank(WorldPoint playerLocation)
	{
		WorldPoint bankLocation = config.treeType().getBankLocation();
		if (bankLocation == null)
		{
			return;
		}
		int dx = bankLocation.getX() - playerLocation.getX();
		int dy = bankLocation.getY() - playerLocation.getY();
		int distance = Math.max(Math.abs(dx), Math.abs(dy));
		if (distance == 0)
		{
			currentStatus = Status.WALKING_TO_BANK;
			actionDelay = 2;
			return;
		}

		int step = Math.min(BANK_APPROACH_DISTANCE, distance);
		WorldPoint stepLocation = new WorldPoint(
			playerLocation.getX() + dx * step / distance,
			playerLocation.getY() + dy * step / distance,
			playerLocation.getPlane());
		LocalPoint localPoint = LocalPoint.fromWorld(client, stepLocation);
		if (localPoint == null)
		{
			currentStatus = Status.OUT_OF_AREA;
			actionDelay = 2;
			return;
		}

		currentStatus = Status.WALKING_TO_BANK;
		client.menuAction(localPoint.getX(), localPoint.getY(), MenuAction.WALK, 0, 0, "", "");
		actionDelay = 5;
	}

	private void closeBank()
	{
		Widget bankContainer = client.getWidget(WidgetInfo.BANK_CONTAINER);
		if (clickCloseButton(bankContainer))
		{
			return;
		}

		for (WidgetNode node : client.getComponentTable())
		{
			if (node.getId() == InterfaceID.BANKMAIN)
			{
				client.closeInterface(node, true);
				return;
			}
		}
	}

	private boolean clickCloseButton(Widget widget)
	{
		if (widget == null)
		{
			return false;
		}

		String[] actions = widget.getActions();
		if (actions != null)
		{
			for (int i = 0; i < actions.length; i++)
			{
				if ("Close".equalsIgnoreCase(actions[i]))
				{
					Object[] onOpListener = widget.getOnOpListener();
					if (onOpListener != null)
					{
						client.createScriptEventBuilder(onOpListener)
							.setSource(widget)
							.setOp(i + 1)
							.build()
							.setCanSendPackets(true)
							.run();
					}
					else
					{
						client.menuAction(-1, widget.getId(), MenuAction.CC_OP, i + 1,
							-1, actions[i], "");
					}
					return true;
				}
			}
		}

		Widget[] children = widget.getNestedChildren();
		if (children != null)
		{
			for (Widget child : children)
			{
				if (clickCloseButton(child))
				{
					return true;
				}
			}
		}
		return false;
	}

	private void updateMetrics()
	{
		if (config.startScript())
		{
			elapsedNanos = System.nanoTime() - scriptStartedAt;
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				int currentXp = client.getSkillExperience(Skill.WOODCUTTING);
				if (xpTracking)
				{
					woodcuttingXpGained = Math.max(0, currentXp - startingWoodcuttingXp);
				}
				else
				{
					startingWoodcuttingXp = currentXp;
					xpTracking = true;
				}
			}
		}
	}

	/**
	 * Anti-AFK handler: prevents auto-logout and simulates subtle human interaction.
	 */
	private void handleAntiLogout()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		if (!config.antiAfk())
		{
			return;
		}

		client.setIdleTimeout(EXTENDED_LOGOUT_TIMEOUT);

		int idleTicks = Math.min(client.getKeyboardIdleTicks(), client.getMouseIdleTicks());
		long now = System.currentTimeMillis();

		if (idleTicks >= nextAntiLogoutThreshold && (now - lastAntiLogoutTimeMs) > 60_000L)
		{
			log.debug("Anti-logout triggered: idleTicks={}, nextThreshold={}", idleTicks, nextAntiLogoutThreshold);
			triggerAntiLogoutInput();
			lastAntiLogoutTimeMs = now;
			nextAntiLogoutThreshold = 5_000 + (int) (Math.random() * 4_500);
		}
	}

	private void triggerAntiLogoutInput()
	{
		Canvas canvas = client.getCanvas();
		if (canvas == null || !canvas.isShowing())
		{
			return;
		}

		SwingUtilities.invokeLater(() ->
		{
			try
			{
				net.runelite.api.Point mousePos = client.getMouseCanvasPosition();
				int width = canvas.getWidth();
				int height = canvas.getHeight();
				if (width <= 0 || height <= 0)
				{
					return;
				}

				int x = (mousePos != null && mousePos.getX() > 0) ? mousePos.getX() : width / 2;
				int y = (mousePos != null && mousePos.getY() > 0) ? mousePos.getY() : height / 2;

				x = Math.max(2, Math.min(x, width - 2));
				y = Math.max(2, Math.min(y, height - 2));

				int jitterX = (x > width / 2) ? x - 1 : x + 1;

				long time = System.currentTimeMillis();
				MouseEvent mouseMoveEvent = new MouseEvent(
					canvas,
					MouseEvent.MOUSE_MOVED,
					time,
					0,
					jitterX,
					y,
					0,
					false
				);
				canvas.dispatchEvent(mouseMoveEvent);

				// Occasionally simulate subtle arrow key camera tap, otherwise simulate harmless VK_CONTROL
				int key = (Math.random() < 0.25)
					? (Math.random() < 0.5 ? KeyEvent.VK_LEFT : KeyEvent.VK_RIGHT)
					: KeyEvent.VK_CONTROL;

				KeyEvent press = new KeyEvent(canvas, KeyEvent.KEY_PRESSED, time, 0, key, KeyEvent.CHAR_UNDEFINED);
				KeyEvent release = new KeyEvent(canvas, KeyEvent.KEY_RELEASED, time + 25, 0, key, KeyEvent.CHAR_UNDEFINED);
				canvas.dispatchEvent(press);
				canvas.dispatchEvent(release);
			}
			catch (Exception e)
			{
				log.warn("Error triggering anti-logout input", e);
			}
		});
	}

	public String getFormattedUptime()
	{
		long elapsedSeconds = elapsedNanos / 1_000_000_000L;
		long hours = elapsedSeconds / 3600;
		long minutes = elapsedSeconds / 60 % 60;
		long seconds = elapsedSeconds % 60;
		return String.format("%02d:%02d:%02d", hours, minutes, seconds);
	}

	public String getStatusLabel()
	{
		return currentStatus != null ? currentStatus.getLabel() : "STOPPED";
	}
}
