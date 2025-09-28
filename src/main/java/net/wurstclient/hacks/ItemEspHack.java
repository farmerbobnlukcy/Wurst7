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
	
	// Map to track unique items by name for notification purposes
	private final java.util.HashMap<String, Integer> itemNotificationMap =
		new java.util.HashMap<>();
	private boolean elytraFound = false;
	
	// Boss bar-related fields
	private long lastBossBarUpdateTime = 0;
	
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
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(CameraTransformViewBobbingListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		// Clear item tracking when disabled
		knownItemIds.clear();
		itemNotificationMap.clear();
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
					// Check if this is a new item we haven't seen before
					int itemId = item.getId();
					if(!knownItemIds.contains(itemId))
					{
						knownItemIds.add(itemId);
						newItems.add(item);
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
		
		// Process notifications for special items
		for(ItemEntity item : newItems)
		{
			ItemStack stack = item.getStack();
			
			// Skip if null to avoid errors
			if(stack == null)
				continue;
			
			// Get item names
			String itemTypeName = stack.getItem().getName().getString();
			String displayName = stack.getName().getString();
			boolean hasCustomName = stack.getCustomName() != null;
			int count = stack.getCount();
			
			// Play appropriate sound for special items
			if(isElytraItem(stack))
			{
				playSpecialItemSound(stack);
				this.showTitleMessage("§d§lELYTRA FOUND!",
					"§e" + count + "x " + itemTypeName, 60);
			}else if(isNetheriteItem(stack))
			{
				playSpecialItemSound(stack);
				this.showTitleMessage("§4§lNETHERITE FOUND!",
					"§e" + count + "x " + itemTypeName, 60);
			}else if(isShulkerBox(stack))
			{
				playSpecialItemSound(stack);
				this.showTitleMessage("§d§lSHULKER BOX FOUND!",
					"§e" + count + "x " + itemTypeName, 60);
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
	
	private boolean isSpecialLabeledItem(ItemStack stack)
	{
		String itemName = stack.getItem().toString().toLowerCase();
		for(String specialItem : SPECIAL_ITEMS)
			if(itemName.contains(specialItem))
				return true;
		return false;
	}
	
	/**
	 * Shows a title message to the player
	 */
	private void showTitleMessage(String title, String subtitle, int ticks)
	{
		if(MC.player == null || MC.inGameHud == null)
			return;
		
		// Show title with fade-in, stay, and fade-out times
		MC.inGameHud.setTitle(Text.of(title));
		MC.inGameHud.setSubtitle(Text.of(subtitle));
		MC.inGameHud.setTitleTicks(10, ticks, 20);
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
		
		// If we removed items, log it
		if(!idsToRemove.isEmpty() && MC.inGameHud != null)
		{
			MC.inGameHud.getChatHud()
				.addMessage(Text.of("§a[ItemESP]§f Removed §b"
					+ idsToRemove.size()
					+ "§f items from tracking (out of range or no longer exist)"));
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
	}
}
