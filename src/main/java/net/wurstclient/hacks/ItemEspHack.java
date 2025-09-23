/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.awt.Color;
import java.util.ArrayList;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
import net.wurstclient.settings.filters.FilterItemCategorySetting;
import net.wurstclient.settings.filterlists.ItemFilterList;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.RenderUtils;

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
	}
	
	@Override
	public void onUpdate()
	{
		// Clear all item lists
		items.clear();
		arrows.clear();
		
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
					items.add(item);
				}
			});
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
		int arrowLineColor = arrowColor.getColorI(0x80);
		
		// Render regular items
		if(style.hasBoxes())
		{
			double extraSize = boxSize.getExtraSize() / 2;
			
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
		}
		
		if(style.hasLines())
		{
			// Render regular item tracers
			if(!items.isEmpty())
			{
				ArrayList<Vec3d> ends = new ArrayList<>();
				
				// Add tracers for all filtered items
				for(ItemEntity e : items)
				{
					ends.add(
						EntityUtils.getLerpedBox(e, partialTicks).getCenter());
				}
				
				RenderUtils.drawTracers(matrixStack, partialTicks, ends,
					lineColor, false);
			}
			
			// Render arrow tracers separately
			if(!arrows.isEmpty() && highlightArrows.isChecked())
			{
				ArrayList<Vec3d> arrowEnds = new ArrayList<>();
				
				// Add tracers for all arrows
				for(ItemEntity e : arrows)
				{
					arrowEnds.add(
						EntityUtils.getLerpedBox(e, partialTicks).getCenter());
				}
				
				RenderUtils.drawTracers(matrixStack, partialTicks, arrowEnds,
					arrowLineColor, false);
			}
		}
	}
}
