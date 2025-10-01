/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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
import net.wurstclient.settings.*;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.StreamUtils;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@SearchTags({"item esp", "ItemTracers", "item tracers"})
public final class ItemEspHack extends Hack implements UpdateListener,
	CameraTransformViewBobbingListener, RenderListener
{
	
	/**
	 * Categories for different types of items.
	 * Used for filtering in the ItemESP hack.
	 */
	public enum ItemCategory
	{
		WEAPONS("Weapons", "Swords, bows, crossbows, and other weapons"),
		TOOLS("Tools", "Pickaxes, axes, shovels, and other tools"),
		ARMOR("Armor", "Helmets, chestplates, leggings, and boots"),
		BLOCKS("Blocks", "Common building blocks and materials"),
		VALUABLE_BLOCKS("Valuable Blocks",
			"Diamond, gold, emerald blocks, etc."),
		FOOD("Food", "All edible items"),
		POTIONS("Potions", "Potions, splash potions, and lingering potions"),
		REDSTONE("Redstone", "Redstone dust, repeaters, comparators, etc."),
		TRANSPORTATION("Transportation",
			"Minecarts, boats, and other vehicles"),
		RARE("Rare Items", "Netherite, elytra, enchanted golden apples, etc."),
		CONTAINERS("Containers", "Chests, shulker boxes, barrels, etc."),
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
		
		/**
		 * Gets the description of this item category.
		 *
		 * @return A detailed description of what items belong in this category
		 */
		public String getDescription()
		{
			return description;
		}
	}
	
	/**
	 * Determines which category an item belongs to.
	 *
	 * @param stack
	 *            The item stack to categorize
	 * @return The category of the item
	 */
	public static ItemCategory getItemCategory(ItemStack stack)
	{
		Item item = stack.getItem();
		String itemId = item.toString();
		
		// Check for weapons
		if(itemId.contains("sword") || itemId.contains("bow")
			|| itemId.contains("trident") || itemId.contains("crossbow"))
			return ItemCategory.WEAPONS;
		
		// Check for tools
		if(itemId.contains("pickaxe") || itemId.contains("axe")
			|| itemId.contains("shovel") || itemId.contains("hoe"))
			return ItemCategory.TOOLS;
		
		// Check for armor
		if(itemId.contains("helmet") || itemId.contains("chestplate")
			|| itemId.contains("leggings") || itemId.contains("boots"))
			return ItemCategory.ARMOR;
		
		// Check for valuable blocks
		if(itemId.contains("diamond_block")
			|| itemId.contains("netherite_block")
			|| itemId.contains("emerald_block")
			|| itemId.contains("gold_block"))
			return ItemCategory.VALUABLE_BLOCKS;
		
		// Check for containers
		if(itemId.contains("chest") || itemId.contains("shulker")
			|| itemId.contains("barrel") || itemId.contains("hopper"))
			return ItemCategory.CONTAINERS;
		
		// Check for rare items
		if(itemId.contains("netherite") || itemId.contains("elytra")
			|| itemId.contains("enchanted_golden_apple")
			|| itemId.contains("beacon"))
			return ItemCategory.RARE;
		
		// Check for transportation items
		if(itemId.contains("minecart") || itemId.contains("boat")
			|| itemId.contains("elytra") || itemId.contains("saddle"))
			return ItemCategory.TRANSPORTATION;
		
		// Check for food items
		if(itemId.contains("cooked") || itemId.contains("raw")
			|| itemId.contains("baked") || itemId.contains("cake")
			|| itemId.contains("cookie") || itemId.contains("carrot")
			|| itemId.contains("poison") || itemId.contains("beet"))
			return ItemCategory.FOOD;
		
		// Check for potions
		if(itemId.contains("potion"))
			return ItemCategory.POTIONS;
		
		// Check for redstone items
		if(itemId.contains("redstone") || itemId.contains("comparator")
			|| itemId.contains("repeater") || itemId.contains("observer"))
			return ItemCategory.REDSTONE;
		
		// Check for blocks (most items with "block" in name)
		if(itemId.contains("block") || itemId.contains("stone")
			|| itemId.contains("dirt") || itemId.contains("log"))
			return ItemCategory.BLOCKS;
		
		// Default category
		return ItemCategory.OTHER;
	}
	
	private final EspStyleSetting style = new EspStyleSetting();
	
	private final EspBoxSizeSetting boxSize = new EspBoxSizeSetting(
		"\u00a7lAccurate\u00a7r mode shows the exact hitbox of each item.\n"
			+ "\u00a7lFancy\u00a7r mode shows larger boxes that look better.");
	
	private final ColorSetting color = new ColorSetting("Color",
		"Items will be highlighted in this color.", Color.YELLOW);
	
	private final FileSetting filterList = new FileSetting("Filter list",
		"A list of items to filter out.\nPut each item name on a separate line.\n"
			+ "The names must match the item's ID (e.g., minecraft:diamond).",
		"item_esp_filters", this::createDefaultFilterList);
	
	private final FileSetting priorityList = new FileSetting("Priority list",
		"A list of items to prioritize.\nWhen these items are found, only they will be shown.\n"
			+ "The names must match the item's ID (e.g., minecraft:diamond).",
		"item_esp_priorities", this::createDefaultPriorityList);
	
	private final CheckboxSetting dashedLines =
		new CheckboxSetting("Dashed lines",
			"Draw tracers as dashed lines instead of solid lines.", true);
	
	private final SliderSetting dashLength = new SliderSetting("Dash length",
		"Length of each dash in the dashed line.", 0.5, 0.1, 3.0, 0.1,
		SliderSetting.ValueDisplay.DECIMAL);
	
	private final CheckboxSetting showItemInfo =
		new CheckboxSetting("Show item info",
			"Shows information about the item you're looking at.", true);
	
	private final CheckboxSetting enableSoundAlerts =
		new CheckboxSetting("Sound alerts",
			"Plays a sound when valuable items are detected.", true);
	
	private final CheckboxSetting enableTitleAlerts =
		new CheckboxSetting("Title alerts",
			"Shows a title message when valuable items are detected.", true);
	
	private final CheckboxSetting showBossBar = new CheckboxSetting("Boss bar",
		"Shows a boss bar with item information.", true);
	
	// List of special items that trigger alerts
	private static final Set<String> VALUABLE_ITEMS = new HashSet<>(
		Arrays.asList("minecraft:netherite_ingot", "minecraft:netherite_scrap",
			"minecraft:netherite_block", "minecraft:netherite_sword",
			"minecraft:netherite_pickaxe", "minecraft:netherite_axe",
			"minecraft:netherite_shovel", "minecraft:netherite_hoe",
			"minecraft:netherite_helmet", "minecraft:netherite_chestplate",
			"minecraft:netherite_leggings", "minecraft:netherite_boots",
			"minecraft:elytra", "minecraft:shulker_box",
			"minecraft:white_shulker_box", "minecraft:orange_shulker_box",
			"minecraft:magenta_shulker_box", "minecraft:light_blue_shulker_box",
			"minecraft:yellow_shulker_box", "minecraft:lime_shulker_box",
			"minecraft:pink_shulker_box", "minecraft:gray_shulker_box",
			"minecraft:light_gray_shulker_box", "minecraft:cyan_shulker_box",
			"minecraft:purple_shulker_box", "minecraft:blue_shulker_box",
			"minecraft:brown_shulker_box", "minecraft:green_shulker_box",
			"minecraft:red_shulker_box", "minecraft:black_shulker_box",
			"minecraft:enchanted_golden_apple", "minecraft:beacon"));
	
	private final Set<String> filteredItems = new HashSet<>();
	private final Set<String> priorityItems = new HashSet<>();
	private final ArrayList<ItemEntity> items = new ArrayList<>();
	private final ArrayList<ItemEntity> priorityItemsFound = new ArrayList<>();
	private final ArrayList<ItemEntity> valuableItemsFound = new ArrayList<>();
	private boolean priorityItemsPresent = false;
	
	// Item targeting and tracking
	private ItemEntity targetItem = null;
	private long lastInfoUpdateTime = 0;
	private static final long INFO_UPDATE_DELAY = 100; // milliseconds
	
	// Tracking for alerts
	private long lastAlertTime = 0;
	private static final long ALERT_COOLDOWN = 5000; // 5 seconds
	
	public ItemEspHack()
	{
		super("ItemESP");
		setCategory(Category.RENDER);
		addSetting(style);
		addSetting(boxSize);
		addSetting(color);
		addSetting(filterList);
		addSetting(priorityList);
		addSetting(dashedLines);
		addSetting(dashLength);
		addSetting(showItemInfo);
		addSetting(enableSoundAlerts);
		addSetting(enableTitleAlerts);
		addSetting(showBossBar);
	}
	
	private void createDefaultFilterList(Path folder)
	{
		Path path = folder.resolve("default_filters.txt");
		
		try
		{
			Files.writeString(path,
				"# Add items to filter out (one per line)\n" + "# Example:\n"
					+ "# minecraft:dirt\n" + "# minecraft:stone\n");
			
		}catch(IOException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	private void createDefaultPriorityList(Path folder)
	{
		Path path = folder.resolve("default_priorities.txt");
		
		try
		{
			Files.writeString(path, "# Add priority items (one per line)\n"
				+ "# When these items are found, only they will be highlighted\n"
				+ "# Example:\n" + "# minecraft:diamond\n"
				+ "# minecraft:netherite_ingot\n"
				+ "# minecraft:enchanted_golden_apple\n");
			
		}catch(IOException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	private void loadFilteredItems()
	{
		filteredItems.clear();
		try
		{
			ArrayList<String> lines = StreamUtils.readAllLines(
				Files.newInputStream(filterList.getSelectedFile()));
			
			for(String line : lines)
			{
				// Skip empty lines and comments
				if(line.trim().isEmpty() || line.trim().startsWith("#"))
					continue;
				
				filteredItems.add(line.trim());
			}
			
		}catch(IOException e)
		{
			System.out
				.println("Couldn't load filtered items: " + e.getMessage());
		}
	}
	
	private void loadPriorityItems()
	{
		priorityItems.clear();
		try
		{
			ArrayList<String> lines = StreamUtils.readAllLines(
				Files.newInputStream(priorityList.getSelectedFile()));
			
			for(String line : lines)
			{
				// Skip empty lines and comments
				if(line.trim().isEmpty() || line.trim().startsWith("#"))
					continue;
				
				priorityItems.add(line.trim());
			}
			
		}catch(IOException e)
		{
			System.out
				.println("Couldn't load priority items: " + e.getMessage());
		}
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(CameraTransformViewBobbingListener.class, this);
		EVENTS.add(RenderListener.class, this);
		
		loadFilteredItems();
		loadPriorityItems();
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(CameraTransformViewBobbingListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		// Clear any overlay messages when disabled
		if(MC.inGameHud != null)
		{
			MC.inGameHud.setOverlayMessage(Text.of(""), false);
		}
	}
	
	@Override
	public void onUpdate()
	{
		items.clear();
		priorityItemsFound.clear();
		valuableItemsFound.clear();
		priorityItemsPresent = false;
		
		// Reload the filter list if the file was changed
		if(filterList.getSelectedFile().toFile().exists())
			loadFilteredItems();
		
		// Reload the priority list if the file was changed
		if(priorityList.getSelectedFile().toFile().exists())
			loadPriorityItems();
		
		for(Entity entity : MC.world.getEntities())
		{
			if(entity instanceof ItemEntity)
			{
				ItemEntity item = (ItemEntity)entity;
				String itemId = item.getStack().getItem().toString();
				
				// Skip filtered items
				if(filteredItems.contains(itemId))
					continue;
				
				// Check if it's a valuable item first (highest priority)
				boolean isValuable = isValuableItem(item.getStack());
				if(isValuable)
				{
					valuableItemsFound.add(item);
					
					// Check if we should trigger alerts
					if(System.currentTimeMillis()
						- lastAlertTime >= ALERT_COOLDOWN)
					{
						if(enableSoundAlerts.isChecked())
							playSoundAlert();
						if(enableTitleAlerts.isChecked())
							showTitleAlert(item);
						lastAlertTime = System.currentTimeMillis();
					}
				}
				
				// Categorize the item (for filtering purposes)
				ItemCategory category = getItemCategory(item.getStack());
				
				// Check if it's a priority item
				boolean isPriority = priorityItems.contains(itemId);
				if(isPriority)
				{
					priorityItemsFound.add(item);
					priorityItemsPresent = true;
				}
				
				// Add to general items list
				items.add(item);
			}
		}
		
		// If valuable items are found, override priority items
		if(!valuableItemsFound.isEmpty())
		{
			priorityItemsFound.clear();
			priorityItemsFound.addAll(valuableItemsFound);
			priorityItemsPresent = true;
		}
		
		// Find the item player is looking at
		if(System.currentTimeMillis() - lastInfoUpdateTime >= INFO_UPDATE_DELAY)
		{
			updateTargetItem();
			lastInfoUpdateTime = System.currentTimeMillis();
		}
	}
	
	/**
	 * Finds the item that the player is currently looking at
	 * and updates the targetItem field.
	 */
	private void updateTargetItem()
	{
		if(MC.player == null || items.isEmpty())
		{
			targetItem = null;
			return;
		}
		
		// Determine which items to consider based on priority
		ArrayList<ItemEntity> itemsToConsider =
			priorityItemsPresent ? priorityItemsFound : items;
		
		// Get player's look vector
		Vec3d cameraPos = MC.player.getCameraPosVec(1.0F);
		Vec3d lookVec = MC.player.getRotationVec(1.0F).normalize();
		
		// Find closest item in player's line of sight
		double closestDistance = Double.MAX_VALUE;
		ItemEntity closestItem = null;
		
		for(ItemEntity item : itemsToConsider)
		{
			// Get the center of the item
			Vec3d itemPos = item.getBoundingBox().getCenter();
			
			// Calculate vector from camera to item
			Vec3d cameraToItem = itemPos.subtract(cameraPos);
			double distanceToItem = cameraToItem.length();
			
			// Skip items that are too far away (more than 32 blocks)
			if(distanceToItem > 32)
				continue;
			
			// Project the camera-to-item vector onto the look vector
			double dot = cameraToItem.normalize().dotProduct(lookVec);
			
			// Consider items within a certain angle in front of the player
			if(dot > 0.9)
			{ // Roughly 25 degrees on each side
				if(distanceToItem < closestDistance)
				{
					closestDistance = distanceToItem;
					closestItem = item;
				}
			}
		}
		
		targetItem = closestItem;
	}
	
	@Override
	public void onCameraTransformViewBobbing(
		CameraTransformViewBobbingEvent event)
	{
		if(style.hasLines())
			event.cancel();
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		int lineColor = color.getColorI(0x80);
		
		// Determine which items to render based on priority
		ArrayList<ItemEntity> itemsToRender =
			priorityItemsPresent ? priorityItemsFound : items;
		
		if(style.hasBoxes())
		{
			double extraSize = boxSize.getExtraSize() / 2;
			
			ArrayList<Box> boxes = new ArrayList<>(itemsToRender.size());
			for(ItemEntity e : itemsToRender)
				boxes.add(EntityUtils.getLerpedBox(e, partialTicks)
					.offset(0, extraSize, 0).expand(extraSize));
			
			RenderUtils.drawOutlinedBoxes(matrixStack, boxes, lineColor, false);
		}
		
		if(style.hasLines())
		{
			ArrayList<Vec3d> ends = new ArrayList<>(itemsToRender.size());
			for(ItemEntity e : itemsToRender)
				ends.add(EntityUtils.getLerpedBox(e, partialTicks).getCenter());
			
			if(dashedLines.isChecked())
				RenderUtils.drawDashedTracers(matrixStack, partialTicks, ends,
					lineColor, false, (float)dashLength.getValue());
			else
				RenderUtils.drawTracers(matrixStack, partialTicks, ends,
					lineColor, false);
		}
		
		// Display information about the target item
		displayTargetItemInfo();
	}
	
	/**
	 * Displays information about the target item as a subtitle.
	 */
	private void displayTargetItemInfo()
	{
		if(!showItemInfo.isChecked() || targetItem == null || MC.player == null)
			return;
		
		try
		{
			// Get item details
			String itemName = targetItem.getStack().getName().getString();
			int quantity = targetItem.getStack().getCount();
			
			// Calculate distance
			double distance =
				MC.player.getPos().distanceTo(targetItem.getPos());
			DecimalFormat df = new DecimalFormat("0.0");
			String formattedDistance = df.format(distance);
			
			// Create and display the subtitle
			String message = "§e" + itemName + " §fx" + quantity + " §7("
				+ formattedDistance + "m)";
			
			// Just set the title and subtitle directly - no need to check the
			// title field
			MC.inGameHud.setTitle(Text.of(""));
			MC.inGameHud.setSubtitle(Text.of(message));
			MC.inGameHud.setTitleTicks(1, 5, 1); // fade in, stay, fade out
			
			// Update boss bar if enabled
			updateBossBar(targetItem);
			
		}catch(Exception e)
		{
			// Silently fail rather than crash the game
			System.out.println("Error displaying item info: " + e.getMessage());
		}
	}
	
	/**
	 * Displays item information in an overlay message at the top of the screen
	 * as a simpler alternative to a boss bar.
	 */
	private void updateBossBar(ItemEntity item)
	{
		if(!showBossBar.isChecked() || item == null || MC.player == null)
			return;
		
		try
		{
			// Get item details
			String itemName = item.getStack().getName().getString();
			int quantity = item.getStack().getCount();
			
			// Calculate distance
			double distance = MC.player.getPos().distanceTo(item.getPos());
			DecimalFormat df = new DecimalFormat("0.0");
			String formattedDistance = df.format(distance);
			
			// Get category
			ItemCategory category = getItemCategory(item.getStack());
			
			// Choose color prefix based on item value
			String colorPrefix;
			if(isValuableItem(item.getStack()))
			{
				colorPrefix = "§d"; // Purple for valuable items
			}else if(priorityItems
				.contains(item.getStack().getItem().toString()))
			{
				colorPrefix = "§e"; // Yellow for priority items
			}else
			{
				colorPrefix = "§a"; // Green for normal items
			}
			
			// Create formatted message
			String message = colorPrefix + itemName + " §fx" + quantity + " §7("
				+ formattedDistance + "m) §8[" + category + "]";
			
			// Display message at the top of the screen
			MC.inGameHud.setOverlayMessage(Text.of(message), false);
			
		}catch(Exception e)
		{
			// Silently fail rather than crash the game
			System.out
				.println("Error showing item info overlay: " + e.getMessage());
		}
	}
	
	/**
	 * Checks if an item is considered valuable and should trigger alerts.
	 */
	private boolean isValuableItem(ItemStack stack)
	{
		String itemId = stack.getItem().toString();
		return VALUABLE_ITEMS.contains(itemId);
	}
	
	/**
	 * Plays a sound alert for a valuable item.
	 */
	private void playSoundAlert()
	{
		if(!enableSoundAlerts.isChecked() || MC.player == null
			|| MC.world == null)
			return;
		
		try
		{
			// Play alert sound at the player's location
			MC.world.playSound(MC.player, // entity (player)
				MC.player.getX(), // x position
				MC.player.getY(), // y position
				MC.player.getZ(), // z position
				SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, // sound event
				SoundCategory.PLAYERS, // sound category
				1.0F, // volume
				1.0F); // pitch
		}catch(Exception e)
		{
			System.out.println("Failed to play sound alert: " + e.getMessage());
		}
	}
	
	/**
	 * Shows a title alert for a valuable item.
	 */
	private void showTitleAlert(ItemEntity item)
	{
		if(!enableTitleAlerts.isChecked() || MC.player == null || item == null)
			return;
		
		try
		{
			String itemName = item.getStack().getName().getString();
			int quantity = item.getStack().getCount();
			
			// Calculate distance
			double distance = MC.player.getPos().distanceTo(item.getPos());
			DecimalFormat df = new DecimalFormat("0.0");
			String formattedDistance = df.format(distance);
			
			// Show alert title and subtitle
			MC.inGameHud.setTitle(Text.of("§5§l" + itemName));
			MC.inGameHud.setSubtitle(Text.of(
				"§e" + quantity + " found " + formattedDistance + "m away"));
			MC.inGameHud.setTitleTicks(10, 60, 10); // fade in, stay, fade out
		}catch(Exception e)
		{
			System.out.println("Error showing title alert: " + e.getMessage());
		}
	}
}
