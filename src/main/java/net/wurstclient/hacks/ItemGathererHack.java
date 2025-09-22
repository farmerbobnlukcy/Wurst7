/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstClient;
import net.wurstclient.ai.PathFinder;
import net.wurstclient.ai.PathPos;
import net.wurstclient.ai.PathProcessor;
import net.wurstclient.commands.PathCmd;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ItemListSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.RenderUtils;

@SearchTags({"item gatherer", "loot finder", "collector"})
@DontSaveState
public final class ItemGathererHack extends Hack
	implements UpdateListener, RenderListener
{
	private final SliderSetting range =
		new SliderSetting("Range", "Maximum distance to search for items.", 32,
			8, 64, 1, ValueDisplay.INTEGER);
	
	private final SliderSetting minDistance = new SliderSetting("Min Distance",
		"Minimum distance to items before considering the path complete.", 1.5,
		0.5, 4, 0.1, ValueDisplay.DECIMAL);
	
	private final CheckboxSetting filterMode =
		new CheckboxSetting("Filter Mode",
			"Only gather certain items (configured in the Item List).", false);
	
	private final ItemListSetting itemList = new ItemListSetting("Item List",
		"Items that ItemGatherer will collect when Filter Mode is enabled.",
		"minecraft:diamond", "minecraft:emerald", "minecraft:gold_ingot",
		"minecraft:iron_ingot", "minecraft:netherite_ingot");
	
	private final CheckboxSetting stopWhenInventoryFull = new CheckboxSetting(
		"Stop When Full",
		"Automatically disables the hack when your inventory is full.", true);
	
	private final SliderSetting maxAttempts = new SliderSetting("Max Attempts",
		"Maximum number of pathfinding attempts before giving up on an item.",
		5, 1, 10, 1, ValueDisplay.INTEGER);
	
	private final SliderSetting priorityDistance = new SliderSetting(
		"Priority Distance",
		"Items closer than this distance (in blocks) will be prioritized over more distant items.",
		8, 2, 16, 0.5, ValueDisplay.DECIMAL);
	
	private ItemEntity targetItem;
	private ItemPathFinder pathFinder;
	private PathProcessor processor;
	
	private final ArrayList<ItemEntity> itemsToRender = new ArrayList<>();
	private ArrayList<String> itemNamesCache;
	private boolean wasInventoryFullLastCheck = false;
	private int currentAttempts = 0;
	private ArrayList<ItemEntity> unreachableItems = new ArrayList<>();
	
	public ItemGathererHack()
	{
		super("ItemGatherer");
		setCategory(Category.MOVEMENT);
		addSetting(range);
		addSetting(minDistance);
		addSetting(filterMode);
		addSetting(itemList);
		addSetting(stopWhenInventoryFull);
		addSetting(maxAttempts);
		addSetting(priorityDistance);
	}
	
	@Override
	public String getRenderName()
	{
		if(targetItem != null)
			return getName() + " ["
				+ targetItem.getStack().getItem().getName().getString() + "]";
		
		if(pathFinder != null && !pathFinder.isDone() && !pathFinder.isFailed())
			return getName() + " [Searching]";
		
		if(processor != null && !processor.isDone())
			return getName() + " [Going]";
		
		return getName();
	}
	
	@Override
	protected void onEnable()
	{
		// Cache item names for filter
		itemNamesCache = new ArrayList<>(itemList.getItemNames());
		
		wasInventoryFullLastCheck = false;
		targetItem = null;
		pathFinder = null;
		processor = null;
		itemsToRender.clear();
		unreachableItems.clear();
		currentAttempts = 0;
		
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		PathProcessor.releaseControls();
		
		targetItem = null;
		pathFinder = null;
		processor = null;
		itemsToRender.clear();
		itemNamesCache = null;
		unreachableItems.clear();
	}
	
	@Override
	public void onUpdate()
	{
		// Check if we're in AFK mode
		
		// Check if inventory is full
		if(stopWhenInventoryFull.isChecked() && isInventoryFull())
		{
			if(!wasInventoryFullLastCheck)
			{
				wasInventoryFullLastCheck = true;
				ChatUtils.message(
					"§8[§6§lItemGatherer§8] §fInventory full, stopping.");
				setEnabled(false);
			}
			return;
		}
		wasInventoryFullLastCheck = false;
		
		// Find suitable target if we don't have one
		if(targetItem == null || !targetItem.isAlive()
			|| unreachableItems.contains(targetItem))
		{
			// Reset attempt counter for new target
			currentAttempts = 0;
			
			// Find a new target
			targetItem = findBestItemToGet();
			pathFinder = null;
			processor = null;
			
			if(targetItem == null)
			{
				if(!unreachableItems.isEmpty())
				{
					ChatUtils.message(
						"§8[§6§lItemGatherer§8] §fNo reachable items found, stopping.");
					setEnabled(false);
				}
				return;
			}
		}
		
		Vec3d playerPos = MC.player.getPos();
		Vec3d targetPos = targetItem.getPos();
		
		// Check if we're already close enough to grab the item
		double distanceSq = playerPos.squaredDistanceTo(targetPos);
		if(distanceSq <= minDistance.getValueSq())
		{
			targetItem = null;
			pathFinder = null;
			processor = null;
			return;
		}
		
		// Initialize pathfinder
		if(pathFinder == null)
		{
			pathFinder = new ItemPathFinder(targetItem);
			return;
		}
		
		// Continue finding path
		if(!pathFinder.isDone() && !pathFinder.isFailed())
		{
			PathProcessor.lockControls();
			pathFinder.think();
			return;
		}
		
		// Process path if not failed
		if(!pathFinder.isFailed())
		{
			if(processor == null)
			{
				pathFinder.formatPath();
				processor = pathFinder.getProcessor();
			}
			
			if(!processor.isDone())
			{
				processor.process();
				return;
			}
		}else
		{
			// Increment attempt counter when path fails
			currentAttempts++;
			
			// If we've tried too many times, mark this item as unreachable
			if(currentAttempts >= maxAttempts.getValueI())
			{
				ChatUtils.message("§8[§6§lItemGatherer§8] §fCouldn't reach "
					+ targetItem.getStack().getItem().getName().getString()
					+ " after " + maxAttempts.getValueI()
					+ " attempts, skipping.");
				
				unreachableItems.add(targetItem);
				targetItem = null;
			}
		}
		
		// Reset path finder and processor for next attempt or new target
		pathFinder = null;
		processor = null;
		
		// Check if target is still valid
		if(targetItem != null && (!targetItem.isAlive()
			|| playerPos.squaredDistanceTo(targetPos) > range.getValueSq()))
		{
			targetItem = null;
		}
	}
	
	private boolean isInventoryFull()
	{
		ClientPlayerEntity player = MC.player;
		if(player == null)
			return false;
		
		// Check if all non-hotbar slots are full
		for(int i = 9; i < 36; i++)
		{
			if(player.getInventory().getStack(i).isEmpty())
				return false;
		}
		
		// Check if all hotbar slots are full
		for(int i = 0; i < 9; i++)
		{
			if(player.getInventory().getStack(i).isEmpty())
				return false;
		}
		
		return true;
	}
	
	private ItemEntity findBestItemToGet()
	{
		ClientPlayerEntity player = MC.player;
		double rangeSq = range.getValueSq();
		Vec3d playerPos = player.getPos();
		double priorityDistanceSq = priorityDistance.getValueSq();
		
		// Check for AFK state
		
		// Collect all item entities within range
		ArrayList<ItemEntity> itemsInRange = new ArrayList<>();
		for(Entity entity : MC.world.getEntities())
		{
			if(!(entity instanceof ItemEntity))
				continue;
			
			ItemEntity item = (ItemEntity)entity;
			if(!item.isAlive())
				continue;
			
			if(playerPos.squaredDistanceTo(item.getPos()) > rangeSq)
				continue;
			
			// Skip items that we've already determined are unreachable
			if(unreachableItems.contains(item))
				continue;
			
			itemsInRange.add(item);
		}
		
		// If no items found, return null
		if(itemsInRange.isEmpty())
			return null;
		
		// Apply filters if filter mode is enabled
		if(filterMode.isChecked())
		{
			ArrayList<ItemEntity> filteredItems = new ArrayList<>();
			for(ItemEntity item : itemsInRange)
			{
				if(isAllowedItem(item))
					filteredItems.add(item);
			}
			
			itemsInRange = filteredItems;
		}
		
		// If after filtering no items remain, return null
		if(itemsInRange.isEmpty())
			return null;
		
		// Find the closest item to prioritize
		ItemEntity closestItem = null;
		double closestDistSq = Double.MAX_VALUE;
		
		for(ItemEntity item : itemsInRange)
		{
			double distSq = playerPos.squaredDistanceTo(item.getPos());
			if(distSq < closestDistSq)
			{
				closestDistSq = distSq;
				closestItem = item;
			}
		}
		
		// If the closest item is within priority distance, return it
		if(closestItem != null && closestDistSq <= priorityDistanceSq)
			return closestItem;
		
		// Otherwise, prioritize items that are more valuable
		ItemEntity mostValuableItem = null;
		int highestValue = -1;
		
		for(ItemEntity item : itemsInRange)
		{
			int value = getItemValue(item);
			if(value > highestValue)
			{
				highestValue = value;
				mostValuableItem = item;
			}
		}
		
		return mostValuableItem != null ? mostValuableItem : closestItem;
	}
	
	private boolean isAllowedItem(ItemEntity entity)
	{
		// Get the item name
		String itemName = entity.getStack().getItem().toString();
		
		// Check if the item is in our allowed items list
		return itemNamesCache.contains(itemName);
	}
	
	private int getItemValue(ItemEntity entity)
	{
		Item item = entity.getStack().getItem();
		int count = entity.getStack().getCount();
		
		// Assign value based on item type - high value for rare items
		if(item == Items.DIAMOND || item == Items.DIAMOND_BLOCK
			|| item == Items.NETHERITE_INGOT || item == Items.NETHERITE_BLOCK)
			return 1000 * count;
		
		if(item == Items.EMERALD || item == Items.EMERALD_BLOCK)
			return 800 * count;
		
		if(item == Items.GOLD_INGOT || item == Items.GOLD_BLOCK)
			return 500 * count;
		
		if(item == Items.IRON_INGOT || item == Items.IRON_BLOCK)
			return 300 * count;
		
		// Default value - based on stack size
		return count;
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		if(!isEnabled())
			return;
		
		// Draw path
		PathCmd pathCmd = WurstClient.INSTANCE.getCmds().pathCmd;
		if(pathFinder != null)
			pathFinder.renderPath(matrixStack, pathCmd.isDebugMode(),
				pathCmd.isDepthTest());
		
		// Highlight target item
		if(targetItem != null && targetItem.isAlive())
		{
			// Calculate actual position with partial ticks for smooth rendering
			double x = targetItem.prevX
				+ (targetItem.getX() - targetItem.prevX) * partialTicks;
			double y = targetItem.prevY
				+ (targetItem.getY() - targetItem.prevY) * partialTicks;
			double z = targetItem.prevZ
				+ (targetItem.getZ() - targetItem.prevZ) * partialTicks;
			
			Vec3d pos = new Vec3d(x, y, z);
			Box box = new Box(pos, pos).expand(0.5);
			RenderUtils.drawOutlinedBox(matrixStack, box, 0xFFFFFF00, true);
		}
		
		// Update and render all items within range
		updateItemsToRender();
		for(ItemEntity item : itemsToRender)
		{
			if(item == targetItem || !item.isAlive())
				continue;
			
			// Use different colors for allowed and filtered items
			int color = 0x7700FF00; // Default green for all items
			
			if(unreachableItems.contains(item))
				color = 0x77FF00FF; // Purple for unreachable items
			else if(filterMode.isChecked() && !isAllowedItem(item))
				color = 0x77FF0000; // Red for filtered-out items
				
			// Calculate actual position with partial ticks for smooth rendering
			double x = item.prevX + (item.getX() - item.prevX) * partialTicks;
			double y = item.prevY + (item.getY() - item.prevY) * partialTicks;
			double z = item.prevZ + (item.getZ() - item.prevZ) * partialTicks;
			
			Vec3d pos = new Vec3d(x, y, z);
			Box box = new Box(pos, pos).expand(0.25);
			RenderUtils.drawOutlinedBox(matrixStack, box, color, true);
		}
	}
	
	private void updateItemsToRender()
	{
		itemsToRender.clear();
		
		// Check if player is null (can happen during world loading)
		if(MC.player == null || MC.world == null)
			return;
		
		ClientPlayerEntity player = MC.player;
		double rangeSq = range.getValueSq();
		Vec3d playerPos = player.getPos();
		
		for(Entity entity : MC.world.getEntities())
		{
			if(!(entity instanceof ItemEntity))
				continue;
			
			ItemEntity item = (ItemEntity)entity;
			if(!item.isAlive())
				continue;
			
			if(playerPos.squaredDistanceTo(item.getPos()) > rangeSq)
				continue;
			
			itemsToRender.add(item);
		}
	}
	
	private class ItemPathFinder extends PathFinder
	{
		private final ItemEntity target;
		
		public ItemPathFinder(ItemEntity target)
		{
			super(BlockPos.ofFloored(target.getPos()));
			this.target = target;
			setThinkTime(1);
		}
		
		@Override
		protected boolean isMineable(BlockPos pos)
		{
			return false; // We don't want to break blocks to get to items
		}
		
		@Override
		protected boolean checkDone()
		{
			Vec3d playerPos = Vec3d.ofBottomCenter(current);
			return done = playerPos
				.squaredDistanceTo(target.getPos()) <= minDistance.getValueSq();
		}
		
		@Override
		public ArrayList<PathPos> formatPath()
		{
			// Don't automatically mark as failed if not done
			return super.formatPath();
		}
	}
}
