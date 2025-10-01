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
import java.util.HashSet;
import java.util.Set;

@SearchTags({"item esp", "ItemTracers", "item tracers"})
public final class ItemEspHack extends Hack implements UpdateListener,
		CameraTransformViewBobbingListener, RenderListener {
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
	
	private final CheckboxSetting dashedLines = new CheckboxSetting(
			"Dashed lines", "Draw tracers as dashed lines instead of solid lines.",
			true);
	
	private final SliderSetting dashLength = new SliderSetting("Dash length",
			"Length of each dash in the dashed line.", 0.5, 0.1, 3.0, 0.1,
			SliderSetting.ValueDisplay.DECIMAL);
	
	private final Set<String> filteredItems = new HashSet<>();
	private final ArrayList<ItemEntity> items = new ArrayList<>();
	
	public ItemEspHack() {
		super("ItemESP");
		setCategory(Category.RENDER);
		addSetting(style);
		addSetting(boxSize);
		addSetting(color);
		addSetting(filterList);
		addSetting(dashedLines);
		addSetting(dashLength);
		addSetting(showItemInfo);
	}
	
	private void createDefaultFilterList(Path folder) {
		Path path = folder.resolve("default_filters.txt");
		
		try {
			Files.writeString(path,
					"# Add items to filter out (one per line)\n" +
							"# Example:\n" +
							"# minecraft:dirt\n" +
							"# minecraft:stone\n");
			
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private void loadFilteredItems() {
		filteredItems.clear();
		try {
			ArrayList<String> lines =
					StreamUtils.readAllLines(Files.newInputStream(filterList.getSelectedFile()));
			
			for (String line : lines) {
				// Skip empty lines and comments
				if (line.trim().isEmpty() || line.trim().startsWith("#"))
					continue;
				
				filteredItems.add(line.trim());
			}
			
		} catch (IOException e) {
			System.out.println("Couldn't load filtered items: " + e.getMessage());
		}
	}
	
	@Override
	protected void onEnable() {
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(CameraTransformViewBobbingListener.class, this);
		EVENTS.add(RenderListener.class, this);
		
		loadFilteredItems();
	}
	
	@Override
	protected void onDisable() {
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(CameraTransformViewBobbingListener.class, this);
		EVENTS.remove(RenderListener.class, this);
	}
	
	@Override
	public void onUpdate() {
		items.clear();
		
		// Reload the filter list if the file was changed
		if (filterList.getSelectedFile().toFile().exists())
			loadFilteredItems();
		
		for (Entity entity : MC.world.getEntities()) {
			if (entity instanceof ItemEntity) {
				ItemEntity item = (ItemEntity) entity;
				String itemId = item.getStack().getItem().toString();
				
				// Skip filtered items
				if (filteredItems.contains(itemId))
					continue;
				
				items.add(item);
			}
		}
		
		// Find the item player is looking at
		if (showItemInfo.isChecked() && System.currentTimeMillis() - lastInfoUpdateTime >= INFO_UPDATE_DELAY) {
			updateTargetItem();
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
		
		// Get player's look vector
		Vec3d cameraPos = MC.player.getCameraPosVec(1.0F);
		Vec3d lookVec = MC.player.getRotationVec(1.0F).normalize();
		
		// Find closest item in player's line of sight
		double closestDistance = Double.MAX_VALUE;
		ItemEntity closestItem = null;
		
		for (ItemEntity item : items) {
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
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks) {
		int lineColor = color.getColorI(0x80);
		
		if (style.hasBoxes()) {
			double extraSize = boxSize.getExtraSize() / 2;
			
			ArrayList<Box> boxes = new ArrayList<>(items.size());
			for (ItemEntity e : items)
				boxes.add(EntityUtils.getLerpedBox(e, partialTicks)
						.offset(0, extraSize, 0).expand(extraSize));
			
			RenderUtils.drawOutlinedBoxes(matrixStack, boxes, lineColor, false);
		}
		
		if (style.hasLines()) {
			ArrayList<Vec3d> ends = new ArrayList<>(items.size());
			for (ItemEntity e : items)
				ends.add(EntityUtils.getLerpedBox(e, partialTicks).getCenter());
			
			if (dashedLines.isChecked())
				RenderUtils.drawDashedTracers(matrixStack, partialTicks, ends, lineColor,
						false, (float) dashLength.getValue());
			else
				RenderUtils.drawTracers(matrixStack, partialTicks, ends, lineColor,
						false);
		}
		
		// Display information about the target item
		displayTargetItemInfo();
	}
	
	/**
	 * Displays information about the target item as a subtitle.
	 */
	private void displayTargetItemInfo() {
		if (!showItemInfo.isChecked() || targetItem == null || MC.player == null)
			return;
		
		// Get item details
		String itemName = targetItem.getStack().getName().getString();
		int quantity = targetItem.getStack().getCount();
		
		// Calculate distance
		double distance = MC.player.getPos().distanceTo(targetItem.getPos());
		DecimalFormat df = new DecimalFormat("0.0");
		String formattedDistance = df.format(distance);
		
		// Create and display the subtitle
		String message = "§e" + itemName + " §fx" + quantity + " §7(" + formattedDistance + "m)";
		MC.inGameHud.setSubtitle(Text.of(message));
		
		// Set an empty title to make the subtitle visible without a title
		if (MC.inGameHud.title == null || MC.inGameHud.title.getString().isEmpty()) {
			MC.inGameHud.setTitle(Text.of(""));
			// Set a short title stay time so it will only show while looking at item
			MC.inGameHud.setTitleTicks(1, 2, 1);
		}
	}
}
