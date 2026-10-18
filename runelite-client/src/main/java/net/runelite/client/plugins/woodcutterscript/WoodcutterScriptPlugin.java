/*
 * Copyright (c) 2026, RuneLite
 * All rights reserved.
 */
package net.runelite.client.plugins.woodcutterscript;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Comparator;
import java.util.Set;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Scene;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.WidgetNode;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Woodcutter Script",
	description = "Chops selected trees and banks the logs",
	tags = {"woodcutting", "oak", "yew", "magic", "bank", "script"},
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

	@Getter
	public enum TreeType
	{
		OAK("Oak", "Varrock West", "Bank booth", 10820, ItemID.OAK_LOGS, Set.of(34810, 10583),
			new WorldPoint(3185, 3441, 0)),
		YEW("Yew", "Woodcutting Guild", "Bank chest", ObjectID.YEWTREE, ItemID.YEW_LOGS,
			Set.of(ObjectID.WCGUILD_BANKCHEST), new WorldPoint(1592, 3475, 0)),
		MAGIC("Magic", "Woodcutting Guild", "Bank chest", ObjectID.MAGICTREE, ItemID.MAGIC_LOGS,
			Set.of(ObjectID.WCGUILD_BANKCHEST), new WorldPoint(1592, 3475, 0));

		private final String treeName;
		private final String label;
		private final String bankTarget;
		private final int treeId;
		private final int logId;
		private final Set<Integer> bankObjectIds;
		private final WorldPoint bankLocation;

		TreeType(String treeName, String locationName, String bankTarget, int treeId, int logId,
			Set<Integer> bankObjectIds, WorldPoint bankLocation)
		{
			this.treeName = treeName;
			this.label = treeName + " - " + locationName;
			this.bankTarget = bankTarget;
			this.treeId = treeId;
			this.logId = logId;
			this.bankObjectIds = bankObjectIds;
			this.bankLocation = bankLocation;
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
		STOPPED("Stopped"),
		OUT_OF_AREA("Go to selected tree area"),
		CHOPPING("Chopping tree"),
		WAITING_FOR_TREE("Waiting for tree spawn"),
		WALKING_TO_BANK("Walking to bank"),
		BANKING("Banking");

		private final String label;

		Status(String label)
		{
			this.label = label;
		}
	}

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private WoodcutterScriptOverlay overlay;

	@Inject
	private WoodcutterScriptConfig config;

	@Inject
	private WoodcutterScriptPanel panel;

	private NavigationButton navigationButton;

	@Getter
	private volatile boolean running;

	@Getter
	private volatile TreeType treeType = TreeType.OAK;

	private int actionDelay;
	private boolean tryBoothNext = true;
	private long scriptStartedAt;
	private long elapsedNanos;
	private int startingWoodcuttingXp;

	@Getter
	private long woodcuttingXpGained;

	@Getter
	private int logsCut;

	private int previousLogCount;
	private boolean xpTracking;
	private Status currentStatus = Status.STOPPED;

	@Provides
	WoodcutterScriptConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(WoodcutterScriptConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		panel.init(this);
		navigationButton = NavigationButton.builder()
			.tooltip("Woodcutter Script")
			.priority(6)
			.icon(createIcon())
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);
		setStatus(Status.STOPPED);
	}

	@Override
	protected void shutDown()
	{
		running = false;
		clientToolbar.removeNavigation(navigationButton);
		overlayManager.remove(overlay);
		setStatus(Status.STOPPED);
	}

	public void startScript()
	{
		clientThread.invokeLater(() ->
		{
			running = true;
			actionDelay = 0;
			tryBoothNext = true;
			scriptStartedAt = System.nanoTime();
			elapsedNanos = 0;
			xpTracking = client.getGameState() == GameState.LOGGED_IN;
			startingWoodcuttingXp = xpTracking ? client.getSkillExperience(Skill.WOODCUTTING) : 0;
			woodcuttingXpGained = 0;
			logsCut = 0;
			previousLogCount = getInventoryLogCount(treeType);
			updateMetrics();
			setStatus(Status.CHOPPING);
		});
	}

	public void stopScript()
	{
		clientThread.invokeLater(() ->
		{
			if (running)
			{
				updateMetrics();
				elapsedNanos = System.nanoTime() - scriptStartedAt;
			}
			running = false;
			actionDelay = 0;
			updateMetrics();
			setStatus(Status.STOPPED);
		});
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!running || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		updateMetrics();

		if (actionDelay > 0)
		{
			--actionDelay;
			return;
		}

		if (client.getLocalPlayer() == null)
		{
			return;
		}

		WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();
		if (playerLocation.distanceTo(treeType.getBankLocation()) > BANK_RADIUS)
		{
			setStatus(Status.OUT_OF_AREA);
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
				setStatus(Status.WALKING_TO_BANK);
				interactWithObject(bankBooth, BANK_ACTION, treeType.getBankTarget());
				tryBoothNext = false;
				actionDelay = 5;
				return;
			}

			if (banker != null)
			{
				setStatus(Status.WALKING_TO_BANK);
				MenuAction bankAction = getBankerAction(banker);
				client.menuAction(banker.getIndex(), 0, bankAction,
					BANKER_ID, 0, BANK_ACTION, "Banker");
				tryBoothNext = true;
				actionDelay = 5;
				return;
			}

			if (bankBooth != null)
			{
				setStatus(Status.WALKING_TO_BANK);
				interactWithObject(bankBooth, BANK_ACTION, treeType.getBankTarget());
				tryBoothNext = true;
				actionDelay = 5;
				return;
			}

			walkTowardBank(playerLocation);
			return;
		}

		if (client.getLocalPlayer().getAnimation() != -1)
		{
			setStatus(Status.CHOPPING);
			return;
		}

		GameObject tree = findNearestTree(playerLocation);
		if (tree == null)
		{
			setStatus(Status.WAITING_FOR_TREE);
			return;
		}

		setStatus(Status.CHOPPING);
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

		int currentLogCount = getInventoryLogCount(treeType, event.getItemContainer());
		if (running)
		{
			logsCut += Math.max(0, currentLogCount - previousLogCount);
			updateMetrics();
		}
		previousLogCount = currentLogCount;
	}

	private void interactWithObject(GameObject object, String option, String target)
	{
		String[] actions = client.getObjectDefinition(object.getId()).getActions();
		MenuAction menuAction = MenuAction.GAME_OBJECT_FIRST_OPTION;
		if (actions != null)
		{
			for (int i = 0; i < Math.min(actions.length, 5); i++)
			{
				if (option.equalsIgnoreCase(actions[i]))
				{
					menuAction = gameObjectMenuAction(i);
					break;
				}
			}
		}

		client.menuAction(object.getSceneMinLocation().getX(), object.getSceneMinLocation().getY(),
			menuAction, object.getId(), -1, option, target);
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

	private static MenuAction gameObjectMenuAction(int index)
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
		WorldPoint bankLocation = treeType.getBankLocation();
		int dx = bankLocation.getX() - playerLocation.getX();
		int dy = bankLocation.getY() - playerLocation.getY();
		int distance = Math.max(Math.abs(dx), Math.abs(dy));
		if (distance == 0)
		{
			setStatus(Status.WALKING_TO_BANK);
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
			setStatus(Status.OUT_OF_AREA);
			actionDelay = 2;
			return;
		}

		setStatus(Status.WALKING_TO_BANK);
		client.menuAction(localPoint.getX(), localPoint.getY(), MenuAction.WALK, 0, 0, "", "");
		actionDelay = 5;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGGED_IN && running)
		{
			updateMetrics();
			elapsedNanos = System.nanoTime() - scriptStartedAt;
			running = false;
			updateMetrics();
			setStatus(Status.STOPPED);
		}
	}

	private GameObject findNearestTree(WorldPoint playerLocation)
	{
		return findNearestObject(Set.of(treeType.getTreeId()), playerLocation);
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

	private boolean isInventoryFull()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null || inventory.getItems().length < INVENTORY_CAPACITY)
		{
			return false;
		}

		Item[] items = inventory.getItems();
		for (int slot = 0; slot < INVENTORY_CAPACITY; slot++)
		{
			if (items[slot] == null || items[slot].getId() == -1)
			{
				return false;
			}
		}
		return true;
	}

	private boolean isBankOpen()
	{
		Widget bankItemContainer = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER);
		return bankItemContainer != null && !bankItemContainer.isHidden();
	}

	private void bankLogs()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory != null)
		{
			Item[] items = inventory.getItems();
			for (int slot = 0; slot < items.length; slot++)
			{
				Item item = items[slot];
				if (item != null && item.getId() == treeType.getLogId())
				{
					setStatus(Status.BANKING);
					client.menuAction(slot, WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER.getId(),
						MenuAction.CC_OP_LOW_PRIORITY, DEPOSIT_ALL_OP, treeType.getLogId(),
						"Deposit-All", treeType.getTreeName() + " logs");
					actionDelay = 2;
					return;
				}
			}
		}

		closeBank();

		actionDelay = 2;
		setStatus(Status.CHOPPING);
	}

	public void setTreeType(TreeType treeType)
	{
		clientThread.invokeLater(() ->
		{
			this.treeType = treeType;
			previousLogCount = getInventoryLogCount(treeType);
			actionDelay = 0;
			tryBoothNext = true;
			if (running)
			{
				setStatus(Status.CHOPPING);
			}
		});
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

	private void updateMetrics()
	{
		if (running)
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

		WoodcutterScriptPanel currentPanel = panel;
		if (currentPanel != null)
		{
			currentPanel.setMetrics(getFormattedUptime(), logsCut, woodcuttingXpGained);
		}
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
		return currentStatus != null ? currentStatus.getLabel() : "Stopped";
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

	private void setStatus(Status newStatus)
	{
		this.currentStatus = newStatus;
		WoodcutterScriptPanel currentPanel = panel;
		if (currentPanel != null)
		{
			currentPanel.setStatus(newStatus.getLabel());
		}
	}

	private static BufferedImage createIcon()
	{
		BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = icon.createGraphics();
		graphics.setColor(new Color(83, 135, 64));
		graphics.fillOval(2, 1, 12, 10);
		graphics.setColor(new Color(126, 82, 45));
		graphics.fillRect(7, 9, 3, 6);
		graphics.dispose();
		return ImageUtil.resizeImage(icon, 16, 16);
	}
}
