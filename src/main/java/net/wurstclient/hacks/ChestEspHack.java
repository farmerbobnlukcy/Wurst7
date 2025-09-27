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
import net.wurstclient.settings.SliderSetting;
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
	
	private final SliderSetting enderChestMaxDistance = new SliderSetting(
		"Ender chest max distance",
		"Maximum distance at which ender chests will be rendered.\n"
			+ "This prevents overwhelming rendering of too many ender chests.",
		64, 16, 256, 16, SliderSetting.ValueDisplay.INTEGER);
	
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
	
	// Add individual line settings for each group
	private final CheckboxSetting basicChestLines = new CheckboxSetting(
		"Chest lines", "Show tracers for normal chests.", true);
	
	private final CheckboxSetting trapChestLines = new CheckboxSetting(
		"Trap chest lines", "Show tracers for trap chests.", true);
	
	private final CheckboxSetting enderChestLines = new CheckboxSetting(
		"Ender chest lines", "Show tracers for ender chests.", true);
	
	private final CheckboxSetting chestCartLines = new CheckboxSetting(
		"Chest cart lines", "Show tracers for chest minecarts.", true);
	
	private final CheckboxSetting chestBoatLines = new CheckboxSetting(
		"Chest boat lines", "Show tracers for chest boats.", true);
	
	private final CheckboxSetting barrelLines =
		new CheckboxSetting("Barrel lines", "Show tracers for barrels.", true);
	
	private final CheckboxSetting potLines =
		new CheckboxSetting("Pot lines", "Show tracers for pots.", true);
	
	private final CheckboxSetting shulkerLines = new CheckboxSetting(
		"Shulker lines", "Show tracers for shulker boxes.", true);
	
	private final CheckboxSetting hopperLines =
		new CheckboxSetting("Hopper lines", "Show tracers for hoppers.", true);
	
	private final CheckboxSetting hopperCartLines = new CheckboxSetting(
		"Hopper cart lines", "Show tracers for hopper minecarts.", true);
	
	private final CheckboxSetting dropperLines = new CheckboxSetting(
		"Dropper lines", "Show tracers for droppers.", true);
	
	private final CheckboxSetting dispenserLines = new CheckboxSetting(
		"Dispenser lines", "Show tracers for dispensers.", true);
	
	private final CheckboxSetting crafterLines = new CheckboxSetting(
		"Crafter lines", "Show tracers for crafters.", true);
	
	private final CheckboxSetting furnaceLines = new CheckboxSetting(
		"Furnace lines", "Show tracers for furnaces.", true);
	
	private final List<ChestEspEntityGroup> entityGroups =
		Arrays.asList(chestCarts, chestBoats, hopperCarts);
	
	// Lists to track double chests for special handling
	private final List<Box> doubleChestBoxes = new ArrayList<>();
	private final List<Box> doubleTrappedChestBoxes = new ArrayList<>();
	private final Map<BlockPos, ChestBlockEntity> chestBlockEntities =
		new HashMap<>();
	
	// Colors for double chest tracers
	private final ColorSetting doubleChestColor = new ColorSetting(
		"Double chest color",
		"Double chests will be highlighted in this color (tracers always enabled).",
		new Color(255, 215, 0)); // Gold color
	
	private final ColorSetting doubleTrappedChestColor = new ColorSetting(
		"Double trapped chest color",
		"Double trapped chests will be highlighted in this color (tracers always enabled).",
		new Color(255, 140, 0)); // Dark orange color
	
	private final CheckboxSetting renderChests = new CheckboxSetting(
		"Render chests", "If unchecked, chests will not be rendered.", true);
	
	// Add line setting for double chests
	private final CheckboxSetting doubleChestLines = new CheckboxSetting(
		"Double chest lines", "Show tracers for double chests.", true);
	
	private final CheckboxSetting doubleTrappedChestLines =
		new CheckboxSetting("Double trapped chest lines",
			"Show tracers for double trapped chests.", true);
	
	// Map to associate groups with their line settings
	private final Map<ChestEspGroup, CheckboxSetting> lineSettings =
		new HashMap<>();
	
	public ChestEspHack()
	{
		super("ChestESP");
		setCategory(Category.RENDER);
		
		addSetting(style);
		addSetting(renderChests);
		addSetting(doubleChestColor);
		addSetting(doubleChestLines);
		addSetting(doubleTrappedChestColor);
		addSetting(doubleTrappedChestLines);
		addSetting(enderChestMaxDistance);
		
		// Associate each group with its line setting
		lineSettings.put(basicChests, basicChestLines);
		lineSettings.put(trapChests, trapChestLines);
		lineSettings.put(enderChests, enderChestLines);
		lineSettings.put(chestCarts, chestCartLines);
		lineSettings.put(chestBoats, chestBoatLines);
		lineSettings.put(barrels, barrelLines);
		lineSettings.put(pots, potLines);
		lineSettings.put(shulkerBoxes, shulkerLines);
		lineSettings.put(hoppers, hopperLines);
		lineSettings.put(hopperCarts, hopperCartLines);
		lineSettings.put(droppers, dropperLines);
		lineSettings.put(dispensers, dispenserLines);
		lineSettings.put(crafters, crafterLines);
		lineSettings.put(furnaces, furnaceLines);
		
		// Add all group settings first
		groups.stream().flatMap(ChestEspGroup::getSettings)
			.forEach(this::addSetting);
		
		// Add line settings after the respective group settings
		addSetting(basicChestLines);
		addSetting(trapChestLines);
		addSetting(enderChestLines);
		addSetting(chestCartLines);
		addSetting(chestBoatLines);
		addSetting(barrelLines);
		addSetting(potLines);
		addSetting(shulkerLines);
		addSetting(hopperLines);
		addSetting(hopperCartLines);
		addSetting(dropperLines);
		addSetting(dispenserLines);
		addSetting(crafterLines);
		addSetting(furnaceLines);
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
		doubleTrappedChestBoxes.clear();
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
			{
				// Only add ender chests within the maximum distance
				BlockPos pos = blockEntity.getPos();
				Vec3d playerPos = MC.player.getPos();
				Vec3d chestPos = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5,
					pos.getZ() + 0.5);
				double distance = playerPos.squaredDistanceTo(chestPos);
				double maxDistSq = enderChestMaxDistance.getValueSq();
				
				if(distance <= maxDistSq)
					enderChests.add(blockEntity);
			}else if(blockEntity instanceof ShulkerBoxBlockEntity)
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
					
					// Add to appropriate list based on chest type
					if(be instanceof TrappedChestBlockEntity)
						doubleTrappedChestBoxes.add(doubleBox);
					else
						doubleChestBoxes.add(doubleBox);
				}
			}
		}
	}
	
	@Override
	public void onCameraTransformViewBobbing(
		CameraTransformViewBobbingEvent event)
	{
		// Check if any line settings are enabled
		boolean anyLinesEnabled =
			style.hasLines() || doubleChestLines.isChecked()
				|| doubleTrappedChestLines.isChecked() || lineSettings.values()
					.stream().anyMatch(CheckboxSetting::isChecked);
		
		if(anyLinesEnabled)
			event.cancel();
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		entityGroups.stream().filter(ChestEspGroup::isEnabled)
			.forEach(g -> g.updateBoxes(partialTicks));
		
		if(style.hasBoxes())
			renderBoxes(matrixStack);
		
		// We now render tracers for each group individually
		renderIndividualTracers(matrixStack, partialTicks);
		
		// Render tracers for double chests based on settings
		renderDoubleChestTracers(matrixStack, partialTicks);
	}
	
	private void renderBoxes(MatrixStack matrixStack)
	{
		for(ChestEspGroup group : groups)
		{
			if(!group.isEnabled())
				continue;
			
			// Skip chest rendering if the setting is disabled
			if(!renderChests.isChecked()
				&& (group == basicChests || group == trapChests))
				continue;
			
			List<Box> boxes = group.getBoxes();
			
			// Ender chests are already filtered by distance in onUpdate
			
			int quadsColor = group.getColorI(0x40);
			int linesColor = group.getColorI(0x80);
			
			RenderUtils.drawSolidBoxes(matrixStack, boxes, quadsColor, false);
			RenderUtils.drawOutlinedBoxes(matrixStack, boxes, linesColor,
				false);
		}
		
		// Render double chest boxes with special color
		if(!doubleChestBoxes.isEmpty() && renderChests.isChecked())
		{
			int quadsColor = doubleChestColor.getColorI(0x40);
			int linesColor = doubleChestColor.getColorI(0x80);
			
			RenderUtils.drawSolidBoxes(matrixStack, doubleChestBoxes,
				quadsColor, false);
			RenderUtils.drawOutlinedBoxes(matrixStack, doubleChestBoxes,
				linesColor, false);
		}
		
		// Render double trapped chest boxes with special color
		if(!doubleTrappedChestBoxes.isEmpty() && renderChests.isChecked())
		{
			int quadsColor = doubleTrappedChestColor.getColorI(0x40);
			int linesColor = doubleTrappedChestColor.getColorI(0x80);
			
			RenderUtils.drawSolidBoxes(matrixStack, doubleTrappedChestBoxes,
				quadsColor, false);
			RenderUtils.drawOutlinedBoxes(matrixStack, doubleTrappedChestBoxes,
				linesColor, false);
		}
	}
	
	private void renderIndividualTracers(MatrixStack matrixStack,
		float partialTicks)
	{
		for(ChestEspGroup group : groups)
		{
			if(!group.isEnabled())
				continue;
			
			// Skip chest tracers if the setting is disabled
			if(!renderChests.isChecked()
				&& (group == basicChests || group == trapChests))
				continue;
			
			// Check if lines for this group are enabled
			CheckboxSetting lineSetting = lineSettings.get(group);
			if(lineSetting == null || !lineSetting.isChecked())
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
		if(!renderChests.isChecked())
			return;
		
		// Draw regular double chest tracers
		if(!doubleChestBoxes.isEmpty())
		{
			List<Vec3d> ends =
				doubleChestBoxes.stream().map(Box::getCenter).toList();
			int color = doubleChestColor.getColorI(0x80);
			
			RenderUtils.drawTracers(matrixStack, partialTicks, ends, color,
				false);
		}
		
		// Draw trapped double chest tracers
		if(!doubleTrappedChestBoxes.isEmpty())
		{
			List<Vec3d> ends =
				doubleTrappedChestBoxes.stream().map(Box::getCenter).toList();
			int color = doubleTrappedChestColor.getColorI(0x80);
			
			RenderUtils.drawTracers(matrixStack, partialTicks, ends, color,
				false);
		}
	}
}
