/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.bluedragonscript;

import com.google.inject.Provides;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
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
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
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
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.ParamID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
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
	name = "Blue Dragon Script",
	description = "Teleports to Falador, banks supplies, runs to Taverley dungeon, drinks potions, fights Blue Dragons and repeats",
	tags = {"magic", "teleport", "falador", "bank", "agility", "wall", "ladder", "taverley", "pipe", "dragon", "combat"}
)
public class BlueDragonScriptPlugin extends Plugin
{
	private static final WorldPoint FALADOR_CENTER = new WorldPoint(2964, 3378, 0);
	private static final WorldPoint FALADOR_WEST_BANK = new WorldPoint(2945, 3369, 0);
	private static final WorldPoint CRUMBLING_WALL_TILE = new WorldPoint(2936, 3355, 0);
	private static final int TREE_ID = 1286;
	private static final WorldPoint TAVERLEY_LADDER_WAYPOINT = new WorldPoint(2905, 3382, 0);
	private static final WorldPoint TAVERLEY_LADDER_OUTSIDE = new WorldPoint(2884, 3397, 0);
	private static final WorldPoint TAVERLEY_PIPE_TILE = new WorldPoint(2888, 9799, 0);

	private static final int BANK_BOOTH_ID = 24101;
	private static final int CRUMBLING_WALL_ID = 24222;
	private static final int TAVERLEY_LADDER_DOWN_ID = 16680;
	private static final int TAVERLEY_LADDER_UP_ID = 17385;
	private static final int OBSTACLE_PIPE_ID = 16509;

	// Item IDs
	public static final int ID_SUPER_COMBAT_4 = 12695;
	public static final int ID_ANTIFIRE_4 = 2452;
	public static final int ID_SHARK = 385;
	public static final int ID_DRAGON_BONES = ItemID.DRAGON_BONES; // 536
	public static final int ID_BLUE_DRAGONHIDE = ItemID.BLUE_DRAGONHIDE; // 1751

	public static final Set<Integer> SUPER_COMBAT_IDS = new HashSet<>(Arrays.asList(
		ItemID.SUPER_COMBAT_POTION4, // 12695
		ItemID.SUPER_COMBAT_POTION3, // 12697
		ItemID.SUPER_COMBAT_POTION2, // 12699
		ItemID.SUPER_COMBAT_POTION1, // 12701
		ItemID.DIVINE_SUPER_COMBAT_POTION4, // 23685
		ItemID.DIVINE_SUPER_COMBAT_POTION3, // 23688
		ItemID.DIVINE_SUPER_COMBAT_POTION2, // 23691
		ItemID.DIVINE_SUPER_COMBAT_POTION1  // 23694
	));

	public static final Set<Integer> SUPER_ATTACK_IDS = new HashSet<>(Arrays.asList(
		ItemID.SUPER_ATTACK4, // 2436
		ItemID.SUPER_ATTACK3, // 145
		ItemID.SUPER_ATTACK2, // 147
		ItemID.SUPER_ATTACK1, // 149
		ItemID.DIVINE_SUPER_ATTACK_POTION4, // 23697
		ItemID.DIVINE_SUPER_ATTACK_POTION3, // 23700
		ItemID.DIVINE_SUPER_ATTACK_POTION2, // 23703
		ItemID.DIVINE_SUPER_ATTACK_POTION1  // 23706
	));

	public static final Set<Integer> SUPER_STRENGTH_IDS = new HashSet<>(Arrays.asList(
		ItemID.SUPER_STRENGTH4, // 2440
		ItemID.SUPER_STRENGTH3, // 157
		ItemID.SUPER_STRENGTH2, // 159
		ItemID.SUPER_STRENGTH1, // 161
		ItemID.DIVINE_SUPER_STRENGTH_POTION4, // 23709
		ItemID.DIVINE_SUPER_STRENGTH_POTION3, // 23712
		ItemID.DIVINE_SUPER_STRENGTH_POTION2, // 23715
		ItemID.DIVINE_SUPER_STRENGTH_POTION1  // 23718
	));

	public static final Set<Integer> ANTIFIRE_IDS = new HashSet<>(Arrays.asList(
		ItemID.ANTIFIRE_POTION4, // 2452
		ItemID.ANTIFIRE_POTION3, // 2454
		ItemID.ANTIFIRE_POTION2, // 2456
		ItemID.ANTIFIRE_POTION1, // 2458
		ItemID.EXTENDED_ANTIFIRE4, // 11951
		ItemID.EXTENDED_ANTIFIRE3, // 11953
		ItemID.EXTENDED_ANTIFIRE2, // 11955
		ItemID.EXTENDED_ANTIFIRE1, // 11957
		ItemID.SUPER_ANTIFIRE_POTION4, // 21978
		ItemID.SUPER_ANTIFIRE_POTION3, // 21981
		ItemID.SUPER_ANTIFIRE_POTION2, // 21984
		ItemID.SUPER_ANTIFIRE_POTION1, // 21987
		ItemID.EXTENDED_SUPER_ANTIFIRE4, // 22209
		ItemID.EXTENDED_SUPER_ANTIFIRE3, // 22212
		ItemID.EXTENDED_SUPER_ANTIFIRE2, // 22215
		ItemID.EXTENDED_SUPER_ANTIFIRE1  // 22218
	));

	public static final Set<Integer> SHARK_IDS = new HashSet<>(Arrays.asList(
		ItemID.SHARK, // 385
		ItemID.MANTA_RAY, // 391
		ItemID.TUNA_POTATO, // 7060
		ItemID.POTATO_WITH_CHEESE, // 6705
		ItemID.ANGLERFISH, // 13441
		ItemID.DARK_CRAB, // 11936
		ItemID.SEA_TURTLE, // 397
		ItemID.MONKFISH, // 7946
		ItemID.COOKED_KARAMBWAN, // 3144
		ItemID.SWORDFISH, // 373
		ItemID.LOBSTER, // 379
		ItemID.BASS // 365
	));

	public static final Set<Integer> VIAL_IDS = new HashSet<>(Arrays.asList(
		ItemID.VIAL,
		ItemID.EMPTY_VIAL
	));

	// Items to automatically deposit when opening the bank (potions with doses are kept!)
	private static final Set<Integer> LOOT_AND_TRASH_IDS = new HashSet<>(Arrays.asList(
		ItemID.DRAGON_BONES,
		ItemID.BLUE_DRAGONHIDE,
		ItemID.VIAL,
		ItemID.EMPTY_VIAL
	));

	// Elemental staves providing unlimited Water runes
	private static final Set<Integer> WATER_STAVES = new HashSet<>(Arrays.asList(
		ItemID.STAFF_OF_WATER, ItemID.WATER_BATTLESTAFF, ItemID.MYSTIC_WATER_STAFF,
		ItemID.MUD_BATTLESTAFF, ItemID.MYSTIC_MUD_STAFF,
		ItemID.STEAM_BATTLESTAFF, ItemID.MYSTIC_STEAM_STAFF,
		ItemID.TOME_OF_WATER, ItemID.KODAI_WAND
	));

	// Elemental staves providing unlimited Air runes
	private static final Set<Integer> AIR_STAVES = new HashSet<>(Arrays.asList(
		ItemID.STAFF_OF_AIR, ItemID.AIR_BATTLESTAFF, ItemID.MYSTIC_AIR_STAFF,
		ItemID.DUST_BATTLESTAFF, ItemID.MYSTIC_DUST_STAFF,
		ItemID.MIST_BATTLESTAFF, ItemID.MYSTIC_MIST_STAFF,
		ItemID.SMOKE_BATTLESTAFF, ItemID.MYSTIC_SMOKE_STAFF
	));

	private enum TeleportState
	{
		IDLE,
		OPENING_SPELLBOOK,
		WAITING_SPELLBOOK,
		CASTING,
		WAITING_ARRIVAL,
		CLICKING_BANK,
		RESTOCKING,
		HEALING_AT_BANK,
		CLOSING_BANK,
		CLIMBING_WALL,
		CLICKING_TREE,
		CLIMBING_LADDER,
		SQUEEZING_PIPE,
		DRINKING_COMBAT_BOOST,
		DRINKING_STRENGTH_BOOST,
		DRINKING_ANTIFIRE,
		FIGHTING_DRAGONS,
		COMPLETE
	}

	@Inject
	private Client client;

	@Inject
	private BlueDragonScriptConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BlueDragonScriptOverlay overlay;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Getter
	private String status = "READY";

	@Getter
	private Color statusColor = Color.GREEN;

	@Getter
	private int teleportCount = 0;

	@Getter
	private int magicLevel = 1;

	@Getter
	private int lawRunes = 0;

	@Getter
	private int waterRunes = 0;

	@Getter
	private int airRunes = 0;

	@Getter
	private int currentSuperCombat = 0;

	@Getter
	private int currentSuperAttack = 0;

	@Getter
	private int currentSuperStrength = 0;

	@Getter
	private int currentAntifire = 0;

	public String getBoostPotionStatus()
	{
		if (currentSuperCombat > 0)
		{
			return "Super combat (" + currentSuperCombat + ")";
		}
		if (currentSuperAttack > 0 || currentSuperStrength > 0)
		{
			return "Atk: " + currentSuperAttack + " | Str: " + currentSuperStrength;
		}
		return "0 / 1";
	}

	@Getter
	private int currentShark = 0;

	@Getter
	private int currentHp = 10;

	@Getter
	private int maxHp = 10;

	@Getter
	private int bonesLooted = 0;

	@Getter
	private int hidesLooted = 0;

	@Getter
	private int tripsCompleted = 0;

	private boolean hasWaterSource = false;
	private boolean hasAirSource = false;

	public boolean hasWaterSource() { return hasWaterSource; }
	public boolean hasAirSource() { return hasAirSource; }

	private TeleportState state = TeleportState.IDLE;
	private int cooldownTicks = 0;
	private int openAttempts = 0;
	private int spellbookWaitTicks = 0;
	private int bankClickAttempts = 0;
	private int wallClickAttempts = 0;
	private int ladderClickAttempts = 0;
	private int pipeClickAttempts = 0;
	private int deadDragonWaitTicks = 0;
	private boolean potionDrunk = false;
	private LocalPoint previousPlayerLoc = null;

	private boolean superCombatMissingReported = false;
	private boolean antifireMissingReported = false;
	private boolean sharkMissingReported = false;
	private int bankHealPasses = 0;

	private static class GroundItemTarget
	{
		final TileItem item;
		final Tile tile;

		GroundItemTarget(TileItem item, Tile tile)
		{
			this.item = item;
			this.tile = tile;
		}
	}

	@Provides
	BlueDragonScriptConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BlueDragonScriptConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		status = "READY";
		statusColor = Color.GREEN;
		cooldownTicks = 0;
		openAttempts = 0;
		spellbookWaitTicks = 0;
		bankClickAttempts = 0;
		wallClickAttempts = 0;
		ladderClickAttempts = 0;
		pipeClickAttempts = 0;
		deadDragonWaitTicks = 0;
		potionDrunk = false;
		previousPlayerLoc = null;
		bonesLooted = 0;
		hidesLooted = 0;
		tripsCompleted = 0;
		resetMissingFlags();

		if (config.startTeleport())
		{
			clientThread.invokeLater(this::triggerStart);
		}
		else
		{
			state = TeleportState.IDLE;
		}

		log.info("Falador Teleport plugin started (initial state: {}).", state);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		state = TeleportState.IDLE;
		status = "STOPPED";
		statusColor = Color.RED;
		log.info("Falador Teleport plugin stopped.");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			state = TeleportState.IDLE;
			status = "NOT LOGGED IN";
			statusColor = Color.RED;
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		// Intercept and block accidental clicks on ladder up 17385 when underground
		if (state == TeleportState.SQUEEZING_PIPE || state == TeleportState.DRINKING_COMBAT_BOOST
			|| state == TeleportState.DRINKING_STRENGTH_BOOST
			|| state == TeleportState.DRINKING_ANTIFIRE || state == TeleportState.FIGHTING_DRAGONS)
		{
			if (event.getId() == TAVERLEY_LADDER_UP_ID)
			{
				log.warn("Blocked unwanted interaction with Ladder Up 17385!");
				event.consume();
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("bluedragonscript"))
		{
			return;
		}

		if (event.getKey().equals("startTeleport"))
		{
			if (config.startTeleport())
			{
				log.info("Process triggered via config toggle.");
				clientThread.invokeLater(this::triggerStart);
			}
			else
			{
				if (state != TeleportState.WAITING_ARRIVAL && state != TeleportState.CLICKING_BANK
					&& state != TeleportState.RESTOCKING && state != TeleportState.HEALING_AT_BANK
					&& state != TeleportState.CLOSING_BANK && state != TeleportState.CLIMBING_WALL
					&& state != TeleportState.CLICKING_TREE && state != TeleportState.CLIMBING_LADDER
					&& state != TeleportState.SQUEEZING_PIPE && state != TeleportState.DRINKING_COMBAT_BOOST
					&& state != TeleportState.DRINKING_STRENGTH_BOOST
					&& state != TeleportState.DRINKING_ANTIFIRE && state != TeleportState.FIGHTING_DRAGONS)
				{
					state = TeleportState.IDLE;
					status = "READY";
					statusColor = Color.GREEN;
				}
			}
		}
	}

	private void resetMissingFlags()
	{
		superCombatMissingReported = false;
		antifireMissingReported = false;
		sharkMissingReported = false;
		bankHealPasses = 0;
	}

	private void startTeleportCycle()
	{
		tripsCompleted++;
		deadDragonWaitTicks = 0;
		resetMissingFlags();
		state = TeleportState.OPENING_SPELLBOOK;
		openAttempts = 0;
		spellbookWaitTicks = 0;
		cooldownTicks = 1;
		status = "TELEPORTING (FALADOR)";
		statusColor = Color.YELLOW;
		openMagicTab();
	}

	private void triggerStart()
	{
		resetMissingFlags();
		Player localPlayer = client.getLocalPlayer();
		WorldPoint loc = localPlayer != null ? localPlayer.getWorldLocation() : null;

		// 1. If already in dragon lair past obstacle pipe (Y >= 9000 and X >= 2890)
		if (loc != null && loc.getY() >= 9000 && loc.getX() >= 2890)
		{
			log.info("Already in Blue Dragon lair at {}! Starting potion & combat routine.", loc);
			sendGameMessage("Already in Blue Dragon lair! Drinking potions...");
			state = TeleportState.DRINKING_COMBAT_BOOST;
			status = "DRINKING BOOST POTIONS";
			statusColor = Color.YELLOW;
			potionDrunk = false;
			cooldownTicks = 1;
			return;
		}

		// 2. If in Taverley dungeon near ladder before obstacle pipe (Y >= 9000 and X < 2890)
		if (loc != null && loc.getY() >= 9000)
		{
			log.info("In Taverley dungeon near ladder at {}! Squeezing through Obstacle Pipe 16509.", loc);
			sendGameMessage("In dungeon! Squeezing through Obstacle Pipe 16509...");
			state = TeleportState.SQUEEZING_PIPE;
			pipeClickAttempts = 0;
			status = "SQUEEZING PIPE";
			statusColor = Color.CYAN;
			clickObstaclePipeDirectly();
			cooldownTicks = 3;
			return;
		}

		// 3. If already west of crumbling wall on surface
		if (loc != null && loc.getX() <= 2935 && loc.getY() >= 3345 && loc.getY() <= 3410)
		{
			TileObject ladder = findLadder();
			if (ladder != null || loc.getX() <= 2910 || loc.distanceTo(TAVERLEY_LADDER_OUTSIDE) <= 25)
			{
				log.info("Already near ladder at {}! Clicking ladder 16680 directly.", loc);
				state = TeleportState.CLIMBING_LADDER;
				ladderClickAttempts = 0;
				status = "CLIMBING LADDER";
				statusColor = Color.CYAN;
				clickLadderDirectly();
				cooldownTicks = 3;
				return;
			}

			log.info("Already west of crumbling wall at {}! Clicking Tree 1286 towards ladder...", loc);
			state = TeleportState.CLICKING_TREE;
			status = "CLICKING TREE 1286";
			statusColor = Color.CYAN;
			clickTreeDirectly();
			cooldownTicks = 3;
			return;
		}

		// 4. If bank is open
		if (isBankOpen())
		{
			log.info("Bank is already open, proceeding directly to restock.");
			state = TeleportState.RESTOCKING;
			status = "RESTOCKING";
			statusColor = Color.YELLOW;
			cooldownTicks = 1;
			return;
		}

		// 5. If already in Falador
		if (isInFalador(loc))
		{
			log.info("Already in Falador at {}! Directly clicking bank booth 24101.", loc);
			state = TeleportState.CLICKING_BANK;
			bankClickAttempts = 0;
			status = "CLICKING BANK";
			statusColor = Color.CYAN;
			clickBankBoothDirectly();
			cooldownTicks = 3;
		}
		else
		{
			log.info("Outside Falador, opening spellbook to teleport...");
			state = TeleportState.OPENING_SPELLBOOK;
			openAttempts = 0;
			spellbookWaitTicks = 0;
			status = "STARTING";
			statusColor = Color.YELLOW;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		updateRequirements();
		handleRunEnergy();

		if (cooldownTicks > 0)
		{
			cooldownTicks--;
		}

		if (spellbookWaitTicks > 0)
		{
			spellbookWaitTicks--;
		}

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		WorldPoint loc = localPlayer.getWorldLocation();
		LocalPoint currentLoc = localPlayer.getLocalLocation();
		boolean isMoving = previousPlayerLoc != null && !previousPlayerLoc.equals(currentLoc);
		previousPlayerLoc = currentLoc;

		switch (state)
		{
			case IDLE:
				if (config.startTeleport())
				{
					triggerStart();
				}
				else
				{
					if (loc.getY() >= 9000 && loc.getX() >= 2890)
					{
						status = "IN DRAGON LAIR";
						statusColor = Color.GREEN;
					}
					else if (isBankOpen())
					{
						status = "BANK OPEN";
						statusColor = Color.GREEN;
					}
					else if (isInFalador(loc))
					{
						status = "IN FALADOR";
						statusColor = Color.CYAN;
					}
					else
					{
						status = "READY";
						statusColor = Color.GREEN;
					}
				}
				break;

			case OPENING_SPELLBOOK:
				if (isSpellbookOpen())
				{
					log.info("Spellbook is open, proceeding to cast.");
					state = TeleportState.CASTING;
					status = "CASTING";
					statusColor = Color.YELLOW;
					performCast();
				}
				else
				{
					openAttempts++;
					status = "OPENING SPELLBOOK (F3)";
					statusColor = Color.YELLOW;
					log.info("Opening spellbook using F3 and tab click (attempt {})...", openAttempts);
					openMagicTab();

					if (openAttempts >= 6)
					{
						log.warn("Failed to open spellbook after 6 attempts.");
						sendGameMessage("Could not open spellbook! Please open it manually.");
						state = TeleportState.IDLE;
						configManager.setConfiguration("bluedragonscript", "startTeleport", false);
					}
					else
					{
						spellbookWaitTicks = 1;
						state = TeleportState.WAITING_SPELLBOOK;
					}
				}
				break;

			case WAITING_SPELLBOOK:
				if (isSpellbookOpen())
				{
					log.info("Spellbook opened successfully!");
					state = TeleportState.CASTING;
					status = "CASTING";
					statusColor = Color.YELLOW;
					performCast();
				}
				else if (spellbookWaitTicks <= 0)
				{
					state = TeleportState.OPENING_SPELLBOOK;
				}
				break;

			case CASTING:
				if (!isSpellbookOpen())
				{
					log.info("Spellbook not visible during CASTING, switching to OPENING_SPELLBOOK.");
					state = TeleportState.OPENING_SPELLBOOK;
					return;
				}

				performCast();
				break;

			case WAITING_ARRIVAL:
				if (isInFalador(loc))
				{
					log.info("Arrived safely in Falador at {}! Directly clicking bank booth 24101 from teleport spot.", loc);
					sendGameMessage("Arrived in Falador! Clicking Bank booth...");
					state = TeleportState.CLICKING_BANK;
					bankClickAttempts = 0;
					status = "CLICKING BANK";
					statusColor = Color.CYAN;
					clickBankBoothDirectly();
					cooldownTicks = 3;
				}
				else if (cooldownTicks <= 0)
				{
					state = TeleportState.IDLE;
					status = "TELEPORT TIMEOUT";
					statusColor = Color.ORANGE;
					log.warn("Teleport arrival timed out.");
					configManager.setConfiguration("bluedragonscript", "startTeleport", false);
				}
				break;

			case CLICKING_BANK:
				if (isBankOpen())
				{
					state = TeleportState.RESTOCKING;
					status = "RESTOCKING";
					statusColor = Color.YELLOW;
					log.info("Bank booth opened successfully! Starting restock sequence.");
					cooldownTicks = 2;
					return;
				}

				if (isMoving)
				{
					status = "RUNNING TO BANK";
					statusColor = Color.CYAN;
					return;
				}

				if (cooldownTicks <= 0)
				{
					bankClickAttempts++;
					if (bankClickAttempts >= 8)
					{
						log.warn("Failed to open bank booth after 8 attempts.");
						sendGameMessage("Could not reach bank booth!");
						state = TeleportState.IDLE;
						configManager.setConfiguration("bluedragonscript", "startTeleport", false);
						return;
					}

					log.info("Clicking Bank booth 24101 (attempt {})...", bankClickAttempts);
					status = "CLICKING BANK";
					statusColor = Color.YELLOW;
					clickBankBoothDirectly();
					cooldownTicks = 3;
				}
				break;

			case RESTOCKING:
				if (!isBankOpen())
				{
					log.info("Bank closed during restock, re-opening bank booth...");
					state = TeleportState.CLICKING_BANK;
					return;
				}

				if (cooldownTicks > 0)
				{
					return;
				}

				handleRestock();
				break;

			case HEALING_AT_BANK:
				if (isBankOpen())
				{
					log.info("Closing bank before eating...");
					closeBank();
					cooldownTicks = 2;
					return;
				}

				if (cooldownTicks > 0)
				{
					return;
				}

				// Still need healing and have food
				if (currentHp < maxHp && currentShark > 0)
				{
					status = "HEALING (" + currentHp + "/" + maxHp + ")";
					statusColor = Color.YELLOW;
					if (!eatFood())
					{
						log.warn("eatFood() failed in HEALING_AT_BANK");
						break;
					}
					cooldownTicks = 3;
					return;
				}

				// Healed to full HP or out of food
				if (currentHp >= maxHp)
				{
					log.info("Successfully healed to full HP ({}/{})!", currentHp, maxHp);
					sendGameMessage(String.format("Full HP reached (%d/%d)!", currentHp, maxHp));
				}
				else
				{
					log.warn("Stopped bank healing: no food left in inventory (HP: {}/{})", currentHp, maxHp);
				}

				// Top off food from bank if we ate some and pass limit not exceeded
				if (currentShark < config.sharkQuantity() && bankHealPasses < 2)
				{
					bankHealPasses++;
					log.info("Topping off food ({}/{}) at bank after eating...", currentShark, config.sharkQuantity());
					sendGameMessage(String.format("Topping off food (%d/%d) at bank...", currentShark, config.sharkQuantity()));
					state = TeleportState.CLICKING_BANK;
					bankClickAttempts = 0;
					status = "CLICKING BANK";
					statusColor = Color.CYAN;
					clickBankBoothDirectly();
					cooldownTicks = 3;
					return;
				}

				// Heading out to crumbling wall!
				log.info("Bank healing and restock done! Directly clicking Crumbling Wall 24222...");
				state = TeleportState.CLIMBING_WALL;
				wallClickAttempts = 0;
				bankHealPasses = 0;
				cooldownTicks = 0;
				clickCrumblingWallDirectly();
				break;

			case CLOSING_BANK:
				if (isBankOpen())
				{
					log.info("Closing bank...");
					closeBank();
					cooldownTicks = 2;
					return;
				}

				log.info("Bank closed successfully! Directly clicking Crumbling Wall 24222...");
				state = TeleportState.CLIMBING_WALL;
				wallClickAttempts = 0;
				bankHealPasses = 0;
				cooldownTicks = 0;
				break;

			case CLIMBING_WALL:
				if (isBankOpen())
				{
					closeBank();
					cooldownTicks = 2;
					return;
				}

				// Check if player has hopped over crumbling wall into west side (X <= 2935)
				if (loc.getX() <= 2935 && loc.getY() >= 3345 && loc.getY() <= 3375)
				{
					log.info("Successfully hopped over Crumbling Wall! Clicking Tree 1286 to head towards ladder...");
					sendGameMessage("Wall climbed! Clicking Tree 1286...");
					state = TeleportState.CLICKING_TREE;
					status = "CLICKING TREE 1286";
					statusColor = Color.CYAN;
					clickTreeDirectly();
					cooldownTicks = 3;
					return;
				}

				int animWall = localPlayer.getAnimation();
				if (animWall != -1 || isMoving)
				{
					status = "CLIMBING CRUMBLING WALL";
					statusColor = Color.CYAN;
					return;
				}

				if (cooldownTicks <= 0)
				{
					wallClickAttempts++;
					if (wallClickAttempts >= 8)
					{
						log.warn("Failed to reach/climb Crumbling wall after 8 attempts.");
						sendGameMessage("Could not climb Crumbling Wall!");
						state = TeleportState.IDLE;
						configManager.setConfiguration("bluedragonscript", "startTeleport", false);
						return;
					}

					clickCrumblingWallDirectly();
					cooldownTicks = 4;
				}
				break;

			case CLICKING_TREE:
				// If ladder is now loaded in scene or we are close to ladder (X <= 2910)
				TileObject ladder = findLadder();
				if (ladder != null || loc.getX() <= 2910 || loc.distanceTo(TAVERLEY_LADDER_OUTSIDE) <= 25)
				{
					log.info("Ladder 16680 is within reach! Proceeding to ladder...");
					sendGameMessage("Near ladder! Clicking Ladder 16680...");
					state = TeleportState.CLIMBING_LADDER;
					ladderClickAttempts = 0;
					cooldownTicks = 0;
					clickLadderDirectly();
					return;
				}

				if (isMoving)
				{
					status = "RUNNING TO TREE 1286";
					statusColor = Color.CYAN;
					return;
				}

				if (cooldownTicks <= 0)
				{
					log.info("Clicking Tree 1286...");
					clickTreeDirectly();
					cooldownTicks = 3;
				}
				break;

			case CLIMBING_LADDER:
				// Check if player has arrived underground in Taverley dungeon (Y >= 9000)
				if (loc.getY() >= 9000)
				{
					int anim = localPlayer.getAnimation();
					// Wait until ladder climbing animation finishes completely
					if (anim != -1)
					{
						status = "CLIMBING LADDER";
						statusColor = Color.CYAN;
						return;
					}

					log.info("Successfully climbed down ladder into dungeon at {}!", loc);
					sendGameMessage("Arrived in Taverley Dungeon! Squeezing through Obstacle Pipe 16509...");
					state = TeleportState.SQUEEZING_PIPE;
					pipeClickAttempts = 0;
					cooldownTicks = 2; // give 2 ticks buffer before pipe click
					return;
				}

				int animLadder = localPlayer.getAnimation();
				if (animLadder != -1)
				{
					status = "CLIMBING LADDER";
					statusColor = Color.CYAN;
					return;
				}

				if (isMoving)
				{
					status = "RUNNING TO LADDER";
					statusColor = Color.CYAN;
					return;
				}

				if (cooldownTicks <= 0)
				{
					ladderClickAttempts++;
					if (ladderClickAttempts >= 12)
					{
						log.warn("Failed to reach/climb ladder after 12 attempts.");
						sendGameMessage("Could not reach ladder!");
						state = TeleportState.IDLE;
						configManager.setConfiguration("bluedragonscript", "startTeleport", false);
						return;
					}

					log.info("Clicking ladder 16680 (attempt {})...", ladderClickAttempts);
					clickLadderDirectly();
					cooldownTicks = 4;
				}
				break;

			case SQUEEZING_PIPE:
				// Check if player has squeezed through the pipe (Y >= 9000 and X >= 2890)
				if (loc.getY() >= 9000 && loc.getX() >= 2890)
				{
					int anim = localPlayer.getAnimation();
					if (anim != -1)
					{
						status = "SQUEEZING THROUGH PIPE";
						statusColor = Color.CYAN;
						return;
					}

					log.info("Successfully squeezed through Obstacle Pipe 16509 into Dragon lair at {}!", loc);
					sendGameMessage("Passed Obstacle Pipe 16509! Drinking boost potions...");
					state = TeleportState.DRINKING_COMBAT_BOOST;
					status = "DRINKING BOOST POTIONS";
					statusColor = Color.YELLOW;
					potionDrunk = false;
					cooldownTicks = 1;
					return;
				}

				int animPipe = localPlayer.getAnimation();
				if (animPipe != -1 || isMoving)
				{
					status = "SQUEEZING THROUGH PIPE";
					statusColor = Color.CYAN;
					return;
				}

				if (cooldownTicks <= 0)
				{
					pipeClickAttempts++;
					if (pipeClickAttempts >= 8)
					{
						log.warn("Failed to reach/squeeze obstacle pipe 16509 after 8 attempts.");
						sendGameMessage("Could not squeeze through Obstacle Pipe 16509!");
						state = TeleportState.IDLE;
						configManager.setConfiguration("bluedragonscript", "startTeleport", false);
						return;
					}

					log.info("Clicking Obstacle Pipe 16509 (attempt {})...", pipeClickAttempts);
					clickObstaclePipeDirectly();
					cooldownTicks = 4;
				}
				break;

			case DRINKING_COMBAT_BOOST:
				if (cooldownTicks > 0)
				{
					return;
				}

				if (!potionDrunk)
				{
					// 1. If player has Super Combat (any dose 1-4):
					if (findItemSlotInInventory(SUPER_COMBAT_IDS) != -1)
					{
						log.info("Drinking 1 dose of Super combat potion...");
						status = "DRINKING SUPER COMBAT";
						statusColor = Color.YELLOW;
						drinkPotion(SUPER_COMBAT_IDS, "Super combat potion");
						potionDrunk = true;
						cooldownTicks = 2;
						return;
					}

					// 2. If player has Super Attack (any dose 1-4):
					if (findItemSlotInInventory(SUPER_ATTACK_IDS) != -1)
					{
						log.info("Drinking 1 dose of Super attack potion...");
						status = "DRINKING SUPER ATTACK";
						statusColor = Color.YELLOW;
						drinkPotion(SUPER_ATTACK_IDS, "Super attack potion");
						potionDrunk = true;
						cooldownTicks = 2;
						return;
					}
				}

				// Check if Super strength is present in inventory
				if (findItemSlotInInventory(SUPER_STRENGTH_IDS) != -1 && findItemSlotInInventory(SUPER_COMBAT_IDS) == -1)
				{
					log.info("Now drinking Super strength potion...");
					state = TeleportState.DRINKING_STRENGTH_BOOST;
					status = "DRINKING SUPER STRENGTH";
					statusColor = Color.YELLOW;
					potionDrunk = false;
					cooldownTicks = 1;
					break;
				}

				log.info("Combat boost consumed, now checking Antifire potion...");
				sendGameMessage("Drinking Antifire potion...");
				state = TeleportState.DRINKING_ANTIFIRE;
				status = "DRINKING ANTIFIRE";
				statusColor = Color.YELLOW;
				potionDrunk = false;
				cooldownTicks = 1;
				break;

			case DRINKING_STRENGTH_BOOST:
				if (cooldownTicks > 0)
				{
					return;
				}

				if (!potionDrunk)
				{
					if (findItemSlotInInventory(SUPER_STRENGTH_IDS) != -1)
					{
						log.info("Drinking 1 dose of Super strength potion...");
						status = "DRINKING SUPER STRENGTH";
						statusColor = Color.YELLOW;
						drinkPotion(SUPER_STRENGTH_IDS, "Super strength potion");
						potionDrunk = true;
						cooldownTicks = 2;
						return;
					}
				}

				log.info("Super strength consumed, now checking Antifire potion...");
				sendGameMessage("Drinking Antifire potion...");
				state = TeleportState.DRINKING_ANTIFIRE;
				status = "DRINKING ANTIFIRE";
				statusColor = Color.YELLOW;
				potionDrunk = false;
				cooldownTicks = 1;
				break;

			case DRINKING_ANTIFIRE:
				if (cooldownTicks > 0)
				{
					return;
				}

				if (!potionDrunk)
				{
					if (findItemSlotInInventory(ANTIFIRE_IDS) != -1)
					{
						log.info("Drinking 1 dose of Antifire potion...");
						status = "DRINKING ANTIFIRE";
						statusColor = Color.YELLOW;
						drinkPotion(ANTIFIRE_IDS, "Antifire potion");
						potionDrunk = true;
						cooldownTicks = 2;
						return;
					}
				}

				log.info("Potions consumed! Engaging Blue Dragons...");
				sendGameMessage("Potions consumed! Hunting Blue Dragons...");
				state = TeleportState.FIGHTING_DRAGONS;
				status = "HUNTING DRAGONS";
				statusColor = Color.GREEN;
				cooldownTicks = 1;
				break;

			case FIGHTING_DRAGONS:
				// 1. Survival health check: Eat food when HP drops below eatHpPercent
				if (currentHp <= (maxHp * config.eatHpPercent() / 100))
				{
					if (eatFood())
					{
						cooldownTicks = 3;
						return;
					}
				}

				if (cooldownTicks > 0)
				{
					return;
				}

				// 2. Check for ground loot (Dragon bones, Blue dragonhide)
				GroundItemTarget loot = findNearestLoot();
				boolean isInvFull = getInventoryFilledCount() >= 28;

				if (loot != null)
				{
					deadDragonWaitTicks = 0; // Found loot, cancel wait
					if (isInvFull)
					{
						// Inventory full! Free up a slot by dropping empty vials or eating food
						boolean freed = makeSpaceForLoot();
						if (freed)
						{
							status = "MAKING SPACE FOR LOOT";
							statusColor = Color.YELLOW;
							cooldownTicks = 3;
							return;
						}
					}
					else
					{
						takeGroundItem(loot);
						cooldownTicks = 3;
						return;
					}
				}

				// Check if currently interacting with a Blue dragon to track death
				Actor interacting = localPlayer.getInteracting();
				NPC currentTarget = (interacting instanceof NPC && isBlueDragon((NPC) interacting)) ? (NPC) interacting : null;
				if (currentTarget != null && currentTarget.isDead())
				{
					if (deadDragonWaitTicks <= 0)
					{
						deadDragonWaitTicks = 4;
						log.info("Blue dragon died! Waiting up to 4 ticks for loot to spawn...");
					}
				}

				// 3. Trip completion check: Teleport back to Falador when:
				//    - Food is at or below emergency food threshold: currentShark <= config.emergencyFoodCount()
				//    - OR Inventory is full (28 items) and no space can be freed (cannot drop vials or eat food without dipping below threshold)
				boolean outOfFood = currentShark <= config.emergencyFoodCount();
				boolean cannotFreeSpace = (currentShark <= config.emergencyFoodCount() || findItemSlotInInventory(SHARK_IDS) == -1)
					&& findItemSlotInInventory(VIAL_IDS) == -1;
				boolean fullTripDone = isInvFull && cannotFreeSpace;
				boolean emergencyLowHp = outOfFood && currentHp <= (maxHp * 35 / 100);

				boolean shouldTeleport = outOfFood || fullTripDone;

				if (shouldTeleport)
				{
					// If inventory is NOT full and a dragon recently died, wait briefly to loot it (unless HP is critically low)
					if (!isInvFull && !emergencyLowHp && deadDragonWaitTicks > 0)
					{
						deadDragonWaitTicks--;
						status = "WAITING FOR LOOT";
						statusColor = Color.CYAN;
						return;
					}

					log.info("Trip ending (OutOfFood: {}, Sharks: {} <= {}, InvFull: {}, EmergencyHP: {}). Initiating teleport to Falador!",
						outOfFood, currentShark, config.emergencyFoodCount(), isInvFull, emergencyLowHp);
					sendGameMessage(emergencyLowHp
						? "Food running out and low HP! Teleporting back to Falador to bank and restart cycle..."
						: (outOfFood
							? "Food threshold reached (" + currentShark + " remaining <= " + config.emergencyFoodCount() + ")! Teleporting back to Falador to bank and restart..."
							: "Inventory full of dragon bones and hides! Teleporting back to Falador to bank and restart..."));
					startTeleportCycle();
					return;
				}

				// 4. Check if currently attacking a living Blue dragon
				if (currentTarget != null && !currentTarget.isDead())
				{
					status = "FIGHTING: " + currentTarget.getName();
					statusColor = Color.GREEN;
					deadDragonWaitTicks = 0;
					return;
				}

				// If dragon recently died, wait on the spot for bones and hide to appear
				if (deadDragonWaitTicks > 0)
				{
					deadDragonWaitTicks--;
					status = "WAITING FOR LOOT";
					statusColor = Color.CYAN;
					return;
				}

				// 5. If moving towards target or loot, wait
				if (isMoving)
				{
					return;
				}

				// 6. Find nearest available Blue dragon and attack
				NPC dragon = findClosestBlueDragon();
				if (dragon != null)
				{
					attackNpc(dragon);
					cooldownTicks = 3;
				}
				else
				{
					status = "SEARCHING DRAGONS";
					statusColor = Color.CYAN;
				}
				break;

			case COMPLETE:
				status = "DONE";
				statusColor = Color.GREEN;
				if (!config.startTeleport())
				{
					state = TeleportState.IDLE;
				}
				break;
		}
	}

	private void handleRestock()
	{
		// 1. First, deposit all loot (Dragon bones, Blue dragonhide, empty vials, partial potions)
		int depositSlot = findItemSlotInInventory(LOOT_AND_TRASH_IDS);
		if (depositSlot != -1)
		{
			ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
			Item item = inv != null ? inv.getItem(depositSlot) : null;
			int itemId = item != null ? item.getId() : -1;
			String itemName = "Loot";
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

			log.info("Depositing {} (ID: {}) from slot {}", itemName, itemId, depositSlot);
			status = "DEPOSITING " + itemName.toUpperCase();
			statusColor = Color.YELLOW;

			client.menuAction(
				depositSlot,
				InterfaceID.Bankside.ITEMS,
				MenuAction.CC_OP,
				8, // Deposit-All
				itemId,
				"Deposit-All",
				itemName
			);
			cooldownTicks = 2;
			return;
		}

		// 2. Combat Boost Potion check (Super combat OR Super attack + Super strength, supports 1-4 doses)
		boolean hasSuperCombat = currentSuperCombat > 0;
		boolean hasSuperAttack = currentSuperAttack > 0;
		boolean hasSuperStrength = currentSuperStrength > 0;
		boolean combatBoostSatisfied = hasSuperCombat || (hasSuperAttack && hasSuperStrength);

		if (!combatBoostSatisfied)
		{
			BlueDragonScriptConfig.PotionChoice choice = config.potionChoice();
			boolean preferSuperCombat = choice == BlueDragonScriptConfig.PotionChoice.SUPER_COMBAT;
			boolean preferAtkStr = choice == BlueDragonScriptConfig.PotionChoice.SUPER_ATTACK_AND_STRENGTH;

			Widget superCombatBank = findItemInBank(SUPER_COMBAT_IDS);
			Widget superAtkBank = findItemInBank(SUPER_ATTACK_IDS);
			Widget superStrBank = findItemInBank(SUPER_STRENGTH_IDS);

			if (!preferAtkStr && superCombatBank != null && !hasSuperAttack && !hasSuperStrength)
			{
				status = "WITHDRAWING SUPER COMBAT";
				statusColor = Color.YELLOW;
				withdrawItem(superCombatBank, 1);
				cooldownTicks = 2;
				return;
			}
			else if (!preferSuperCombat && ((superAtkBank != null || hasSuperAttack) && (superStrBank != null || hasSuperStrength)))
			{
				if (!hasSuperAttack && superAtkBank != null)
				{
					status = "WITHDRAWING SUPER ATTACK";
					statusColor = Color.YELLOW;
					withdrawItem(superAtkBank, 1);
					cooldownTicks = 2;
					return;
				}
				if (!hasSuperStrength && superStrBank != null)
				{
					status = "WITHDRAWING SUPER STRENGTH";
					statusColor = Color.YELLOW;
					withdrawItem(superStrBank, 1);
					cooldownTicks = 2;
					return;
				}
			}
			else if (superCombatBank != null)
			{
				status = "WITHDRAWING SUPER COMBAT";
				statusColor = Color.YELLOW;
				withdrawItem(superCombatBank, 1);
				cooldownTicks = 2;
				return;
			}
			else if (!superCombatMissingReported)
			{
				log.warn("No Super combat or Super attack/strength potions found in bank!");
				sendGameMessage("No boost potions found in bank!");
				superCombatMissingReported = true;
			}
		}

		// 3. Antifire Potion check (supports 1, 2, 3, 4 doses)
		if (currentAntifire < config.antifireQuantity())
		{
			Widget bankWidget = findItemInBank(ANTIFIRE_IDS);
			if (bankWidget != null)
			{
				int needed = config.antifireQuantity() - currentAntifire;
				status = "WITHDRAWING ANTIFIRE";
				statusColor = Color.YELLOW;
				withdrawItem(bankWidget, needed);
				cooldownTicks = 2;
				return;
			}
			else if (!antifireMissingReported)
			{
				log.warn("Antifire potion not found in bank!");
				sendGameMessage("Antifire potion not found in bank!");
				antifireMissingReported = true;
			}
		}

		// 4. Food check
		if (currentShark < config.sharkQuantity())
		{
			Widget bankWidget = findItemInBank(SHARK_IDS);
			if (bankWidget != null)
			{
				int needed = config.sharkQuantity() - currentShark;
				status = "WITHDRAWING FOOD (" + currentShark + "/" + config.sharkQuantity() + ")";
				statusColor = Color.YELLOW;
				withdrawItem(bankWidget, needed);
				cooldownTicks = 2;
				return;
			}
			else if (!sharkMissingReported)
			{
				log.warn("Food (ID: 385) not found in bank!");
				sendGameMessage("Food not found in bank!");
				sharkMissingReported = true;
			}
		}

		// 5. HP check: Eat to full HP at bank before departing if needed
		if (config.eatToFullHpAtBank() && currentHp < maxHp && currentShark > 0)
		{
			log.info("HP is {}/{}, closing bank to eat to full HP before departing...", currentHp, maxHp);
			sendGameMessage(String.format("HP is %d/%d! Eating to full HP at bank...", currentHp, maxHp));
			closeBank();
			state = TeleportState.HEALING_AT_BANK;
			status = "HEALING AT BANK (" + currentHp + "/" + maxHp + ")";
			statusColor = Color.YELLOW;
			cooldownTicks = 2;
			return;
		}

		// All restocks satisfied! Transition to closing bank
		log.info("Restock sequence complete! Super Combat: {}, Super Atk: {}, Super Str: {}, Antifire: {}, Food: {}/{}",
			currentSuperCombat, currentSuperAttack, currentSuperStrength, currentAntifire, currentShark, config.sharkQuantity());

		sendGameMessage(String.format("Restock complete! Boost: %s, Antifire: %d, Food: %d. Closing bank...",
			(currentSuperCombat > 0 ? "Super combat" : (currentSuperAttack > 0 ? "Super Atk+Str" : "None")), currentAntifire, currentShark));

		closeBank();
		state = TeleportState.CLOSING_BANK;
		status = "CLOSING BANK";
		statusColor = Color.YELLOW;
		cooldownTicks = 2;
	}

	private void closeBank()
	{
		log.info("Closing bank interface...");
		client.menuAction(-1, InterfaceID.Bankmain.UNIVERSE, MenuAction.CC_OP, 1, -1, "Close", "");
		pressKey(KeyEvent.VK_ESCAPE);
	}

	private void clickObstaclePipeDirectly()
	{
		TileObject pipe = findObstaclePipe();
		if (pipe != null)
		{
			log.info("Directly clicking Obstacle Pipe {} at {}", pipe.getId(), pipe.getWorldLocation());
			status = "SQUEEZING PIPE";
			statusColor = Color.CYAN;
			interactWithObject(pipe, "Squeeze-through", "Obstacle pipe");
			return;
		}

		// Fallback: walk towards pipe tile (2888, 9799)
		log.info("Obstacle pipe not found in scene, walking towards {}", TAVERLEY_PIPE_TILE);
		walkToWorldPoint(TAVERLEY_PIPE_TILE);
	}

	private TileObject findObstaclePipe()
	{
		Tile[][][] tiles = client.getScene().getTiles();
		if (tiles == null)
		{
			return null;
		}

		Player localPlayer = client.getLocalPlayer();
		WorldPoint playerLoc = localPlayer != null ? localPlayer.getWorldLocation() : null;
		TileObject best = null;
		int bestDist = Integer.MAX_VALUE;

		int plane = client.getPlane();
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
						if (go != null && isObstaclePipe(go))
						{
							int dist = playerLoc != null ? playerLoc.distanceTo(go.getWorldLocation()) : 0;
							if (dist < bestDist)
							{
								bestDist = dist;
								best = go;
							}
						}
					}
				}

				TileObject wall = tile.getWallObject();
				if (wall != null && isObstaclePipe(wall))
				{
					int dist = playerLoc != null ? playerLoc.distanceTo(wall.getWorldLocation()) : 0;
					if (dist < bestDist)
					{
						bestDist = dist;
						best = wall;
					}
				}

				TileObject decorative = tile.getDecorativeObject();
				if (decorative != null && isObstaclePipe(decorative))
				{
					int dist = playerLoc != null ? playerLoc.distanceTo(decorative.getWorldLocation()) : 0;
					if (dist < bestDist)
					{
						bestDist = dist;
						best = decorative;
					}
				}

				TileObject ground = tile.getGroundObject();
				if (ground != null && isObstaclePipe(ground))
				{
					int dist = playerLoc != null ? playerLoc.distanceTo(ground.getWorldLocation()) : 0;
					if (dist < bestDist)
					{
						bestDist = dist;
						best = ground;
					}
				}
			}
		}
		return best;
	}

	private boolean isObstaclePipe(TileObject obj)
	{
		if (obj == null)
		{
			return false;
		}
		// Never match ladder up
		if (obj.getId() == TAVERLEY_LADDER_UP_ID)
		{
			return false;
		}
		if (obj.getId() == OBSTACLE_PIPE_ID)
		{
			return true;
		}
		try
		{
			ObjectComposition def = client.getObjectDefinition(obj.getId());
			if (def != null)
			{
				if (def.getImpostorIds() != null)
				{
					ObjectComposition imp = def.getImpostor();
					if (imp != null && (imp.getId() == OBSTACLE_PIPE_ID || (imp.getName() != null && imp.getName().equalsIgnoreCase("Obstacle pipe"))))
					{
						return true;
					}
				}
				if (def.getId() == OBSTACLE_PIPE_ID)
				{
					return true;
				}
				String name = def.getName();
				if (name != null && name.equalsIgnoreCase("Obstacle pipe"))
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

	private void clickTreeDirectly()
	{
		TileObject tree = findTree();
		if (tree != null)
		{
			log.info("Directly clicking Tree {} at {}", tree.getId(), tree.getWorldLocation());
			status = "CLICKING TREE 1286";
			statusColor = Color.CYAN;
			interactWithObject(tree, "Chop down", "Tree");
			return;
		}

		log.info("Tree 1286 not found in scene, walking towards ladder fallback {}", TAVERLEY_LADDER_OUTSIDE);
		walkToWorldPoint(TAVERLEY_LADDER_OUTSIDE);
	}

	private TileObject findTree()
	{
		Tile[][][] tiles = client.getScene().getTiles();
		if (tiles == null)
		{
			return null;
		}

		Player localPlayer = client.getLocalPlayer();
		WorldPoint playerLoc = localPlayer != null ? localPlayer.getWorldLocation() : null;
		TileObject best = null;
		int bestDistToLadder = Integer.MAX_VALUE;

		int plane = client.getPlane();
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
						if (go != null && isTree(go))
						{
							int distToLadder = go.getWorldLocation().distanceTo(TAVERLEY_LADDER_OUTSIDE);
							if (distToLadder < bestDistToLadder)
							{
								bestDistToLadder = distToLadder;
								best = go;
							}
						}
					}
				}
			}
		}
		return best;
	}

	private boolean isTree(GameObject obj)
	{
		if (obj == null)
		{
			return false;
		}
		if (obj.getId() == TREE_ID)
		{
			return true;
		}
		try
		{
			ObjectComposition def = client.getObjectDefinition(obj.getId());
			if (def != null)
			{
				if (def.getImpostorIds() != null)
				{
					ObjectComposition imp = def.getImpostor();
					if (imp != null && imp.getId() == TREE_ID)
					{
						return true;
					}
				}
				if (def.getId() == TREE_ID)
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

	private void clickLadderDirectly()
	{
		TileObject ladder = findLadder();
		if (ladder != null)
		{
			log.info("Directly clicking Ladder {} at {}", ladder.getId(), ladder.getWorldLocation());
			status = "CLIMBING LADDER";
			statusColor = Color.CYAN;
			interactWithObject(ladder, "Climb-down", "Ladder");
			return;
		}

		// Fallback: walk towards ladder location via waypoint or directly
		Player localPlayer = client.getLocalPlayer();
		WorldPoint loc = localPlayer != null ? localPlayer.getWorldLocation() : null;
		if (loc != null && loc.distanceTo(TAVERLEY_LADDER_WAYPOINT) > 4 && loc.getX() > TAVERLEY_LADDER_WAYPOINT.getX())
		{
			log.info("Ladder not yet in scene, walking towards waypoint {}", TAVERLEY_LADDER_WAYPOINT);
			walkToWorldPoint(TAVERLEY_LADDER_WAYPOINT);
		}
		else
		{
			log.info("Ladder not yet in scene, walking towards ladder {}", TAVERLEY_LADDER_OUTSIDE);
			walkToWorldPoint(TAVERLEY_LADDER_OUTSIDE);
		}
	}

	private TileObject findLadder()
	{
		Tile[][][] tiles = client.getScene().getTiles();
		if (tiles == null)
		{
			return null;
		}

		Player localPlayer = client.getLocalPlayer();
		WorldPoint playerLoc = localPlayer != null ? localPlayer.getWorldLocation() : null;
		TileObject best = null;
		int bestDist = Integer.MAX_VALUE;

		int plane = client.getPlane();
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
						if (go != null && isLadder(go))
						{
							int dist = playerLoc != null ? playerLoc.distanceTo(go.getWorldLocation()) : 0;
							if (dist < bestDist)
							{
								bestDist = dist;
								best = go;
							}
						}
					}
				}

				TileObject wall = tile.getWallObject();
				if (wall != null && isLadder(wall))
				{
					int dist = playerLoc != null ? playerLoc.distanceTo(wall.getWorldLocation()) : 0;
					if (dist < bestDist)
					{
						bestDist = dist;
						best = wall;
					}
				}

				TileObject decorative = tile.getDecorativeObject();
				if (decorative != null && isLadder(decorative))
				{
					int dist = playerLoc != null ? playerLoc.distanceTo(decorative.getWorldLocation()) : 0;
					if (dist < bestDist)
					{
						bestDist = dist;
						best = decorative;
					}
				}

				TileObject ground = tile.getGroundObject();
				if (ground != null && isLadder(ground))
				{
					int dist = playerLoc != null ? playerLoc.distanceTo(ground.getWorldLocation()) : 0;
					if (dist < bestDist)
					{
						bestDist = dist;
						best = ground;
					}
				}
			}
		}
		return best;
	}

	private boolean isLadder(TileObject obj)
	{
		if (obj == null)
		{
			return false;
		}
		if (obj.getId() == TAVERLEY_LADDER_UP_ID)
		{
			return false;
		}
		if (obj.getId() == TAVERLEY_LADDER_DOWN_ID)
		{
			return true;
		}
		try
		{
			ObjectComposition def = client.getObjectDefinition(obj.getId());
			if (def != null)
			{
				if (def.getImpostorIds() != null)
				{
					ObjectComposition imp = def.getImpostor();
					if (imp != null && imp.getId() == TAVERLEY_LADDER_DOWN_ID)
					{
						return true;
					}
				}
				if (def.getId() == TAVERLEY_LADDER_DOWN_ID)
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

	private void clickCrumblingWallDirectly()
	{
		TileObject wall = findCrumblingWall();
		if (wall != null)
		{
			log.info("Directly clicking Crumbling Wall {} at {}", wall.getId(), wall.getWorldLocation());
			status = "CLIMBING CRUMBLING WALL";
			statusColor = Color.CYAN;
			interactWithObject(wall, "Climb-over", "Crumbling wall");
			return;
		}

		// Fallback: walk towards crumbling wall tile
		log.info("Crumbling wall not found in scene, walking towards {}", CRUMBLING_WALL_TILE);
		walkToWorldPoint(CRUMBLING_WALL_TILE);
	}

	private TileObject findCrumblingWall()
	{
		Tile[][][] tiles = client.getScene().getTiles();
		if (tiles == null)
		{
			return null;
		}

		Player localPlayer = client.getLocalPlayer();
		WorldPoint playerLoc = localPlayer != null ? localPlayer.getWorldLocation() : null;
		TileObject best = null;
		int bestDist = Integer.MAX_VALUE;

		int plane = client.getPlane();
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
						if (go != null && isCrumblingWall(go))
						{
							int dist = playerLoc != null ? playerLoc.distanceTo(go.getWorldLocation()) : 0;
							if (dist < bestDist)
							{
								bestDist = dist;
								best = go;
							}
						}
					}
				}

				TileObject wall = tile.getWallObject();
				if (wall != null && isCrumblingWall(wall))
				{
					int dist = playerLoc != null ? playerLoc.distanceTo(wall.getWorldLocation()) : 0;
					if (dist < bestDist)
					{
						bestDist = dist;
						best = wall;
					}
				}
			}
		}
		return best;
	}

	private boolean isCrumblingWall(TileObject obj)
	{
		if (obj == null)
		{
			return false;
		}
		if (obj.getId() == CRUMBLING_WALL_ID)
		{
			return true;
		}
		try
		{
			ObjectComposition def = client.getObjectDefinition(obj.getId());
			if (def != null && def.getName() != null && def.getName().equalsIgnoreCase("Crumbling wall"))
			{
				return true;
			}
		}
		catch (Exception ignored)
		{
		}
		return false;
	}

	private boolean drinkPotion(Set<Integer> potionIds, String potionType)
	{
		int slot = findItemSlotInInventory(potionIds);
		if (slot == -1)
		{
			log.warn("No {} found in inventory to drink!", potionType);
			sendGameMessage("No " + potionType + " found in inventory!");
			return false;
		}

		ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
		Item item = inv != null ? inv.getItem(slot) : null;
		int itemId = item != null ? item.getId() : -1;
		String itemName = potionType;
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

		log.info("Drinking {} (ID: {}) in slot {}", itemName, itemId, slot);
		openInventoryTab();

		// 1. Direct menuAction: CC_OP (Option 1 = "Drink")
		client.menuAction(
			slot,
			InterfaceID.Inventory.ITEMS,
			MenuAction.CC_OP,
			2,
			itemId,
			"Drink",
			itemName
		);

		// 2. Simulate canvas click on the item slot
		Widget invWidget = client.getWidget(InterfaceID.Inventory.ITEMS);
		if (invWidget != null)
		{
			Widget child = invWidget.getChild(slot);
			if (child != null)
			{
				Rectangle bounds = child.getBounds();
				if (bounds != null && bounds.width > 0 && bounds.height > 0)
				{
					dispatchCanvasClick(new Point((int) bounds.getCenterX(), (int) bounds.getCenterY()));
				}
			}
		}

		sendGameMessage("Drank 1 dose of " + itemName + "!");
		return true;
	}

	private int getInventoryFilledCount()
	{
		ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
		if (inv == null)
		{
			return 0;
		}
		int count = 0;
		for (Item item : inv.getItems())
		{
			if (item != null && item.getId() > 0)
			{
				count++;
			}
		}
		return count;
	}

	private void dropItem(int slot, int itemId, String itemName)
	{
		log.info("Dropping {} (ID: {}) in slot {}", itemName, itemId, slot);
		openInventoryTab();

		client.menuAction(
			slot,
			InterfaceID.Inventory.ITEMS,
			MenuAction.CC_OP_LOW_PRIORITY,
			7,
			itemId,
			"Drop",
			itemName
		);

		sendGameMessage("Dropped " + itemName + " to make room for loot!");
	}

	private boolean makeSpaceForLoot()
	{
		// 1. Drop empty vials first (saves food!)
		int vialSlot = findItemSlotInInventory(VIAL_IDS);
		if (vialSlot != -1)
		{
			ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
			Item item = inv != null ? inv.getItem(vialSlot) : null;
			int itemId = item != null ? item.getId() : ItemID.VIAL;
			log.info("Freeing inventory slot: dropping empty vial in slot {}", vialSlot);
			dropItem(vialSlot, itemId, "Vial");
			return true;
		}

		// 2. Eat food (Shark) if we have more than emergency food count
		if (currentShark > config.emergencyFoodCount())
		{
			if (currentHp < maxHp)
			{
				log.info("Freeing inventory slot: eating food (HP: {}/{}, Sharks left: {})", currentHp, maxHp, currentShark);
				sendGameMessage("Eating food to make space for dragon loot!");
				return eatFood();
			}
			else
			{
				// If HP is at 100%, in OSRS clicking Eat displays 'You are already at full health'
				// If currently attacking / taking damage from a living dragon, wait to take a hit:
				Player localPlayer = client.getLocalPlayer();
				Actor interacting = localPlayer != null ? localPlayer.getInteracting() : null;
				if (interacting instanceof NPC && !((NPC) interacting).isDead())
				{
					log.info("HP full ({}/{}) but fighting dragon; waiting to take hit to eat food...", currentHp, maxHp);
					return false;
				}

				// If not taking damage and HP is 100%, drop 1 food to secure the valuable dragon loot
				int sharkSlot = findItemSlotInInventory(SHARK_IDS);
				if (sharkSlot != -1)
				{
					ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
					Item item = inv != null ? inv.getItem(sharkSlot) : null;
					int itemId = item != null ? item.getId() : ItemID.SHARK;
					log.info("HP full ({}/{}), dropping 1 food to make space for loot!", currentHp, maxHp);
					sendGameMessage("HP full! Dropping 1 food to pick up dragon loot!");
					dropItem(sharkSlot, itemId, "Shark");
					return true;
				}
			}
		}

		return false;
	}

	private boolean eatFood()
	{
		int slot = findItemSlotInInventory(SHARK_IDS);
		if (slot == -1)
		{
			log.warn("No food found in inventory to eat!");
			return false;
		}

		ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
		Item item = inv != null ? inv.getItem(slot) : null;
		int itemId = item != null ? item.getId() : ItemID.SHARK;
		String itemName = "Shark";
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

		log.info("Eating {} (ID: {}) in slot {} (HP: {}/{})", itemName, itemId, slot, currentHp, maxHp);
		openInventoryTab();

		// 1. Direct menuAction: CC_OP (Option 1 = "Eat")
		client.menuAction(
			slot,
			InterfaceID.Inventory.ITEMS,
			MenuAction.CC_OP,
			2,
			itemId,
			"Eat",
			itemName
		);

		// 2. Simulate canvas click on slot
		Widget invWidget = client.getWidget(InterfaceID.Inventory.ITEMS);
		if (invWidget != null)
		{
			Widget child = invWidget.getChild(slot);
			if (child != null)
			{
				Rectangle bounds = child.getBounds();
				if (bounds != null && bounds.width > 0 && bounds.height > 0)
				{
					dispatchCanvasClick(new Point((int) bounds.getCenterX(), (int) bounds.getCenterY()));
				}
			}
		}

		sendGameMessage("Ate " + itemName + " (HP: " + currentHp + "/" + maxHp + ")!");
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

	private GroundItemTarget findNearestLoot()
	{
		Tile[][][] tiles = client.getScene().getTiles();
		if (tiles == null)
		{
			return null;
		}

		Player localPlayer = client.getLocalPlayer();
		WorldPoint playerLoc = localPlayer != null ? localPlayer.getWorldLocation() : null;
		if (playerLoc == null)
		{
			return null;
		}

		GroundItemTarget best = null;
		int bestDist = 16; // within 15 tiles

		int plane = client.getPlane();
		for (int x = 0; x < Constants.SCENE_SIZE; x++)
		{
			for (int y = 0; y < Constants.SCENE_SIZE; y++)
			{
				Tile tile = tiles[plane][x][y];
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
					if (item == null)
					{
						continue;
					}
					int id = item.getId();
					boolean isLoot = (config.lootDragonBones() && id == ID_DRAGON_BONES)
						|| (config.lootDragonhide() && id == ID_BLUE_DRAGONHIDE);

					if (isLoot)
					{
						int dist = playerLoc.distanceTo(tile.getWorldLocation());
						if (dist < bestDist)
						{
							bestDist = dist;
							best = new GroundItemTarget(item, tile);
						}
					}
				}
			}
		}
		return best;
	}

	private void takeGroundItem(GroundItemTarget target)
	{
		if (target == null || target.item == null || target.tile == null)
		{
			return;
		}

		int itemId = target.item.getId();
		LocalPoint lp = target.tile.getLocalLocation();
		if (lp == null)
		{
			return;
		}

		int sceneX = lp.getSceneX();
		int sceneY = lp.getSceneY();
		String itemName = itemId == ID_DRAGON_BONES ? "Dragon bones" : "Blue dragonhide";

		log.info("Looting {} (ID: {}) at scene ({}, {})", itemName, itemId, sceneX, sceneY);
		status = "LOOTING " + itemName.toUpperCase();
		statusColor = Color.CYAN;

		if (itemId == ID_DRAGON_BONES)
		{
			bonesLooted++;
		}
		else if (itemId == ID_BLUE_DRAGONHIDE)
		{
			hidesLooted++;
		}

		// Direct menuAction: GROUND_ITEM_THIRD_OPTION (Take)
		client.menuAction(
			sceneX,
			sceneY,
			MenuAction.GROUND_ITEM_THIRD_OPTION,
			itemId,
			0,
			"Take",
			itemName
		);
	}

	private NPC findClosestBlueDragon()
	{
		Player localPlayer = client.getLocalPlayer();
		WorldPoint playerLoc = localPlayer != null ? localPlayer.getWorldLocation() : null;
		if (playerLoc == null)
		{
			return null;
		}

		NPC best = null;
		int bestDist = Integer.MAX_VALUE;

		for (NPC npc : client.getNpcs())
		{
			if (npc != null && isBlueDragon(npc))
			{
				// Check if dragon is not already in combat with someone else
				Actor interacting = npc.getInteracting();
				if (interacting != null && interacting != localPlayer)
				{
					continue;
				}

				int dist = playerLoc.distanceTo(npc.getWorldLocation());
				if (dist < bestDist)
				{
					bestDist = dist;
					best = npc;
				}
			}
		}
		return best;
	}

	private boolean isBlueDragon(NPC npc)
	{
		if (npc == null || npc.getName() == null || npc.isDead())
		{
			return false;
		}
		String name = npc.getName();
		return name.equalsIgnoreCase("Blue dragon") && !name.toLowerCase().contains("baby");
	}

	private void attackNpc(NPC npc)
	{
		if (npc == null)
		{
			return;
		}

		log.info("Attacking NPC: {} (ID: {}, Index: {})", npc.getName(), npc.getId(), npc.getIndex());
		status = "ATTACKING: " + npc.getName();
		statusColor = Color.YELLOW;

		MenuAction menuAction = MenuAction.NPC_SECOND_OPTION; // default for Attack
		try
		{
			NPCComposition def = client.getNpcDefinition(npc.getId());
			if (def != null && def.getActions() != null)
			{
				for (int i = 0; i < def.getActions().length; i++)
				{
					String act = def.getActions()[i];
					if (act != null && act.equalsIgnoreCase("Attack"))
					{
						switch (i)
						{
							case 0: menuAction = MenuAction.NPC_FIRST_OPTION; break;
							case 1: menuAction = MenuAction.NPC_SECOND_OPTION; break;
							case 2: menuAction = MenuAction.NPC_THIRD_OPTION; break;
							case 3: menuAction = MenuAction.NPC_FOURTH_OPTION; break;
							case 4: menuAction = MenuAction.NPC_FIFTH_OPTION; break;
						}
						break;
					}
				}
			}
		}
		catch (Exception ignored)
		{
		}

		// Direct menuAction to attack NPC
		client.menuAction(
			0,
			0,
			menuAction,
			npc.getIndex(),
			0,
			"Attack",
			npc.getName()
		);
	}

	private void openInventoryTab()
	{
		Widget tabWidget = getInventoryTabWidget();
		if (tabWidget != null)
		{
			client.menuAction(
				1,
				tabWidget.getId(),
				MenuAction.CC_OP,
				1,
				-1,
				"Inventory",
				""
			);

			Rectangle bounds = tabWidget.getBounds();
			if (bounds != null && bounds.width > 0 && bounds.height > 0)
			{
				dispatchCanvasClick(new Point((int) bounds.getCenterX(), (int) bounds.getCenterY()));
			}
		}
	}

	private Widget getInventoryTabWidget()
	{
		int[] tabWidgets = {
			InterfaceID.Toplevel.STONE3,
			InterfaceID.ToplevelOsrsStretch.STONE3,
			InterfaceID.ToplevelPreEoc.STONE3,
			InterfaceID.Toplevel.ICON3,
			InterfaceID.ToplevelOsrsStretch.ICON3,
			InterfaceID.ToplevelPreEoc.ICON3
		};

		for (int widgetId : tabWidgets)
		{
			Widget w = client.getWidget(widgetId);
			if (w != null && !w.isHidden() && w.getWidth() > 0)
			{
				return w;
			}
		}
		return null;
	}

	private Widget findItemInBank(Set<Integer> validIds)
	{
		Widget bankContainer = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (bankContainer == null)
		{
			return null;
		}

		Widget[] children = bankContainer.getDynamicChildren();
		if (children == null || children.length == 0)
		{
			children = bankContainer.getChildren();
		}
		if (children == null)
		{
			return null;
		}

		for (Widget itemWidget : children)
		{
			if (itemWidget != null && validIds.contains(itemWidget.getItemId()))
			{
				return itemWidget;
			}
		}
		return null;
	}

	private void withdrawItem(Widget itemWidget, int amountNeeded)
	{
		if (itemWidget == null)
		{
			return;
		}

		int slot = itemWidget.getIndex();
		int itemId = itemWidget.getItemId();
		int containerId = itemWidget.getParentId();
		if (containerId <= 0)
		{
			containerId = InterfaceID.Bankmain.ITEMS;
		}

		String name = "Item";
		try
		{
			ItemComposition def = client.getItemDefinition(itemId);
			if (def != null && def.getName() != null)
			{
				name = def.getName();
			}
		}
		catch (Exception ignored)
		{
		}

		if (amountNeeded == 1)
		{
			log.info("Withdrawing 1x {} (ID: {}) from slot {}", name, itemId, slot);
			client.menuAction(slot, containerId, MenuAction.CC_OP, 1, itemId, "Withdraw-1", name);
		}
		else if (amountNeeded >= 5)
		{
			log.info("Withdrawing 5x {} (ID: {}) from slot {} (needed: {})", name, itemId, slot, amountNeeded);
			client.menuAction(slot, containerId, MenuAction.CC_OP, 3, itemId, "Withdraw-5", name);
		}
		else
		{
			log.info("Withdrawing 1x {} (ID: {}) from slot {} (needed: {})", name, itemId, slot, amountNeeded);
			client.menuAction(slot, containerId, MenuAction.CC_OP, 1, itemId, "Withdraw-1", name);
		}
	}

	private boolean isInFalador(WorldPoint loc)
	{
		if (loc == null)
		{
			return false;
		}
		return loc.getPlane() == 0 && (loc.distanceTo(FALADOR_CENTER) <= 50 || loc.distanceTo(FALADOR_WEST_BANK) <= 40);
	}

	private boolean isBankOpen()
	{
		if (client.getItemContainer(InventoryID.BANK) != null)
		{
			return true;
		}
		Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		return bank != null && !bank.isHidden();
	}

	private TileObject findBankBooth()
	{
		Tile[][][] tiles = client.getScene().getTiles();
		if (tiles == null)
		{
			return null;
		}

		Player localPlayer = client.getLocalPlayer();
		WorldPoint playerLoc = localPlayer != null ? localPlayer.getWorldLocation() : null;
		TileObject best = null;
		int bestDist = Integer.MAX_VALUE;

		int plane = client.getPlane();
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
						if (go != null && isBankBooth(go))
						{
							int dist = playerLoc != null ? playerLoc.distanceTo(go.getWorldLocation()) : 0;
							if (dist < bestDist)
							{
								bestDist = dist;
								best = go;
							}
						}
					}
				}

				TileObject wall = tile.getWallObject();
				if (wall != null && isBankBooth(wall))
				{
					int dist = playerLoc != null ? playerLoc.distanceTo(wall.getWorldLocation()) : 0;
					if (dist < bestDist)
					{
						bestDist = dist;
						best = wall;
					}
				}
			}
		}
		return best;
	}

	private boolean isBankBooth(TileObject obj)
	{
		if (obj.getId() == BANK_BOOTH_ID)
		{
			return true;
		}
		try
		{
			ObjectComposition def = client.getObjectDefinition(obj.getId());
			if (def != null)
			{
				if (def.getImpostorIds() != null)
				{
					try
					{
						ObjectComposition imp = def.getImpostor();
						if (imp != null)
						{
							def = imp;
						}
					}
					catch (Exception ignored)
					{
					}
				}
				String name = def.getName();
				if (name != null && (name.equalsIgnoreCase("Bank booth") || name.equalsIgnoreCase("Bank chest")))
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

	private void clickBankBoothDirectly()
	{
		TileObject booth = findBankBooth();
		if (booth != null)
		{
			log.info("Directly interacting with Bank booth {} at {}", booth.getId(), booth.getWorldLocation());
			interactWithObject(booth, "Bank", "Bank booth");
			return;
		}

		// Fallback: Banker NPC if booth not loaded
		for (NPC npc : client.getNpcs())
		{
			if (npc != null && npc.getName() != null && npc.getName().equalsIgnoreCase("Banker"))
			{
				log.info("Interacting with Banker NPC at {}", npc.getWorldLocation());
				client.menuAction(
					0,
					0,
					MenuAction.NPC_SECOND_OPTION,
					npc.getIndex(),
					0,
					"Bank",
					npc.getName()
				);
				return;
			}
		}

		// Fallback: walk towards bank
		walkToWorldPoint(FALADOR_WEST_BANK);
	}

	private void interactWithObject(TileObject object, String actionName, String targetName)
	{
		if (object == null)
		{
			return;
		}
		// Explicit safety check: never interact with ladder up 17385
		if (object.getId() == TAVERLEY_LADDER_UP_ID)
		{
			log.warn("Blocked attempt to interact with ladder up 17385!");
			return;
		}

		LocalPoint loc = object.getLocalLocation();
		if (loc == null)
		{
			return;
		}

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

		MenuAction menuAction = MenuAction.GAME_OBJECT_FIRST_OPTION;
		int identifier = object.getId();
		String chosenAction = actionName;
		String target = targetName;

		try
		{
			ObjectComposition def = client.getObjectDefinition(object.getId());
			if (def != null)
			{
				if (def.getImpostorIds() != null)
				{
					def = def.getImpostor();
				}
				if (def != null && def.getName() != null)
				{
					target = def.getName();
				}
				if (def != null && def.getActions() != null)
				{
					for (int i = 0; i < def.getActions().length; i++)
					{
						String act = def.getActions()[i];
						if (act != null && (act.equalsIgnoreCase(actionName)
							|| act.toLowerCase().contains(actionName.toLowerCase())
							|| act.toLowerCase().contains("climb")
							|| act.toLowerCase().contains("down")
							|| act.toLowerCase().contains("squeeze")
							|| act.toLowerCase().contains("through")))
						{
							switch (i)
							{
								case 0: menuAction = MenuAction.GAME_OBJECT_FIRST_OPTION; break;
								case 1: menuAction = MenuAction.GAME_OBJECT_SECOND_OPTION; break;
								case 2: menuAction = MenuAction.GAME_OBJECT_THIRD_OPTION; break;
								case 3: menuAction = MenuAction.GAME_OBJECT_FOURTH_OPTION; break;
								case 4: menuAction = MenuAction.GAME_OBJECT_FIFTH_OPTION; break;
							}
							chosenAction = act;
							break;
						}
					}
				}
			}
		}
		catch (Exception ignored)
		{
		}

		log.info("Interacting with object {} at ({}, {}) action {} ('{}' on '{}')", identifier, param0, param1, menuAction, chosenAction, target);

		// Direct menuAction with the exact object identifier (NO physical canvas click to avoid hitting ladder 17385)
		client.menuAction(param0, param1, menuAction, identifier, 0, chosenAction, target);
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
			// If target WorldPoint is outside current scene, calculate clamped point towards target
			Player localPlayer = client.getLocalPlayer();
			if (localPlayer != null)
			{
				WorldPoint playerLoc = localPlayer.getWorldLocation();
				int dx = Integer.compare(wp.getX(), playerLoc.getX());
				int dy = Integer.compare(wp.getY(), playerLoc.getY());
				WorldPoint intermediate = new WorldPoint(playerLoc.getX() + dx * 15, playerLoc.getY() + dy * 15, playerLoc.getPlane());
				lp = LocalPoint.fromWorld(client, intermediate);
			}
		}
		if (lp == null)
		{
			return;
		}

		// Direct menuAction walk without canvas mouse events
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

	private void handleRunEnergy()
	{
		if (client.getEnergy() > 1500 && client.getVarpValue(VarPlayerID.OPTION_RUN) == 0)
		{
			client.menuAction(1, InterfaceID.Orbs.RUNBUTTON, MenuAction.CC_OP, 1, -1, "Toggle Run", "");
		}
	}

	private boolean isSpellbookOpen()
	{
		int currentPanel = client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL);
		if (currentPanel == 6)
		{
			return true;
		}

		Widget faladorWidget = findFaladorTeleportWidget();
		if (faladorWidget != null && !faladorWidget.isHidden() && faladorWidget.getWidth() > 0)
		{
			return true;
		}

		Widget spellLayer = client.getWidget(InterfaceID.MagicSpellbook.SPELLLAYER);
		if (spellLayer != null && !spellLayer.isHidden() && spellLayer.getWidth() > 0)
		{
			return true;
		}

		return false;
	}

	private void openMagicTab()
	{
		if (client.isWidgetSelected())
		{
			log.info("Cancelling previously selected spell or target mode...");
			client.setWidgetSelected(false);
			pressKey(KeyEvent.VK_ESCAPE);
		}

		// Try pressing F-keys based on attempt count (user hotkey F3, or standard F4/F6)
		if (openAttempts % 3 == 1)
		{
			pressKey(KeyEvent.VK_F3);
		}
		else if (openAttempts % 3 == 2)
		{
			pressKey(KeyEvent.VK_F4);
		}
		else
		{
			pressKey(KeyEvent.VK_F6);
		}

		Widget tabWidget = getMagicTabWidget();
		if (tabWidget != null)
		{
			log.info("Clicking magic tab widget 0x{:x}", tabWidget.getId());
			client.menuAction(
				-1,
				tabWidget.getId(),
				MenuAction.CC_OP,
				1,
				-1,
				"Magic",
				""
			);

			Rectangle bounds = tabWidget.getBounds();
			if (bounds != null && bounds.width > 0 && bounds.height > 0)
			{
				dispatchCanvasClick(new Point((int) bounds.getCenterX(), (int) bounds.getCenterY()));
			}
		}
	}

	private Widget getMagicTabWidget()
	{
		int[] tabWidgets = {
			InterfaceID.Toplevel.STONE6,
			InterfaceID.ToplevelOsrsStretch.STONE6,
			InterfaceID.ToplevelPreEoc.STONE6,
			InterfaceID.Toplevel.ICON6,
			InterfaceID.ToplevelOsrsStretch.ICON6,
			InterfaceID.ToplevelPreEoc.ICON6
		};

		for (int widgetId : tabWidgets)
		{
			Widget w = client.getWidget(widgetId);
			if (w != null && !w.isHidden() && w.getWidth() > 0)
			{
				return w;
			}
		}
		return null;
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

	private void performCast()
	{
		if (magicLevel < 37)
		{
			status = "LOW MAGIC LVL (<37)";
			statusColor = Color.RED;
			sendGameMessage("Magic level 37 required to cast Falador Teleport (Current: " + magicLevel + ")!");
			log.warn("Magic level too low: {} / 37", magicLevel);
			state = TeleportState.IDLE;
			configManager.setConfiguration("bluedragonscript", "startTeleport", false);
			return;
		}

		if (lawRunes < 1)
		{
			status = "NO LAW RUNES";
			statusColor = Color.RED;
			sendGameMessage("Missing Law runes for Falador Teleport!");
			log.warn("Missing Law rune!");
			state = TeleportState.IDLE;
			configManager.setConfiguration("bluedragonscript", "startTeleport", false);
			return;
		}

		if (!hasWaterSource && waterRunes < 1)
		{
			status = "NO WATER RUNES";
			statusColor = Color.RED;
			sendGameMessage("Missing Water runes or staff for Falador Teleport!");
			log.warn("Missing Water runes!");
			state = TeleportState.IDLE;
			configManager.setConfiguration("bluedragonscript", "startTeleport", false);
			return;
		}

		if (!hasAirSource && airRunes < 3)
		{
			status = "NO AIR RUNES";
			statusColor = Color.RED;
			sendGameMessage("Missing Air runes (need 3) or staff for Falador Teleport!");
			log.warn("Missing Air runes!");
			state = TeleportState.IDLE;
			configManager.setConfiguration("bluedragonscript", "startTeleport", false);
			return;
		}

		if (client.isWidgetSelected())
		{
			log.info("Cancelling previously selected widget/spell on cursor...");
			client.setWidgetSelected(false);
			pressKey(KeyEvent.VK_ESCAPE);
		}

		Widget faladorWidget = findFaladorTeleportWidget();
		int widgetId = faladorWidget != null ? faladorWidget.getId() : InterfaceID.MagicSpellbook.FALADOR_TELEPORT;
		String widgetName = faladorWidget != null && faladorWidget.getName() != null ? faladorWidget.getName() : "Falador Teleport";

		log.info("Casting Falador Teleport via widget 0x{:x} ('{}')...", widgetId, widgetName);
		status = "TELEPORTING";
		statusColor = Color.YELLOW;

		// 1. Direct menuAction
		client.menuAction(
			-1,
			widgetId,
			MenuAction.CC_OP,
			1,
			-1,
			"Cast",
			widgetName
		);

		// 2. Dispatch canvas click directly to Falador Teleport widget center
		if (faladorWidget != null)
		{
			Rectangle bounds = faladorWidget.getBounds();
			if (bounds != null && bounds.width > 0 && bounds.height > 0)
			{
				log.info("Dispatching canvas click to Falador Teleport widget at ({}, {})", bounds.getCenterX(), bounds.getCenterY());
				dispatchCanvasClick(new Point((int) bounds.getCenterX(), (int) bounds.getCenterY()));
			}
		}

		teleportCount++;
		cooldownTicks = 10;
		state = TeleportState.WAITING_ARRIVAL;
	}

	private Widget findFaladorTeleportWidget()
	{
		// Method 1: Look up via OSRS spellbook Enum
		try
		{
			int subSpellbookId = client.getEnum(EnumID.SPELLBOOKS_SUB).getIntValue(client.getVarbitValue(VarbitID.SPELLBOOK));
			int spellbookId = client.getEnum(subSpellbookId).getIntValue(client.getVarbitValue(VarbitID.SPELLBOOK_SUBLIST));
			EnumComposition spellbook = client.getEnum(spellbookId);
			if (spellbook != null)
		{
				for (int i = 0; i < spellbook.size(); ++i)
				{
					int spellObjId = spellbook.getIntValue(i);
					ItemComposition spellObj = client.getItemDefinition(spellObjId);
					if (spellObj != null && spellObj.getName() != null && spellObj.getName().toLowerCase().contains("falador"))
					{
						int spellComponent = spellObj.getIntValue(ParamID.SPELL_BUTTON);
						Widget w = client.getWidget(spellComponent);
						if (w != null && !w.isHidden() && w.getWidth() > 0)
						{
							return w;
						}
					}
				}
			}
		}
		catch (Exception e)
		{
			log.debug("Spellbook enum lookup failed: {}", e.getMessage());
		}

		// Method 2: Check SPELLLAYER children
		Widget spellLayer = client.getWidget(InterfaceID.MagicSpellbook.SPELLLAYER);
		if (spellLayer != null)
		{
			Widget found = searchWidgetTreeForFalador(spellLayer);
			if (found != null)
			{
				return found;
			}
		}

		// Method 3: Check UNIVERSE children
		Widget universe = client.getWidget(InterfaceID.MagicSpellbook.UNIVERSE);
		if (universe != null)
		{
			Widget found = searchWidgetTreeForFalador(universe);
			if (found != null)
			{
				return found;
			}
		}

		// Method 4: Scan all widgets in interface 218 (0xda)
		for (int childId = 0; childId < 250; childId++)
		{
			Widget w = client.getWidget(218, childId);
			if (w != null)
			{
				Widget found = searchWidgetTreeForFalador(w);
				if (found != null)
				{
					return found;
				}
			}
		}

		return null;
	}

	private Widget searchWidgetTreeForFalador(Widget w)
	{
		if (w == null)
		{
			return null;
		}

		if (isFaladorWidget(w))
		{
			return w;
		}

		Widget[] children = w.getChildren();
		if (children != null)
		{
			for (Widget c : children)
			{
				Widget found = searchWidgetTreeForFalador(c);
				if (found != null) return found;
			}
		}

		Widget[] dynamicChildren = w.getDynamicChildren();
		if (dynamicChildren != null)
		{
			for (Widget c : dynamicChildren)
			{
				Widget found = searchWidgetTreeForFalador(c);
				if (found != null) return found;
			}
		}

		Widget[] staticChildren = w.getStaticChildren();
		if (staticChildren != null)
		{
			for (Widget c : staticChildren)
			{
				Widget found = searchWidgetTreeForFalador(c);
				if (found != null) return found;
			}
		}

		Widget[] nestedChildren = w.getNestedChildren();
		if (nestedChildren != null)
		{
			for (Widget c : nestedChildren)
			{
				Widget found = searchWidgetTreeForFalador(c);
				if (found != null) return found;
			}
		}

		return null;
	}

	private boolean isFaladorWidget(Widget w)
	{
		if (w == null || w.isHidden() || w.getWidth() <= 0)
		{
			return false;
		}

		String name = w.getName();
		if (name != null)
		{
			String clean = name.replaceAll("<[^>]*>", "").toLowerCase();
			if (clean.contains("falador") && (clean.contains("teleport") || clean.contains("tele")))
			{
				return true;
			}
		}

		String[] actions = w.getActions();
		if (actions != null)
		{
			for (String a : actions)
			{
				if (a != null)
				{
					String clean = a.replaceAll("<[^>]*>", "").toLowerCase();
					if (clean.contains("falador"))
					{
						return true;
					}
				}
			}
		}

		return false;
	}

	private int countItemInInventory(Set<Integer> itemIds)
	{
		ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
		if (inv == null)
		{
			return 0;
		}
		int count = 0;
		for (Item item : inv.getItems())
		{
			if (item != null && itemIds.contains(item.getId()))
			{
				count += item.getQuantity();
			}
		}
		return count;
	}

	private void updateRequirements()
	{
		magicLevel = client.getBoostedSkillLevel(Skill.MAGIC);
		currentHp = client.getBoostedSkillLevel(Skill.HITPOINTS);
		maxHp = client.getRealSkillLevel(Skill.HITPOINTS);

		lawRunes = 0;
		waterRunes = 0;
		airRunes = 0;
		hasWaterSource = false;
		hasAirSource = false;

		currentSuperCombat = countItemInInventory(SUPER_COMBAT_IDS);
		currentSuperAttack = countItemInInventory(SUPER_ATTACK_IDS);
		currentSuperStrength = countItemInInventory(SUPER_STRENGTH_IDS);
		currentAntifire = countItemInInventory(ANTIFIRE_IDS);
		currentShark = countItemInInventory(SHARK_IDS);

		// Check equipped weapon / shield
		ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
		if (equipment != null)
		{
			for (Item item : equipment.getItems())
			{
				if (item != null && item.getId() > 0)
				{
					if (WATER_STAVES.contains(item.getId()))
					{
						hasWaterSource = true;
					}
					if (AIR_STAVES.contains(item.getId()))
					{
						hasAirSource = true;
					}
				}
			}
		}

		// Check inventory
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		if (inventory != null)
		{
			for (Item item : inventory.getItems())
			{
				if (item == null || item.getId() <= 0)
				{
					continue;
				}

				int id = item.getId();
				int qty = item.getQuantity();

				if (id == ItemID.LAW_RUNE)
				{
					lawRunes += qty;
				}
				else if (id == ItemID.WATER_RUNE)
				{
					waterRunes += qty;
				}
				else if (id == ItemID.AIR_RUNE)
				{
					airRunes += qty;
				}
				else if (WATER_STAVES.contains(id))
				{
					hasWaterSource = true;
				}
				else if (AIR_STAVES.contains(id))
				{
					hasAirSource = true;
				}
			}
		}
	}

	private void sendGameMessage(String message)
	{
		String chatMessage = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append("[Blue Dragon Script] ")
			.append(ChatColorType.NORMAL)
			.append(message)
			.build();

		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(chatMessage)
			.build());
	}
}
