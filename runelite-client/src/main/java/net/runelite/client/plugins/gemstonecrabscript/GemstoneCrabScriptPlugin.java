/*
 * Copyright (c) 2026, Antigravity
 * All rights reserved.
 */
package net.runelite.client.plugins.gemstonecrabscript;

import com.google.inject.Provides;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
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
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Gemstone Crab Script",
	description = "Automates attacking the Gemstone Crab, detects spawn and burrow/death, traverses cave passages, and includes anti-afk",
	tags = {"combat", "crab", "gemstone", "varlamore", "afk", "script", "anti-afk"},
	enabledByDefault = false
)
public class GemstoneCrabScriptPlugin extends Plugin
{
	private static final int CAVE_OBJECT_ID = 57631;
	private static final int CAVE_OBJECT_ID_ALT = 57634;
	private static final int CAVE_SEARCH_RADIUS = 30;

	// Anti-logout timeouts
	private static final int EXTENDED_LOGOUT_TIMEOUT = (int) Duration.ofMinutes(25).toMillis() / Constants.CLIENT_TICK_LENGTH;
	private static final int DEFAULT_LOGOUT_TIMEOUT = (int) Duration.ofMinutes(5).toMillis() / Constants.CLIENT_TICK_LENGTH;

	@Getter
	public enum Status
	{
		STOPPED("STOPPED"),
		WAITING_FOR_CRAB("Waiting for crab spawn"),
		ATTACKING_CRAB("Attacking Gemstone Crab"),
		FIGHTING_CRAB("Fighting Gemstone Crab"),
		MINING_SHELL("Mining crab shell"),
		ENTERING_CAVE("Entering cave to next pit");

		private final String label;

		Status(String label)
		{
			this.label = label;
		}
	}

	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private GemstoneCrabScriptOverlay overlay;

	@Inject
	private GemstoneCrabScriptConfig config;

	private int actionDelay = 0;
	private int idleCombatTicks = 0;
	private long scriptStartedAt;
	private long elapsedNanos;

	private int startingCombatXp;
	@Getter
	private long totalCombatXpGained;
	private boolean xpTracking;

	@Getter
	private int crabsDefeated = 0;

	@Getter
	private int cavesEntered = 0;

	@Getter
	private boolean crabAlive = false;

	private Status currentStatus = Status.STOPPED;
	private NPC currentCrab = null;
	private WorldPoint lastWorldLocation = null;

	// Anti-AFK state
	private long lastAntiLogoutTimeMs = 0;
	private int nextAntiLogoutThreshold = 5_000 + (int) (Math.random() * 4_500);

	@Provides
	GemstoneCrabScriptConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GemstoneCrabScriptConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		crabsDefeated = 0;
		cavesEntered = 0;
		totalCombatXpGained = 0;
		currentStatus = Status.STOPPED;
		currentCrab = null;
		crabAlive = false;
		actionDelay = 0;
		idleCombatTicks = 0;
		lastWorldLocation = null;

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
		currentCrab = null;
		crabAlive = false;
	}

	private void startSession()
	{
		actionDelay = 0;
		idleCombatTicks = 0;
		scriptStartedAt = System.nanoTime();
		elapsedNanos = 0;
		xpTracking = client.getGameState() == GameState.LOGGED_IN;
		startingCombatXp = xpTracking ? getCombatXp() : 0;
		totalCombatXpGained = 0;
		crabsDefeated = 0;
		cavesEntered = 0;
		currentStatus = Status.WAITING_FOR_CRAB;
		lastWorldLocation = null;
		lastAntiLogoutTimeMs = System.currentTimeMillis();
		nextAntiLogoutThreshold = 5_000 + (int) (Math.random() * 4_500);

		if (config.antiAfk())
		{
			client.setIdleTimeout(EXTENDED_LOGOUT_TIMEOUT);
		}

		// Initial scan for crab
		currentCrab = findGemstoneCrab();
		crabAlive = currentCrab != null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("gemstonecrabscript"))
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
				currentCrab = null;
				crabAlive = false;
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
	public void onNpcSpawned(NpcSpawned event)
	{
		NPC npc = event.getNpc();
		if (isGemstoneCrab(npc))
		{
			currentCrab = npc;
			crabAlive = true;
			log.info("Gemstone Crab spawned at {}!", npc.getWorldLocation());
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		NPC npc = event.getNpc();
		if (npc.getId() == NpcID.GEMSTONE_CRAB || npc.getId() == 14779)
		{
			crabAlive = false;
			currentCrab = null;
			if (config.startScript())
			{
				crabsDefeated++;
				log.info("Gemstone Crab burrowed / died! Total crabs defeated: {}", crabsDefeated);
			}
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.MESBOX)
		{
			return;
		}

		String msg = event.getMessage();
		if (msg.contains("The gemstone crab burrows away") || msg.contains("burrowing crab forces you out"))
		{
			log.info("Crab burrow message detected: {}", msg);
			crabAlive = false;
			currentCrab = null;
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

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		WorldPoint currentLocation = localPlayer.getWorldLocation();

		// Detect teleportation between pits (position jumps > 25 tiles)
		if (lastWorldLocation != null && currentLocation.distanceTo(lastWorldLocation) > 25)
		{
			log.info("Arrived at new pit location: {}", currentLocation);
			actionDelay = 2; // Allow entities to load
			currentCrab = null;
			crabAlive = false;
		}
		lastWorldLocation = currentLocation;

		if (actionDelay > 0)
		{
			actionDelay--;
			return;
		}

		// 1. Scan for active Gemstone Crab (ONLY alive crab, NOT remains)
		NPC crab = findGemstoneCrab();
		if (crab != null)
		{
			currentCrab = crab;
			crabAlive = true;

			Actor interacting = localPlayer.getInteracting();
			boolean isAttackingCrab = interacting instanceof NPC && isGemstoneCrab((NPC) interacting);

			if (isAttackingCrab)
			{
				currentStatus = Status.FIGHTING_CRAB;
				idleCombatTicks = 0;
				return;
			}

			// If animating combat
			if (localPlayer.getAnimation() != -1)
			{
				currentStatus = Status.FIGHTING_CRAB;
				idleCombatTicks = 0;
				return;
			}

			idleCombatTicks++;
			if (idleCombatTicks >= 1)
			{
				currentStatus = Status.ATTACKING_CRAB;
				log.info("Attacking Gemstone Crab at {}", crab.getWorldLocation());
				attackCrab(crab);
				actionDelay = 2;
				idleCombatTicks = 0;
				return;
			}
			return;
		}

		// 2. Crab is DEAD / BURROWED / NOT PRESENT
		crabAlive = false;
		currentCrab = null;

		// Option to mine crab shell before entering cave
		if (config.mineShell())
		{
			NPC shell = findCrabShell();
			if (shell != null)
			{
				currentStatus = Status.MINING_SHELL;
				log.info("Mining crab shell remains at {}", shell.getWorldLocation());
				interactWithNpc(shell, "Mine");
				actionDelay = 3;
				return;
			}
		}

		// Option to enter cave to travel to the active pit
		if (config.enterCaveWhenDead())
		{
			TileObject cave = findNearbyCave(currentLocation);
			if (cave != null)
			{
				currentStatus = Status.ENTERING_CAVE;
				enterCave(cave);
				cavesEntered++;
				actionDelay = 5; // Transit through cave
				return;
			}
		}

		currentStatus = Status.WAITING_FOR_CRAB;
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
			currentCrab = null;
			crabAlive = false;
		}
	}

	/**
	 * Finds the active Gemstone Crab NPC in the scene (strictly ID 14779, excludes shell remains).
	 */
	private NPC findGemstoneCrab()
	{
		List<NPC> npcs = client.getNpcs();
		if (npcs == null || npcs.isEmpty())
		{
			return null;
		}

		return npcs.stream()
			.filter(this::isGemstoneCrab)
			.findFirst()
			.orElse(null);
	}

	private boolean isGemstoneCrab(NPC npc)
	{
		if (npc == null || npc.isDead())
		{
			return false;
		}
		// Explicitly exclude shell remains (ID 14780)
		if (npc.getId() == NpcID.GEMSTONE_CRAB_REMAINS || npc.getId() == 14780)
		{
			return false;
		}
		if (npc.getId() == NpcID.GEMSTONE_CRAB || npc.getId() == 14779)
		{
			return true;
		}
		String name = npc.getName();
		if (name != null && (name.toLowerCase().contains("remains") || name.toLowerCase().contains("shell")))
		{
			return false;
		}
		return name != null && name.equalsIgnoreCase("Gemstone Crab");
	}

	/**
	 * Finds the shed shell remains left after the crab burrows (ID 14780).
	 */
	private NPC findCrabShell()
	{
		List<NPC> npcs = client.getNpcs();
		if (npcs == null || npcs.isEmpty())
		{
			return null;
		}

		return npcs.stream()
			.filter(npc -> npc != null && !npc.isDead() && (npc.getId() == NpcID.GEMSTONE_CRAB_REMAINS || npc.getId() == 14780))
			.findFirst()
			.orElse(null);
	}

	/**
	 * Finds the cave object connecting the pits within search radius.
	 */
	private TileObject findNearbyCave(WorldPoint playerLocation)
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

		TileObject bestCave = null;
		int bestDistance = Integer.MAX_VALUE;

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
				if (objects != null)
				{
					for (GameObject object : objects)
					{
						if (object != null && isCaveObject(object))
						{
							int dist = object.getWorldLocation().distanceTo(playerLocation);
							if (dist <= CAVE_SEARCH_RADIUS && dist < bestDistance)
							{
								bestDistance = dist;
								bestCave = object;
							}
						}
					}
				}

				if (tile.getWallObject() != null && isCaveObject(tile.getWallObject()))
				{
					int dist = tile.getWallObject().getWorldLocation().distanceTo(playerLocation);
					if (dist <= CAVE_SEARCH_RADIUS && dist < bestDistance)
					{
						bestDistance = dist;
						bestCave = tile.getWallObject();
					}
				}

				if (tile.getGroundObject() != null && isCaveObject(tile.getGroundObject()))
				{
					int dist = tile.getGroundObject().getWorldLocation().distanceTo(playerLocation);
					if (dist <= CAVE_SEARCH_RADIUS && dist < bestDistance)
					{
						bestDistance = dist;
						bestCave = tile.getGroundObject();
					}
				}
			}
		}

		return bestCave;
	}

	private boolean isCaveObject(TileObject object)
	{
		if (object == null)
		{
			return false;
		}
		if (object.getId() == CAVE_OBJECT_ID || object.getId() == CAVE_OBJECT_ID_ALT)
		{
			return true;
		}
		try
		{
			ObjectComposition def = client.getObjectDefinition(object.getId());
			if (def != null)
			{
				if (def.getImpostorIds() != null)
				{
					try
					{
						ObjectComposition impostor = def.getImpostor();
						if (impostor != null)
						{
							def = impostor;
						}
					}
					catch (Exception ignored)
					{
					}
				}
				String name = def.getName();
				if (name != null && (name.equalsIgnoreCase("Cave")
					|| name.equalsIgnoreCase("Cave entrance")
					|| name.equalsIgnoreCase("Passage")
					|| name.equalsIgnoreCase("Crevice")
					|| name.toLowerCase().contains("crab hole")
					|| name.toLowerCase().contains("cave")))
				{
					return true;
				}

				String[] actions = def.getActions();
				if (actions != null)
				{
					for (String action : actions)
					{
						if (action != null && (action.equalsIgnoreCase("Crawl-through")
							|| action.equalsIgnoreCase("Enter")
							|| action.equalsIgnoreCase("Crawl")
							|| action.toLowerCase().contains("crawl")
							|| action.toLowerCase().contains("enter")))
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

	private void enterCave(TileObject cave)
	{
		log.info("Entering cave at worldLocation={}, sceneLocation=({}, {})",
			cave.getWorldLocation(),
			cave.getLocalLocation().getSceneX(),
			cave.getLocalLocation().getSceneY());
		interactWithObject(cave, "Crawl-through", "Cave");
	}

	private void attackCrab(NPC crab)
	{
		if (crab == null)
		{
			return;
		}

		String target = crab.getName() != null ? crab.getName() : "Gemstone Crab";
		log.info("Executing Attack on Gemstone Crab [index={}, id={}]", crab.getIndex(), crab.getId());
		client.menuAction(
			0,
			0,
			MenuAction.NPC_SECOND_OPTION,
			crab.getIndex(),
			0,
			"Attack",
			target
		);
	}

	private void interactWithNpc(NPC npc, String option)
	{
		if (npc == null)
		{
			return;
		}

		MenuAction menuAction = "Attack".equalsIgnoreCase(option)
			? MenuAction.NPC_SECOND_OPTION
			: MenuAction.NPC_FIRST_OPTION;
		String actualOption = option;
		String actualTarget = npc.getName() != null ? npc.getName() : "NPC";

		try
		{
			NPCComposition def = npc.getTransformedComposition();
			if (def == null)
			{
				def = client.getNpcDefinition(npc.getId());
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
							menuAction = getNpcMenuAction(i);
							actualOption = actions[i];
							break;
						}
					}
				}
			}
		}
		catch (Exception ignored)
		{
		}

		client.menuAction(
			0,
			0,
			menuAction,
			npc.getIndex(),
			0,
			actualOption,
			actualTarget
		);
	}

	private void interactWithObject(TileObject object, String preferredOption, String target)
	{
		if (object == null)
		{
			return;
		}

		MenuAction menuAction = MenuAction.GAME_OBJECT_FIRST_OPTION;
		LocalPoint loc = object.getLocalLocation();
		int param0 = loc != null ? loc.getSceneX() : 0;
		int param1 = loc != null ? loc.getSceneY() : 0;
		if (object instanceof GameObject)
		{
			net.runelite.api.Point minLoc = ((GameObject) object).getSceneMinLocation();
			if (minLoc != null)
			{
				param0 = minLoc.getX();
				param1 = minLoc.getY();
			}
		}

		int identifier = object.getId();
		String actualOption = preferredOption;
		String actualTarget = target;

		try
		{
			ObjectComposition def = client.getObjectDefinition(object.getId());
			if (def != null)
			{
				if (def.getImpostorIds() != null)
				{
					try
					{
						ObjectComposition impostor = def.getImpostor();
						if (impostor != null)
						{
							def = impostor;
						}
					}
					catch (Exception ignored)
					{
					}
				}
				if (def.getName() != null && !def.getName().isEmpty())
				{
					actualTarget = def.getName();
				}
				String[] actions = def.getActions();
				if (actions != null)
				{
					boolean found = false;
					for (int i = 0; i < actions.length; i++)
					{
						if (actions[i] != null && (actions[i].equalsIgnoreCase(preferredOption)
							|| actions[i].toLowerCase().contains("crawl")
							|| actions[i].toLowerCase().contains("enter")))
						{
							menuAction = getGameObjectMenuAction(i);
							actualOption = actions[i];
							found = true;
							break;
						}
					}
					if (!found)
					{
						for (int i = 0; i < actions.length; i++)
						{
							if (actions[i] != null && !actions[i].isEmpty())
							{
								menuAction = getGameObjectMenuAction(i);
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

		log.info("Interacting with object: option='{}', target='{}', action={}, id={}, scene=({}, {})",
			actualOption, actualTarget, menuAction, identifier, param0, param1);

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

	private static MenuAction getNpcMenuAction(int index)
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

	private static MenuAction getGameObjectMenuAction(int index)
	{
		switch (index)
		{
			case 1:
				return MenuAction.GAME_OBJECT_SECOND_OPTION;
			case 2:
				return MenuAction.GAME_OBJECT_THIRD_OPTION;
			case 3:
				return MenuAction.GAME_OBJECT_FOURTH_OPTION;
			case 4:
				return MenuAction.GAME_OBJECT_FIFTH_OPTION;
			default:
				return MenuAction.GAME_OBJECT_FIRST_OPTION;
		}
	}

	private int getCombatXp()
	{
		return client.getSkillExperience(Skill.ATTACK)
			+ client.getSkillExperience(Skill.STRENGTH)
			+ client.getSkillExperience(Skill.DEFENCE)
			+ client.getSkillExperience(Skill.RANGED)
			+ client.getSkillExperience(Skill.MAGIC)
			+ client.getSkillExperience(Skill.HITPOINTS);
	}

	private void updateMetrics()
	{
		if (config.startScript())
		{
			elapsedNanos = System.nanoTime() - scriptStartedAt;
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				int currentXp = getCombatXp();
				if (xpTracking)
				{
					totalCombatXpGained = Math.max(0, currentXp - startingCombatXp);
				}
				else
				{
					startingCombatXp = currentXp;
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
