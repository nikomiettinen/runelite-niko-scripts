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
	private static final WorldPoint LOWER_HOPPER_LOCATION = new WorldPoint(3748, 5673, 0);

	// The broken strut closest to the deposit hopper (User attachment: World 3742, 5669, 0 / Region 14936, local 30, 37)
	private static final WorldPoint HOPPER_STRUT_LOCATION = new WorldPoint(3742, 5669, 0);
	private static final int HOPPER_STRUT_X = 3742;
	private static final int HOPPER_STRUT_Y = 5669;
	private static final int HOPPER_STRUT_REGION_X = 30;
	private static final int HOPPER_STRUT_REGION_Y = 37;

	private static final Set<Integer> LADDERS_BOTTOM = ImmutableSet.of(
		ObjectID.MOTHERLODE_LADDER_BOTTOM,
		ObjectID.MOTHERLODE_LADDER_BOTTOM_ACTIVE,
		ObjectID.MOTHERLODE_LADDER_BOTTOM_INACTIVE,
		19044, 19047, 19048
	);

	private static final Set<Integer> LADDERS_TOP = ImmutableSet.of(
		ObjectID.MOTHERLODE_LADDER_TOP,
		ObjectID.MOTHERLODE_LADDER_TOP_ACTIVE,
		ObjectID.MOTHERLODE_LADDER_TOP_INACTIVE,
		19045, 19049, 19050
	);

	private static final WorldPoint LADDER_BOTTOM_LOCATION = new WorldPoint(3755, 5675, 0);

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
		else if (msg.contains("reach that") || msg.contains("can't reach that") || msg.contains("cannot reach that"))
		{
			log.info("Received 'I can't reach that!' message in chat.");
			if (config.clearRockfalls())
			{
				Player localPlayer = client.getLocalPlayer();
				if (localPlayer != null)
				{
					GameObject rock = findAdjacentRockfall(localPlayer);
					if (rock == null && currentVeinTarget != null)
					{
						rock = findRockfallBetween(localPlayer, currentVeinTarget);
					}
					if (rock != null)
					{
						log.info("Mining blocking rockfall at {} after 'can't reach that'!", rock.getWorldLocation());
						currentVeinTarget = null;
						currentRockfallTarget = rock;
						currentState = ScriptState.CLEARING_ROCKFALL;
						interactObject(rock, "Mine", "Rockfall");
						cooldownTicks = 3;
						idleMiningTicks = 0;
						return;
					}
				}
			}
			currentVeinTarget = null;
			idleMiningTicks = 0;
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
		if (veins.isEmpty())
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

		// When mining upstairs and player is downstairs, never target any rockfalls
		if (targetUpstairs && !playerUpstairs)
		{
			currentRockfallTarget = null;
		}

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
		if (currentRockfallTarget != null)
		{
			boolean isUpstairsRock = isUpstairs(currentRockfallTarget);
			if (isUpstairsRock != playerUpstairs || (targetUpstairs && !playerUpstairs))
			{
				log.info("Cancelling rockfall target on wrong floor or downstairs with upper floor enabled (rock upstairs: {}, player upstairs: {})",
					isUpstairsRock, playerUpstairs);
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
				currentState = ScriptState.CLEARING_ROCKFALL;
				// Stay on this rockfall until destroyed; re-click if idle between swings for 3+ ticks
				if (idleMiningTicks >= 3)
				{
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
					// Moving towards the vein - never interrupt while moving!
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
						if (config.clearRockfalls())
						{
							GameObject blockingRock = findAdjacentRockfall(localPlayer);
							if (blockingRock == null)
							{
								blockingRock = findRockfallBetween(localPlayer, currentVeinTarget);
							}
							if (blockingRock != null)
							{
								log.info("Blocked by rockfall at {} while trying to reach vein! Mining rockfall to pass.", blockingRock.getWorldLocation());
								currentVeinTarget = null;
								currentRockfallTarget = blockingRock;
								currentState = ScriptState.CLEARING_ROCKFALL;
								interactObject(blockingRock, "Mine", "Rockfall");
								cooldownTicks = 3;
								idleMiningTicks = 0;
								return;
							}
							// If downstairs and blocked by tunnel rockfall, mine it to pass!
							if (!playerUpstairs && !config.upperFloor())
							{
								GameObject tunnelRock = findBlockingTunnelRockfall(localPlayer, false);
								if (tunnelRock != null)
								{
									log.info("Blocked by tunnel rockfall at {} while trying to reach vein! Mining rockfall to pass.", tunnelRock.getWorldLocation());
									currentVeinTarget = null;
									currentRockfallTarget = tunnelRock;
									currentState = ScriptState.CLEARING_ROCKFALL;
									interactObject(tunnelRock, "Mine", "Rockfall");
									cooldownTicks = 3;
									idleMiningTicks = 0;
									return;
								}
							}
						}
						log.info("Player stopped moving before reaching vein (stalled). Clearing vein target to re-evaluate route.");
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

			boolean canUseUpperHopper = config.upperFloor() && config.upperHopperUnlocked() && upperHopper != null;

			if (playerUpstairs)
			{
				if (canUseUpperHopper)
				{
					if (config.clearRockfalls())
					{
						GameObject blockingRock = findAdjacentUpstairsRockfall(localPlayer);
						if (blockingRock != null)
						{
							currentState = ScriptState.CLEARING_ROCKFALL;
							currentRockfallTarget = blockingRock;
							interactObject(blockingRock, "Mine", "Rockfall");
							cooldownTicks = 3;
							return;
						}
					}

					currentState = ScriptState.DEPOSITING_HOPPER;
					interactObject(upperHopper, "Deposit", "Hopper");
					cooldownTicks = 3;
					return;
				}
				else
				{
					// Upper hopper not unlocked or not available: climb down ladder to use lower hopper!
					if (config.clearRockfalls())
					{
						GameObject blockingRock = findAdjacentUpstairsRockfall(localPlayer);
						if (blockingRock != null)
						{
							currentState = ScriptState.CLEARING_ROCKFALL;
							currentRockfallTarget = blockingRock;
							interactObject(blockingRock, "Mine", "Rockfall");
							cooldownTicks = 3;
							return;
						}
					}

					currentState = ScriptState.CLIMBING_LADDER_DOWN;
					climbLadderDown(localPlayer);
					return;
				}
			}

			// Downstairs hopper handling (player is downstairs)
			// Clear rockfalls blocking downstairs tunnel to hopper ONLY if mining downstairs!
			// When upper floor is enabled, player came downstairs ONLY to deposit at lower hopper, NEVER go to northwest tunnel rockfalls!
			if (config.clearRockfalls() && !config.upperFloor())
			{
				// Always check the closest blocking tunnel rockfall first!
				GameObject tunnelRock = findBlockingTunnelRockfall(localPlayer, true);
				if (tunnelRock != null)
				{
					log.info("Heading to deposit hopper: clearing blocking tunnel rockfall at {}", tunnelRock.getWorldLocation());
					currentState = ScriptState.CLEARING_ROCKFALL;
					currentRockfallTarget = tunnelRock;
					interactObject(tunnelRock, "Mine", "Rockfall");
					cooldownTicks = 3;
					return;
				}
			}

			GameObject activeHopper = lowerHopper != null ? lowerHopper : findHopper(false);
			if (activeHopper != null)
			{

				// When downstairs near hopper, check and repair ONLY the 1 broken strut closest to the deposit hopper (tile 3742, 5669, 0)
				GameObject targetStrut = findHopperStrut();
				if (config.repairStruts() && targetStrut != null
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
			currentRockfallTarget = null;
			currentState = ScriptState.CLIMBING_LADDER_UP;
			climbLadderUp(localPlayer);
			return;
		}
		else if (!targetUpstairs && playerUpstairs)
		{
			currentRockfallTarget = null;
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

		// 7. Mine ore vein (ONLY if not already mining a vein)
		// When downstairs: check if tunnel rockfalls block passage to the preferred vein room ONLY when starting/entering with 0 pay-dirt!
		if (!targetUpstairs && !playerUpstairs && config.clearRockfalls())
		{
			if (!isInsideVeinRoom(localPlayer) && currentPayDirt == 0)
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
		}

		WallObject nearestVein = findBestVein(localPlayer.getLocalLocation(), targetUpstairs);
		if (currentVeinTarget == null && nearestVein != null)
		{
			if (config.clearRockfalls())
			{
				GameObject blockingRock = findAdjacentRockfall(localPlayer);
				if (blockingRock == null)
				{
					blockingRock = findRockfallBetween(localPlayer, nearestVein);
				}
				if (blockingRock != null)
				{
					log.info("Path to vein is blocked by rockfall at {}! Mining rockfall first.", blockingRock.getWorldLocation());
					currentState = ScriptState.CLEARING_ROCKFALL;
					currentRockfallTarget = blockingRock;
					currentVeinTarget = null;
					interactObject(blockingRock, "Mine", "Rockfall");
					cooldownTicks = 3;
					idleMiningTicks = 0;
					return;
				}
			}

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
		if (localPlayer == null || !config.clearRockfalls())
		{
			return null;
		}

		LocalPoint playerLoc = localPlayer.getLocalLocation();
		WorldPoint playerWorld = localPlayer.getWorldLocation();
		if (playerWorld == null || isUpstairs(playerLoc))
		{
			return null;
		}

		List<GameObject> activeTunnelRocks = rockfalls.stream()
			.filter(r -> !isUpstairs(r) && !isBlacklistedRockfall(r) && isAnyTunnelRock(r))
			.collect(Collectors.toList());

		if (activeTunnelRocks.isEmpty())
		{
			return null;
		}

		// Immediate obstacle check: if the player is directly adjacent to ANY active tunnel rockfall (within 2 tiles),
		// it is physically blocking movement!
		GameObject adjacentRock = activeTunnelRocks.stream()
			.filter(r -> playerLoc.distanceTo(r.getLocalLocation()) <= 2 * Perspective.LOCAL_TILE_SIZE)
			.min(Comparator.comparingInt(r -> playerLoc.distanceTo(r.getLocalLocation())))
			.orElse(null);

		if (adjacentRock != null)
		{
			return adjacentRock;
		}

		if (headingToHopper)
		{
			// Heading to hopper: Player travels East from vein room (X ~ 3726) to hopper (X ~ 3748)
			// Progression order: West -> Mid -> East
			if (isInsideVeinRoom(localPlayer) || playerWorld.getX() <= 3728)
			{
				GameObject west = activeTunnelRocks.stream().filter(this::isTunnelRockWest).findFirst().orElse(null);
				if (west != null) return west;
				GameObject mid = activeTunnelRocks.stream().filter(this::isTunnelRockMid).findFirst().orElse(null);
				if (mid != null) return mid;
				GameObject east = activeTunnelRocks.stream().filter(this::isTunnelRockEast).findFirst().orElse(null);
				if (east != null) return east;
				return activeTunnelRocks.stream().min(Comparator.comparingInt(r -> playerLoc.distanceTo(r.getLocalLocation()))).orElse(null);
			}
			else if (playerWorld.getX() <= 3731)
			{
				// Past West rockfall; next is Mid, then East
				GameObject mid = activeTunnelRocks.stream().filter(this::isTunnelRockMid).findFirst().orElse(null);
				if (mid != null) return mid;
				GameObject east = activeTunnelRocks.stream().filter(this::isTunnelRockEast).findFirst().orElse(null);
				if (east != null) return east;
				return null;
			}
			else if (playerWorld.getX() <= 3733)
			{
				// Past Mid rockfall; next is East
				GameObject east = activeTunnelRocks.stream().filter(this::isTunnelRockEast).findFirst().orElse(null);
				if (east != null) return east;
				return null;
			}
			// Past East rockfall (X >= 3734); in hopper area
			return null;
		}
		else
		{
			// Heading to veins: Player travels West from hopper (X ~ 3748) to vein room (X ~ 3726)
			// Progression order: East -> Mid -> West
			if (playerWorld.getX() >= 3734)
			{
				GameObject east = activeTunnelRocks.stream().filter(this::isTunnelRockEast).findFirst().orElse(null);
				if (east != null) return east;
				GameObject mid = activeTunnelRocks.stream().filter(this::isTunnelRockMid).findFirst().orElse(null);
				if (mid != null) return mid;
				GameObject west = activeTunnelRocks.stream().filter(this::isTunnelRockWest).findFirst().orElse(null);
				if (west != null) return west;
				return null;
			}
			else if (playerWorld.getX() >= 3732)
			{
				// Past East rockfall; next is Mid, then West
				GameObject mid = activeTunnelRocks.stream().filter(this::isTunnelRockMid).findFirst().orElse(null);
				if (mid != null) return mid;
				GameObject west = activeTunnelRocks.stream().filter(this::isTunnelRockWest).findFirst().orElse(null);
				if (west != null) return west;
				return null;
			}
			else if (playerWorld.getX() >= 3729)
			{
				// Past Mid rockfall; next is West
				GameObject west = activeTunnelRocks.stream().filter(this::isTunnelRockWest).findFirst().orElse(null);
				if (west != null) return west;
				return null;
			}
			// Inside or at entrance to vein room; no more tunnel rockfalls block veins
			return null;
		}
	}

	/**
	 * Finds any upstairs rockfall blocking the way or close to the player.
	 * If destination is given, finds rockfalls between player and destination.
	 */
	/**
	 * Finds an active rockfall on the player's current floor directly adjacent to the player (within ~2.5 tiles)
	 * that physically blocks movement or pathing.
	 */
	private GameObject findAdjacentRockfall(Player localPlayer)
	{
		if (localPlayer == null || !config.clearRockfalls())
		{
			return null;
		}
		LocalPoint playerLoc = localPlayer.getLocalLocation();
		if (playerLoc == null)
		{
			return null;
		}
		boolean upstairs = isUpstairs(playerLoc);
		return rockfalls.stream()
			.filter(r -> isUpstairs(r) == upstairs)
			.filter(r -> playerLoc.distanceTo(r.getLocalLocation()) <= 320)
			.min(Comparator.comparingInt(r -> playerLoc.distanceTo(r.getLocalLocation())))
			.orElse(null);
	}

	private GameObject findAdjacentUpstairsRockfall(Player localPlayer)
	{
		return findAdjacentRockfall(localPlayer);
	}

	/**
	 * Finds an active rockfall on the same floor located between the player and the target vein.
	 */
	private GameObject findRockfallBetween(Player localPlayer, WallObject vein)
	{
		if (localPlayer == null || vein == null || !config.clearRockfalls())
		{
			return null;
		}
		LocalPoint pLoc = localPlayer.getLocalLocation();
		LocalPoint vLoc = vein.getLocalLocation();
		if (pLoc == null || vLoc == null)
		{
			return null;
		}
		boolean upstairs = isUpstairs(pLoc);
		int distPV = pLoc.distanceTo(vLoc);

		return rockfalls.stream()
			.filter(r -> isUpstairs(r) == upstairs)
			.filter(r -> {
				LocalPoint rLoc = r.getLocalLocation();
				if (rLoc == null)
				{
					return false;
				}
				int distPR = pLoc.distanceTo(rLoc);
				int distRV = rLoc.distanceTo(vLoc);
				// Rock is within 5 tiles of player and closer to the vein than player is
				return distPR <= 5 * Perspective.LOCAL_TILE_SIZE && distRV < distPV;
			})
			.min(Comparator.comparingInt(r -> pLoc.distanceTo(r.getLocalLocation())))
			.orElse(null);
	}

	private GameObject findUpstairsRockfall(Player localPlayer, LocalPoint destination)
	{
		if (localPlayer == null || !config.clearRockfalls())
		{
			return null;
		}

		LocalPoint playerLoc = localPlayer.getLocalLocation();
		if (playerLoc == null)
		{
			return null;
		}

		List<GameObject> upstairsRocks = rockfalls.stream()
			.filter(this::isUpstairs)
			.collect(Collectors.toList());

		if (upstairsRocks.isEmpty())
		{
			return null;
		}

		// If player is standing right next to an upstairs rockfall (within 3 tiles), it is blocking the player!
		GameObject adjacentRock = upstairsRocks.stream()
			.filter(r -> playerLoc.distanceTo(r.getLocalLocation()) <= 3 * Perspective.LOCAL_TILE_SIZE)
			.min(Comparator.comparingInt(r -> playerLoc.distanceTo(r.getLocalLocation())))
			.orElse(null);

		if (adjacentRock != null)
		{
			return adjacentRock;
		}

		if (destination != null)
		{
			int distToDest = playerLoc.distanceTo(destination);
			return upstairsRocks.stream()
				.filter(r -> {
					int rockToDest = r.getLocalLocation().distanceTo(destination);
					int rockToPlayer = playerLoc.distanceTo(r.getLocalLocation());
					return (rockToDest < distToDest && rockToPlayer < distToDest) || rockToPlayer <= 4 * Perspective.LOCAL_TILE_SIZE;
				})
				.min(Comparator.comparingInt(r -> playerLoc.distanceTo(r.getLocalLocation())))
				.orElse(null);
		}

		return upstairsRocks.stream()
			.filter(r -> playerLoc.distanceTo(r.getLocalLocation()) <= 10 * Perspective.LOCAL_TILE_SIZE)
			.min(Comparator.comparingInt(r -> playerLoc.distanceTo(r.getLocalLocation())))
			.orElse(null);
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
		return (wp.getX() >= 3725 && wp.getX() <= 3728 && wp.getY() >= 5681 && wp.getY() <= 5685)
			|| (Math.abs(wp.getX() - TUNNEL_ROCK_WEST_X) <= 1 && Math.abs(wp.getY() - TUNNEL_ROCK_WEST_Y) <= 1);
	}

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
		return (wp.getX() >= 3729 && wp.getX() <= 3731 && wp.getY() >= 5681 && wp.getY() <= 5685)
			|| (Math.abs(wp.getX() - TUNNEL_ROCK_MID_X) <= 1 && Math.abs(wp.getY() - TUNNEL_ROCK_MID_Y) <= 1);
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
		return (wp.getX() >= 3732 && wp.getX() <= 3736 && wp.getY() >= 5678 && wp.getY() <= 5685)
			|| (Math.abs(wp.getX() - TUNNEL_ROCK_EAST_X) <= 1 && Math.abs(wp.getY() - TUNNEL_ROCK_EAST_Y) <= 1);
	}

	private boolean isAnyTunnelRock(GameObject r)
	{
		if (r == null)
		{
			return false;
		}
		if (isBlacklistedRockfall(r) || isUpstairs(r))
		{
			return false;
		}
		WorldPoint wp = r.getWorldLocation();
		if (wp == null)
		{
			return false;
		}
		return (wp.getX() >= 3725 && wp.getX() <= 3736 && wp.getY() >= 5678 && wp.getY() <= 5685)
			|| isTunnelRockWest(r) || isTunnelRockMid(r) || isTunnelRockEast(r);
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

		// NEVER trigger failsafe in the middle of mining veins when inventory is not full!
		// Only trigger when inventory is full and stuck on the way to the hopper.
		if (!inventoryFull)
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

		// Universal rockfall failsafe: if stuck for 3+ ticks near any rockfall on current floor, clear it!
		if (config.clearRockfalls() && stuckTicks >= 3 && !rockfalls.isEmpty())
		{
			GameObject blockingRock = findAdjacentRockfall(localPlayer);
			if (blockingRock != null)
			{
				log.info("Failsafe triggered: stuck adjacent to rockfall at {}, mining it.", blockingRock.getWorldLocation());
				currentState = ScriptState.CLEARING_ROCKFALL;
				currentRockfallTarget = blockingRock;
				currentVeinTarget = null;
				interactObject(blockingRock, "Mine", "Rockfall");
				cooldownTicks = 3;
				stuckTicks = 0;
				return true;
			}
		}

		// Downstairs failsafe: when mining downstairs and inventory full
		if (inventoryFull && !isUpstairs(localPlayer.getLocalLocation()) && !config.upperFloor() && config.clearRockfalls())
		{
			if (stuckTicks >= 2 && !rockfalls.isEmpty())
			{
				GameObject tunnelRock = findBlockingTunnelRockfall(localPlayer, true);
				if (tunnelRock != null)
				{
					log.info("Failsafe triggered: clearing closest blocking tunnel rockfall at {}", tunnelRock.getWorldLocation());
					currentState = ScriptState.CLEARING_ROCKFALL;
					currentRockfallTarget = tunnelRock;
					currentVeinTarget = null;
					interactObject(tunnelRock, "Mine", "Rockfall");
					cooldownTicks = 3;
					stuckTicks = 0;
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Returns true if the rockfall is on the blacklisted useless tile (attachment coordinates).
	 */
	private boolean isBlacklistedRockfall(GameObject rockfall)
	{
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
		currentRockfallTarget = null;
		currentVeinTarget = null;
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
		currentRockfallTarget = null;
		currentVeinTarget = null;
		if (isUpstairs(localPlayer.getLocalLocation()))
		{
			currentState = ScriptState.CLIMBING_LADDER_DOWN;
			climbLadderDown(localPlayer);
			return;
		}

		WorldPoint playerWorld = localPlayer.getWorldLocation();

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
		currentRockfallTarget = null;
		currentVeinTarget = null;
		idleMiningTicks = 0;

		if (localPlayer.getAnimation() != -1)
		{
			return;
		}

		GameObject ladder = laddersBottom.stream()
			.filter(l -> l != null && LADDERS_BOTTOM.contains(l.getId()))
			.min(Comparator.comparingInt(l -> localPlayer.getLocalLocation().distanceTo(l.getLocalLocation())))
			.orElse(null);

		if (ladder == null)
		{
			ladder = findLadder(false, localPlayer.getLocalLocation());
		}
		if (ladder == null)
		{
			LocalPoint lp = LocalPoint.fromWorld(client, LADDER_BOTTOM_LOCATION);
			if (lp != null)
			{
				ladder = findObjectOnTile(lp);
				if (ladder != null && !LADDERS_BOTTOM.contains(ladder.getId()))
				{
					ladder = null;
				}
			}
		}

		if (ladder != null)
		{
			log.info("Climbing ladder up at ({}, {}) id {}",
				ladder.getLocalLocation().getSceneX(), ladder.getLocalLocation().getSceneY(), ladder.getId());
			interactObject(ladder, "Climb", "Ladder");
			cooldownTicks = 3;
		}
		else
		{
			log.warn("Cannot find ladder bottom to climb up!");
		}
	}

	private void climbLadderDown(Player localPlayer)
	{
		currentRockfallTarget = null;
		currentVeinTarget = null;
		idleMiningTicks = 0;

		if (localPlayer.getAnimation() != -1)
		{
			return;
		}

		GameObject ladder = laddersTop.stream()
			.filter(l -> l != null && LADDERS_TOP.contains(l.getId()))
			.min(Comparator.comparingInt(l -> localPlayer.getLocalLocation().distanceTo(l.getLocalLocation())))
			.orElse(null);
		if (ladder == null)
		{
			ladder = findLadder(true, localPlayer.getLocalLocation());
		}

		if (ladder != null)
		{
			log.info("Climbing ladder down at ({}, {}) id {}",
				ladder.getLocalLocation().getSceneX(), ladder.getLocalLocation().getSceneY(), ladder.getId());
			interactObject(ladder, "Climb", "Ladder");
			cooldownTicks = 3;
		}
		else
		{
			log.warn("Cannot find ladder top to climb down!");
		}
	}

	private GameObject findObjectOnTile(LocalPoint lp)
	{
		if (lp == null)
		{
			return null;
		}
		int sceneX = lp.getSceneX();
		int sceneY = lp.getSceneY();
		int plane = client.getPlane();
		Tile[][][] tiles = client.getScene().getTiles();
		if (tiles == null || plane < 0 || plane >= tiles.length)
		{
			return null;
		}
		if (sceneX < 0 || sceneX >= Constants.SCENE_SIZE || sceneY < 0 || sceneY >= Constants.SCENE_SIZE)
		{
			return null;
		}
		Tile tile = tiles[plane][sceneX][sceneY];
		if (tile != null && tile.getGameObjects() != null)
		{
			for (GameObject go : tile.getGameObjects())
			{
				if (go != null)
				{
					return go;
				}
			}
		}
		return null;
	}

	private GameObject findLadder(boolean upstairs, LocalPoint playerLoc)
	{
		Tile[][][] tiles = client.getScene().getTiles();
		if (tiles == null)
		{
			return null;
		}

		Set<Integer> targetIds = upstairs ? LADDERS_TOP : LADDERS_BOTTOM;
		int z = client.getPlane();
		GameObject bestLadder = null;
		int bestDist = Integer.MAX_VALUE;

		for (int x = 0; x < Constants.SCENE_SIZE; ++x)
		{
			for (int y = 0; y < Constants.SCENE_SIZE; ++y)
			{
				Tile tile = tiles[z][x][y];
				if (tile == null)
				{
					continue;
				}

				GameObject[] gameObjects = tile.getGameObjects();
				if (gameObjects == null)
				{
					continue;
				}

				for (GameObject go : gameObjects)
				{
					if (go != null && targetIds.contains(go.getId()) && (isUpstairs(go) == upstairs))
					{
						int dist = playerLoc != null ? playerLoc.distanceTo(go.getLocalLocation()) : 0;
						if (dist < bestDist)
						{
							bestDist = dist;
							bestLadder = go;
						}
					}
				}
			}
		}

		return bestLadder;
	}

	private GameObject findHopper(boolean upstairs)
	{
		if (upstairs && upperHopper != null)
		{
			return upperHopper;
		}
		if (!upstairs && lowerHopper != null)
		{
			return lowerHopper;
		}

		if (!upstairs)
		{
			LocalPoint lp = LocalPoint.fromWorld(client, LOWER_HOPPER_LOCATION);
			if (lp != null)
			{
				GameObject go = findObjectOnTile(lp);
				if (go != null && (go.getId() == ObjectID.MOTHERLODE_HOPPER || isHopper(go)))
				{
					lowerHopper = go;
					return go;
				}
			}
		}

		Tile[][][] tiles = client.getScene().getTiles();
		if (tiles == null)
		{
			return null;
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

				GameObject[] gameObjects = tile.getGameObjects();
				if (gameObjects == null)
				{
					continue;
				}

				for (GameObject go : gameObjects)
				{
					if (go != null && isHopper(go) && (isUpstairs(go) == upstairs))
					{
						if (upstairs)
						{
							upperHopper = go;
						}
						else
						{
							lowerHopper = go;
						}
						return go;
					}
				}
			}
		}

		return upstairs ? upperHopper : lowerHopper;
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

		MenuAction menuAction = MenuAction.GAME_OBJECT_FIRST_OPTION;
		int param0 = loc.getSceneX();
		int param1 = loc.getSceneY();
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
			if (def != null)
			{
				if (def.getImpostorIds() != null)
				{
					def = def.getImpostor();
				}
				if (def != null && def.getName() != null && "Hopper".equalsIgnoreCase(def.getName()))
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

	private boolean isLadder(GameObject gameObject)
	{
		if (gameObject == null)
		{
			return false;
		}
		int id = gameObject.getId();
		if (id == ObjectID.MOTHERLODE_LADDER_JOIN || id == 19046
			|| id == ObjectID.MOTHERLODE_LADDER_STEPS || id == 53243)
		{
			return false;
		}
		return LADDERS_BOTTOM.contains(id) || LADDERS_TOP.contains(id);
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
			rockfalls.add(gameObject);
			log.debug("Rockfall added at {} (upstairs: {})", gameObject.getWorldLocation(), isUpstairs(gameObject));
		}
		else if (id == ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN)
		{
			brokenStruts.add(gameObject);
		}
		else if (id == ObjectID.MOTHERLODE_HOPPER || isHopper(gameObject))
		{
			if (gameObject.getWorldLocation() != null && gameObject.getWorldLocation().equals(LOWER_HOPPER_LOCATION))
			{
				lowerHopper = gameObject;
			}
			else if (isUpstairs(gameObject))
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
		else if (isLadder(gameObject))
		{
			if (isUpstairs(gameObject))
			{
				laddersTop.add(gameObject);
			}
			else
			{
				laddersBottom.add(gameObject);
			}
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
		else if (isLadder(gameObject))
		{
			laddersBottom.remove(gameObject);
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
