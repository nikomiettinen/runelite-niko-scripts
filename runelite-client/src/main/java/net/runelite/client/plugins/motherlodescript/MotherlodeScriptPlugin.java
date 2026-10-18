/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.motherlodescript;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Motherlode Mine Script",
	description = "Automates Motherlode Mine mining, gem dropping, pathing, hopper depositing, sack emptying, and banking",
	tags = {"motherlode", "mlm", "mining", "bot", "script", "automation"},
	enabledByDefault = false
)
public class MotherlodeScriptPlugin extends Plugin
{
	private static final Set<Integer> MOTHERLODE_MAP_REGIONS = ImmutableSet.of(
		14679, 14680, 14681, 14935, 14936, 14937, 15191, 15192, 15193
	);

	private static final Set<Integer> MINE_SPOTS = ImmutableSet.of(
		ObjectID.MOTHERLODE_ORE_SINGLE,
		ObjectID.MOTHERLODE_ORE_LEFT,
		ObjectID.MOTHERLODE_ORE_MIDDLE,
		ObjectID.MOTHERLODE_ORE_RIGHT
	);

	private static final Set<Integer> ROCK_OBSTACLES = ImmutableSet.of(
		ObjectID.MOTHERLODE_ROCKFALL_1,
		ObjectID.MOTHERLODE_ROCKFALL_2
	);

	private static final Set<Integer> UNCUT_GEMS = ImmutableSet.of(
		ItemID.UNCUT_SAPPHIRE,
		ItemID.UNCUT_EMERALD,
		ItemID.UNCUT_RUBY,
		ItemID.UNCUT_DIAMOND
	);

	private static final Set<Integer> MLM_ORES = ImmutableSet.of(
		ItemID.RUNITE_ORE,
		ItemID.ADAMANTITE_ORE,
		ItemID.MITHRIL_ORE,
		ItemID.GOLD_ORE,
		ItemID.COAL,
		ItemID.MOTHERLODE_NUGGET
	);

	// ONLY actual clickable sack objects (26688 is the primary base sack object)
	private static final Set<Integer> SACK_OBJECTS = ImmutableSet.of(
		ObjectID.MOTHERLODE_SACK, // 26688
		ObjectID.MOTHERLODE_SACK_FULL, // 26678
		ObjectID.MOTHERLODE_SACK_EMPTY // 26677
	);

	// The exact world location of the Motherlode Mine ore sack
	private static final WorldPoint SACK_LOCATION = new WorldPoint(3748, 5659, 0);

	// The broken strut closest to the deposit hopper (User attachment: World 3742, 5669, 0 / Region 14936, local 30, 37)
	private static final WorldPoint HOPPER_STRUT_LOCATION = new WorldPoint(3742, 5669, 0);
	private static final int HOPPER_STRUT_X = 3742;
	private static final int HOPPER_STRUT_Y = 5669;
	private static final int HOPPER_STRUT_REGION_X = 30;
	private static final int HOPPER_STRUT_REGION_Y = 37;

	private static final Set<Integer> LADDERS_BOTTOM = ImmutableSet.of(
		ObjectID.MOTHERLODE_LADDER_BOTTOM,
		ObjectID.MOTHERLODE_LADDER_BOTTOM_ACTIVE,
		ObjectID.MOTHERLODE_LADDER_BOTTOM_INACTIVE
	);

	private static final Set<Integer> LADDERS_TOP = ImmutableSet.of(
		ObjectID.MOTHERLODE_LADDER_TOP,
		ObjectID.MOTHERLODE_LADDER_TOP_ACTIVE,
		ObjectID.MOTHERLODE_LADDER_TOP_INACTIVE
	);

	private static final int UPPER_FLOOR_HEIGHT = -490;
	private static final int SACK_NORMAL_CAPACITY = 108;
	private static final int SACK_LARGE_CAPACITY = 189;

	// Blacklisted useless rockfall coordinate from user attachment (tile 3728, 5688, 0 / region 14936, local 16, 56)
	private static final int BLACKLISTED_ROCKFALL_X = 3728;
	private static final int BLACKLISTED_ROCKFALL_Y = 5688;
	private static final int BLACKLISTED_REGION_ID = 14936;
	private static final int BLACKLISTED_REGION_X = 16;
	private static final int BLACKLISTED_REGION_Y = 56;

	// The 3 critical tunnel rockfalls connecting the hopper to the northwest vein room:
	// Rockfall West (closest to vein room): 3727, 5683 (Region 14936, local 15, 51)
	private static final int TUNNEL_ROCK_WEST_X = 3727;
	private static final int TUNNEL_ROCK_WEST_Y = 5683;
	private static final int TUNNEL_ROCK_WEST_REGION_X = 15;
	private static final int TUNNEL_ROCK_WEST_REGION_Y = 51;

	// Rockfall Mid (middle of tunnel): 3731, 5683 (Region 14936, local 19, 51)
	private static final int TUNNEL_ROCK_MID_X = 3731;
	private static final int TUNNEL_ROCK_MID_Y = 5683;
	private static final int TUNNEL_ROCK_MID_REGION_X = 19;
	private static final int TUNNEL_ROCK_MID_REGION_Y = 51;

	// Rockfall East (closest to hopper): 3733, 5680 (Region 14936, local 21, 48)
	private static final int TUNNEL_ROCK_EAST_X = 3733;
	private static final int TUNNEL_ROCK_EAST_Y = 5680;
	private static final int TUNNEL_ROCK_EAST_REGION_X = 21;
	private static final int TUNNEL_ROCK_EAST_REGION_Y = 48;

	// Preferred downstairs mining area (veins around the northwest area near 3728, 5688)
	private static final WorldPoint PREFERRED_MINING_AREA = new WorldPoint(3728, 5688, 0);

	public enum ScriptState
	{
		IDLE("Idle / Paused"),
		DROPPING_GEMS("Dropping Gems"),
		CLEARING_ROCKFALL("Mining Rockfall"),
		CLIMBING_LADDER_DOWN("Climbing Down"),
		CLIMBING_LADDER_UP("Climbing Up"),
		DEPOSITING_HOPPER("Depositing at Hopper"),
		GETTING_HAMMER("Taking Hammer from Crate"),
		REPAIRING_STRUT("Fixing Water Wheel"),
		EMPTYING_SACK("Collecting Ores from Sack"),
		BANKING("Banking Ores"),
		MINING_VEIN("Mining Ore Vein");

		@Getter
		private final String description;

		ScriptState(String description)
		{
			this.description = description;
		}
	}

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MotherlodeScriptOverlay overlay;

	@Inject
	private MotherlodeScriptConfig config;

	@Getter
	private ScriptState currentState = ScriptState.IDLE;

	@Getter
	private boolean inMlm;

	@Getter
	private int depositedInventories = 0;

	@Getter
	private int currentPayDirt = 0;

	@Getter
	private int currentSackCount = 0;

	private int cooldownTicks = 0;
	private int idleMiningTicks = 0;
	private int idleStrutTicks = 0;
	private int previousPayDirt = 0;

	private boolean shouldEmptySackOnStart = true;
	private boolean previousScriptActive = false;

	private LocalPoint previousPlayerLoc = null;
	private int stuckTicks = 0;

	private GameObject currentRockfallTarget = null;
	private WallObject currentVeinTarget = null;
	private GameObject currentStrutTarget = null;

	private final Set<WallObject> veins = new HashSet<>();
	private final Set<GameObject> rockfalls = new HashSet<>();
	private final Set<GameObject> brokenStruts = new HashSet<>();
	private final Set<GameObject> sacks = new HashSet<>();
	private final Set<GameObject> laddersBottom = new HashSet<>();
	private final Set<GameObject> laddersTop = new HashSet<>();
	private GameObject lowerHopper = null;
	private GameObject upperHopper = null;
	private GameObject hammerCrate = null;
	private GameObject bankChest = null;

	@Provides
	MotherlodeScriptConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MotherlodeScriptConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		shouldEmptySackOnStart = true;
		previousScriptActive = config.startScript();
		inMlm = checkInMlm();
		if (inMlm)
		{
			clientThread.invokeLater(this::scanScene);
		}
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		clearObjects();
		currentState = ScriptState.IDLE;
		inMlm = false;
		stuckTicks = 0;
		previousPlayerLoc = null;
		currentRockfallTarget = null;
		currentVeinTarget = null;
		currentStrutTarget = null;
		idleStrutTicks = 0;
		shouldEmptySackOnStart = true;
		previousScriptActive = false;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING)
		{
			clearObjects();
			inMlm = checkInMlm();
			stuckTicks = 0;
			previousPlayerLoc = null;
			currentRockfallTarget = null;
			currentVeinTarget = null;
			currentStrutTarget = null;
			idleStrutTicks = 0;
			if (inMlm)
			{
				clientThread.invokeLater(this::scanScene);
			}
		}
		else if (event.getGameState() == GameState.LOGGED_IN)
		{
			shouldEmptySackOnStart = true;
			inMlm = checkInMlm();
			if (inMlm)
			{
				clientThread.invokeLater(this::scanScene);
			}
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			inMlm = false;
			clearObjects();
			stuckTicks = 0;
			previousPlayerLoc = null;
			currentRockfallTarget = null;
			currentVeinTarget = null;
			currentStrutTarget = null;
			idleStrutTicks = 0;
			shouldEmptySackOnStart = true;
			previousScriptActive = false;
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!inMlm)
		{
			return;
		}
		String msg = event.getMessage().toLowerCase();
		if (msg.contains("sack is empty") || msg.contains("the sack is empty"))
		{
			log.info("Sack reported empty in chat!");
			shouldEmptySackOnStart = false;
			depositedInventories = 0;
		}
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned event)
	{
		if (!inMlm)
		{
			return;
		}
		WallObject wallObject = event.getWallObject();
		if (MINE_SPOTS.contains(wallObject.getId()))
		{
			veins.add(wallObject);
		}
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned event)
	{
		if (!inMlm)
		{
			return;
		}
		WallObject wallObject = event.getWallObject();
		veins.remove(wallObject);
		if (currentVeinTarget != null && (currentVeinTarget == wallObject || currentVeinTarget.getLocalLocation().equals(wallObject.getLocalLocation())))
		{
			log.debug("Current vein target depleted (despawned)!");
			currentVeinTarget = null;
			idleMiningTicks = 0;
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		if (!inMlm)
		{
			return;
		}
		addGameObject(event.getGameObject());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		if (!inMlm)
		{
			return;
		}
		GameObject gameObject = event.getGameObject();
		removeGameObject(gameObject);
		if (currentRockfallTarget == gameObject)
		{
			log.info("Target rockfall destroyed!");
			currentRockfallTarget = null;
			idleMiningTicks = 0;
		}
		if (currentStrutTarget == gameObject)
		{
			log.info("Target strut repaired!");
			currentStrutTarget = null;
			idleStrutTicks = 0;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!inMlm)
		{
			inMlm = checkInMlm();
			if (!inMlm)
			{
				currentState = ScriptState.IDLE;
				return;
			}
			else
			{
				scanScene();
			}
		}

		// Ensure objects are populated if missed during load
		if (sacks.isEmpty() || lowerHopper == null)
		{
			scanScene();
		}

		updateInventoryAndSackData();

		boolean scriptActive = config.startScript();
		if (scriptActive && !previousScriptActive)
		{
			shouldEmptySackOnStart = true;
			log.info("Script activated: will empty sack on start if it contains ores");
		}
		previousScriptActive = scriptActive;

		if (!scriptActive)
		{
			currentState = ScriptState.IDLE;
			stuckTicks = 0;
			currentRockfallTarget = null;
			currentVeinTarget = null;
			currentStrutTarget = null;
			idleStrutTicks = 0;
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		// Track movement and stuck state
		LocalPoint currentLoc = localPlayer.getLocalLocation();
		boolean isMoving = previousPlayerLoc != null && !previousPlayerLoc.equals(currentLoc);
		if (localPlayer.getAnimation() != -1)
		{
			stuckTicks = 0;
		}
		else if (!isMoving)
		{
			stuckTicks++;
		}
		else
		{
			stuckTicks = 0;
		}
		previousPlayerLoc = currentLoc;

		if (cooldownTicks > 0)
		{
			cooldownTicks--;
			return;
		}

		processScriptLogic(localPlayer, isMoving);
	}

	private void processScriptLogic(Player localPlayer, boolean isMoving)
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return;
		}

		boolean targetUpstairs = config.upperFloor();
		boolean playerUpstairs = isUpstairs(localPlayer.getLocalLocation());

		// 1. Drop uncut gems if enabled (ONLY when NOT actively mining a vein so animation is never interrupted!)
		if (config.dropGems() && currentVeinTarget == null && localPlayer.getAnimation() == -1)
		{
			Item gemItem = null;
			int gemSlot = -1;
			for (int i = 0; i < inventory.size(); i++)
			{
				Item item = inventory.getItem(i);
				if (item != null && UNCUT_GEMS.contains(item.getId()))
				{
					gemItem = item;
					gemSlot = i;
					break;
				}
			}

			if (gemItem != null && gemSlot >= 0)
			{
				currentState = ScriptState.DROPPING_GEMS;
				log.debug("Dropping gem {} in slot {}", gemItem.getId(), gemSlot);
				client.menuAction(gemSlot, InterfaceID.Inventory.ITEMS, MenuAction.CC_OP_LOW_PRIORITY, 7, gemItem.getId(), "Drop", "");
				cooldownTicks = 1;
				return;
			}
		}

		// 2. Check if inventory contains cleaned ores or nuggets -> Bank them
		boolean hasOresToBank = Arrays.stream(inventory.getItems())
			.anyMatch(item -> item != null && MLM_ORES.contains(item.getId()));

		if (hasOresToBank)
		{
			currentRockfallTarget = null;
			currentVeinTarget = null;
			currentStrutTarget = null;
			currentState = ScriptState.BANKING;
			handleBanking(localPlayer);
			return;
		}

		// 3. Check sack capacity & emptying (including automatic emptying on login / script restart)
		boolean isBiggerSack = config.biggerSack() || client.getVarbitValue(VarbitID.MOTHERLODE_BIGGERSACK) == 1;
		int maxDeposits = isBiggerSack ? 7 : 4;
		int sackCapacity = isBiggerSack ? SACK_LARGE_CAPACITY : SACK_NORMAL_CAPACITY;

		int invFreeSlots = 28 - inventory.count();
		boolean inventoryFullOfPayDirt = currentPayDirt >= config.minPayDirt() || (invFreeSlots == 0 && currentPayDirt > 0);

		// Handle startup sack emptying: only empty if sack actually contains ores!
		if (shouldEmptySackOnStart)
		{
			if (currentSackCount > 0)
			{
				if (currentPayDirt == 0 || invFreeSlots > 0)
				{
					currentRockfallTarget = null;
					currentVeinTarget = null;
					currentStrutTarget = null;
					currentState = ScriptState.EMPTYING_SACK;
					handleEmptyingSack(localPlayer);
					return;
				}
			}
			else
			{
				log.info("Sack is empty on start (count: {}), clearing start sack empty flag.", currentSackCount);
				shouldEmptySackOnStart = false;
				depositedInventories = 0;
			}
		}

		// Normal sack emptying during gameplay: only empty when deposits reached and sack has ores
		boolean sackFullOrNeedsEmpty = (depositedInventories >= maxDeposits && currentSackCount > 0)
			|| currentSackCount >= sackCapacity - 26;

		if (sackFullOrNeedsEmpty && currentSackCount > 0)
		{
			if (currentPayDirt == 0 || invFreeSlots > 0)
			{
				currentRockfallTarget = null;
				currentVeinTarget = null;
				currentStrutTarget = null;
				currentState = ScriptState.EMPTYING_SACK;
				handleEmptyingSack(localPlayer);
				return;
			}
		}
		else if (currentSackCount == 0 && depositedInventories >= maxDeposits)
		{
			depositedInventories = 0;
		}

		// A: Active Rockfall mining check - MUST complete current rockfall action before doing anything else!
		// BUT NEVER mine rockfalls if player is in the middle of a mining run (e.g. 7/26 paydirt)!
		if (currentRockfallTarget != null)
		{
			if (currentPayDirt > 0 && !inventoryFullOfPayDirt)
			{
				log.info("Cancelling active rockfall target because player is in the middle of mining veins (pay-dirt: {})", currentPayDirt);
				currentRockfallTarget = null;
				idleMiningTicks = 0;
			}
			else if (!rockfalls.contains(currentRockfallTarget))
			{
				log.info("Target rockfall cleared!");
				currentRockfallTarget = null;
				idleMiningTicks = 0;
			}
			else
			{
				// Ensure no vein target competes while mining rockfall
				currentVeinTarget = null;

				if (localPlayer.getAnimation() != -1 || isMoving)
				{
					currentState = ScriptState.CLEARING_ROCKFALL;
					idleMiningTicks = 0;
					return;
				}

				idleMiningTicks++;
				// Stay on this rockfall until destroyed; re-click if idle between swings for 3+ ticks
				if (idleMiningTicks >= 3)
				{
					currentState = ScriptState.CLEARING_ROCKFALL;
					interactObject(currentRockfallTarget, "Mine", "Rockfall");
					cooldownTicks = 3;
					idleMiningTicks = 0;
				}
				return;
			}
		}

		// Detect if player is already mining an adjacent vein (e.g. manually clicked or resumed)
		if (currentVeinTarget == null && localPlayer.getAnimation() != -1)
		{
			WallObject adjacentVein = findAdjacentVein(localPlayer.getLocalLocation(), targetUpstairs);
			if (adjacentVein != null && isVeinAlive(adjacentVein))
			{
				currentVeinTarget = adjacentVein;
			}
		}

		// B: Active Vein mining check - MUST complete current vein to depletion/disappearance before clicking ANY other vein or action!
		// But only protect vein target when player is actually at the vein; if blocked on the way from hopper/bank, clear the rockfall!
		if (currentVeinTarget != null)
		{
			boolean veinAlive = isVeinAlive(currentVeinTarget);
			if (inventoryFullOfPayDirt || !veinAlive)
			{
				if (!veinAlive)
				{
					log.debug("Current vein target depleted and disappeared!");
				}
				currentVeinTarget = null;
				idleMiningTicks = 0;
			}
			else
			{
				LocalPoint veinLp = currentVeinTarget.getLocalLocation();
				boolean atVein = veinLp != null && localPlayer.getLocalLocation().distanceTo(veinLp) <= 2 * Perspective.LOCAL_TILE_SIZE;

				if (atVein)
				{
					// Player is physically at the vein: actively mining or waiting for pickaxe swing
					if (localPlayer.getAnimation() != -1 || isMoving)
					{
						currentState = ScriptState.MINING_VEIN;
						idleMiningTicks = 0;
						return;
					}

					// Pickaxe swing has 1-3 ticks pause between animations.
					idleMiningTicks++;
					if (idleMiningTicks < 5)
					{
						currentState = ScriptState.MINING_VEIN;
						return;
					}

					// Stalled for 5+ consecutive ticks at the vein: re-click the SAME vein!
					currentState = ScriptState.MINING_VEIN;
					interactObject(currentVeinTarget, "Mine", "Ore vein");
					cooldownTicks = 3;
					idleMiningTicks = 0;
					return;
				}
				else
				{
					// Player is NOT at the vein (vein is far away, e.g. navigating from sack/bank)
					// If returning from hopper/sack/bank (currentPayDirt == 0), check for the 3 tunnel rockfalls!
					if (currentPayDirt == 0 && !targetUpstairs && config.clearRockfalls())
					{
						GameObject tunnelRock = findBlockingTunnelRockfall(localPlayer, false);
						if (tunnelRock != null)
						{
							log.info("Path to distant vein blocked by tunnel rockfall at {}! Mining rockfall first.", tunnelRock.getWorldLocation());
							currentVeinTarget = null;
							currentRockfallTarget = tunnelRock;
							currentState = ScriptState.CLEARING_ROCKFALL;
							interactObject(tunnelRock, "Mine", "Rockfall");
							cooldownTicks = 3;
							idleMiningTicks = 0;
							return;
						}
					}

					// Moving towards the vein
					if (localPlayer.getAnimation() != -1 || isMoving)
					{
						currentState = ScriptState.MINING_VEIN;
						idleMiningTicks = 0;
						return;
					}

					// Player stopped moving without reaching the vein (blocked or path cancelled)
					idleMiningTicks++;
					if (idleMiningTicks >= 2)
					{
						log.info("Player stopped moving before reaching vein (stalled). Clearing vein target to re-evaluate route/rockfalls.");
						currentVeinTarget = null;
						idleMiningTicks = 0;
					}
					else
					{
						currentState = ScriptState.MINING_VEIN;
						return;
					}
				}
			}
		}

		// Failsafe: Check if stuck between rockfalls (only when NOT in the middle of mining veins)
		if (handleStuckFailsafe(localPlayer, inventoryFullOfPayDirt))
		{
			return;
		}

		// 5. Handling Full Inventory (Depositing - heading to deposit hopper when 26/26)
		if (inventoryFullOfPayDirt)
		{
			currentVeinTarget = null;

			if (targetUpstairs)
			{
				if (playerUpstairs)
				{
					if (upperHopper != null)
					{
						currentState = ScriptState.DEPOSITING_HOPPER;
						interactObject(upperHopper, "Deposit", "Hopper");
						cooldownTicks = 3;
						return;
					}
					else
					{
						currentState = ScriptState.CLIMBING_LADDER_DOWN;
						climbLadderDown(localPlayer);
						return;
					}
				}
			}
			else
			{
				if (playerUpstairs)
				{
					currentState = ScriptState.CLIMBING_LADDER_DOWN;
					climbLadderDown(localPlayer);
					return;
				}
			}

			// Downstairs hopper handling
			GameObject activeHopper = (targetUpstairs && playerUpstairs && upperHopper != null) ? upperHopper : lowerHopper;
			if (activeHopper != null)
			{
				// Clear rockfalls blocking downstairs tunnel to hopper ONLY when inventory is full!
				if (config.clearRockfalls() && !playerUpstairs)
				{
					// Always check the closest blocking tunnel rockfall first!
					GameObject tunnelRock = findBlockingTunnelRockfall(localPlayer, true);
					if (tunnelRock != null)
					{
						currentState = ScriptState.CLEARING_ROCKFALL;
						currentRockfallTarget = tunnelRock;
						interactObject(tunnelRock, "Mine", "Rockfall");
						cooldownTicks = 3;
						return;
					}
				}

				// When downstairs near hopper, check and repair ONLY the 1 broken strut closest to the deposit hopper (tile 3742, 5669, 0)
				GameObject targetStrut = findHopperStrut();
				if (config.repairStruts() && targetStrut != null && !playerUpstairs
					&& localPlayer.getLocalLocation().distanceTo(activeHopper.getLocalLocation()) <= 12 * Perspective.LOCAL_TILE_SIZE)
				{
					boolean hasHammer = inventory.contains(ItemID.HAMMER);
					if (!hasHammer)
					{
						currentState = ScriptState.GETTING_HAMMER;
						if (hammerCrate != null)
						{
							interactObject(hammerCrate, "Search", "Crate");
							cooldownTicks = 3;
							return;
						}
					}
					else
					{
						// Active strut repair: fix to completion before clicking again or proceeding
						if (currentStrutTarget != null)
						{
							if (!brokenStruts.contains(currentStrutTarget))
							{
								log.info("Hopper strut repaired successfully!");
								currentStrutTarget = null;
								idleStrutTicks = 0;
							}
							else
							{
								// Player is hammering or walking to strut: wait without clicking!
								if (localPlayer.getAnimation() != -1 || isMoving)
								{
									currentState = ScriptState.REPAIRING_STRUT;
									idleStrutTicks = 0;
									return;
								}

								idleStrutTicks++;
								if (idleStrutTicks < 3)
								{
									currentState = ScriptState.REPAIRING_STRUT;
									return;
								}

								// Idle for 3+ ticks: re-interact with the SAME strut
								currentState = ScriptState.REPAIRING_STRUT;
								interactObject(currentStrutTarget, "Hammer", "Broken strut");
								cooldownTicks = 4;
								idleStrutTicks = 0;
								return;
							}
						}

						// Interact with the broken strut closest to hopper (tile 3742, 5669)
						currentState = ScriptState.REPAIRING_STRUT;
						currentStrutTarget = targetStrut;
						interactObject(targetStrut, "Hammer", "Broken strut");
						cooldownTicks = 4;
						idleStrutTicks = 0;
						return;
					}
				}
				else
				{
					currentStrutTarget = null;
					idleStrutTicks = 0;
				}

				currentState = ScriptState.DEPOSITING_HOPPER;
				interactObject(activeHopper, "Deposit", "Hopper");
				cooldownTicks = 3;
				return;
			}
		}

		// 6. Navigate to target floor for mining (Only use upper floor if enabled in settings)
		if (targetUpstairs && !playerUpstairs)
		{
			currentState = ScriptState.CLIMBING_LADDER_UP;
			climbLadderUp(localPlayer);
			return;
		}
		else if (!targetUpstairs && playerUpstairs)
		{
			currentState = ScriptState.CLIMBING_LADDER_DOWN;
			climbLadderDown(localPlayer);
			return;
		}

		// Safety check: ensure player is on the correct target floor before trying to mine veins
		if (targetUpstairs != playerUpstairs)
		{
			if (targetUpstairs)
			{
				currentState = ScriptState.CLIMBING_LADDER_UP;
				climbLadderUp(localPlayer);
			}
			else
			{
				currentState = ScriptState.CLIMBING_LADDER_DOWN;
				climbLadderDown(localPlayer);
			}
			return;
		}

		// 7. Clear rockfalls downstairs ONLY when returning from hopper/bank/sack towards the vein room!
		// STRICT CONDITION: MUST be coming from hopper with 0 pay-dirt (currentPayDirt == 0)!
		// Strictly targets ONLY the 3 user-specified tunnel rockfalls (East: 3733,5680, Mid: 3731,5683, West: 3727,5683)!
		boolean returningFromHopper = !targetUpstairs && currentPayDirt == 0 && !isInsideVeinRoom(localPlayer);

		if (config.clearRockfalls() && returningFromHopper && currentVeinTarget == null)
		{
			// Check the 3 tunnel rockfalls blocking return path to vein room
			GameObject tunnelRock = findBlockingTunnelRockfall(localPlayer, false);
			if (tunnelRock != null)
			{
				currentState = ScriptState.CLEARING_ROCKFALL;
				currentRockfallTarget = tunnelRock;
				interactObject(tunnelRock, "Mine", "Rockfall");
				cooldownTicks = 3;
				return;
			}
		}

		// 8. Mine ore vein (ONLY if not already mining a vein, and path is clear!)
		// If returning from hopper/sack downstairs, ensure the 3 tunnel rockfalls are destroyed before clicking distant veins!
		if (!targetUpstairs && currentPayDirt == 0 && !isInsideVeinRoom(localPlayer) && config.clearRockfalls())
		{
			GameObject tunnelRock = findBlockingTunnelRockfall(localPlayer, false);
			if (tunnelRock != null)
			{
				currentState = ScriptState.CLEARING_ROCKFALL;
				currentRockfallTarget = tunnelRock;
				interactObject(tunnelRock, "Mine", "Rockfall");
				cooldownTicks = 3;
				return;
			}
		}

		WallObject nearestVein = findBestVein(localPlayer.getLocalLocation(), targetUpstairs);
		if (currentVeinTarget == null && nearestVein != null)
		{
			currentState = ScriptState.MINING_VEIN;
			currentVeinTarget = nearestVein;
			log.debug("Mining vein {} at ({}, {})", nearestVein.getId(), nearestVein.getLocalLocation().getSceneX(), nearestVein.getLocalLocation().getSceneY());
			client.menuAction(
				nearestVein.getLocalLocation().getSceneX(),
				nearestVein.getLocalLocation().getSceneY(),
				MenuAction.GAME_OBJECT_FIRST_OPTION,
				nearestVein.getId(),
				0,
				"Mine",
				"Ore vein"
			);
			cooldownTicks = 3;
			idleMiningTicks = 0;
		}
	}

	/**
	 * Checks if the player is physically inside the northwest downstairs vein area (west/north of the tunnel).
	 */
	private boolean isInsideVeinRoom(Player localPlayer)
	{
		if (localPlayer == null)
		{
			return false;
		}
		WorldPoint wp = localPlayer.getWorldLocation();
		if (wp == null)
		{
			return false;
		}
		// The northwest downstairs vein room is north/west of the tunnel (Y >= 5684 or X <= 3726)
		return wp.getY() >= 5684 || wp.getX() <= 3726;
	}

	/**
	 * Checks if any of the 3 tunnel rockfalls (West: 3727, 5683; Mid: 3731, 5683; East: 3733, 5680)
	 * are blocking passage between the hopper/sack and the preferred mining area.
	 * ALWAYS returns the rockfall CLOSEST to the player in the direction of travel!
	 */
	private GameObject findBlockingTunnelRockfall(Player localPlayer, boolean headingToHopper)
	{
		LocalPoint playerLoc = localPlayer.getLocalLocation();
		WorldPoint playerWorld = localPlayer.getWorldLocation();
		if (playerWorld == null)
		{
			return null;
		}

		GameObject targetHopper = lowerHopper != null ? lowerHopper : upperHopper;
		WorldPoint hopperWorld = targetHopper != null ? targetHopper.getWorldLocation() : null;

		List<GameObject> activeTunnelRocks = rockfalls.stream()
			.filter(this::isAnyTunnelRock)
			.collect(Collectors.toList());

		if (activeTunnelRocks.isEmpty())
		{
			return null;
		}

		if (headingToHopper)
		{
			// Heading to hopper: filter rocks that are in front of player (between player and hopper)
			int playerDistToHopper = hopperWorld != null ? playerWorld.distanceTo(hopperWorld) : 999;

			return activeTunnelRocks.stream()
				.filter(r -> {
					int rockDistToHopper = hopperWorld != null ? r.getWorldLocation().distanceTo(hopperWorld) : 10;
					// Rock must be between player and hopper (closer to hopper than player)
					return rockDistToHopper <= playerDistToHopper;
				})
				// Crucial: ALWAYS pick the rock closest to the player so we never skip a rock in front!
				.min(Comparator.comparingInt(r -> playerLoc.distanceTo(r.getLocalLocation())))
				.orElse(null);
		}
		else
		{
			// Heading to veins: filter rocks that are in front of player (between player and preferred veins)
			int playerDistToVeins = playerWorld.distanceTo(PREFERRED_MINING_AREA);

			return activeTunnelRocks.stream()
				.filter(r -> {
					int rockDistToVeins = r.getWorldLocation().distanceTo(PREFERRED_MINING_AREA);
					// Rock must be between player and veins (closer to veins than player)
					return rockDistToVeins <= playerDistToVeins;
				})
				// Crucial: ALWAYS pick the rock closest to the player so we never skip a rock in front!
				.min(Comparator.comparingInt(r -> playerLoc.distanceTo(r.getLocalLocation())))
				.orElse(null);
		}
	}

	private boolean isTunnelRockWest(GameObject r)
	{
		if (r == null)
		{
			return false;
		}
		WorldPoint wp = r.getWorldLocation();
		if (wp == null)
		{
			return false;
		}
		return (Math.abs(wp.getX() - TUNNEL_ROCK_WEST_X) <= 1 && Math.abs(wp.getY() - TUNNEL_ROCK_WEST_Y) <= 1)
			|| (wp.getRegionID() == BLACKLISTED_REGION_ID && Math.abs(wp.getRegionX() - TUNNEL_ROCK_WEST_REGIONX()) <= 1 && Math.abs(wp.getRegionY() - TUNNEL_ROCK_WEST_REGIONY()) <= 1);
	}

	private int TUNNEL_ROCK_WEST_REGIONX() { return TUNNEL_ROCK_WEST_REGION_X; }
	private int TUNNEL_ROCK_WEST_REGIONY() { return TUNNEL_ROCK_WEST_REGION_Y; }

	private boolean isTunnelRockMid(GameObject r)
	{
		if (r == null)
		{
			return false;
		}
		WorldPoint wp = r.getWorldLocation();
		if (wp == null)
		{
			return false;
		}
		return (Math.abs(wp.getX() - TUNNEL_ROCK_MID_X) <= 1 && Math.abs(wp.getY() - TUNNEL_ROCK_MID_Y) <= 1)
			|| (wp.getRegionID() == BLACKLISTED_REGION_ID && Math.abs(wp.getRegionX() - TUNNEL_ROCK_MID_REGION_X) <= 1 && Math.abs(wp.getRegionY() - TUNNEL_ROCK_MID_REGION_Y) <= 1);
	}

	private boolean isTunnelRockEast(GameObject r)
	{
		if (r == null)
		{
			return false;
		}
		WorldPoint wp = r.getWorldLocation();
		if (wp == null)
		{
			return false;
		}
		return (Math.abs(wp.getX() - TUNNEL_ROCK_EAST_X) <= 1 && Math.abs(wp.getY() - TUNNEL_ROCK_EAST_Y) <= 1)
			|| (wp.getRegionID() == BLACKLISTED_REGION_ID && Math.abs(wp.getRegionX() - TUNNEL_ROCK_EAST_REGION_X) <= 1 && Math.abs(wp.getRegionY() - TUNNEL_ROCK_EAST_REGION_Y) <= 1);
	}

	private boolean isAnyTunnelRock(GameObject r)
	{
		if (r == null)
		{
			return false;
		}
		if (isBlacklistedRockfall(r))
		{
			return false;
		}
		// Strictly match ONLY the 3 user-specified tunnel rockfalls:
		// East (3733, 5680), Mid (3731, 5683), West (3727, 5683)
		return isTunnelRockWest(r) || isTunnelRockMid(r) || isTunnelRockEast(r);
	}

	/**
	 * Returns true if the strut is the one closest to the deposit hopper (tile 3742, 5669, 0).
	 */
	private boolean isHopperStrut(GameObject strut)
	{
		if (strut == null)
		{
			return false;
		}
		WorldPoint wp = strut.getWorldLocation();
		if (wp == null)
		{
			return false;
		}
		if (Math.abs(wp.getX() - HOPPER_STRUT_X) <= 1 && Math.abs(wp.getY() - HOPPER_STRUT_Y) <= 1)
		{
			return true;
		}
		if (wp.getRegionID() == BLACKLISTED_REGION_ID
			&& Math.abs(wp.getRegionX() - HOPPER_STRUT_REGION_X) <= 1
			&& Math.abs(wp.getRegionY() - HOPPER_STRUT_REGION_Y) <= 1)
		{
			return true;
		}
		return false;
	}

	/**
	 * Finds ONLY the broken strut closest to the deposit hopper (tile 3742, 5669, 0).
	 * Ignores any other broken struts further away so only 1 strut is ever repaired.
	 */
	private GameObject findHopperStrut()
	{
		for (GameObject strut : brokenStruts)
		{
			if (strut != null && isHopperStrut(strut))
			{
				return strut;
			}
		}

		if (lowerHopper != null && !brokenStruts.isEmpty())
		{
			LocalPoint hopperLoc = lowerHopper.getLocalLocation();
			GameObject closestToHopper = brokenStruts.stream()
				.min(Comparator.comparingInt(s -> s.getLocalLocation().distanceTo(hopperLoc)))
				.orElse(null);
			if (closestToHopper != null)
			{
				WorldPoint wp = closestToHopper.getWorldLocation();
				if (wp != null && Math.abs(wp.getY() - HOPPER_STRUT_Y) <= 2)
				{
					return closestToHopper;
				}
			}
		}

		return null;
	}

	/**
	 * Failsafe: if the player is stuck (idle position for multiple ticks) and rockfalls are nearby,
	 * mines the rockfall in the desired direction of travel.
	 * NEVER triggers if the player is in the middle of mining veins (currentPayDirt > 0 && !inventoryFull).
	 */
	private boolean handleStuckFailsafe(Player localPlayer, boolean inventoryFull)
	{
		// NEVER interrupt active vein mining animation
		if (localPlayer.getAnimation() != -1)
		{
			return false;
		}

		// Only protect vein target if player is actually standing at the vein!
		if (currentVeinTarget != null)
		{
			LocalPoint veinLp = currentVeinTarget.getLocalLocation();
			if (veinLp != null && localPlayer.getLocalLocation().distanceTo(veinLp) <= 2 * Perspective.LOCAL_TILE_SIZE)
			{
				return false;
			}
		}

		// Only mine rockfalls if going to hopper (inventoryFull) OR returning from hopper/bank with empty inventory (currentPayDirt == 0)
		if (!inventoryFull && currentPayDirt > 0)
		{
			return false;
		}

		if (stuckTicks < 4 || rockfalls.isEmpty())
		{
			return false;
		}

		// First check tunnel rockfalls
		if (!isUpstairs(localPlayer.getLocalLocation()) && config.clearRockfalls())
		{
			GameObject tunnelRock = findBlockingTunnelRockfall(localPlayer, inventoryFull);
			if (tunnelRock != null)
			{
				log.info("Failsafe triggered: clearing closest blocking tunnel rockfall at {}", tunnelRock.getWorldLocation());
				currentState = ScriptState.CLEARING_ROCKFALL;
				currentRockfallTarget = tunnelRock;
				interactObject(tunnelRock, "Mine", "Rockfall");
				cooldownTicks = 3;
				stuckTicks = 0;
				return true;
			}
		}

		LocalPoint playerLoc = localPlayer.getLocalLocation();
		List<GameObject> nearbyRocks = rockfalls.stream()
			.filter(r -> !isBlacklistedRockfall(r))
			.filter(r -> playerLoc.distanceTo(r.getLocalLocation()) <= 8 * Perspective.LOCAL_TILE_SIZE)
			.collect(Collectors.toList());

		if (nearbyRocks.isEmpty())
		{
			return false;
		}

		GameObject targetHopper = lowerHopper != null ? lowerHopper : upperHopper;
		LocalPoint hopperLoc = (targetHopper != null) ? targetHopper.getLocalLocation() : playerLoc;

		GameObject targetRock;
		if (inventoryFull)
		{
			// Stuck with full inventory: mine rockfall closest to hopper / central hub
			targetRock = nearbyRocks.stream()
				.min(Comparator.comparingInt(r -> r.getLocalLocation().distanceTo(hopperLoc)))
				.orElse(null);
		}
		else
		{
			// Stuck with empty/space: mine rockfall heading towards preferred veins
			targetRock = nearbyRocks.stream()
				.min(Comparator.comparingInt(r -> r.getWorldLocation().distanceTo(PREFERRED_MINING_AREA)))
				.orElse(null);
		}

		if (targetRock != null)
		{
			log.info("Failsafe triggered: stuck between rockfalls! Mining rockfall towards {}", inventoryFull ? "hopper" : "veins");
			currentState = ScriptState.CLEARING_ROCKFALL;
			currentRockfallTarget = targetRock;
			currentVeinTarget = null;
			interactObject(targetRock, "Mine", "Rockfall");
			cooldownTicks = 3;
			stuckTicks = 0;
			return true;
		}
		return false;
	}

	/**
	 * Returns true if the rockfall is on the blacklisted useless tile (attachment coordinates).
	 */
	private boolean isBlacklistedRockfall(GameObject rockfall)
	{
		if (rockfall == null)
		{
			return true;
		}
		WorldPoint wp = rockfall.getWorldLocation();
		if (wp == null)
		{
			return false;
		}
		if (Math.abs(wp.getX() - BLACKLISTED_ROCKFALL_X) <= 1 && Math.abs(wp.getY() - BLACKLISTED_ROCKFALL_Y) <= 1)
		{
			return true;
		}
		if (wp.getRegionID() == BLACKLISTED_REGION_ID
			&& Math.abs(wp.getRegionX() - BLACKLISTED_REGION_X) <= 1
			&& Math.abs(wp.getRegionY() - BLACKLISTED_REGION_Y) <= 1)
		{
			return true;
		}
		return false;
	}

	/**
	 * Checks directly if an ore vein is still present on its tile in the game scene.
	 */
	private boolean isVeinAlive(WallObject vein)
	{
		if (vein == null)
		{
			return false;
		}
		LocalPoint loc = vein.getLocalLocation();
		if (loc == null)
		{
			return false;
		}
		int sceneX = loc.getSceneX();
		int sceneY = loc.getSceneY();
		int plane = client.getPlane();
		Tile[][][] tiles = client.getScene().getTiles();
		if (tiles == null || plane < 0 || plane >= tiles.length)
		{
			return false;
		}
		if (sceneX < 0 || sceneX >= Constants.SCENE_SIZE || sceneY < 0 || sceneY >= Constants.SCENE_SIZE)
		{
			return false;
		}
		Tile tile = tiles[plane][sceneX][sceneY];
		if (tile == null)
		{
			return false;
		}
		WallObject wall = tile.getWallObject();
		return wall != null && MINE_SPOTS.contains(wall.getId());
	}



	private void handleBanking(Player localPlayer)
	{
		if (isUpstairs(localPlayer.getLocalLocation()))
		{
			currentState = ScriptState.CLIMBING_LADDER_DOWN;
			climbLadderDown(localPlayer);
			return;
		}

		Widget depositInvWidget = client.getWidget(InterfaceID.Bankmain.DEPOSITINV);
		if (depositInvWidget != null && !depositInvWidget.isHidden())
		{
			client.menuAction(-1, InterfaceID.Bankmain.DEPOSITINV, MenuAction.CC_OP, 1, -1, "Deposit inventory", "");
			cooldownTicks = 2;
			return;
		}

		Widget depositBoxWidget = client.getWidget(InterfaceID.BankDepositbox.DEPOSIT_INV);
		if (depositBoxWidget != null && !depositBoxWidget.isHidden())
		{
			client.menuAction(-1, InterfaceID.BankDepositbox.DEPOSIT_INV, MenuAction.CC_OP, 1, -1, "Deposit inventory", "");
			cooldownTicks = 2;
			return;
		}

		if (bankChest != null)
		{
			interactObject(bankChest, "Use", "Bank chest");
			cooldownTicks = 3;
		}
	}

	private GameObject findSack(Player localPlayer)
	{
		// 1. Direct match on SACK_LOCATION (3748, 5659, 0) in sacks collection
		for (GameObject sack : sacks)
		{
			if (sack != null && sack.getWorldLocation() != null && sack.getWorldLocation().equals(SACK_LOCATION))
			{
				return sack;
			}
		}

		// 2. Direct lookup at known sack tile in scene
		LocalPoint lp = LocalPoint.fromWorld(client, SACK_LOCATION);
		if (lp != null)
		{
			int sceneX = lp.getSceneX();
			int sceneY = lp.getSceneY();
			Tile[][][] tiles = client.getScene().getTiles();
			if (tiles != null && sceneX >= 0 && sceneX < Constants.SCENE_SIZE && sceneY >= 0 && sceneY < Constants.SCENE_SIZE)
			{
				Tile tile = tiles[0][sceneX][sceneY];
				if (tile != null && tile.getGameObjects() != null)
				{
					for (GameObject go : tile.getGameObjects())
					{
						if (go != null && (SACK_OBJECTS.contains(go.getId()) || go.getId() == ObjectID.MOTHERLODE_SACK))
						{
							return go;
						}
					}
				}
			}
		}

		// 3. Match any object in sacks set with valid sack ID
		for (GameObject sack : sacks)
		{
			if (sack != null && SACK_OBJECTS.contains(sack.getId()))
			{
				return sack;
			}
		}

		return findClosestObject(sacks, localPlayer.getLocalLocation());
	}

	private void handleEmptyingSack(Player localPlayer)
	{
		if (isUpstairs(localPlayer.getLocalLocation()))
		{
			currentState = ScriptState.CLIMBING_LADDER_DOWN;
			climbLadderDown(localPlayer);
			return;
		}

		WorldPoint playerWorld = localPlayer.getWorldLocation();
		// Only check tunnel rockfalls if player is far away in the western mining room (X < 3733)
		if (playerWorld != null && playerWorld.getX() < 3733 && config.clearRockfalls())
		{
			GameObject tunnelRock = findBlockingTunnelRockfall(localPlayer, true);
			if (tunnelRock != null)
			{
				currentState = ScriptState.CLEARING_ROCKFALL;
				currentRockfallTarget = tunnelRock;
				interactObject(tunnelRock, "Mine", "Rockfall");
				cooldownTicks = 3;
				return;
			}
		}

		// If player is moving or animating (e.g. searching sack), wait
		if (localPlayer.getAnimation() != -1)
		{
			cooldownTicks = 1;
			return;
		}

		GameObject sack = findSack(localPlayer);
		LocalPoint sackLp = sack != null ? sack.getLocalLocation() : LocalPoint.fromWorld(client, SACK_LOCATION);
		int sackId = (sack != null && SACK_OBJECTS.contains(sack.getId())) ? sack.getId() : ObjectID.MOTHERLODE_SACK;

		if (sackLp != null)
		{
			log.info("Searching sack at ({}, {}) id {}", sackLp.getSceneX(), sackLp.getSceneY(), sackId);
			client.menuAction(
				sackLp.getSceneX(),
				sackLp.getSceneY(),
				MenuAction.GAME_OBJECT_FIRST_OPTION,
				sackId,
				0,
				"Search",
				"Sack"
			);
			cooldownTicks = 3;
			if (currentSackCount == 0)
			{
				shouldEmptySackOnStart = false;
				depositedInventories = 0;
			}
		}
	}

	private void climbLadderUp(Player localPlayer)
	{
		GameObject ladder = findClosestObject(laddersBottom, localPlayer.getLocalLocation());
		if (ladder != null)
		{
			interactObject(ladder, "Climb", "Ladder");
			cooldownTicks = 3;
		}
	}

	private void climbLadderDown(Player localPlayer)
	{
		GameObject ladder = findClosestObject(laddersTop, localPlayer.getLocalLocation());
		if (ladder != null)
		{
			interactObject(ladder, "Climb", "Ladder");
			cooldownTicks = 3;
		}
	}

	private void interactObject(TileObject object, String option, String target)
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
		client.menuAction(
			loc.getSceneX(),
			loc.getSceneY(),
			MenuAction.GAME_OBJECT_FIRST_OPTION,
			object.getId(),
			0,
			option,
			target
		);
	}

	/**
	 * Finds the best vein: when upstairs picks closest upstairs vein.
	 * When downstairs, prioritizes the area towards the blacklisted rockfall (3728, 5688).
	 */
	private WallObject findBestVein(LocalPoint playerLoc, boolean upperFloor)
	{
		if (upperFloor)
		{
			return veins.stream()
				.filter(this::isUpstairs)
				.filter(this::isVeinAlive)
				.min(Comparator.comparingInt(v -> playerLoc.distanceTo(v.getLocalLocation())))
				.orElse(null);
		}

		// Downstairs: Target the area towards the blacklisted rockfall (3728, 5688)
		// First check for available veins near the preferred mining area (within 18 tiles of 3728, 5688)
		List<WallObject> preferredVeins = veins.stream()
			.filter(v -> !isUpstairs(v))
			.filter(this::isVeinAlive)
			.filter(v -> v.getWorldLocation().distanceTo(PREFERRED_MINING_AREA) <= 18)
			.sorted(Comparator.comparingInt(v -> playerLoc.distanceTo(v.getLocalLocation())))
			.collect(Collectors.toList());

		if (!preferredVeins.isEmpty())
		{
			return preferredVeins.get(0);
		}

		// Fallback: any downstairs alive vein, sorted closest to preferred area
		return veins.stream()
			.filter(v -> !isUpstairs(v))
			.filter(this::isVeinAlive)
			.min(Comparator.comparingInt(v -> v.getWorldLocation().distanceTo(PREFERRED_MINING_AREA)))
			.orElse(null);
	}

	private WallObject findAdjacentVein(LocalPoint playerLoc, boolean upperFloor)
	{
		return veins.stream()
			.filter(v -> isUpstairs(v) == upperFloor)
			.filter(this::isVeinAlive)
			.filter(v -> playerLoc.distanceTo(v.getLocalLocation()) <= 2 * Perspective.LOCAL_TILE_SIZE)
			.min(Comparator.comparingInt(v -> playerLoc.distanceTo(v.getLocalLocation())))
			.orElse(null);
	}

	private GameObject findClosestObject(Set<GameObject> objects, LocalPoint playerLoc)
	{
		return objects.stream()
			.min(Comparator.comparingInt(o -> playerLoc.distanceTo(o.getLocalLocation())))
			.orElse(null);
	}

	private void updateInventoryAndSackData()
	{
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		if (inv != null)
		{
			currentPayDirt = inv.count(ItemID.PAYDIRT);
			if (previousPayDirt > 0 && currentPayDirt == 0)
			{
				boolean nearLowerHopper = lowerHopper != null && client.getLocalPlayer() != null
					&& client.getLocalPlayer().getLocalLocation().distanceTo(lowerHopper.getLocalLocation()) < 6 * Perspective.LOCAL_TILE_SIZE;
				boolean nearUpperHopper = upperHopper != null && client.getLocalPlayer() != null
					&& client.getLocalPlayer().getLocalLocation().distanceTo(upperHopper.getLocalLocation()) < 6 * Perspective.LOCAL_TILE_SIZE;

				if (currentState == ScriptState.DEPOSITING_HOPPER || nearLowerHopper || nearUpperHopper)
				{
					depositedInventories++;
					log.info("Pay-dirt deposited! Total inventories deposited: {}", depositedInventories);
				}
			}
			previousPayDirt = currentPayDirt;
		}
		else
		{
			currentPayDirt = 0;
			previousPayDirt = 0;
		}

		currentSackCount = client.getVarbitValue(VarbitID.MOTHERLODE_SACK_TRANSMIT);
	}

	public boolean isUpstairs(TileObject object)
	{
		if (object == null)
		{
			return false;
		}
		return isUpstairs(object.getLocalLocation());
	}

	public boolean isUpstairs(LocalPoint localPoint)
	{
		if (localPoint == null)
		{
			return false;
		}
		return Perspective.getTileHeight(client, localPoint, 0) < UPPER_FLOOR_HEIGHT;
	}

	private boolean isHopper(GameObject gameObject)
	{
		if (gameObject == null)
		{
			return false;
		}
		if (gameObject.getId() == ObjectID.MOTHERLODE_HOPPER)
		{
			return true;
		}
		try
		{
			ObjectComposition def = client.getObjectDefinition(gameObject.getId());
			if (def != null && def.getName() != null && "Hopper".equalsIgnoreCase(def.getName()))
			{
				return true;
			}
		}
		catch (Exception ignored)
		{
		}
		return false;
	}

	private boolean checkInMlm()
	{
		GameState gameState = client.getGameState();
		if (gameState != GameState.LOGGED_IN && gameState != GameState.LOADING)
		{
			return false;
		}

		int[] currentMapRegions = client.getMapRegions();
		if (currentMapRegions == null || currentMapRegions.length == 0)
		{
			return false;
		}

		for (int region : currentMapRegions)
		{
			if (!MOTHERLODE_MAP_REGIONS.contains(region))
			{
				return false;
			}
		}
		return true;
	}

	private void scanScene()
	{
		clearObjects();
		Tile[][][] tiles = client.getScene().getTiles();
		if (tiles == null)
		{
			return;
		}

		int z = client.getPlane();
		for (int x = 0; x < Constants.SCENE_SIZE; ++x)
		{
			for (int y = 0; y < Constants.SCENE_SIZE; ++y)
			{
				Tile tile = tiles[z][x][y];
				if (tile == null)
				{
					continue;
				}

				WallObject wall = tile.getWallObject();
				if (wall != null && MINE_SPOTS.contains(wall.getId()))
				{
					veins.add(wall);
				}

				GameObject[] gameObjects = tile.getGameObjects();
				if (gameObjects != null)
				{
					for (GameObject go : gameObjects)
					{
						if (go != null)
						{
							addGameObject(go);
						}
					}
				}
			}
		}
	}

	private void addGameObject(GameObject gameObject)
	{
		int id = gameObject.getId();
		if (ROCK_OBSTACLES.contains(id))
		{
			// Strictly track ONLY the 3 user-specified tunnel rockfalls!
			// All other rockfalls in the mine are completely ignored so the bot never wanders off to other rockfalls!
			if (isAnyTunnelRock(gameObject))
			{
				rockfalls.add(gameObject);
			}
			else
			{
				log.debug("Non-tunnel rockfall at {} ignored.", gameObject.getWorldLocation());
			}
		}
		else if (id == ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN)
		{
			brokenStruts.add(gameObject);
		}
		else if (id == ObjectID.MOTHERLODE_HOPPER || isHopper(gameObject))
		{
			if (isUpstairs(gameObject))
			{
				upperHopper = gameObject;
			}
			else
			{
				lowerHopper = gameObject;
			}
		}
		else if (id == ObjectID.CRATE2_OLD)
		{
			hammerCrate = gameObject;
		}
		else if (SACK_OBJECTS.contains(id) || id == ObjectID.MOTHERLODE_SACK || (gameObject.getWorldLocation() != null && gameObject.getWorldLocation().equals(SACK_LOCATION)))
		{
			sacks.add(gameObject);
		}
		else if (id == ObjectID.BLAST_BANK_CHEST)
		{
			bankChest = gameObject;
		}
		else if (LADDERS_BOTTOM.contains(id))
		{
			laddersBottom.add(gameObject);
		}
		else if (LADDERS_TOP.contains(id))
		{
			laddersTop.add(gameObject);
		}
	}

	private void removeGameObject(GameObject gameObject)
	{
		int id = gameObject.getId();
		if (ROCK_OBSTACLES.contains(id))
		{
			rockfalls.remove(gameObject);
		}
		else if (id == ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN)
		{
			brokenStruts.remove(gameObject);
		}
		else if (upperHopper == gameObject)
		{
			upperHopper = null;
		}
		else if (lowerHopper == gameObject)
		{
			lowerHopper = null;
		}
		else if (id == ObjectID.CRATE2_OLD && hammerCrate == gameObject)
		{
			hammerCrate = null;
		}
		else if (SACK_OBJECTS.contains(id) || (gameObject.getWorldLocation() != null && gameObject.getWorldLocation().equals(SACK_LOCATION)))
		{
			sacks.remove(gameObject);
		}
		else if (id == ObjectID.BLAST_BANK_CHEST && bankChest == gameObject)
		{
			bankChest = null;
		}
		else if (LADDERS_BOTTOM.contains(id))
		{
			laddersBottom.remove(gameObject);
		}
		else if (LADDERS_TOP.contains(id))
		{
			laddersTop.remove(gameObject);
		}
	}

	private void clearObjects()
	{
		veins.clear();
		rockfalls.clear();
		brokenStruts.clear();
		sacks.clear();
		laddersBottom.clear();
		laddersTop.clear();
		lowerHopper = null;
		upperHopper = null;
		hammerCrate = null;
		bankChest = null;
		currentRockfallTarget = null;
		currentVeinTarget = null;
		currentStrutTarget = null;
		idleStrutTicks = 0;
	}
}
