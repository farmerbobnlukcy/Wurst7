/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.CameraTransformViewBobbingListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EspBoxSizeSetting;
import net.wurstclient.settings.EspStyleSetting;
import net.wurstclient.settings.filterlists.ItemFilterList;
import net.wurstclient.settings.filters.FilterItemCategorySetting;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.TextRenderer3D;

import java.awt.*;
import java.util.ArrayList;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@SearchTags({"item esp", "ItemTracers", "item tracers"})
public final class ItemEspHack extends Hack implements UpdateListener,
	CameraTransformViewBobbingListener, RenderListener
{
	private final EspStyleSetting style = new EspStyleSetting();
	
	private final EspBoxSizeSetting boxSize = new EspBoxSizeSetting(
		"\u00a7lAccurate\u00a7r mode shows the exact hitbox of each item.\n"
			+ "\u00a7lFancy\u00a7r mode shows larger boxes that look better.");
	
	private final ColorSetting color = new ColorSetting("Color",
		"Items will be highlighted in this color.", Color.YELLOW);
	
	// Special settings for rotten flesh and arrows
	private final CheckboxSetting filterRottenFlesh =
		new CheckboxSetting("Filter Rotten Flesh",
			"When enabled, rotten flesh will not be highlighted.", false);
	
	private final CheckboxSetting highlightArrows =
		new CheckboxSetting("Highlight Arrows",
			"Specifically highlights arrows with a different color.", false);
	
	private final ColorSetting arrowColor = new ColorSetting("Arrow Color",
		"Arrows will be highlighted in this color when 'Highlight Arrows' is enabled.",
		new Color(0, 128, 255)); // Light blue color
	
	private final CheckboxSetting highlightExpOrbs =
		new CheckboxSetting("Highlight Exp Orbs",
			"Highlights experience orbs with a different color.", false);
	
	private final ColorSetting expOrbColor = new ColorSetting("Exp Orb Color",
		"Experience orbs will be highlighted in this color when 'Highlight Exp Orbs' is enabled.",
		new Color(0, 255, 0)); // Green color
	
	private final CheckboxSetting highlightSpecialItems = new CheckboxSetting(
		"Highlight Special Items",
		"Highlights valuable items like Elytras, Diamonds, and Netherite with special colors.",
		true);
	
	private final ColorSetting elytraColor = new ColorSetting("Elytra Color",
		"Elytras will be highlighted in this color when 'Highlight Special Items' is enabled.",
		new Color(170, 0, 170)); // Purple color
	
	private final ColorSetting diamondColor = new ColorSetting("Diamond Color",
		"Diamond items will be highlighted in this color when 'Highlight Special Items' is enabled.",
		new Color(0, 170, 255)); // Aqua blue color
	
	private final ColorSetting netheriteColor = new ColorSetting(
		"Netherite Color",
		"Netherite items will be highlighted in this color when 'Highlight Special Items' is enabled.",
		new Color(77, 0, 0)); // Dark red color
	
	private final CheckboxSetting showItemText = new CheckboxSetting(
		"Show Item Text",
		"Displays text labels above valuable items like Elytra, Shulker Boxes, etc.",
		true);
	
	private final CheckboxSetting showSpecialItemText = new CheckboxSetting(
		"Show Special Item Text",
		"Displays text labels for special items like Elytra, Shulker Box, etc.",
		true);
	
	private final CheckboxSetting showValuableItemText = new CheckboxSetting(
		"Show Valuable Item Text",
		"Displays text labels for all valuable items like diamonds, enchanted books, etc.",
		true);
	
	private final net.wurstclient.settings.SliderSetting trackingRange =
		new net.wurstclient.settings.SliderSetting("Tracking Range",
			"Items beyond this distance will be removed from tracking", 64, 16,
			256, 8,
			net.wurstclient.settings.SliderSetting.ValueDisplay.INTEGER);
	
	private final net.wurstclient.settings.SliderSetting titleMessageDelay =
		new net.wurstclient.settings.SliderSetting("Title Message Delay",
			"Delay in seconds between title messages to prevent spam", 3, 1, 10,
			0.5, net.wurstclient.settings.SliderSetting.ValueDisplay.DECIMAL);
	
	private final CheckboxSetting enableNotifications = new CheckboxSetting(
		"Enable Notifications",
		"When enabled, shows notifications for found items. When disabled, no messages will appear.",
		true);
	
	private final CheckboxSetting simpleNotifications = new CheckboxSetting(
		"Simple Notifications",
		"When enabled, notifications will be shorter and only show basic information.",
		false);
	
	private final CheckboxSetting showLookingAtInfo = new CheckboxSetting(
		"Show Looking At Info",
		"Shows the name and distance of the item you are currently looking at in the subtitle area.",
		true);
	
	private final CheckboxSetting highlightItemCount = new CheckboxSetting(
		"Highlight Item Count",
		"When enabled, the item count will be shown more prominently in the subtitle.",
		true);
	
	public enum ItemCategory
	{
		TOOLS("Tools", "Tools like swords, pickaxes, elytras, etc."),
		FOOD("Food", "Edible items like steak, fish, apples, etc."),
		MATERIALS("Materials",
			"Crafting materials like iron ingots, diamonds, netherite, etc."),
		VALUABLES("Valueables", "Valuable Items like diamonds, emeralds, etc."),
		BLOCKS("Blocks", "Building blocks that appear in the world"),
		OTHER("Other", "Items that don't fit in other categories");
		
		private final String name;
		private final String description;
		
		ItemCategory(String name, String description)
		{
			this.name = name;
			this.description = description;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
		
		public String getDescription()
		{
			return description;
		}
	}
	
	private final ItemFilterList itemFilters;
	private final ArrayList<ItemEntity> items = new ArrayList<>();
	private final ArrayList<ItemEntity> arrows = new ArrayList<>();
	private final ArrayList<net.minecraft.entity.ExperienceOrbEntity> expOrbs =
		new ArrayList<>();
	// Items that will have text labels
	private static final String[] SPECIAL_ITEMS = {"elytra", "shulker_box",
		"shulker_shell", "ender_pearl", "ender_eye", "blaze_rod", "emerald"};
	private final ArrayList<ItemEntity> specialItems = new ArrayList<>();
	private final ArrayList<ItemEntity> valuableItems = new ArrayList<>();
	
	// Specialized item lists for custom rendering
	private final ArrayList<ItemEntity> elytraItems = new ArrayList<>();
	private final ArrayList<ItemEntity> diamondItems = new ArrayList<>();
	private final ArrayList<ItemEntity> netheriteItems = new ArrayList<>();
	
	// Track previously seen items to only notify of new ones
	private final ArrayList<Integer> knownItemIds = new ArrayList<>();
	
	// Track when sounds were last played to prevent spam
	private long lastElytraSoundTime = 0;
	private long lastNetheriteItemSoundTime = 0;
	private long lastShulkerBoxSoundTime = 0;
	
	// Track unique item identifiers to prevent duplicate notifications
	private final java.util.HashSet<String> uniqueItemIdentifiers =
		new java.util.HashSet<>();
	
	// Map to track unique items by name for notification purposes
	private final java.util.HashMap<String, Integer> itemNotificationMap =
		new java.util.HashMap<>();
	private boolean elytraFound = false;
	
	// Boss bar-related fields
	private long lastBossBarUpdateTime = 0;
	
	// Title message system
	private long lastTitleMessageTime = 0;
	
	// Title message queue system
	private final java.util.Queue<TitleMessage> titleMessageQueue =
		new java.util.LinkedList<>();
	private boolean isProcessingTitleQueue = false;
	
	// Anti-spam tracking variables
	private long lastCleanupReportTime = 0;
	private int totalItemsRemovedSinceLastReport = 0;
	private static final int CLEANUP_REPORT_THRESHOLD = 10;
	private int titleMessagesShownCount = 0;
	private static final int MAX_TITLE_MESSAGES_PER_SESSION = 15;
	private final java.util.HashSet<String> recentTitleTypes =
		new java.util.HashSet<>();
	private long lastTitleTypesClearTime = 0;
	
	// Looking at item tracking
	private ItemEntity currentLookingAt = null;
	private long lastSubtitleUpdateTime = 0;
	private static final int SUBTITLE_UPDATE_INTERVAL_MS = 250;
	
	public ItemEspHack()
	{
		super("ItemESP");
		setCategory(Category.RENDER);
		
		// Add style and box size settings
		addSetting(style);
		addSetting(boxSize);
		addSetting(color);
		
		// Add special settings
		addSetting(filterRottenFlesh);
		addSetting(highlightArrows);
		addSetting(arrowColor);
		addSetting(highlightExpOrbs);
		addSetting(expOrbColor);
		addSetting(highlightSpecialItems);
		addSetting(elytraColor);
		addSetting(diamondColor);
		addSetting(netheriteColor);
		addSetting(showItemText);
		addSetting(showSpecialItemText);
		addSetting(showValuableItemText);
		addSetting(trackingRange);
		addSetting(titleMessageDelay);
		addSetting(enableNotifications);
		addSetting(simpleNotifications);
		addSetting(showLookingAtInfo);
		addSetting(highlightItemCount);
		
		// Create filters for each category
		itemFilters = new ItemFilterList(
			FilterItemCategorySetting.create(ItemCategory.TOOLS,
				stack -> isToolItem(stack), true),
			FilterItemCategorySetting.create(ItemCategory.FOOD,
				stack -> isFoodItem(stack), true),
			FilterItemCategorySetting.create(ItemCategory.MATERIALS,
				stack -> isMaterialItem(stack), true),
			FilterItemCategorySetting.create(ItemCategory.VALUABLES,
				stack -> isValuableItem(stack), true),
			FilterItemCategorySetting.create(ItemCategory.BLOCKS,
				stack -> isBlockItem(stack), true),
			FilterItemCategorySetting.create(ItemCategory.OTHER,
				stack -> isOtherItem(stack), true));
		
		// Add all filters to settings
		itemFilters.forEach(this::addSetting);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(CameraTransformViewBobbingListener.class, this);
		EVENTS.add(RenderListener.class, this);
		
		// Reset all message tracking
		lastTitleMessageTime = 0;
		titleMessagesShownCount = 0;
		totalItemsRemovedSinceLastReport = 0;
		lastCleanupReportTime = 0;
		recentTitleTypes.clear();
		lastTitleTypesClearTime = 0;
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(CameraTransformViewBobbingListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		// Clear all tracking when disabled
		knownItemIds.clear();
		itemNotificationMap.clear();
		uniqueItemIdentifiers.clear();
		titleMessageQueue.clear();
		recentTitleTypes.clear();
		titleMessagesShownCount = 0;
		
		// Clear subtitle if we were showing an item
		if(currentLookingAt != null && MC.inGameHud != null)
		{
			MC.inGameHud.setSubtitle(Text.of(""));
			currentLookingAt = null;
		}
	}
	
	@Override
	public void onUpdate()
	{
		// Clean up tracked items that are no longer valid or out of range
		cleanupTrackedItems();
		
		// Clear all item lists
		items.clear();
		arrows.clear();
		expOrbs.clear();
		specialItems.clear();
		valuableItems.clear();
		elytraItems.clear();
		diamondItems.clear();
		netheriteItems.clear();
		
		// Create a list to track new items found this update
		ArrayList<ItemEntity> newItems = new ArrayList<>();
		
		// Create maps to group items by priority
		java.util.Map<ItemPriority, java.util.List<ItemEntity>> priorityGroups =
			new java.util.HashMap<>();
		for(ItemPriority priority : ItemPriority.values())
		{
			priorityGroups.put(priority, new ArrayList<>());
		}
		
		// Track if any elytra items are found this update
		boolean foundElytra = false;
		
		// Find items using streams and filter them
		Stream<ItemEntity> stream =
			StreamSupport.stream(MC.world.getEntities().spliterator(), false)
				.filter(ItemEntity.class::isInstance).map(e -> (ItemEntity)e);
		
		// Apply category filters
		stream = itemFilters.applyTo(stream);
		
		// Process and filter items
		StreamSupport.stream(MC.world.getEntities().spliterator(), false)
			.filter(ItemEntity.class::isInstance).map(e -> (ItemEntity)e)
			.forEach(item -> {
				ItemStack stack = item.getStack();
				
				// Check for rotten flesh filter
				if(filterRottenFlesh.isChecked() && isRottenFlesh(stack))
					return;
				
				// Handle arrows separately
				if(isArrow(stack))
				{
					if(highlightArrows.isChecked())
						arrows.add(item);
					return;
				}
				
				// Apply normal category filters
				boolean passesFilter =
					(isToolItem(stack) && itemFilters.testOne(item))
						|| (isFoodItem(stack) && itemFilters.testOne(item))
						|| (isMaterialItem(stack) && itemFilters.testOne(item))
						|| (isValuableItem(stack) && itemFilters.testOne(item))
						|| (isBlockItem(stack) && itemFilters.testOne(item))
						|| (isOtherItem(stack) && itemFilters.testOne(item));
				
				if(passesFilter)
				{
					// Check if this is a new item we haven't seen before by
					// entity ID
					int itemId = item.getId();
					if(!knownItemIds.contains(itemId))
					{
						knownItemIds.add(itemId);
						
						// Check if it's a unique item we haven't seen before by
						// location/name/count
						if(isNewUniqueItem(item))
						{
							newItems.add(item);
						}
					}
					
					// Sort items into special categories if enabled
					if(highlightSpecialItems.isChecked())
					{
						String itemName =
							stack.getItem().toString().toLowerCase();
						
						if(itemName.contains("elytra"))
						{
							elytraItems.add(item);
							elytraFound = true;
						}else if(itemName.contains("diamond"))
						{
							diamondItems.add(item);
						}else if(itemName.contains("netherite"))
						{
							netheriteItems.add(item);
						}else
						{
							items.add(item);
						}
					}else
					{
						items.add(item);
					}
					
					// Track special items separately for text rendering
					if(isSpecialLabeledItem(stack))
						specialItems.add(item);
					
					// Track valuable items separately for text rendering
					if(isValuableItem(stack))
						valuableItems.add(item);
				}
			});
		
		// Send chat notifications for new items
		if(!newItems.isEmpty())
		{
			for(ItemEntity item : newItems)
			{
				ItemStack stack = item.getStack();
				
				// Only show chat notifications for specific items
				if(!shouldShowChatNotification(stack))
					continue;
					
				// Get the base item type name (e.g., "Diamond Pickaxe",
				// "Shulker Box")
				String itemTypeName = stack.getItem().getName().getString();
				
				// Get the actual display name which might include custom names
				String displayName = stack.getName().getString();
				
				// Check if the item has a custom name
				boolean hasCustomName = stack.getCustomName() != null;
				
				int count = stack.getCount();
				
				// Format message based on whether item has a custom name
				String message;
				if(hasCustomName && !itemTypeName.equals(displayName))
				{
					// Format: Found 1x Diamond Pickaxe named "Destroyer of
					// Worlds"
					message = "§a[ItemESP]§f Found: §b" + count + "x §e"
						+ itemTypeName + "§f named \"§d" + displayName + "§f\"";
				}else
				{
					// Standard format: Found 1x Diamond Pickaxe
					message = "§a[ItemESP]§f Found: §b" + count + "x §e"
						+ itemTypeName;
				}
				
				MC.inGameHud.getChatHud().addMessage(Text.of(message));
			}
		}
		
		// Group similar items for notifications
		java.util.Map<String, Integer> elytraItems = new java.util.HashMap<>();
		java.util.Map<String, Integer> netheriteItems =
			new java.util.HashMap<>();
		java.util.Map<String, Integer> shulkerBoxItems =
			new java.util.HashMap<>();
		java.util.Map<String, Integer> otherValuableItems =
			new java.util.HashMap<>();
		
		// Process notifications for special items
		for(ItemEntity item : newItems)
		{
			ItemStack stack = item.getStack();
			
			// Skip if null to avoid errors
			if(stack == null)
				continue;
			
			// Get item names
			String itemTypeName = stack.getItem().getName().getString();
			int count = stack.getCount();
			
			// Group items by type
			if(isElytraItem(stack))
			{
				playSpecialItemSound(stack);
				elytraItems.put(itemTypeName,
					elytraItems.getOrDefault(itemTypeName, 0) + count);
			}else if(isNetheriteItem(stack))
			{
				playSpecialItemSound(stack);
				netheriteItems.put(itemTypeName,
					netheriteItems.getOrDefault(itemTypeName, 0) + count);
			}else if(isShulkerBox(stack))
			{
				playSpecialItemSound(stack);
				shulkerBoxItems.put(itemTypeName,
					shulkerBoxItems.getOrDefault(itemTypeName, 0) + count);
			}else if(isValuableItem(stack))
			{
				// Only track other valuable items if they're worth notifying
				// about
				otherValuableItems.put(itemTypeName,
					otherValuableItems.getOrDefault(itemTypeName, 0) + count);
			}
		}
		
		// Queue title messages for each group
		// First priority: Elytra
		for(java.util.Map.Entry<String, Integer> entry : elytraItems.entrySet())
		{
			this.showTitleMessage("§d§lELYTRA FOUND!",
				"§e" + entry.getValue() + "x " + entry.getKey(), 60);
		}
		
		// Second priority: Netherite items
		for(java.util.Map.Entry<String, Integer> entry : netheriteItems
			.entrySet())
		{
			this.showTitleMessage("§4§lNETHERITE FOUND!",
				"§e" + entry.getValue() + "x " + entry.getKey(), 60);
		}
		
		// Third priority: Shulker Boxes
		for(java.util.Map.Entry<String, Integer> entry : shulkerBoxItems
			.entrySet())
		{
			this.showTitleMessage("§d§lSHULKER BOX FOUND!",
				"§e" + entry.getValue() + "x " + entry.getKey(), 60);
		}
		
		// If there are multiple other valuable items, create a summary message
		if(otherValuableItems.size() > 3)
		{
			int totalItems = otherValuableItems.values().stream()
				.mapToInt(Integer::intValue).sum();
			showItemSummaryMessage(java.util.Collections
				.singletonMap("valuable items", totalItems));
		}else
		{
			// Queue individual messages for a small number of valuable items
			for(java.util.Map.Entry<String, Integer> entry : otherValuableItems
				.entrySet())
			{
				this.showTitleMessage("§e§lITEM FOUND!",
					"§e" + entry.getValue() + "x " + entry.getKey(), 40);
			}
		}
		
		// Collect experience orbs if enabled
		if(highlightExpOrbs.isChecked())
		{
			StreamSupport.stream(MC.world.getEntities().spliterator(), false)
				.filter(
					e -> e instanceof net.minecraft.entity.ExperienceOrbEntity)
				.map(e -> (net.minecraft.entity.ExperienceOrbEntity)e)
				.forEach(expOrbs::add);
		}
		
		// Find closest item for boss bar display
		ItemEntity closestItem = findClosestItem();
		if(closestItem != null)
		{
			updateBossBar(closestItem);
		}
		
		// Process the next title message if the queue isn't empty and enough
		// time has passed
		if(!titleMessageQueue.isEmpty())
		{
			processNextTitleMessage();
		}
	}
	
	/**
	 * Process high priority items for title notifications
	 */
	private void processHighPriorityItems(java.util.List<ItemEntity> items,
		ItemPriority priority)
	{
		if(items.isEmpty())
			return;
		
		// Group items by type to consolidate notifications
		java.util.Map<String, Integer> itemCounts = new java.util.HashMap<>();
		
		for(ItemEntity item : items)
		{
			ItemStack stack = item.getStack();
			String itemName = stack.getItem().getName().getString();
			int count = stack.getCount();
			
			itemCounts.put(itemName,
				itemCounts.getOrDefault(itemName, 0) + count);
		}
		
		// Select title and color based on priority
		String titleText;
		String titleColor;
		int displayTicks;
		
		switch(priority)
		{
			case SHULKER:
			titleText = "SHULKER BOX FOUND!";
			titleColor = "§d§l"; // Light purple
			displayTicks = 60;
			break;
			
			case NETHERITE:
			titleText = "NETHERITE FOUND!";
			titleColor = "§4§l"; // Dark red
			displayTicks = 60;
			break;
			
			case DIAMOND_GEAR:
			titleText = "DIAMOND GEAR FOUND!";
			titleColor = "§b§l"; // Aqua
			displayTicks = 50;
			break;
			
			case DIAMOND_EMERALD:
			titleText = "VALUABLES FOUND!";
			titleColor = "§a§l"; // Green
			displayTicks = 40;
			break;
			
			case GUNPOWDER:
			titleText = "GUNPOWDER FOUND!";
			titleColor = "§7§l"; // Gray
			displayTicks = 30;
			break;
			
			default:
			titleText = "ITEM FOUND!";
			titleColor = "§e§l"; // Yellow
			displayTicks = 30;
			break;
		}
		
		// If multiple types, show a summary
		if(itemCounts.size() > 1)
		{
			int totalItems =
				itemCounts.values().stream().mapToInt(Integer::intValue).sum();
			showTitleMessage(titleColor + titleText,
				"§e" + totalItems + "x items", displayTicks);
		}
		// Otherwise show the specific item
		else if(itemCounts.size() == 1)
		{
			java.util.Map.Entry<String, Integer> entry =
				itemCounts.entrySet().iterator().next();
			showTitleMessage(titleColor + titleText,
				"§e" + entry.getValue() + "x " + entry.getKey(), displayTicks);
		}
		
		// Send chat messages for all items in this priority
		for(ItemEntity item : items)
		{
			sendChatMessage(item);
		}
	}
	
	/**
	 * Send a chat message for an item
	 */
	private void sendChatMessage(ItemEntity itemEntity)
	{
		ItemStack stack = itemEntity.getStack();
		if(stack == null)
			return;
		
		// Skip non-priority items
		if(getItemPriority(stack) == ItemPriority.NONE)
			return;
		
		// Get the base item type name
		String itemTypeName = stack.getItem().getName().getString();
		
		// Get the actual display name which might include custom names
		String displayName = stack.getName().getString();
		
		// Check if the item has a custom name
		boolean hasCustomName = stack.getCustomName() != null;
		
		int count = stack.getCount();
		
		// Format message based on whether item has a custom name
		String message;
		if(hasCustomName && !itemTypeName.equals(displayName))
		{
			// Format: Found 1x Diamond Pickaxe named "Destroyer of Worlds"
			message = "§a[ItemESP]§f Found: §b" + count + "x §e" + itemTypeName
				+ "§f named \"§d" + displayName + "§f\"";
		}else
		{
			// Standard format: Found 1x Diamond Pickaxe
			message = "§a[ItemESP]§f Found: §b" + count + "x §e" + itemTypeName;
		}
		
		MC.inGameHud.getChatHud().addMessage(Text.of(message));
	}
	
	private boolean isRottenFlesh(ItemStack stack)
	{
		return stack.isOf(Items.ROTTEN_FLESH);
	}
	
	private boolean isArrow(ItemStack stack)
	{
		return stack.isOf(Items.ARROW) || stack.isOf(Items.TIPPED_ARROW)
			|| stack.isOf(Items.SPECTRAL_ARROW);
	}
	
	private boolean isToolItem(ItemStack stack)
	{
		String itemName = stack.getItem().toString().toLowerCase();
		return itemName.endsWith("sword") || itemName.endsWith("axe")
			|| itemName.endsWith("pickaxe") || itemName.endsWith("shovel")
			|| itemName.endsWith("hoe") || itemName.contains("elytra")
			|| itemName.contains("bow") || itemName.contains("trident")
			|| itemName.contains("fishing_rod") || itemName.contains("shield")
			|| itemName.contains("helmet") || itemName.contains("chestplate")
			|| itemName.contains("leggings") || itemName.contains("boots");
	}
	
	private boolean isFoodItem(ItemStack stack)
	{
		String itemName = stack.getItem().toString().toLowerCase();
		return itemName.contains("apple") || itemName.contains("bread")
			|| itemName.contains("fish") || itemName.contains("beef")
			|| itemName.contains("pork") || itemName.contains("chicken")
			|| itemName.contains("carrot") || itemName.contains("potato")
			|| itemName.contains("steak") || itemName.contains("mutton")
			|| itemName.contains("rabbit") || itemName.contains("cookie")
			|| itemName.contains("cake") || itemName.contains("melon")
			|| itemName.contains("berries");
	}
	
	private boolean isMaterialItem(ItemStack stack)
	{
		String itemName = stack.getItem().toString().toLowerCase();
		return itemName.contains("gold") || itemName.contains("iron")
			|| itemName.contains("nugget") || itemName.contains("stick")
			|| itemName.contains("string") || itemName.contains("redstone")
			|| itemName.contains("dye") || itemName.contains("lapis");
	}
	
	private boolean isValuableItem(ItemStack stack)
	{
		String itemName = stack.getItem().toString().toLowerCase();
		return itemName.contains("diamond") || itemName.contains("emerald")
			|| itemName.contains("obsidian") || itemName.contains("netherite")
			|| itemName.contains("powder") || itemName.contains("shulker")
			|| itemName.contains("bundle") || itemName.contains("book")
			|| itemName.contains("lead") || itemName.contains("shield")
			|| itemName.contains("tnt") || itemName.contains("work")
			|| itemName.contains("golden") || itemName.contains("mace")
			|| itemName.contains("trident") || itemName.contains("enchanted");
	}
	
	private boolean isSpecialItem(ItemStack stack)
	{
		String itemName = stack.getItem().toString().toLowerCase();
		return itemName.contains("elytra") || itemName.contains("shulker_box")
			|| itemName.contains("shulker_shell")
			|| itemName.contains("ender_pearl")
			|| itemName.contains("ender_eye") || itemName.contains("blaze_rod")
			|| itemName.equals("emerald");
	}
	
	private boolean isBlockItem(ItemStack stack)
	{
		String itemName = stack.getItem().toString().toLowerCase();
		return itemName.endsWith("block") || itemName.contains("stone")
			|| itemName.contains("dirt") || itemName.contains("wood")
			|| itemName.contains("planks") || itemName.contains("log")
			|| itemName.contains("sand") || itemName.contains("gravel")
			|| itemName.contains("glass") || itemName.contains("ore")
			|| itemName.contains("wool") || itemName.contains("obsidian");
	}
	
	private boolean isOtherItem(ItemStack stack)
	{
		// If it doesn't match any other category, it's "other"
		return !isToolItem(stack) && !isFoodItem(stack)
			&& !isMaterialItem(stack) && !isValuableItem(stack)
			&& !isBlockItem(stack);
	}
	
	// Define priority levels for item notifications
	private enum ItemPriority
	{
		NONE, // Non-priority items (no notifications)
		LOW, // Basic items (chat only)
		GUNPOWDER, // Gunpowder (chat, title if nothing better)
		DIAMOND_EMERALD, // Diamonds/Emeralds (chat, title if nothing better)
		DIAMOND_GEAR, // Diamond armor/tools (chat, title if nothing better)
		NETHERITE, // Netherite items (title message)
		SHULKER // Shulker boxes (highest priority - title message)
	}
	
	/**
	 * Determines the notification priority of an item
	 */
	private ItemPriority getItemPriority(ItemStack stack)
	{
		String itemName = stack.getItem().toString().toLowerCase();
		
		// Check for highest priority items first
		if(itemName.contains("shulker_box"))
			return ItemPriority.SHULKER;
		
		if(itemName.contains("netherite"))
			return ItemPriority.NETHERITE;
		
		// Check for diamond armor and tools
		if((itemName.contains("diamond") && (itemName.contains("helmet")
			|| itemName.contains("chestplate") || itemName.contains("leggings")
			|| itemName.contains("boots") || itemName.contains("sword")
			|| itemName.contains("pickaxe") || itemName.contains("axe")
			|| itemName.contains("shovel") || itemName.contains("hoe"))))
			return ItemPriority.DIAMOND_GEAR;
		
		// Check for diamonds and emeralds
		if(itemName.contains("diamond") || itemName.contains("emerald"))
			return ItemPriority.DIAMOND_EMERALD;
		
		// Check for gunpowder
		if(itemName.contains("gunpowder"))
			return ItemPriority.GUNPOWDER;
		
		// Check if this is any priority item at all
		if(itemName.contains("enderchest") || itemName.contains("blaze_rod"))
			return ItemPriority.LOW;
		
		return ItemPriority.NONE;
	}
	
	/**
	 * Checks if an item should show any notification (title or chat)
	 */
	private boolean shouldShowNotification(ItemStack stack)
	{
		return getItemPriority(stack) != ItemPriority.NONE;
	}
	
	/**
	 * Checks if an item should show a title message (based on its priority)
	 */
	private boolean shouldShowTitleMessage(ItemStack stack)
	{
		ItemPriority priority = getItemPriority(stack);
		return priority != ItemPriority.NONE && priority != ItemPriority.LOW;
	}
	
	/**
	 * Checks if an item is critically important and should always trigger
	 * alerts
	 * regardless of notification settings
	 */
	private boolean isCriticalImportantItem(ItemStack stack)
	{
		if(stack == null)
			return false;
		
		String itemName = stack.getItem().toString().toLowerCase();
		return itemName.contains("netherite")
			|| itemName.contains("shulker_box");
	}
	
	/**
	 * Checks if an item entity contains critically important items
	 */
	private boolean containsCriticalItems(ItemEntity item)
	{
		if(item == null)
			return false;
		return isCriticalImportantItem(item.getStack());
	}
	
	/**
	 * Generates a unique identifier for an item based on location, name, and
	 * count
	 *
	 * @param item
	 *            The ItemEntity to generate an identifier for
	 * @return A unique string identifier
	 */
	private String generateUniqueItemIdentifier(ItemEntity item)
	{
		if(item == null || item.getStack() == null)
			return "";
		
		// Get position rounded to the nearest block
		int x = (int)Math.round(item.getX());
		int y = (int)Math.round(item.getY());
		int z = (int)Math.round(item.getZ());
		
		// Get item details
		String itemName = item.getStack().getItem().toString();
		int count = item.getStack().getCount();
		
		// Combine into unique ID
		return x + "," + y + "," + z + ":" + itemName + ":" + count;
	}
	
	/**
	 * Checks if an item has been seen before (using its unique identifier)
	 *
	 * @param item
	 *            The ItemEntity to check
	 * @return true if this is a new item, false if we've seen it before
	 */
	private boolean isNewUniqueItem(ItemEntity item)
	{
		String uniqueId = generateUniqueItemIdentifier(item);
		if(uniqueId.isEmpty())
			return false;
		
		// If we haven't seen this item before, add it and return true
		if(!uniqueItemIdentifiers.contains(uniqueId))
		{
			uniqueItemIdentifiers.add(uniqueId);
			return true;
		}
		
		return false;
	}
	
	private boolean shouldShowChatNotification(ItemStack stack)
	{
		String itemName = stack.getItem().toString().toLowerCase();
		return itemName.contains("diamond") || itemName.contains("emerald")
			|| itemName.contains("netherrite")
			|| itemName.contains("enderchest")
			|| itemName.contains("blaze_rod");
	}
	
	private boolean isSpecialLabeledItem(ItemStack stack)
	{
		String itemName = stack.getItem().toString().toLowerCase();
		return itemName.contains("netherrite") || itemName.contains("shulker");
	}
	
	/**
	 * Class to store title message information in the queue
	 */
	private static class TitleMessage
	{
		final String title;
		final String subtitle;
		final int ticks;
		
		TitleMessage(String title, String subtitle, int ticks)
		{
			this.title = title;
			this.subtitle = subtitle;
			this.ticks = ticks;
		}
	}
	
	/**
	 * Shows a title message to the player or queues it if other messages are
	 * being shown
	 */
	private void showTitleMessage(String title, String subtitle, int ticks)
	{
		// Skip if notifications are disabled
		if(!enableNotifications.isChecked())
			return;
			
		// Check if we've reached the max number of notifications for this
		// session
		if(titleMessagesShownCount >= MAX_TITLE_MESSAGES_PER_SESSION)
		{
			// Only allow notifications for critically important items
			boolean isCritical = title.contains("ELYTRA")
				|| title.contains("NETHERITE") || title.contains("SHULKER");
			
			if(!isCritical)
				return;
		}
		
		// Simplify subtitle if simple notifications are enabled
		if(simpleNotifications.isChecked() && subtitle.contains("x "))
		{
			// Extract just the item name without quantity info
			subtitle = subtitle.replaceAll("§e\\d+x §f", "")
				.replaceAll("§e\\d+x ", "");
		}
		
		// Increment the counter for title messages shown
		titleMessagesShownCount++;
		
		// Add the message to the queue
		titleMessageQueue.add(new TitleMessage(title, subtitle, ticks));
		
		// If we're not already processing the queue, start processing it
		if(!isProcessingTitleQueue)
		{
			processNextTitleMessage();
		}
	}
	
	/**
	 * Creates a summary message for multiple items of the same type
	 */
	private void showItemSummaryMessage(java.util.Map<String, Integer> items)
	{
		// If no items, do nothing
		if(items.isEmpty())
			return;
		
		String title = "§e§lITEMS FOUND!";
		
		// Get the first item type and count
		java.util.Map.Entry<String, Integer> entry =
			items.entrySet().iterator().next();
		String itemType = entry.getKey();
		int count = entry.getValue();
		
		String subtitle = "§b" + count + "x §f" + itemType;
		
		// Add the summary message to the queue
		titleMessageQueue.add(new TitleMessage(title, subtitle, 40));
		
		// If we're not already processing the queue, start processing it
		if(!isProcessingTitleQueue)
		{
			processNextTitleMessage();
		}
	}
	
	/**
	 * Processes the next message in the title message queue
	 */
	private void processNextTitleMessage()
	{
		if(titleMessageQueue.isEmpty())
		{
			isProcessingTitleQueue = false;
			return;
		}
		
		isProcessingTitleQueue = true;
		
		// Clear the recent title types every 60 seconds to allow repeat
		// notifications later
		long currentTime = System.currentTimeMillis();
		if(currentTime - lastTitleTypesClearTime > 60000)
		{
			recentTitleTypes.clear();
			lastTitleTypesClearTime = currentTime;
		}
		
		// Check if enough time has passed since the last message
		long delay = (long)(titleMessageDelay.getValue() * 1000);
		if(currentTime - lastTitleMessageTime < delay)
		{
			return; // Will be called again in onUpdate
		}
		
		// Get the next message from the queue without removing it yet
		TitleMessage message = titleMessageQueue.peek();
		
		if(message == null || MC.player == null || MC.inGameHud == null)
		{
			titleMessageQueue.clear(); // Clear the queue if we can't process
			// messages
			isProcessingTitleQueue = false;
			return;
		}
		
		// Check if we've recently shown a title with this text
		String titleType = message.title.replaceAll("§[0-9a-fklmnor]", "");
		if(recentTitleTypes.contains(titleType))
		{
			// Skip this message and remove it from queue
			titleMessageQueue.poll();
			
			// If the queue is now empty, mark as not processing
			if(titleMessageQueue.isEmpty())
			{
				isProcessingTitleQueue = false;
			}
			return;
		}
		
		// Remove the message from the queue and show it
		titleMessageQueue.poll();
		
		// Remember this title type to avoid duplicates
		recentTitleTypes.add(titleType);
		
		// Show title with fade-in, stay, and fade-out times
		MC.inGameHud.setTitle(Text.of(message.title));
		MC.inGameHud.setSubtitle(Text.of(message.subtitle));
		MC.inGameHud.setTitleTicks(10, message.ticks, 20);
		
		// Update the last message time
		lastTitleMessageTime = currentTime;
		
		// If the queue is now empty, mark as not processing
		if(titleMessageQueue.isEmpty())
		{
			isProcessingTitleQueue = false;
		}
	}
	
	// This method has been replaced by updateBossBar and is removed
	
	/**
	 * Helper methods to check for special item types
	 */
	private boolean isElytraItem(ItemStack stack)
	{
		return stack.getItem().toString().toLowerCase().contains("elytra");
	}
	
	private boolean isNetheriteItem(ItemStack stack)
	{
		return stack.getItem().toString().toLowerCase().contains("netherite");
	}
	
	private boolean isShulkerBox(ItemStack stack)
	{
		return stack.getItem().toString().toLowerCase().contains("shulker_box");
	}
	
	/**
	 * Updates the boss bar showing distance to nearest item
	 */
	private void updateBossBar(ItemEntity closestItem)
	{
		// Only update boss bar every 500ms to prevent flickering
		if(System.currentTimeMillis() - lastBossBarUpdateTime < 500)
			return;
		
		lastBossBarUpdateTime = System.currentTimeMillis();
		
		if(MC.player == null || closestItem == null || MC.inGameHud == null)
			return;
		
		// Calculate distance to item
		double distance = MC.player.squaredDistanceTo(closestItem);
		distance = Math.sqrt(distance);
		
		// Format the item name and distance
		String itemName =
			closestItem.getStack().getItem().getName().getString();
		
		// Create actionbar message
		String message = "§e" + itemName + " §f- Distance: §b"
			+ String.format("%.2f", distance) + "m";
		
		// Display as action bar message (appears above hotbar)
		MC.inGameHud.setOverlayMessage(Text.of(message), false);
	}
	
	/**
	 * Play appropriate sound effect for special item
	 */
	private void playSpecialItemSound(ItemStack stack)
	{
		if(MC.player == null)
			return;
		
		if(isElytraItem(stack)
			&& System.currentTimeMillis() - lastElytraSoundTime > 5000)
		{
			// Play anvil sound for elytra
			MC.world.playSound(MC.player, MC.player.getBlockPos(),
				SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 1.0F,
				1.0F);
			lastElytraSoundTime = System.currentTimeMillis();
		}else if(isNetheriteItem(stack)
			&& System.currentTimeMillis() - lastNetheriteItemSoundTime > 5000)
		{
			// Play bell sound for netherite
			MC.world.playSound(MC.player, MC.player.getBlockPos(),
				SoundEvents.BLOCK_BELL_USE, SoundCategory.PLAYERS, 1.0F, 0.5F);
			lastNetheriteItemSoundTime = System.currentTimeMillis();
		}else if(isShulkerBox(stack)
			&& System.currentTimeMillis() - lastShulkerBoxSoundTime > 5000)
		{
			// Play ender chest sound for shulker boxes
			MC.world.playSound(MC.player, MC.player.getBlockPos(),
				SoundEvents.BLOCK_ENDER_CHEST_OPEN, SoundCategory.PLAYERS, 1.0F,
				1.0F);
			lastShulkerBoxSoundTime = System.currentTimeMillis();
		}
	}
	
	/**
	 * Removes items from tracking list if they no longer exist
	 * or are beyond the tracking range
	 */
	private void cleanupTrackedItems()
	{
		if(MC.world == null || MC.player == null)
			return;
		
		// Get the maximum tracking range
		double maxRangeSq = Math.pow(trackingRange.getValue(), 2);
		
		// Create a list of IDs to remove
		ArrayList<Integer> idsToRemove = new ArrayList<>();
		
		// Check each tracked item ID
		for(Integer id : knownItemIds)
		{
			// Try to find the entity with this ID
			net.minecraft.entity.Entity entity = MC.world.getEntityById(id);
			
			// Check if entity still exists and is an item
			if(entity == null || !(entity instanceof ItemEntity))
			{
				idsToRemove.add(id);
				continue;
			}
			
			// Check if item is out of range
			double distanceSq = MC.player.squaredDistanceTo(entity);
			if(distanceSq > maxRangeSq)
			{
				idsToRemove.add(id);
			}
		}
		
		// Remove all invalid/out-of-range IDs
		knownItemIds.removeAll(idsToRemove);
		
		// Add to the running total of removed items
		totalItemsRemovedSinceLastReport += idsToRemove.size();
		
		// Periodically clear unique item identifiers to avoid memory bloat
		// Do this every 5 minutes (300000 ms) but don't send a message
		if(System.currentTimeMillis() % 300000 < 1000)
		{
			uniqueItemIdentifiers.clear();
		}
		
		// Only report cleanup if it's been at least 30 seconds since last
		// report
		// AND we've removed a significant number of items
		long currentTime = System.currentTimeMillis();
		if(totalItemsRemovedSinceLastReport >= CLEANUP_REPORT_THRESHOLD
			&& currentTime - lastCleanupReportTime > 30000
			&& MC.inGameHud != null)
		{
			// Only show the message if notifications are enabled and not set to
			// simple
			if(enableNotifications.isChecked()
				&& !simpleNotifications.isChecked())
			{
				MC.inGameHud.getChatHud()
					.addMessage(Text.of("§a[ItemESP]§f Removed §b"
						+ totalItemsRemovedSinceLastReport
						+ "§f items from tracking"));
			}
			
			// Reset the counter and update the last report time
			totalItemsRemovedSinceLastReport = 0;
			lastCleanupReportTime = currentTime;
		}
	}
	
	/**
	 * Finds the closest item to the player
	 */
	private ItemEntity findClosestItem()
	{
		if(MC.player == null || MC.world == null)
			return null;
		
		ItemEntity closest = null;
		double closestDistSq = Double.MAX_VALUE;
		
		// Combine all item lists
		ArrayList<ItemEntity> allItems = new ArrayList<>();
		allItems.addAll(items);
		allItems.addAll(arrows);
		allItems.addAll(elytraItems);
		allItems.addAll(diamondItems);
		allItems.addAll(netheriteItems);
		
		// Find the closest item
		for(ItemEntity item : allItems)
		{
			double distSq = MC.player.squaredDistanceTo(item);
			if(distSq < closestDistSq)
			{
				closest = item;
				closestDistSq = distSq;
			}
		}
		
		// If exp orbs are enabled, check if any are closer than the closest
		// item
		if(highlightExpOrbs.isChecked() && !expOrbs.isEmpty())
		{
			for(net.minecraft.entity.ExperienceOrbEntity orb : expOrbs)
			{
				double distSq = MC.player.squaredDistanceTo(orb);
				// We only care if they're closer than our current closest item
				if(distSq < closestDistSq)
				{
					// Return the item we found so far, since we can't return an
					// exp orb here
					return closest;
				}
			}
		}
		
		return closest;
	}
	
	@Override
	public void onCameraTransformViewBobbing(
		CameraTransformViewBobbingEvent event)
	{
		if(style.hasLines())
			event.cancel();
	}
	
	/**
	 * Renders text labels above special items
	 */
	private void renderSpecialItemText(MatrixStack matrixStack,
		float partialTicks)
	{
		if(!showSpecialItemText.isChecked() || specialItems.isEmpty())
			return;
		
		// Render text for each special item
		for(ItemEntity item : specialItems)
		{
			// Get the item name and bounding box
			Text itemText = item.getStack().getItem().getName();
			Box box = EntityUtils.getLerpedBox(item, partialTicks);
			
			// Render the text above the item
			TextRenderer3D.renderTextAboveBox(matrixStack, itemText, box, 0.3,
				0xFFFFFFFF, true);
		}
	}
	
	/**
	 * Renders text labels above valuable items
	 */
	private void renderValuableItemText(MatrixStack matrixStack,
		float partialTicks)
	{
		if(!showValuableItemText.isChecked() || valuableItems.isEmpty())
			return;
		
		for(ItemEntity item : valuableItems)
		{
			// Skip special items if they're already being rendered by the
			// special item text renderer
			if(specialItems.contains(item) && showSpecialItemText.isChecked())
				continue;
			
			// Get item name and bounding box
			Text itemText = item.getStack().getItem().getName();
			Box box = EntityUtils.getLerpedBox(item, partialTicks);
			
			// Render the text above the item
			TextRenderer3D.renderTextAboveBox(matrixStack, itemText, box, 0.3,
				0xFFFFFFFF, true);
		}
	}
	
	private void renderItemText(MatrixStack matrixStack, float partialTicks)
	{
		if(!showItemText.isChecked() || specialItems.isEmpty())
			return;
		
		for(ItemEntity item : specialItems)
		{
			// Get item name and bounding box
			Text itemText = item.getStack().getItem().getName();
			Box box = EntityUtils.getLerpedBox(item, partialTicks);
			
			// Render the text above the item
			TextRenderer3D.renderTextAboveBox(matrixStack, itemText, box, 0.3,
				0xFFFFFFFF, true);
		}
	}
	
	/**
	 * Finds which item entity the player is currently looking at
	 *
	 * @param partialTicks
	 *            Partial ticks for smooth rendering
	 * @return The item entity the player is looking at, or null if none
	 */
	private ItemEntity findItemLookingAt(float partialTicks)
	{
		if(MC.player == null || MC.world == null)
			return null;
		
		// Get player's eye position and look vector
		Vec3d eyePos = MC.player.getEyePos();
		Vec3d lookVec = MC.player.getRotationVec(partialTicks).normalize();
		
		// Define max distance to search
		double maxDist = 30.0;
		
		// Prepare variables for closest hit
		ItemEntity closestItem = null;
		double closestDist = Double.MAX_VALUE;
		
		// Check all visible items
		ArrayList<ItemEntity> allItems = new ArrayList<>();
		allItems.addAll(items);
		allItems.addAll(arrows);
		allItems.addAll(elytraItems);
		allItems.addAll(diamondItems);
		allItems.addAll(netheriteItems);
		
		for(ItemEntity item : allItems)
		{
			// Get the item's position
			Vec3d itemPos = item.getPos().add(0, item.getHeight() / 2, 0);
			
			// Vector from eye to item center
			Vec3d eyeToItem = itemPos.subtract(eyePos);
			double distToItem = eyeToItem.length();
			
			// Skip if too far
			if(distToItem > maxDist)
				continue;
			
			// Project the eye-to-item vector onto the look vector
			double projection = eyeToItem.dotProduct(lookVec);
			
			// Skip if item is behind the player
			if(projection <= 0)
				continue;
			
			// Calculate the closest point on the ray to the item
			Vec3d projectedPoint = eyePos.add(lookVec.multiply(projection));
			
			// Calculate distance from this point to the item center
			double distFromRay = projectedPoint.distanceTo(itemPos);
			
			// Size factor based on distance (makes items easier to target from
			// further away)
			double sizeFactor = 0.8 + (distToItem / maxDist) * 1.0;
			
			// Check if we're looking close enough to the item
			// The hitbox increases slightly with distance to make distant items
			// easier to target
			double hitboxSize =
				Math.max(item.getWidth(), item.getHeight()) * sizeFactor;
			
			if(distFromRay < hitboxSize && projection < closestDist)
			{
				closestDist = projection;
				closestItem = item;
			}
		}
		
		return closestItem;
	}
	
	/**
	 * Updates the subtitle with info about the item player is looking at
	 */
	private void updateLookingAtSubtitle(float partialTicks)
	{
		if(!showLookingAtInfo.isChecked() || MC.player == null
			|| MC.inGameHud == null)
			return;
			
		// Don't show looking at info if we're currently displaying a title
		// message
		if(isProcessingTitleQueue || !titleMessageQueue.isEmpty()
			|| (System.currentTimeMillis() - lastTitleMessageTime < 2000))
			return;
		
		// Only update every SUBTITLE_UPDATE_INTERVAL_MS to prevent flickering
		long currentTime = System.currentTimeMillis();
		if(currentTime - lastSubtitleUpdateTime < SUBTITLE_UPDATE_INTERVAL_MS)
			return;
		
		lastSubtitleUpdateTime = currentTime;
		
		// Find what item we're looking at
		ItemEntity lookingAt = findItemLookingAt(partialTicks);
		
		// If we're not looking at anything, clear the subtitle if we previously
		// were
		if(lookingAt == null)
		{
			if(currentLookingAt != null)
			{
				MC.inGameHud.setSubtitle(Text.of(""));
				currentLookingAt = null;
			}
			return;
		}
		
		// Calculate distance
		double distance = MC.player.squaredDistanceTo(lookingAt);
		distance = Math.sqrt(distance);
		
		// Format item name and distance
		ItemStack stack = lookingAt.getStack();
		String itemName = stack.getName().getString();
		int count = stack.getCount();
		
		// Build the subtitle text with appropriate colors
		String subtitle;
		String countStr;
		
		// Format the count string based on setting
		if(count > 1)
		{
			if(highlightItemCount.isChecked())
			{
				countStr = " §l[x" + count + "]§r";
			}else
			{
				countStr = " §fx" + count;
			}
		}else
		{
			countStr = "";
		}
		
		// Use different colors based on item rarity/value
		if(isElytraItem(stack))
		{
			subtitle = "§d§lLooking at: §d" + itemName + countStr + " §7(§f"
				+ String.format("%.1f", distance) + "m§7)";
		}else if(isNetheriteItem(stack))
		{
			subtitle = "§4§lLooking at: §4" + itemName + countStr + " §7(§f"
				+ String.format("%.1f", distance) + "m§7)";
		}else if(isShulkerBox(stack))
		{
			subtitle = "§5§lLooking at: §5" + itemName + countStr + " §7(§f"
				+ String.format("%.1f", distance) + "m§7)";
		}else if(stack.getItem().toString().toLowerCase().contains("diamond"))
		{
			subtitle = "§b§lLooking at: §b" + itemName + countStr + " §7(§f"
				+ String.format("%.1f", distance) + "m§7)";
		}else if(isValuableItem(stack))
		{
			subtitle = "§a§lLooking at: §a" + itemName + countStr + " §7(§f"
				+ String.format("%.1f", distance) + "m§7)";
		}else
		{
			subtitle = "§e§lLooking at: §e" + itemName + countStr + " §7(§f"
				+ String.format("%.1f", distance) + "m§7)";
		}
		
		// Set the subtitle
		MC.inGameHud.setSubtitle(Text.of(subtitle));
		
		// Store current looking at
		currentLookingAt = lookingAt;
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		int lineColor = color.getColorI(0x80);
		int arrowLineColor = arrowColor.getColorI(0x80);
		int expOrbLineColor = expOrbColor.getColorI(0x80);
		int elytraLineColor = elytraColor.getColorI(0x80);
		int diamondLineColor = diamondColor.getColorI(0x80);
		int netheriteLineColor = netheriteColor.getColorI(0x80);
		
		double extraSize = boxSize.getExtraSize() / 2;
		
		// Check if player is flying with elytra
		boolean isElytraFlying = MC.player != null && MC.player.isGliding();
		
		// Render regular items with boxes if enabled
		if(style.hasBoxes())
		{
			// Render regular items
			if(!items.isEmpty())
			{
				ArrayList<Box> boxes = new ArrayList<>();
				
				// Add all items that passed the filters
				for(ItemEntity e : items)
				{
					boxes.add(EntityUtils.getLerpedBox(e, partialTicks)
						.offset(0, extraSize, 0).expand(extraSize));
				}
				
				RenderUtils.drawOutlinedBoxes(matrixStack, boxes, lineColor,
					false);
			}
			
			// Render arrows separately
			if(!arrows.isEmpty() && highlightArrows.isChecked())
			{
				ArrayList<Box> arrowBoxes = new ArrayList<>();
				
				// Add all arrows
				for(ItemEntity e : arrows)
				{
					arrowBoxes.add(EntityUtils.getLerpedBox(e, partialTicks)
						.offset(0, extraSize, 0).expand(extraSize));
				}
				
				RenderUtils.drawOutlinedBoxes(matrixStack, arrowBoxes,
					arrowLineColor, false);
			}
			
			// Render experience orbs separately
			if(!expOrbs.isEmpty() && highlightExpOrbs.isChecked())
			{
				ArrayList<Box> expOrbBoxes = new ArrayList<>();
				
				// Add all exp orbs
				for(net.minecraft.entity.ExperienceOrbEntity e : expOrbs)
				{
					expOrbBoxes.add(EntityUtils.getLerpedBox(e, partialTicks)
						.offset(0, extraSize, 0).expand(extraSize * 0.5));
				}
				
				// Draw filled boxes first with less opacity
				RenderUtils.drawSolidBoxes(matrixStack, expOrbBoxes,
					expOrbColor.getColorI(0x30), false);
				
				// Draw outlined boxes
				RenderUtils.drawOutlinedBoxes(matrixStack, expOrbBoxes,
					expOrbLineColor, false);
			}
			
			// Render special items if enabled
			if(highlightSpecialItems.isChecked())
			{
				// Render elytra items with custom box style
				if(!elytraItems.isEmpty())
				{
					ArrayList<Box> boxes = new ArrayList<>();
					for(ItemEntity e : elytraItems)
					{
						boxes.add(EntityUtils.getLerpedBox(e, partialTicks)
							.offset(0, extraSize, 0).expand(extraSize + 0.05));
					}
					
					// Draw filled boxes first with less opacity
					RenderUtils.drawSolidBoxes(matrixStack, boxes,
						elytraColor.getColorI(0x30), false);
					
					// Draw outlined boxes with more opacity
					RenderUtils.drawOutlinedBoxes(matrixStack, boxes,
						elytraLineColor, false);
				}
				
				// Render diamond items with custom box style
				if(!diamondItems.isEmpty())
				{
					ArrayList<Box> boxes = new ArrayList<>();
					for(ItemEntity e : diamondItems)
					{
						boxes.add(EntityUtils.getLerpedBox(e, partialTicks)
							.offset(0, extraSize, 0).expand(extraSize + 0.02));
					}
					
					// Draw filled boxes first with less opacity
					RenderUtils.drawSolidBoxes(matrixStack, boxes,
						diamondColor.getColorI(0x30), false);
					
					// Draw outlined boxes
					RenderUtils.drawOutlinedBoxes(matrixStack, boxes,
						diamondLineColor, false);
				}
				
				// Render netherite items with custom box style
				if(!netheriteItems.isEmpty())
				{
					ArrayList<Box> boxes = new ArrayList<>();
					for(ItemEntity e : netheriteItems)
					{
						boxes.add(EntityUtils.getLerpedBox(e, partialTicks)
							.offset(0, extraSize, 0).expand(extraSize + 0.03));
					}
					
					// Draw filled boxes first with less opacity
					RenderUtils.drawSolidBoxes(matrixStack, boxes,
						netheriteColor.getColorI(0x30), false);
					
					// Draw outlined boxes
					RenderUtils.drawOutlinedBoxes(matrixStack, boxes,
						netheriteLineColor, false);
				}
			}
		}
		
		// Render tracer lines if enabled
		if(style.hasLines())
		{
			// Render regular item tracers
			if(!items.isEmpty())
			{
				ArrayList<Vec3d> ends = new ArrayList<>();
				
				// Add tracers for all filtered items
				for(ItemEntity e : items)
				{
					Vec3d center =
						EntityUtils.getLerpedBox(e, partialTicks).getCenter();
					
					// Skip items below sea level when flying with elytra
					if(isElytraFlying && center.y < 63)
						continue;
					
					ends.add(center);
				}
				
				if(!ends.isEmpty())
				{
					RenderUtils.drawTracers(matrixStack, partialTicks, ends,
						lineColor, false);
				}
			}
			
			// Render arrow tracers separately
			if(!arrows.isEmpty() && highlightArrows.isChecked())
			{
				ArrayList<Vec3d> arrowEnds = new ArrayList<>();
				
				// Add tracers for all arrows
				for(ItemEntity e : arrows)
				{
					Vec3d center =
						EntityUtils.getLerpedBox(e, partialTicks).getCenter();
					
					// Skip arrows below sea level when flying with elytra
					if(isElytraFlying && center.y < 63)
						continue;
					
					arrowEnds.add(center);
				}
				
				if(!arrowEnds.isEmpty())
				{
					RenderUtils.drawTracers(matrixStack, partialTicks,
						arrowEnds, arrowLineColor, false);
				}
			}
			
			// Render experience orb tracers separately
			if(!expOrbs.isEmpty() && highlightExpOrbs.isChecked())
			{
				ArrayList<Vec3d> expOrbEnds = new ArrayList<>();
				
				// Add tracers for all exp orbs
				for(net.minecraft.entity.ExperienceOrbEntity e : expOrbs)
				{
					Vec3d center =
						EntityUtils.getLerpedBox(e, partialTicks).getCenter();
					
					// Skip orbs below sea level when flying with elytra
					if(isElytraFlying && center.y < 63)
						continue;
					
					expOrbEnds.add(center);
				}
				
				if(!expOrbEnds.isEmpty())
				{
					RenderUtils.drawTracers(matrixStack, partialTicks,
						expOrbEnds, expOrbLineColor, false);
				}
			}
			
			// Render special item tracers if enabled
			if(highlightSpecialItems.isChecked())
			{
				// Render elytra tracers with custom color and style
				if(!elytraItems.isEmpty())
				{
					ArrayList<Vec3d> ends = new ArrayList<>();
					for(ItemEntity e : elytraItems)
					{
						Vec3d center = EntityUtils.getLerpedBox(e, partialTicks)
							.getCenter();
						
						// Skip elytra items below sea level when flying with
						// elytra
						if(isElytraFlying && center.y < 63)
							continue;
						
						ends.add(center);
					}
					
					if(!ends.isEmpty())
					{
						RenderUtils.drawTracers(matrixStack, partialTicks, ends,
							elytraLineColor, false);
					}
				}
				
				// Render diamond tracers with custom color
				if(!diamondItems.isEmpty())
				{
					ArrayList<Vec3d> ends = new ArrayList<>();
					for(ItemEntity e : diamondItems)
					{
						Vec3d center = EntityUtils.getLerpedBox(e, partialTicks)
							.getCenter();
						
						// Skip diamond items below sea level when flying with
						// elytra
						if(isElytraFlying && center.y < 63)
							continue;
						
						ends.add(center);
					}
					
					if(!ends.isEmpty())
					{
						RenderUtils.drawTracers(matrixStack, partialTicks, ends,
							diamondLineColor, false);
					}
				}
				
				// Render netherite tracers with custom color
				if(!netheriteItems.isEmpty())
				{
					ArrayList<Vec3d> ends = new ArrayList<>();
					for(ItemEntity e : netheriteItems)
					{
						Vec3d center = EntityUtils.getLerpedBox(e, partialTicks)
							.getCenter();
						
						// Skip netherite items below sea level when flying with
						// elytra
						if(isElytraFlying && center.y < 63)
							continue;
						
						ends.add(center);
					}
					
					if(!ends.isEmpty())
					{
						RenderUtils.drawTracers(matrixStack, partialTicks, ends,
							netheriteLineColor, false);
					}
				}
			}
		}
		
		// Render text labels for special items
		if(showItemText.isChecked())
			renderItemText(matrixStack, partialTicks);
		
		// Render text labels for special items
		renderSpecialItemText(matrixStack, partialTicks);
		
		// Render text labels for valuable items
		renderValuableItemText(matrixStack, partialTicks);
		
		// Update the subtitle with the item player is looking at
		updateLookingAtSubtitle(partialTicks);
	}
}
