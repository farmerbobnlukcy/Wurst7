/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.itemgatherer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.BlockPos;
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
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.RenderUtils;

@SearchTags({"item gatherer", "loot finder", "collector"})
@DontSaveState
public final class ItemGathererHack extends Hack
		implements UpdateListener, RenderListener
{
	private final SliderSetting range = new SliderSetting("Range",
			"Maximum distance to search for items.", 32, 8, 64, 1,
			ValueDisplay.INTEGER);
	
	private final SliderSetting minDistance = new SliderSetting("Min Distance",
			"Minimum distance to items before considering the path complete.", 1.5, 0.5, 4, 0.1,
			ValueDisplay.DECIMAL);
	
	private final CheckboxSetting filterMode =
			new CheckboxSetting("Filter Mode",
					"Only gather certain items (configurable in itemfilters.json).", false);
	
	private final SliderSetting priorityDistance = new SliderSetting(
			"Priority Distance",
			"Items closer than this distance (in blocks) will be prioritized over more distant items.",
			8, 2, 16, 0.5, ValueDisplay.DECIMAL);
	
	private ItemEntity targetItem;
	private ItemPathFinder pathFinder;
	private PathProcessor processor;
	
	private final ArrayList<ItemEntity> itemsToRender = new ArrayList<>();
	
	public ItemGathererHack()
	{
		super("ItemGatherer");
		setCategory(Category.MOVEMENT);
		addSetting(range);
		addSetting(minDistance);
		addSetting(filterMode);
		addSetting(priorityDistance);
	}
	
	@Override
	public String getRenderName()
	{
		if(targetItem != null)
			return getName() + " [" + targetItem.getStack().getItem().getName().getString() + "]";
		
		if(pathFinder != null && !pathFinder.isDone() && !pathFinder.isFailed())
			return getName() + " [Searching]";
		
		if(processor != null && !processor.isDone())
			return getName() + " [Going]";
		
		return getName();
	}
	
	@Override
	protected void onEnable()
	{
		targetItem = null;
		pathFinder = null;
		processor = null;
		itemsToRender.clear();
		
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
	}
	
	@Override
	public void onUpdate()
	{
		// Find suitable target if we don't have one
		if(targetItem == null || !targetItem.isAlive())
		{
			targetItem = findBestItemToGet();
			pathFinder = null;
			processor = null;
			
			if(targetItem == null)
				return;
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
		
		// Process path
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
		}
		
		// Reset if path failed or processing is done
		pathFinder = null;
		processor = null;
		
		// Check if target is still valid
		if(!targetItem.isAlive() || playerPos.squaredDistanceTo(targetPos) > range.getValueSq())
			targetItem = null;
	}
	
	private ItemEntity findBestItemToGet()
	{
		ClientPlayerEntity player = MC.player;
		double rangeSq = range.getValueSq();
		Vec3d playerPos = player.getPos();
		double priorityDistanceSq = priorityDistance.getValueSq();
		
		// Get all item entities within range
		Stream<ItemEntity> itemsStream = MC.world.getEntities()
				.stream()
				.filter(e -> e instanceof ItemEntity)
				.map(e -> (ItemEntity)e)
				.filter(e -> e.isAlive() && playerPos.squaredDistanceTo(e.getPos()) <= rangeSq);
		
		// Apply filters if filter mode is enabled
		if(filterMode.isChecked())
		{
			// Implement your item filtering logic here
			// For example, check against a list of allowed item names
			// This is where you'd load from itemfilters.json
		}
		
		// Find the closest item to prioritize
		ItemEntity closestItem = itemsStream
				.min(Comparator.comparingDouble(e -> playerPos.squaredDistanceTo(e.getPos())))
				.orElse(null);
		
		if(closestItem == null)
			return null;
		
		// If the closest item is within priority distance, return it
		if(playerPos.squaredDistanceTo(closestItem.getPos()) <= priorityDistanceSq)
			return closestItem;
		
		// Otherwise, prioritize items that are more valuable
		// This is a simple example - you might want more sophisticated item value logic
		return MC.world.getEntities()
				.stream()
				.filter(e -> e instanceof ItemEntity)
				.map(e -> (ItemEntity)e)
				.filter(e -> e.isAlive() && playerPos.squaredDistanceTo(e.getPos()) <= rangeSq)
				.max(Comparator.comparing(e -> getItemValue(e)))
				.orElse(closestItem);
	}
	
	private int getItemValue(ItemEntity entity)
	{
		// Simple implementation - prioritize stacks with more items
		return entity.getStack().getCount();
		
		// For a more complex implementation, you could add logic for:
		// - Item rarity
		// - Item type (tools, weapons, etc.)
		// - Custom priorities
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
			double x = targetItem.prevX + (targetItem.getX() - targetItem.prevX) * partialTicks;
			double y = targetItem.prevY + (targetItem.getY() - targetItem.prevY) * partialTicks;
			double z = targetItem.prevZ + (targetItem.getZ() - targetItem.prevZ) * partialTicks;
			
			Vec3d pos = new Vec3d(x, y, z);
			RenderUtils.drawBoxAround(matrixStack, pos, 0.5, 0xFFFFFF00, true);
		}
		
		// Update and render all items within range
		updateItemsToRender();
		for(ItemEntity item : itemsToRender)
		{
			if(item == targetItem || !item.isAlive())
				continue;
			
			// Calculate actual position with partial ticks for smooth rendering
			double x = item.prevX + (item.getX() - item.prevX) * partialTicks;
			double y = item.prevY + (item.getY() - item.prevY) * partialTicks;
			double z = item.prevZ + (item.getZ() - item.prevZ) * partialTicks;
			
			Vec3d pos = new Vec3d(x, y, z);
			RenderUtils.drawBoxAround(matrixStack, pos, 0.25, 0x7700FF00, true);
		}
	}
	
	private void updateItemsToRender()
	{
		itemsToRender.clear();
		
		ClientPlayerEntity player = MC.player;
		double rangeSq = range.getValueSq();
		Vec3d playerPos = player.getPos();
		
		List<Entity> entities = MC.world.getEntities();
		for(Entity entity : entities)
		{
			if(entity instanceof ItemEntity item && item.isAlive() &&
					playerPos.squaredDistanceTo(item.getPos()) <= rangeSq)
			{
				itemsToRender.add(item);
			}
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
			return done = playerPos.squaredDistanceTo(target.getPos()) <= minDistance.getValueSq();
		}
		
		@Override
		public ArrayList<PathPos> formatPath()
		{
			if(!done)
				failed = true;
			
			return super.formatPath();
		}
	}
}