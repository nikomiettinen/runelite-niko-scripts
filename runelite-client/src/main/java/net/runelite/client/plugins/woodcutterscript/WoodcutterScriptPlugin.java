/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.woodcutterscript;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.time.Instant;
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
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Woodcutter Script",
	description = "Automaattinen puunhakkaaja (Powerchop ja Pankitus)",
	tags = {"woodcutting", "wc", "bot", "script", "trees"},
	enabledByDefault = false
)
public class WoodcutterScriptPlugin extends Plugin
{
	private static final Set<Integer> WOODCUTTING_ANIMS = ImmutableSet.of(
		AnimationID.HUMAN_WOODCUTTING_BRONZE_AXE,
		AnimationID.HUMAN_WOODCUTTING_IRON_AXE,
		AnimationID.HUMAN_WOODCUTTING_STEEL_AXE,
		AnimationID.HUMAN_WOODCUTTING_BLACK_AXE,
		AnimationID.HUMAN_WOODCUTTING_MITHRIL_AXE,
		AnimationID.HUMAN_WOODCUTTING_ADAMANT_AXE,
		AnimationID.HUMAN_WOODCUTTING_RUNE_AXE,
		AnimationID.HUMAN_WOODCUTTING_GILDED_AXE,
		AnimationID.HUMAN_WOODCUTTING_DRAGON_AXE,
		AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_AXE_NO_INFERNAL,
		AnimationID.HUMAN_WOODCUTTING_INFERNAL_AXE,
		AnimationID.HUMAN_WOODCUTTING_3A_AXE,
		AnimationID.HUMAN_WOODCUTTING_CRYSTAL_AXE,
		AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_RELOADED_AXE_NO_INFERNAL,
		AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_AXE,
		AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_RELOADED_AXE,
		AnimationID.FORESTRY_2H_AXE_CHOPPING_BRONZE,
		AnimationID.FORESTRY_2H_AXE_CHOPPING_IRON,
		AnimationID.FORESTRY_2H_AXE_CHOPPING_STEEL,
		AnimationID.FORESTRY_2H_AXE_CHOPPING_BLACK,
		AnimationID.FORESTRY_2H_AXE_CHOPPING_MITHRIL,
		AnimationID.FORESTRY_2H_AXE_CHOPPING_ADAMANT,
		AnimationID.FORESTRY_2H_AXE_CHOPPING_RUNE,
		AnimationID.FORESTRY_2H_AXE_CHOPPING_DRAGON,
		AnimationID.FORESTRY_2H_AXE_CHOPPING_CRYSTAL,
		AnimationID.FORESTRY_2H_AXE_CHOPPING_CRYSTAL_INACTIVE,
		AnimationID.FORESTRY_2H_AXE_CHOPPING_3A
	);

	private static final Set<Integer> PROTECTED_AXE_IDS = ImmutableSet.of(
		ItemID.BRONZE_AXE,
		ItemID.IRON_AXE,
		ItemID.STEEL_AXE,
		ItemID.BLACK_AXE,
		ItemID.MITHRIL_AXE,
		ItemID.ADAMANT_AXE,
		ItemID.RUNE_AXE,
		ItemID.DRAGON_AXE,
		ItemID.INFERNAL_AXE,
		ItemID._3A_AXE,
		ItemID.TRAIL_GILDED_AXE,
		ItemID.CRYSTAL_AXE,
		ItemID.TRAILBLAZER_AXE,
		ItemID.TRAILBLAZER_RELOADED_AXE,
		ItemID.ZOMBIE_AXE
	);

	private static final Set<Integer> LOG_IDS = ImmutableSet.of(
		ItemID.LOGS,
		ItemID.OAK_LOGS,
		ItemID.WILLOW_LOGS,
		ItemID.MAPLE_LOGS,
		ItemID.YEW_LOGS,
		ItemID.MAGIC_LOGS,
		ItemID.REDWOOD_LOGS,
		ItemID.TEAK_LOGS,
		ItemID.MAHOGANY_LOGS
	);

	@Inject
	private Client client;

	@Inject
	private WoodcutterScriptConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private WoodcutterScriptOverlay overlay;

	@Inject
	private ClientThread clientThread;

	@Getter
	private WoodcutterScriptState currentState = WoodcutterScriptState.IDLE;

	@Getter
	private int logsCut = 0;

	@Getter
	private Instant startedAt;

	private final Set<GameObject> trees = new HashSet<>();
	private GameObject currentTreeTarget = null;
	private int cooldownTicks = 0;
	private int idleTicks = 0;
	private WorldPoint lastPlayerLocation = null;

	@Provides
	WoodcutterScriptConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(WoodcutterScriptConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(overlay);
		logsCut = 0;
		currentState = WoodcutterScriptState.IDLE;
		trees.clear();
		currentTreeTarget = null;
		cooldownTicks = 0;
		idleTicks = 0;

		if (config.startScript())
		{
			startedAt = Instant.now();
		}

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(this::scanScene);
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlay);
		trees.clear();
		currentTreeTarget = null;
		currentState = WoodcutterScriptState.IDLE;
		startedAt = null;
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
				startedAt = Instant.now();
				logsCut = 0;
				currentState = WoodcutterScriptState.IDLE;
				if (client.getGameState() == GameState.LOGGED_IN)
				{
					clientThread.invokeLater(this::scanScene);
				}
			}
			else
			{
				currentState = WoodcutterScriptState.IDLE;
				currentTreeTarget = null;
			}
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.SPAM
			&& event.getType() != ChatMessageType.GAMEMESSAGE
			&& event.getType() != ChatMessageType.MESBOX)
		{
			return;
		}

		String msg = event.getMessage();
		if (msg.startsWith("You get some ") || msg.startsWith("You get an ") || msg.startsWith("You get a "))
		{
			if (msg.contains("logs") || msg.contains("mushrooms") || msg.contains("bark"))
			{
				logsCut++;
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN || event.getGameState() == GameState.LOADING)
		{
			trees.clear();
			currentTreeTarget = null;
			clientThread.invokeLater(this::scanScene);
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			trees.clear();
			currentTreeTarget = null;
			currentState = WoodcutterScriptState.IDLE;
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		GameObject go = event.getGameObject();
		if (isChoppableTree(go))
		{
			trees.add(go);
		}
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		GameObject go = event.getGameObject();
		trees.remove(go);
		if (currentTreeTarget != null && currentTreeTarget.equals(go))
		{
			currentTreeTarget = null;
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (!config.startScript() || client.getGameState() != GameState.LOGGED_IN)
		{
			currentState = WoodcutterScriptState.IDLE;
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		if (cooldownTicks > 0)
		{
			cooldownTicks--;
			return;
		}

		// Tarkistetaan inventory
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		boolean isFull = inv != null && inv.count() >= 28;

		// 1. Käsitellään täysi reppu
		if (isFull)
		{
			currentTreeTarget = null;

			if (config.mode() == WoodcutterMode.DROP)
			{
				currentState = WoodcutterScriptState.DROPPING_LOGS;
				dropLogs(inv);
				return;
			}
			else if (config.mode() == WoodcutterMode.BANK)
			{
				handleBanking(localPlayer, inv);
				return;
			}
		}

		// 2. Hakkaustoiminta kun repussa on tilaa
		int anim = localPlayer.getAnimation();
		boolean isChoppingAnim = WOODCUTTING_ANIMS.contains(anim) || (anim != -1 && currentTreeTarget != null);

		// Tarkistetaan onko nykyinen kohdepuu elossa
		if (currentTreeTarget != null && !isTreeAlive(currentTreeTarget))
		{
			log.debug("Target tree died or turned into stump, finding new tree.");
			currentTreeTarget = null;
		}

		if (isChoppingAnim && currentTreeTarget != null)
		{
			currentState = WoodcutterScriptState.CHOPPING;
			idleTicks = 0;
			return;
		}

		// Jos pelaaja liikkuu kohti puuta
		WorldPoint playerLoc = localPlayer.getWorldLocation();
		if (playerLoc != null && playerLoc.equals(lastPlayerLocation))
		{
			idleTicks++;
		}
		else
		{
			idleTicks = 0;
			lastPlayerLocation = playerLoc;
		}

		// Jos meillä on kohdepuu ja pelaaja seisoo paikallaan eikä hakkaa
		if (currentTreeTarget != null && idleTicks >= 3)
		{
			LocalPoint lp = currentTreeTarget.getLocalLocation();
			if (lp != null && localPlayer.getLocalLocation().distanceTo(lp) <= 2 * Perspective.LOCAL_TILE_SIZE)
			{
				log.debug("Standing next to tree but not chopping, clicking again.");
				chopTree(currentTreeTarget);
				cooldownTicks = 2;
				idleTicks = 0;
				return;
			}
			else
			{
				// Stalled or unreachable tree
				currentTreeTarget = null;
			}
		}

		// Jos ei ole kohdepuuta tai se menetettiin, etsitään uusi
		if (currentTreeTarget == null)
		{
			currentState = WoodcutterScriptState.FINDING_TREE;
			GameObject bestTree = findBestTree(localPlayer);
			if (bestTree != null)
			{
				currentTreeTarget = bestTree;
				currentState = WoodcutterScriptState.MOVING_TO_TREE;
				chopTree(bestTree);
				cooldownTicks = 2;
				idleTicks = 0;
			}
		}
	}

	private void dropLogs(ItemContainer inv)
	{
		Item[] items = inv.getItems();
		int dropped = 0;

		for (int slot = 0; slot < items.length; slot++)
		{
			Item item = items[slot];
			if (item == null || item.getId() <= -1)
			{
				continue;
			}

			int itemId = item.getId();
			if (isProtectedItem(itemId))
			{
				continue;
			}

			if (isDroppableLog(itemId))
			{
				client.menuAction(
					slot,
					InterfaceID.Inventory.ITEMS,
					MenuAction.CC_OP_LOW_PRIORITY,
					7, // Drop option
					itemId,
					"Drop",
					""
				);
				dropped++;
				if (dropped >= 2) // Pudotetaan 2 per tick sulavasti
				{
					break;
				}
			}
		}

		cooldownTicks = 1;
	}

	private boolean isProtectedItem(int itemId)
	{
		if (PROTECTED_AXE_IDS.contains(itemId))
		{
			return true;
		}
		ItemComposition def = client.getItemDefinition(itemId);
		if (def != null && def.getName() != null)
		{
			String name = def.getName().toLowerCase();
			if (name.contains("axe") || name.contains("nest") || name.contains("clue") || name.contains("casket"))
			{
				return true;
			}
		}
		return false;
	}

	private boolean isDroppableLog(int itemId)
	{
		if (LOG_IDS.contains(itemId))
		{
			return true;
		}
		ItemComposition def = client.getItemDefinition(itemId);
		if (def != null && def.getName() != null)
		{
			String name = def.getName().toLowerCase();
			return name.endsWith("logs") || name.equals("logs") || name.equals("bark");
		}
		return false;
	}

	private void handleBanking(Player localPlayer, ItemContainer inv)
	{
		// Tarkistetaan onko pankkiwidget auki
		Widget depositInvWidget = client.getWidget(InterfaceID.Bankmain.DEPOSITINV);
		if (depositInvWidget != null && !depositInvWidget.isHidden())
		{
			currentState = WoodcutterScriptState.BANKING;
			client.menuAction(-1, InterfaceID.Bankmain.DEPOSITINV, MenuAction.CC_OP, 1, -1, "Deposit inventory", "");
			cooldownTicks = 2;
			return;
		}

		Widget depositBoxWidget = client.getWidget(InterfaceID.BankDepositbox.DEPOSIT_INV);
		if (depositBoxWidget != null && !depositBoxWidget.isHidden())
		{
			currentState = WoodcutterScriptState.BANKING;
			client.menuAction(-1, InterfaceID.BankDepositbox.DEPOSIT_INV, MenuAction.CC_OP, 1, -1, "Deposit inventory", "");
			cooldownTicks = 2;
			return;
		}

		// Etsitään lähin pankkikohde
		currentState = WoodcutterScriptState.WALKING_TO_BANK;
		GameObject bankObject = findNearestBankObject(localPlayer);
		if (bankObject != null)
		{
			LocalPoint lp = bankObject.getLocalLocation();
			if (lp != null)
			{
				client.menuAction(
					lp.getSceneX(),
					lp.getSceneY(),
					MenuAction.GAME_OBJECT_FIRST_OPTION,
					bankObject.getId(),
					0,
					"Bank",
					""
				);
				cooldownTicks = 3;
				return;
			}
		}

		NPC banker = findNearestBanker(localPlayer);
		if (banker != null)
		{
			client.menuAction(
				0,
				0,
				MenuAction.NPC_FIRST_OPTION,
				banker.getIndex(),
				0,
				"Bank",
				""
			);
			cooldownTicks = 3;
		}
	}

	private GameObject findNearestBankObject(Player localPlayer)
	{
		Tile[][][] tiles = client.getScene().getTiles();
		if (tiles == null)
		{
			return null;
		}

		LocalPoint playerLoc = localPlayer.getLocalLocation();
		int z = client.getPlane();
		GameObject bestBank = null;
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
					if (go == null)
					{
						continue;
					}

					ObjectComposition comp = client.getObjectDefinition(go.getId());
					if (comp == null)
					{
						continue;
					}
					if (comp.getImpostorIds() != null)
					{
						comp = comp.getImpostor();
						if (comp == null)
						{
							continue;
						}
					}

					String name = comp.getName();
					if (name == null)
					{
						continue;
					}

					String lower = name.toLowerCase();
					if (lower.contains("bank booth") || lower.contains("bank chest") || lower.contains("bank counter") || lower.contains("grand exchange booth"))
					{
						int dist = playerLoc.distanceTo(go.getLocalLocation());
						if (dist < bestDist)
						{
							bestDist = dist;
							bestBank = go;
						}
					}
				}
			}
		}

		return bestBank;
	}

	private NPC findNearestBanker(Player localPlayer)
	{
		LocalPoint playerLoc = localPlayer.getLocalLocation();
		NPC bestNpc = null;
		int bestDist = Integer.MAX_VALUE;

		for (NPC npc : client.getNpcs())
		{
			if (npc == null || npc.getName() == null)
			{
				continue;
			}
			if (npc.getName().toLowerCase().contains("banker"))
			{
				int dist = playerLoc.distanceTo(npc.getLocalLocation());
				if (dist < bestDist)
				{
					bestDist = dist;
					bestNpc = npc;
				}
			}
		}

		return bestNpc;
	}

	private GameObject findBestTree(Player localPlayer)
	{
		LocalPoint playerLoc = localPlayer.getLocalLocation();
		int maxDistTiles = config.maxDistance();
		int maxDistLocal = maxDistTiles * Perspective.LOCAL_TILE_SIZE;

		return trees.stream()
			.filter(this::isTreeAlive)
			.filter(t -> isTargetTreeType(t, config.treeType()))
			.filter(t -> playerLoc.distanceTo(t.getLocalLocation()) <= maxDistLocal)
			.min(Comparator.comparingInt(t -> playerLoc.distanceTo(t.getLocalLocation())))
			.orElse(null);
	}

	private void chopTree(GameObject tree)
	{
		LocalPoint lp = tree.getLocalLocation();
		if (lp == null)
		{
			return;
		}

		client.menuAction(
			lp.getSceneX(),
			lp.getSceneY(),
			MenuAction.GAME_OBJECT_FIRST_OPTION,
			tree.getId(),
			0,
			"Chop down",
			""
		);
	}

	private boolean isChoppableTree(GameObject go)
	{
		if (go == null)
		{
			return false;
		}

		ObjectComposition comp = client.getObjectDefinition(go.getId());
		if (comp == null)
		{
			return false;
		}
		if (comp.getImpostorIds() != null)
		{
			comp = comp.getImpostor();
			if (comp == null)
			{
				return false;
			}
		}

		String name = comp.getName();
		if (name == null || name.equalsIgnoreCase("null") || name.isEmpty())
		{
			return false;
		}

		if (name.toLowerCase().contains("stump"))
		{
			return false;
		}

		String[] actions = comp.getActions();
		if (actions == null)
		{
			return false;
		}

		for (String action : actions)
		{
			if (action != null && (action.equalsIgnoreCase("Chop down") || action.equalsIgnoreCase("Chop")))
			{
				return true;
			}
		}

		return false;
	}

	private boolean isTargetTreeType(GameObject go, WoodcutterTreeType type)
	{
		if (go == null)
		{
			return false;
		}

		ObjectComposition comp = client.getObjectDefinition(go.getId());
		if (comp == null)
		{
			return false;
		}
		if (comp.getImpostorIds() != null)
		{
			comp = comp.getImpostor();
			if (comp == null)
			{
				return false;
			}
		}

		String name = comp.getName();
		return type.matches(name);
	}

	private boolean isTreeAlive(GameObject go)
	{
		return isChoppableTree(go);
	}

	private void scanScene()
	{
		trees.clear();
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

				GameObject[] gameObjects = tile.getGameObjects();
				if (gameObjects != null)
				{
					for (GameObject go : gameObjects)
					{
						if (go != null && isChoppableTree(go))
						{
							trees.add(go);
						}
					}
				}
			}
		}
	}
}
