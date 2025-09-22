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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.*;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.ChestBoatEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.entity.vehicle.ChestRaftEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.events.CameraTransformViewBobbingListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.chestesp.ChestEspBlockGroup;
import net.wurstclient.hacks.chestesp.ChestEspEntityGroup;
import net.wurstclient.hacks.chestesp.ChestEspGroup;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EspStyleSetting;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.chunk.ChunkUtils;

public class ChestEspHack extends Hack implements UpdateListener,
	CameraTransformViewBobbingListener, RenderListener
{
	private final EspStyleSetting style = new EspStyleSetting();
	
	private final ChestEspBlockGroup basicChests = new ChestEspBlockGroup(
		new ColorSetting("Chest color",
			"Normal chests will be highlighted in this color.", Color.GREEN),
		null);
	
	private final ChestEspBlockGroup trapChests = new ChestEspBlockGroup(
		new ColorSetting("Trap chest color",
			"Trapped chests will be highlighted in this color.",
			new Color(0xFF8000)),
		new CheckboxSetting("Include trap chests", true));
	
	private final ChestEspBlockGroup enderChests = new ChestEspBlockGroup(
		new ColorSetting("Ender color",
			"Ender chests will be highlighted in this color.", Color.CYAN),
		new CheckboxSetting("Include ender chests", true));
	
	private final ChestEspEntityGroup chestCarts =
		new ChestEspEntityGroup(
			new ColorSetting("Chest cart color",
				"Minecarts with chests will be highlighted in this color.",
				Color.YELLOW),
			new CheckboxSetting("Include chest carts", true));
	
	private final ChestEspEntityGroup chestBoats =
		new ChestEspEntityGroup(
			new ColorSetting("Chest boat color",
				"Boats with chests will be highlighted in this color.",
				Color.YELLOW),
			new CheckboxSetting("Include chest boats", true));
	
	private final ChestEspBlockGroup barrels = new ChestEspBlockGroup(
		new ColorSetting("Barrel color",
			"Barrels will be highlighted in this color.", Color.GREEN),
		new CheckboxSetting("Include barrels", true));
	
	private final ChestEspBlockGroup pots = new ChestEspBlockGroup(
		new ColorSetting("Pots color",
			"Decorated pots will be highlighted in this color.", Color.GREEN),
		new CheckboxSetting("Include pots", false));
	
	private final ChestEspBlockGroup shulkerBoxes = new ChestEspBlockGroup(
		new ColorSetting("Shulker color",
			"Shulker boxes will be highlighted in this color.", Color.MAGENTA),
		new CheckboxSetting("Include shulkers", true));
	
	private final ChestEspBlockGroup hoppers = new ChestEspBlockGroup(
		new ColorSetting("Hopper color",
			"Hoppers will be highlighted in this color.", Color.WHITE),
		new CheckboxSetting("Include hoppers", false));
	
	private final ChestEspEntityGroup hopperCarts =
		new ChestEspEntityGroup(
			new ColorSetting("Hopper cart color",
				"Minecarts with hoppers will be highlighted in this color.",
				Color.YELLOW),
			new CheckboxSetting("Include hopper carts", false));
	
	private final ChestEspBlockGroup droppers = new ChestEspBlockGroup(
		new ColorSetting("Dropper color",
			"Droppers will be highlighted in this color.", Color.WHITE),
		new CheckboxSetting("Include droppers", false));
	
	private final ChestEspBlockGroup dispensers = new ChestEspBlockGroup(
		new ColorSetting("Dispenser color",
			"Dispensers will be highlighted in this color.",
			new Color(0xFF8000)),
		new CheckboxSetting("Include dispensers", false));
	
	private final ChestEspBlockGroup crafters = new ChestEspBlockGroup(
		new ColorSetting("Crafter color",
			"Crafters will be highlighted in this color.", Color.WHITE),
		new CheckboxSetting("Include crafters", false));
	
	private final ChestEspBlockGroup furnaces =
		new ChestEspBlockGroup(new ColorSetting("Furnace color",
			"Furnaces, smokers, and blast furnaces will be highlighted in this color.",
			Color.RED), new CheckboxSetting("Include furnaces", false));
	
	private final List<ChestEspGroup> groups =
		Arrays.asList(basicChests, trapChests, enderChests, chestCarts,
			chestBoats, barrels, pots, shulkerBoxes, hoppers, hopperCarts,
			droppers, dispensers, crafters, furnaces);
	
	private final List<ChestEspEntityGroup> entityGroups =
		Arrays.asList(chestCarts, chestBoats, hopperCarts);
	
	// List to track double chests for special handling
	private final List<Box> doubleChestBoxes = new ArrayList<>();
	private final Map<BlockPos, ChestBlockEntity> chestBlockEntities =
		new HashMap<>();
	
	// Color for double chest tracers
	private final ColorSetting doubleChestColor = new ColorSetting(
		"Double chest color",
		"Double chests will be highlighted in this color (tracers always enabled).",
		new Color(255, 215, 0)); // Gold color
	
	public ChestEspHack()
	{
		super("ChestESP");
		setCategory(Category.RENDER);
		
		addSetting(style);
		addSetting(doubleChestColor);
		groups.stream().flatMap(ChestEspGroup::getSettings)
			.forEach(this::addSetting);
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
		
		groups.forEach(ChestEspGroup::clear);
		doubleChestBoxes.clear();
		chestBlockEntities.clear();
	}
	
	@Override
	public void onUpdate()
	{
		groups.forEach(ChestEspGroup::clear);
		doubleChestBoxes.clear();
		chestBlockEntities.clear();
		
		ArrayList<BlockEntity> blockEntities =
			ChunkUtils.getLoadedBlockEntities()
				.collect(Collectors.toCollection(ArrayList::new));
		
		// First pass - collect all chest entities
		for(BlockEntity blockEntity : blockEntities)
		{
			if(blockEntity instanceof ChestBlockEntity)
				chestBlockEntities.put(blockEntity.getPos(),
					(ChestBlockEntity)blockEntity);
		}
		
		// Second pass - identify double chests and add all entities to their
		// groups
		for(BlockEntity blockEntity : blockEntities)
		{
			if(blockEntity instanceof TrappedChestBlockEntity)
			{
				trapChests.add(blockEntity);
				findAndAddDoubleChest(blockEntity);
			}else if(blockEntity instanceof ChestBlockEntity)
			{
				basicChests.add(blockEntity);
				findAndAddDoubleChest(blockEntity);
			}else if(blockEntity instanceof EnderChestBlockEntity)
				enderChests.add(blockEntity);
			else if(blockEntity instanceof ShulkerBoxBlockEntity)
				shulkerBoxes.add(blockEntity);
			else if(blockEntity instanceof BarrelBlockEntity)
				barrels.add(blockEntity);
			else if(blockEntity instanceof DecoratedPotBlockEntity)
				pots.add(blockEntity);
			else if(blockEntity instanceof HopperBlockEntity)
				hoppers.add(blockEntity);
			else if(blockEntity instanceof DropperBlockEntity)
				droppers.add(blockEntity);
			else if(blockEntity instanceof DispenserBlockEntity)
				dispensers.add(blockEntity);
			else if(blockEntity instanceof CrafterBlockEntity)
				crafters.add(blockEntity);
			else if(blockEntity instanceof AbstractFurnaceBlockEntity)
				furnaces.add(blockEntity);
		}
		
		for(Entity entity : MC.world.getEntities())
			if(entity instanceof ChestMinecartEntity)
				chestCarts.add(entity);
			else if(entity instanceof HopperMinecartEntity)
				hopperCarts.add(entity);
			else if(entity instanceof ChestBoatEntity
				|| entity instanceof ChestRaftEntity)
				chestBoats.add(entity);
	}
	
	private void findAndAddDoubleChest(BlockEntity be)
	{
		if(!(be instanceof ChestBlockEntity chestBE))
			return;
		
		BlockState state = chestBE.getCachedState();
		if(!state.contains(ChestBlock.CHEST_TYPE))
			return;
		
		ChestType chestType = state.get(ChestBlock.CHEST_TYPE);
		
		// Only process one part of a double chest
		if(chestType == ChestType.RIGHT)
		{
			BlockPos pos = chestBE.getPos();
			
			// Check if it's a double chest
			if(chestType != ChestType.SINGLE)
			{
				BlockPos otherPos = pos.offset(ChestBlock.getFacing(state));
				
				if(BlockUtils.canBeClicked(otherPos))
				{
					Box box = BlockUtils.getBoundingBox(pos);
					Box box2 = BlockUtils.getBoundingBox(otherPos);
					Box doubleBox = box.union(box2);
					doubleChestBoxes.add(doubleBox);
				}
			}
		}
	}
	
	@Override
	public void onCameraTransformViewBobbing(
		CameraTransformViewBobbingEvent event)
	{
		// We need to disable view bobbing even if style doesn't have lines
		// because we'll always have tracer lines for double chests
		event.cancel();
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		entityGroups.stream().filter(ChestEspGroup::isEnabled)
			.forEach(g -> g.updateBoxes(partialTicks));
		
		if(style.hasBoxes())
			renderBoxes(matrixStack);
		
		if(style.hasLines())
			renderTracers(matrixStack, partialTicks);
		
		// Always render tracers for double chests
		renderDoubleChestTracers(matrixStack, partialTicks);
	}
	
	private void renderBoxes(MatrixStack matrixStack)
	{
		for(ChestEspGroup group : groups)
		{
			if(!group.isEnabled())
				continue;
			
			List<Box> boxes = group.getBoxes();
			int quadsColor = group.getColorI(0x40);
			int linesColor = group.getColorI(0x80);
			
			RenderUtils.drawSolidBoxes(matrixStack, boxes, quadsColor, false);
			RenderUtils.drawOutlinedBoxes(matrixStack, boxes, linesColor,
				false);
		}
		
		// Render double chest boxes with special color
		if(!doubleChestBoxes.isEmpty())
		{
			int quadsColor = doubleChestColor.getColorI(0x40);
			int linesColor = doubleChestColor.getColorI(0x80);
			
			RenderUtils.drawSolidBoxes(matrixStack, doubleChestBoxes,
				quadsColor, false);
			RenderUtils.drawOutlinedBoxes(matrixStack, doubleChestBoxes,
				linesColor, false);
		}
	}
	
	private void renderTracers(MatrixStack matrixStack, float partialTicks)
	{
		for(ChestEspGroup group : groups)
		{
			if(!group.isEnabled())
				continue;
			
			List<Box> boxes = group.getBoxes();
			List<Vec3d> ends = boxes.stream().map(Box::getCenter).toList();
			int color = group.getColorI(0x80);
			
			RenderUtils.drawTracers(matrixStack, partialTicks, ends, color,
				false);
		}
	}
	
	private void renderDoubleChestTracers(MatrixStack matrixStack,
		float partialTicks)
	{
		if(doubleChestBoxes.isEmpty())
			return;
		
		List<Vec3d> ends =
			doubleChestBoxes.stream().map(Box::getCenter).toList();
		int color = doubleChestColor.getColorI(0x80);
		
		RenderUtils.drawTracers(matrixStack, partialTicks, ends, color, false);
	}
}
