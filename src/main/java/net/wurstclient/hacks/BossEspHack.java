/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.*;
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

@SearchTags({"boss esp", "BossTracers", "boss tracers", "item esp", "ItemTracers", "item tracers"})
public final class BossEspHack extends Hack implements UpdateListener,
		CameraTransformViewBobbingListener, RenderListener {
	
	/**
	 * Categories for different types of boss entities.
	 * Used for boss entity classification.
	 */
	public enum BossCategory {
		WARDEN("Warden", "The blind monster from the Deep Dark"),
		DRAGON("Ender Dragon", "The final boss of Minecraft"),
		WITHER("Wither", "The three-headed boss mob"),
		ELDER_GUARDIAN("Elder Guardian", "The boss of ocean monuments"),
		EVOKER("Evoker", "Illager boss that summons vexes"),
		RAVAGER("Ravager", "Beast that leads illager raids"),
		WITHER_SKELETON("Wither Skeleton", "Nether fortress skeleton variant"),
		BLAZE("Blaze", "Flying nether mob that shoots fireballs"),
		PIGLIN_BRUTE("Piglin Brute", "Aggressive bastion piglin variant"),
		VINDICATOR("Vindicator", "Axe-wielding illager"),
		CHARGED_CREEPER("Charged Creeper", "Supercharged explosive mob"),
		CREEPER("Creeper", "Exploding hostile mob"),
		OTHER("Other Boss", "Other boss-like entities");
		
		private final String name;
		private final String description;
		
		BossCategory(String name, String description) {
			this.name = name;
			this.description = description;
		}
		
		@Override
		public String toString() {
			return name;
		}
		
		public String getDescription() {
			return description;
		}
	}
	
	/**
	 * Categories for different types of items.
	 * Used for filtering in the BossESP hack.
	 */
	public enum ItemCategory {
		WEAPONS("Weapons", "Swords, bows, crossbows, and other weapons"),
		TOOLS("Tools", "Pickaxes, axes, shovels, and other tools"),
		ARMOR("Armor", "Helmets, chestplates, leggings, and boots"),
		BLOCKS("Blocks", "Common building blocks and materials"),
		VALUABLE_BLOCKS("Valuable Blocks", "Diamond, gold, emerald blocks, etc."),
		FOOD("Food", "All edible items"),
		POTIONS("Potions", "Potions, splash potions, and lingering potions"),
		REDSTONE("Redstone", "Redstone dust, repeaters, comparators, etc."),
		TRANSPORTATION("Transportation", "Minecarts, boats, and other vehicles"),
		RARE("Rare Items", "Netherite, elytra, enchanted golden apples, etc."),
		CONTAINERS("Containers", "Chests, shulker boxes, barrels, etc."),
		OTHER("Other", "Items that don't fit in other categories");
		
		private final String name;
		private final String description;
		
		ItemCategory(String name, String description) {
			this.name = name;
			this.description = description;
		}
		
		@Override
		public String toString() {
			return name;
		}
		
		/**
		 * Gets the description of this item category.
		 *
		 * @return A detailed description of what items belong in this category
		 */
		public String getDescription() {
			return description;
		}
	}
	
	/**
	 * Determines which category an item belongs to.
	 *
	 * @param stack The item stack to categorize
	 * @return The category of the item
	 */
	public static BossCategory getBossCategory(Entity entity) {
		if (entity instanceof WardenEntity)
			return BossCategory.WARDEN;
		else if (entity instanceof EnderDragonEntity)
			return BossCategory.DRAGON;
		else if (entity instanceof WitherEntity)
			return BossCategory.WITHER;
		else if (entity instanceof ElderGuardianEntity)
			return BossCategory.ELDER_GUARDIAN;
		else if (entity instanceof EvokerEntity)
			return BossCategory.EVOKER;
		else if (entity instanceof RavagerEntity)
			return BossCategory.RAVAGER;
		else if (entity instanceof WitherSkeletonEntity)
			return BossCategory.WITHER_SKELETON;
		else if (entity instanceof BlazeEntity)
			return BossCategory.BLAZE;
		else if (entity instanceof PiglinBruteEntity)
			return BossCategory.PIGLIN_BRUTE;
		else if (entity instanceof VindicatorEntity)
			return BossCategory.VINDICATOR;
		else if (entity instanceof CreeperEntity) {
			CreeperEntity creeper = (CreeperEntity) entity;
			if (creeper.shouldRenderOverlay()) // Checks for charged state
				return BossCategory.CHARGED_CREEPER;
			else
				return BossCategory.CREEPER;
		} else
			return BossCategory.OTHER;
	}
	
	/**
	 * Determines which category an item belongs to.
	 *
	 * @param stack The item stack to categorize
	 * @return The category of the item
	 */
	public static ItemCategory getItemCategory(ItemStack stack) {
		Item item = stack.getItem();
		String itemId = item.toString();
		
		// Check for weapons
		if (itemId.contains("sword") || itemId.contains("bow") ||
				itemId.contains("trident") || itemId.contains("crossbow"))
			return ItemCategory.WEAPONS;
		
		// Check for tools
		if (itemId.contains("pickaxe") || itemId.contains("axe") ||
				itemId.contains("shovel") || itemId.contains("hoe"))
			return ItemCategory.TOOLS;
		
		// Check for armor
		if (itemId.contains("helmet") || itemId.contains("chestplate") ||
				itemId.contains("leggings") || itemId.contains("boots"))
			return ItemCategory.ARMOR;
		
		// Check for valuable blocks
		if (itemId.contains("diamond_block") || itemId.contains("netherite_block") ||
				itemId.contains("emerald_block") || itemId.contains("gold_block"))
			return ItemCategory.VALUABLE_BLOCKS;
		
		// Check for containers
		if (itemId.contains("chest") || itemId.contains("shulker") ||
				itemId.contains("barrel") || itemId.contains("hopper"))
			return ItemCategory.CONTAINERS;
		
		// Check for rare items
		if (itemId.contains("netherite") || itemId.contains("elytra") ||
				itemId.contains("enchanted_golden_apple") || itemId.contains("beacon"))
			return ItemCategory.RARE;
		
		// Check for transportation items
		if (itemId.contains("minecart") || itemId.contains("boat") ||
				itemId.contains("elytra") || itemId.contains("saddle"))
			return ItemCategory.TRANSPORTATION;
		
		// Check for food items
		if (itemId.contains("cooked") || itemId.contains("raw")
				||
				itemId.contains("baked")
				|| itemId.contains("cake")
				|| itemId.contains("cookie")
				|| itemId.contains("carrot")
				|| itemId.contains("poison") || itemId.contains("beet"))
			return ItemCategory.FOOD;
		
		// Check for potions
		if (itemId.contains("potion"))
			return ItemCategory.POTIONS;
		
		// Check for redstone items
		if (itemId.contains("redstone") || itemId.contains("comparator") ||
				itemId.contains("repeater") || itemId.contains("observer"))
			return ItemCategory.REDSTONE;
		
		// Check for blocks (most items with "block" in name)
		if (itemId.contains("block") || itemId.contains("stone") ||
				itemId.contains("dirt") || itemId.contains("log"))
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
			"boss_esp_filters", this::createDefaultFilterList);
	
	private final FileSetting priorityList = new FileSetting("Priority list",
			"A list of items to prioritize.\nWhen these items are found, only they will be shown.\n"
					+ "The names must match the item's ID (e.g., minecraft:diamond).",
			"boss_esp_priorities", this::createDefaultPriorityList);
	
	private final CheckboxSetting dashedLines = new CheckboxSetting("Dashed lines",
			"Draw tracers as dashed lines instead of solid lines.", true);
	
	private final SliderSetting dashLength = new SliderSetting("Dash length",
			"Length of each dash in the dashed line.", 0.5, 0.1, 3.0, 0.1,
			SliderSetting.ValueDisplay.DECIMAL);
	
	
	private final CheckboxSetting showItemInfo = new CheckboxSetting(
			"Show item info", "Shows information about the item you're looking at.",
			true);
	
	private final CheckboxSetting enableSoundAlerts = new CheckboxSetting(
			"Sound alerts", "Plays a sound when valuable items are detected.",
			true);
	
	private final CheckboxSetting enableTitleAlerts = new CheckboxSetting(
			"Title alerts", "Shows a title message when valuable items are detected.",
			true);
	
	private final CheckboxSetting showBossBar = new CheckboxSetting(
			"Boss bar", "Shows a boss bar with item information.",
			true);
	
	private final CheckboxSetting highlightBossMobs = new CheckboxSetting(
			"Highlight Boss Mobs", "Highlights boss mobs like Wardens, Ender Dragons, etc.",
			true);
	
	private final ColorSetting bossColor = new ColorSetting("Boss Color",
			"Boss entities will be highlighted in this color.", new Color(255, 0, 0));
	
	private final CheckboxSetting showRegularCreepers = new CheckboxSetting(
			"Show Regular Creepers", "Also highlights regular (non-charged) creepers.",
			false);
	
	private final CheckboxSetting showNametags = new CheckboxSetting(
			"Show Nametags", "Shows nametags with health above boss entities.",
			true);
	
	private final CheckboxSetting showHealthBars = new CheckboxSetting(
			"Show Health Bars", "Shows health bars above boss entities.",
			true);
	
	// List of special items that trigger alerts
	private static final Set<String> VALUABLE_ITEMS = new HashSet<>(Arrays.asList(
			"minecraft:netherite_ingot",
			"minecraft:netherite_scrap",
			"minecraft:netherite_block",
			"minecraft:netherite_sword",
			"minecraft:netherite_pickaxe",
			"minecraft:netherite_axe",
			"minecraft:netherite_shovel",
			"minecraft:netherite_hoe",
			"minecraft:netherite_helmet",
			"minecraft:netherite_chestplate",
			"minecraft:netherite_leggings",
			"minecraft:netherite_boots",
			"minecraft:elytra",
			"minecraft:shulker_box",
			"minecraft:white_shulker_box",
			"minecraft:orange_shulker_box",
			"minecraft:magenta_shulker_box",
			"minecraft:light_blue_shulker_box",
			"minecraft:yellow_shulker_box",
			"minecraft:lime_shulker_box",
			"minecraft:pink_shulker_box",
			"minecraft:gray_shulker_box",
			"minecraft:light_gray_shulker_box",
			"minecraft:cyan_shulker_box",
			"minecraft:purple_shulker_box",
			"minecraft:blue_shulker_box",
			"minecraft:brown_shulker_box",
			"minecraft:green_shulker_box",
			"minecraft:red_shulker_box",
			"minecraft:black_shulker_box",
			"minecraft:enchanted_golden_apple",
			"minecraft:beacon"
	));
	
	private final Set<String> filteredItems = new HashSet<>();
	private final Set<String> priorityItems = new HashSet<>();
	// Item tracking
	private final ArrayList<ItemEntity> items = new ArrayList<>();
	private final ArrayList<ItemEntity> priorityItemsFound = new ArrayList<>();
	private final ArrayList<ItemEntity> valuableItemsFound = new ArrayList<>();
	private boolean priorityItemsPresent = false;
	
	// Boss entity tracking
	private final ArrayList<Entity> bossEntities = new ArrayList<>();
	private boolean bossEntitiesPresent = false;
	
	// Item targeting and tracking
	private ItemEntity targetItem = null;
	private long lastInfoUpdateTime = 0;
	private static final long INFO_UPDATE_DELAY = 100; // milliseconds
	
	// Tracking for alerts
	private long lastAlertTime = 0;
	private static final long ALERT_COOLDOWN = 5000; // 5 seconds
	
	public BossEspHack() {
		super("BossESP");
		setCategory(Category.RENDER);
		addSetting(style);
		addSetting(boxSize);
		addSetting(color);
		addSetting(bossColor);
		addSetting(highlightBossMobs);
		addSetting(showRegularCreepers);
		addSetting(showNametags);
		addSetting(showHealthBars);
		addSetting(filterList);
		addSetting(priorityList);
		addSetting(dashedLines);
		addSetting(dashLength);
		addSetting(showItemInfo);
		addSetting(enableSoundAlerts);
		addSetting(enableTitleAlerts);
		addSetting(showBossBar);
	}
	
	private void createDefaultFilterList(Path folder) {
		Path path = folder.resolve("default_filters.txt");
		
		try {
			Files.writeString(path,
					"# Add items to filter out (one per line)\n" + "# Example:\n"
							+ "# minecraft:dirt\n" + "# minecraft:stone\n");
			
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private void createDefaultPriorityList(Path folder) {
		Path path = folder.resolve("default_priorities.txt");
		
		try {
			Files.writeString(path, "# Add priority items (one per line)\n"
					+ "# When these items are found, only they will be highlighted\n"
					+ "# Example:\n" + "# minecraft:diamond\n"
					+ "# minecraft:netherite_ingot\n"
					+ "# minecraft:enchanted_golden_apple\n");
			
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private void loadFilteredItems() {
		filteredItems.clear();
		try {
			ArrayList<String> lines = StreamUtils.readAllLines(
					Files.newInputStream(filterList.getSelectedFile()));
			
			for (String line : lines) {
				// Skip empty lines and comments
				if (line.trim().isEmpty() || line.trim().startsWith("#"))
					continue;
				
				filteredItems.add(line.trim());
			}
			
		} catch (IOException e) {
			System.out
					.println("Couldn't load filtered items: " + e.getMessage());
		}
	}
	
	private void loadPriorityItems() {
		priorityItems.clear();
		try {
			ArrayList<String> lines = StreamUtils.readAllLines(
					Files.newInputStream(priorityList.getSelectedFile()));
			
			for (String line : lines) {
				// Skip empty lines and comments
				if (line.trim().isEmpty() || line.trim().startsWith("#"))
					continue;
				
				priorityItems.add(line.trim());
			}
			
		} catch (IOException e) {
			System.out
					.println("Couldn't load priority items: " + e.getMessage());
		}
	}
	
	@Override
	protected void onEnable() {
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(CameraTransformViewBobbingListener.class, this);
		EVENTS.add(RenderListener.class, this);
		
		loadFilteredItems();
		loadPriorityItems();
	}
	
	@Override
	protected void onDisable() {
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(CameraTransformViewBobbingListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		// Clear any overlay messages when disabled
		if (MC.inGameHud != null) {
			MC.inGameHud.setOverlayMessage(Text.of(""), false);
		}
	}
	
	@Override
	public void onUpdate() {
		// Clear all tracking lists
		items.clear();
		priorityItemsFound.clear();
		valuableItemsFound.clear();
		bossEntities.clear();
		priorityItemsPresent = false;
		bossEntitiesPresent = false;
		
		// Reload the filter list if the file was changed
		if (filterList.getSelectedFile().toFile().exists())
			loadFilteredItems();
		
		// Reload the priority list if the file was changed
		if (priorityList.getSelectedFile().toFile().exists())
			loadPriorityItems();
		
		// Process all entities
		for (Entity entity : MC.world.getEntities()) {
			// Check for boss entities if boss highlighting is enabled
			if (highlightBossMobs.isChecked() && isBossEntity(entity)) {
				bossEntities.add(entity);
				bossEntitiesPresent = true;
				
				// Trigger alerts for boss entities
				if (System.currentTimeMillis() - lastAlertTime >= ALERT_COOLDOWN) {
					if (enableSoundAlerts.isChecked())
						playSoundAlert();
					if (enableTitleAlerts.isChecked())
						showBossAlert(entity);
					lastAlertTime = System.currentTimeMillis();
				}
				continue; // Skip item processing for boss entities
			}
			
			// Process item entities
			if (entity instanceof ItemEntity) {
				ItemEntity item = (ItemEntity) entity;
				String itemId = item.getStack().getItem().toString();
				
				// Skip filtered items
				if (filteredItems.contains(itemId))
					continue;
				
				// Check if it's a valuable item first (highest priority)
				boolean isValuable = isValuableItem(item.getStack());
				if (isValuable) {
					valuableItemsFound.add(item);
					
					// Check if we should trigger alerts
					if (System.currentTimeMillis() - lastAlertTime >= ALERT_COOLDOWN) {
						if (enableSoundAlerts.isChecked())
							playSoundAlert();
						if (enableTitleAlerts.isChecked())
							showItemAlert(item);
						lastAlertTime = System.currentTimeMillis();
					}
				}
				
				// Categorize the item (for filtering purposes)
				ItemCategory category = getItemCategory(item.getStack());
				
				// Check if it's a priority item
				boolean isPriority = priorityItems.contains(itemId);
				if (isPriority) {
					priorityItemsFound.add(item);
					priorityItemsPresent = true;
				}
				
				// Add to general items list
				items.add(item);
			}
		}
		
		// If valuable items are found, override priority items
		if (!valuableItemsFound.isEmpty()) {
			priorityItemsFound.clear();
			priorityItemsFound.addAll(valuableItemsFound);
			priorityItemsPresent = true;
		}
		
		// Update targets if it's time to do so
		if (System.currentTimeMillis() - lastInfoUpdateTime >= INFO_UPDATE_DELAY) {
			updateTargetItem();
			updateTargetBoss();
			lastInfoUpdateTime = System.currentTimeMillis();
		}
	}
	
	/**
	 * Finds the item that the player is currently looking at
	 * and updates the targetItem field.
	 */
	private void updateTargetItem() {
		if (MC.player == null || items.isEmpty()) {
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
		
		for (ItemEntity item : itemsToConsider) {
			// Get the center of the item
			Vec3d itemPos = item.getBoundingBox().getCenter();
			
			// Calculate vector from camera to item
			Vec3d cameraToItem = itemPos.subtract(cameraPos);
			double distanceToItem = cameraToItem.length();
			
			// Skip items that are too far away (more than 32 blocks)
			if (distanceToItem > 32)
				continue;
			
			// Project the camera-to-item vector onto the look vector
			double dot = cameraToItem.normalize().dotProduct(lookVec);
			
			// Consider items within a certain angle in front of the player
			if (dot > 0.9) { // Roughly 25 degrees on each side
				if (distanceToItem < closestDistance) {
					closestDistance = distanceToItem;
					closestItem = item;
				}
			}
		}
		
		targetItem = closestItem;
	}
	
	@Override
	public void onCameraTransformViewBobbing(
			CameraTransformViewBobbingEvent event) {
		if (style.hasLines())
			event.cancel();
	}
	
	/**
	 * Renders a nametag with health information above an entity.
	 */
	private void renderNametagWithHealth(MatrixStack matrixStack, Entity entity, float partialTicks) {
		if (!(entity instanceof LivingEntity))
			return;
		
		LivingEntity livingEntity = (LivingEntity) entity;
		
		// Get entity details
		String name = livingEntity.getDisplayName().getString();
		float health = livingEntity.getHealth();
		float maxHealth = livingEntity.getMaxHealth();
		
		// Format health string with appropriate color
		String healthText;
		if (health > maxHealth * 0.75f) {
			healthText = "§a" + Math.round(health) + "§f/§a" + Math.round(maxHealth); // Green
		} else if (health > maxHealth * 0.5f) {
			healthText = "§e" + Math.round(health) + "§f/§e" + Math.round(maxHealth); // Yellow
		} else if (health > maxHealth * 0.25f) {
			healthText = "§6" + Math.round(health) + "§f/§6" + Math.round(maxHealth); // Gold
		} else {
			healthText = "§c" + Math.round(health) + "§f/§c" + Math.round(maxHealth); // Red
		}
		
		// Add special indicator for charged creepers
		if (entity instanceof CreeperEntity && ((CreeperEntity) entity).shouldRenderOverlay()) {
			name = "§b⚡ " + name + " ⚡§r"; // Cyan lightning bolt indicators
		}
		
		// Combine name and health
		String tag = name + " §7[" + healthText + "§7]";
		
		// Get entity position
		Vec3d pos = EntityUtils.getLerpedPos(entity, partialTicks);
		pos = pos.add(0, entity.getHeight() + 0.5, 0);
		
		// Adjust for camera position
		pos = pos.subtract(RenderUtils.getCameraPos());
		
		// Save and setup GL state
		matrixStack.push();
		
		// Move to entity position
		matrixStack.translate(pos.x, pos.y, pos.z);
		
		// Rotate to face player
		float yaw = MC.gameRenderer.getCamera().getYaw();
		float pitch = MC.gameRenderer.getCamera().getPitch();
		matrixStack.multiply(Vec3d.POSITIVE_Y.getDegreesQuaternion(-yaw));
		matrixStack.multiply(Vec3d.POSITIVE_X.getDegreesQuaternion(pitch));
		
		// Scale based on distance (further = bigger)
		double distance = Math.max(1, MC.player.getPos().distanceTo(entity.getPos()));
		float scale = 0.025f * (float) Math.max(1.6, distance * 0.15);
		matrixStack.scale(-scale, -scale, scale);
		
		// Draw text
		float halfWidth = -MC.textRenderer.getWidth(tag) / 2f;
		MC.textRenderer.draw(tag, halfWidth, 0, 0xFFFFFFFF, true,
				matrixStack.peek().getPositionMatrix(), MC.getBufferBuilders().getEntityVertexConsumers(),
				TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
		
		// Restore GL state
		matrixStack.pop();
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks) {
		// Determine which items to render based on priority
		ArrayList<ItemEntity> itemsToRender =
				priorityItemsPresent ? priorityItemsFound : items;
		
		// Only render items if no boss entities are present or if we want to show both
		if (!bossEntitiesPresent || !highlightBossMobs.isChecked()) {
			int itemLineColor = color.getColorI(0x80);
			
			if (style.hasBoxes()) {
				double extraSize = boxSize.getExtraSize() / 2;
				
				ArrayList<Box> boxes = new ArrayList<>(itemsToRender.size());
				for (ItemEntity e : itemsToRender)
					boxes.add(EntityUtils.getLerpedBox(e, partialTicks)
							.offset(0, extraSize, 0).expand(extraSize));
				
				RenderUtils.drawOutlinedBoxes(matrixStack, boxes, itemLineColor, false);
			}
			
			if (style.hasLines()) {
				ArrayList<Vec3d> ends = new ArrayList<>(itemsToRender.size());
				for (ItemEntity e : itemsToRender)
					ends.add(EntityUtils.getLerpedBox(e, partialTicks).getCenter());
				
				if (dashedLines.isChecked())
					RenderUtils.drawDashedTracers(matrixStack, partialTicks, ends,
							itemLineColor, false, (float) dashLength.getValue());
				else
					RenderUtils.drawTracers(matrixStack, partialTicks, ends,
							itemLineColor, false);
			}
		}
		
		// Render boss entities if enabled and present
		if (bossEntitiesPresent && highlightBossMobs.isChecked()) {
			int bossLineColor = bossColor.getColorI(0x80);
			
			if (style.hasBoxes()) {
				ArrayList<Box> bossBoxes = new ArrayList<>(bossEntities.size());
				for (Entity e : bossEntities)
					bossBoxes.add(EntityUtils.getLerpedBox(e, partialTicks).expand(0.1));
				
				RenderUtils.drawOutlinedBoxes(matrixStack, bossBoxes, bossLineColor, false);
			}
			
			if (style.hasLines()) {
				ArrayList<Vec3d> bossEnds = new ArrayList<>(bossEntities.size());
				for (Entity e : bossEntities)
					bossEnds.add(EntityUtils.getLerpedBox(e, partialTicks).getCenter());
				
				if (dashedLines.isChecked())
					RenderUtils.drawDashedTracers(matrixStack, partialTicks, bossEnds,
							bossLineColor, false, (float) dashLength.getValue());
				else
					RenderUtils.drawTracers(matrixStack, partialTicks, bossEnds,
							bossLineColor, false);
			}
			
			// Render nametags with health information
			if (showNametags.isChecked() || showHealthBars.isChecked()) {
				for (Entity entity : bossEntities) {
					if (entity instanceof LivingEntity) {
						renderNametagWithHealth(matrixStack, entity, partialTicks);
					}
				}
			}
			
			// Display information about the closest boss entity
			displayBossEntityInfo();
		} else {
			// Display information about the target item
			displayTargetItemInfo();
		}
	}
	
	/**
	 * Displays information about the target item as a subtitle.
	 */
	private void displayTargetItemInfo() {
		if (!showItemInfo.isChecked() || targetItem == null || MC.player == null)
			return;
		
		try {
			// Get item details
			String itemName = targetItem.getStack().getName().getString();
			int quantity = targetItem.getStack().getCount();
			
			// Calculate distance
			double distance = MC.player.getPos().distanceTo(targetItem.getPos());
			DecimalFormat df = new DecimalFormat("0.0");
			String formattedDistance = df.format(distance);
			
			// Create and display the subtitle
			String message = "§e" + itemName + " §fx" + quantity + " §7("
					+ formattedDistance + "m)";
			
			// Just set the title and subtitle directly - no need to check the title field
			MC.inGameHud.setTitle(Text.of(""));
			MC.inGameHud.setSubtitle(Text.of(message));
			MC.inGameHud.setTitleTicks(1, 5, 1); // fade in, stay, fade out
			
			// Update boss bar if enabled
			updateBossBar(targetItem);
			
		} catch (Exception e) {
			// Silently fail rather than crash the game
			System.out.println("Error displaying item info: " + e.getMessage());
		}
	}
	
	/**
	 * Displays item information in an overlay message at the top of the screen
	 * as a simpler alternative to a boss bar.
	 */
	private void updateBossBar(ItemEntity item) {
		if (!showBossBar.isChecked() || item == null || MC.player == null)
			return;
		
		try {
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
			if (isValuableItem(item.getStack())) {
				colorPrefix = "§d"; // Purple for valuable items
			} else if (priorityItems.contains(item.getStack().getItem().toString())) {
				colorPrefix = "§e"; // Yellow for priority items
			} else {
				colorPrefix = "§a"; // Green for normal items
			}
			
			// Create formatted message
			String message = colorPrefix + itemName + " §fx" + quantity +
					" §7(" + formattedDistance + "m) §8[" + category + "]";
			
			// Display message at the top of the screen
			MC.inGameHud.setOverlayMessage(Text.of(message), false);
			
		} catch (Exception e) {
			// Silently fail rather than crash the game
			System.out.println("Error showing item info overlay: " + e.getMessage());
		}
	}
	
	/**
	 * Checks if an entity is a boss entity that should be highlighted.
	 */
	private boolean isBossEntity(Entity entity) {
		return entity instanceof WardenEntity
				|| entity instanceof EnderDragonEntity
				|| entity instanceof WitherEntity
				|| entity instanceof ElderGuardianEntity
				|| entity instanceof EvokerEntity
				|| entity instanceof RavagerEntity
				|| entity instanceof WitherSkeletonEntity
				|| entity instanceof BlazeEntity
				|| entity instanceof PiglinBruteEntity
				|| entity instanceof VindicatorEntity
				|| (entity instanceof CreeperEntity &&
				(((CreeperEntity) entity).shouldRenderOverlay() || showRegularCreepers.isChecked()));
	}
	
	/**
	 * Checks if an item is considered valuable and should trigger alerts.
	 */
	private boolean isValuableItem(ItemStack stack) {
		String itemId = stack.getItem().toString();
		return VALUABLE_ITEMS.contains(itemId);
	}
	
	/**
	 * Plays a sound alert for a valuable item.
	 */
	private void playSoundAlert() {
		if (!enableSoundAlerts.isChecked() || MC.player == null || MC.world == null)
			return;
		
		try {
			// Play alert sound at the player's location
			MC.world.playSound(
					MC.player,               // entity (player)
					MC.player.getX(),        // x position
					MC.player.getY(),        // y position
					MC.player.getZ(),        // z position
					SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,  // sound event
					SoundCategory.PLAYERS,   // sound category
					1.0F,                    // volume
					1.0F);                   // pitch
		} catch (Exception e) {
			System.out.println("Failed to play sound alert: " + e.getMessage());
		}
	}
	
	// Target boss entity
	private Entity targetBoss = null;
	
	/**
	 * Gets the interpolated position of an entity for smooth rendering between ticks.
	 */
	private Vec3d getLerpedEntityPos(Entity entity, float partialTicks) {
		double x = entity.prevX + (entity.getX() - entity.prevX) * partialTicks;
		double y = entity.prevY + (entity.getY() - entity.prevY) * partialTicks;
		double z = entity.prevZ + (entity.getZ() - entity.prevZ) * partialTicks;
		return new Vec3d(x, y, z);
	}
	
	/**
	 * Finds the boss entity that the player is currently looking at.
	 */
	private void updateTargetBoss() {
		if (MC.player == null || bossEntities.isEmpty()) {
			targetBoss = null;
			return;
		}
		
		// Get player's look vector
		Vec3d cameraPos = MC.player.getCameraPosVec(1.0F);
		Vec3d lookVec = MC.player.getRotationVec(1.0F).normalize();
		
		// Find closest boss entity in player's line of sight
		double closestDistance = Double.MAX_VALUE;
		Entity closestBoss = null;
		
		for (Entity boss : bossEntities) {
			// Get the center of the boss entity
			Vec3d bossPos = boss.getBoundingBox().getCenter();
			
			// Calculate vector from camera to boss
			Vec3d cameraToBoss = bossPos.subtract(cameraPos);
			double distanceToBoss = cameraToBoss.length();
			
			// Skip entities that are too far away
			if (distanceToBoss > 100)
				continue;
			
			// Project the camera-to-boss vector onto the look vector
			double dot = cameraToBoss.normalize().dotProduct(lookVec);
			
			// Consider entities within a certain angle in front of the player
			if (dot > 0.8) { // Roughly 36 degrees on each side
				if (distanceToBoss < closestDistance) {
					closestDistance = distanceToBoss;
					closestBoss = boss;
				}
			}
		}
		
		targetBoss = closestBoss;
	}
	
	/**
	 * Displays information about the targeted boss entity.
	 */
	private void displayBossEntityInfo() {
		if (!showItemInfo.isChecked() || targetBoss == null || MC.player == null)
			return;
		
		try {
			// Get entity details
			String entityName = targetBoss.getDisplayName().getString();
			BossCategory category = getBossCategory(targetBoss);
			
			// Calculate distance
			double distance = MC.player.getPos().distanceTo(targetBoss.getPos());
			DecimalFormat df = new DecimalFormat("0.0");
			String formattedDistance = df.format(distance);
			
			// Create and display the subtitle
			String message = "§c" + entityName + " §7(" + formattedDistance + "m) §8[" + category + "]";
			
			// Set the title and subtitle
			MC.inGameHud.setTitle(Text.of(""));
			MC.inGameHud.setSubtitle(Text.of(message));
			MC.inGameHud.setTitleTicks(1, 5, 1); // fade in, stay, fade out
			
			// Update boss bar if enabled
			updateBossBarForEntity(targetBoss);
			
		} catch (Exception e) {
			// Silently fail rather than crash the game
			System.out.println("Error displaying boss entity info: " + e.getMessage());
		}
	}
	
	/**
	 * Displays boss entity information in an overlay message.
	 */
	private void updateBossBarForEntity(Entity entity) {
		if (!showBossBar.isChecked() || entity == null || MC.player == null)
			return;
		
		try {
			// Get entity details
			String entityName = entity.getDisplayName().getString();
			BossCategory category = getBossCategory(entity);
			
			// Calculate distance
			double distance = MC.player.getPos().distanceTo(entity.getPos());
			DecimalFormat df = new DecimalFormat("0.0");
			String formattedDistance = df.format(distance);
			
			// Create formatted message
			String message = "§c" + entityName + " §7(" + formattedDistance + "m) §8[" + category + "]";
			
			// Display message at the top of the screen
			MC.inGameHud.setOverlayMessage(Text.of(message), false);
			
		} catch (Exception e) {
			// Silently fail rather than crash the game
			System.out.println("Error showing boss entity info overlay: " + e.getMessage());
		}
	}
	
	/**
	 * Shows a title alert for a boss entity.
	 */
	private void showBossAlert(Entity entity) {
		if (!enableTitleAlerts.isChecked() || MC.player == null || entity == null)
			return;
		
		try {
			String entityName = entity.getDisplayName().getString();
			BossCategory category = getBossCategory(entity);
			
			// Calculate distance
			double distance = MC.player.getPos().distanceTo(entity.getPos());
			DecimalFormat df = new DecimalFormat("0.0");
			String formattedDistance = df.format(distance);
			
			// Special handling for charged creepers
			String titleColor = "§c§l";
			String message = category + " at " + formattedDistance + "m away";
			
			if (entity instanceof CreeperEntity && ((CreeperEntity) entity).shouldRenderOverlay()) {
				titleColor = "§b§l"; // Cyan for charged creepers
				message = "⚡ DANGER! CHARGED CREEPER DETECTED! ⚡";
			}
			
			// Show alert title and subtitle
			MC.inGameHud.setTitle(Text.of(titleColor + entityName + " SPOTTED!"));
			MC.inGameHud.setSubtitle(Text.of("§e" + message));
			MC.inGameHud.setTitleTicks(10, 60, 10); // fade in, stay, fade out
		} catch (Exception e) {
			System.out.println("Error showing boss entity alert: " + e.getMessage());
		}
	}
	
	/**
	 * Shows a title alert for a valuable item.
	 */
	private void showItemAlert(ItemEntity item) {
		if (!enableTitleAlerts.isChecked() || MC.player == null || item == null)
			return;
		
		try {
			String itemName = item.getStack().getName().getString();
			int quantity = item.getStack().getCount();
			
			// Calculate distance
			double distance = MC.player.getPos().distanceTo(item.getPos());
			DecimalFormat df = new DecimalFormat("0.0");
			String formattedDistance = df.format(distance);
			
			// Show alert title and subtitle
			MC.inGameHud.setTitle(Text.of("§5§l" + itemName));
			MC.inGameHud.setSubtitle(Text.of("§e" + quantity + " found " + formattedDistance + "m away"));
			MC.inGameHud.setTitleTicks(10, 60, 10); // fade in, stay, fade out
		} catch (Exception e) {
			System.out.println("Error showing title alert: " + e.getMessage());
		}
	}
}
