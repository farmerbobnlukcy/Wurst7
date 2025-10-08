/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.RightClickListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.*;
import net.wurstclient.util.BlockPlacer.BlockPlacingParams;

@SearchTags({"auto golem", "iron golem", "golem builder"})
public final class AutoGolemHack extends Hack
	implements UpdateListener, RightClickListener, RenderListener
{
	private static final Box BLOCK_BOX =
		new Box(1 / 16.0, 1 / 16.0, 1 / 16.0, 15 / 16.0, 15 / 16.0, 15 / 16.0);
	
	private final SliderSetting range = new SliderSetting("Range",
		"How far to reach when placing blocks.\n" + "Recommended values:\n"
			+ "6.0 for vanilla\n" + "4.25 for NoCheat+",
		6, 1, 10, 0.05, ValueDisplay.DECIMAL);
	
	private final CheckboxSetting checkLOS = new CheckboxSetting(
		"Check line of sight",
		"Makes sure that you don't reach through walls when placing blocks. Can help with AntiCheat plugins but slows down building.",
		false);
	
	private final CheckboxSetting fastPlace =
		new CheckboxSetting("Always FastPlace",
			"Builds as if FastPlace was enabled, even if it's not.", true);
	
	private Status status = Status.IDLE;
	private LinkedHashMap<BlockPos, Item> remainingBlocks =
		new LinkedHashMap<>();
	
	public AutoGolemHack()
	{
		super("AutoGolem");
		setCategory(Category.BLOCKS);
		addSetting(range);
		addSetting(checkLOS);
		addSetting(fastPlace);
	}
	
	@Override
	public String getRenderName()
	{
		String name = getName();
		
		if(status == Status.BUILDING)
		{
			int total = 5; // 4 iron blocks + 1 pumpkin
			int placed = total - remainingBlocks.size();
			name += " [" + placed + "/" + total + "]";
		}
		
		return name;
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RightClickListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RightClickListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		remainingBlocks.clear();
		status = Status.IDLE;
	}
	
	@Override
	public void onRightClick(RightClickEvent event)
	{
		if(status != Status.IDLE)
			return;
		
		HitResult hitResult = MC.crosshairTarget;
		if(hitResult == null || hitResult.getType() != HitResult.Type.BLOCK
			|| !(hitResult instanceof BlockHitResult blockHitResult))
			return;
		
		BlockPos hitResultPos = blockHitResult.getBlockPos();
		if(!BlockUtils.canBeClicked(hitResultPos))
			return;
		
		// Check if we have the required materials
		if(!hasRequiredMaterials())
		{
			ChatUtils
				.error("You need 4 iron blocks and 1 pumpkin/carved pumpkin!");
			return;
		}
		
		BlockPos startPos = hitResultPos.offset(blockHitResult.getSide());
		Direction direction = MC.player.getHorizontalFacing();
		
		// Build the map of blocks to place (similar to AutoBuild)
		remainingBlocks = getGolemBlocks(startPos, direction);
		
		status = Status.BUILDING;
	}
	
	@Override
	public void onUpdate()
	{
		if(status != Status.BUILDING)
			return;
		
		// Remove blocks that have already been placed
		remainingBlocks.keySet()
			.removeIf(pos -> !BlockUtils.getState(pos).isReplaceable());
		
		if(remainingBlocks.isEmpty())
		{
			ChatUtils.message("Iron golem built successfully!");
			status = Status.IDLE;
			return;
		}
		
		if(!fastPlace.isChecked() && MC.itemUseCooldown > 0)
			return;
		
		double rangeSq = range.getValueSq();
		for(Map.Entry<BlockPos, Item> entry : remainingBlocks.entrySet())
		{
			BlockPos pos = entry.getKey();
			Item item = entry.getValue();
			
			// Skip pumpkin if there are still iron blocks to place
			boolean isPumpkin =
				item == Items.CARVED_PUMPKIN || item == Items.PUMPKIN;
			if(isPumpkin && remainingBlocks.size() > 1)
				continue;
			
			BlockPlacingParams params = BlockPlacer.getBlockPlacingParams(pos);
			if(params == null || params.distanceSq() > rangeSq
				|| checkLOS.isChecked() && !params.lineOfSight())
				continue;
			
			// Select the correct item
			if(!MC.player.getMainHandStack().isOf(item))
			{
				giveOrSelectItem(item);
				return;
			}
			
			// Jump if placing pumpkin (it's 2 blocks high)
			if(isPumpkin && MC.player.isOnGround())
				MC.player.jump();
			
			// Place the block
			MC.itemUseCooldown = 4;
			RotationUtils.getNeededRotations(params.hitVec())
				.sendPlayerLookPacket();
			InteractionSimulator.rightClickBlock(params.toHitResult());
			return;
		}
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		if(status != Status.BUILDING)
			return;
		
		List<BlockPos> blocksToDraw = remainingBlocks.keySet().stream()
			.filter(pos -> BlockUtils.getState(pos).isReplaceable()).limit(1024)
			.toList();
		
		int black = 0x80000000;
		List<Box> outlineBoxes =
			blocksToDraw.stream().map(pos -> BLOCK_BOX.offset(pos)).toList();
		RenderUtils.drawOutlinedBoxes(matrixStack, outlineBoxes, black, true);
		
		int green = 0x2600FF00;
		double rangeSq = range.getValueSq();
		List<Box> greenBoxes = blocksToDraw.stream().filter(pos -> {
			BlockPlacingParams params = BlockPlacer.getBlockPlacingParams(pos);
			return params != null && params.distanceSq() <= rangeSq;
		}).map(pos -> BLOCK_BOX.offset(pos)).toList();
		RenderUtils.drawSolidBoxes(matrixStack, greenBoxes, green, true);
	}
	
	private LinkedHashMap<BlockPos, Item> getGolemBlocks(BlockPos origin,
		Direction direction)
	{
		Direction front = direction;
		Direction left = front.rotateYCounterclockwise();
		LinkedHashMap<BlockPos, Item> blocksToPlace = new LinkedHashMap<>();
		
		// Iron golem structure (similar to AutoBuildTemplate format)
		// Base iron block (center)
		BlockPos pos = origin;
		blocksToPlace.put(pos, Items.IRON_BLOCK);
		
		// Center iron block
		pos = origin.up();
		blocksToPlace.put(pos, Items.IRON_BLOCK);
		
		// Left arm
		pos = origin.up().offset(left);
		blocksToPlace.put(pos, Items.IRON_BLOCK);
		
		// Right arm
		pos = origin.up().offset(left.getOpposite());
		blocksToPlace.put(pos, Items.IRON_BLOCK);
		
		// Pumpkin head (placed last to spawn the golem)
		pos = origin.up(2);
		blocksToPlace.put(pos, Items.CARVED_PUMPKIN);
		
		return blocksToPlace;
	}
	
	private boolean hasRequiredMaterials()
	{
		int ironBlocks = InventoryUtils
			.count(stack -> stack.isOf(Items.IRON_BLOCK), 36, false);
		int pumpkins = InventoryUtils.count(stack -> stack.isOf(Items.PUMPKIN)
			|| stack.isOf(Items.CARVED_PUMPKIN), 36, false);
		
		return ironBlocks >= 4 && pumpkins >= 1;
	}
	
	private void giveOrSelectItem(Item item)
	{
		if(InventoryUtils.selectItem(item, 36, true))
			return;
		
		if(!MC.player.isInCreativeMode())
			return;
		
		PlayerInventory inventory = MC.player.getInventory();
		int slot = inventory.getEmptySlot();
		if(slot < 0)
			slot = inventory.selectedSlot;
		
		ItemStack stack = new ItemStack(item);
		InventoryUtils.setCreativeStack(slot, stack);
	}
	
	private enum Status
	{
		IDLE,
		BUILDING;
	}
}
