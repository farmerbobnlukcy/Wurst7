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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.CameraTransformViewBobbingListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EspBoxSizeSetting;
import net.wurstclient.settings.EspStyleSetting;
import net.wurstclient.settings.FileSetting;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.StreamUtils;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
	
	private final Set<String> filteredItems = new HashSet<>();
	private final ArrayList<ItemEntity> items = new ArrayList<>();
	
	public ItemEspHack() {
		super("ItemESP");
		setCategory(Category.RENDER);
		addSetting(style);
		addSetting(boxSize);
		addSetting(color);
		addSetting(filterList);
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
			
			RenderUtils.drawTracers(matrixStack, partialTicks, ends, lineColor,
					false);
		}
	}
}
