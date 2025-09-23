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
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BedBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
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
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RenderUtils.ColoredPoint;
import net.wurstclient.util.chunk.ChunkUtils;

@SearchTags({"bed esp", "BedTracers", "bed tracers", "bed finder", "bed aura"})
public final class BedEspHack extends Hack implements UpdateListener,
	CameraTransformViewBobbingListener, RenderListener
{
	private final EspStyleSetting style = new EspStyleSetting();
	
	private final EspBoxSizeSetting boxSize = new EspBoxSizeSetting(
		"\u00a7lAccurate\u00a7r mode shows the exact hitbox of each bed.\n"
			+ "\u00a7lFancy\u00a7r mode shows slightly larger boxes that look better.");
	
	private final ColorSetting netherBedColor = new ColorSetting(
		"Nether bed color",
		"Beds in the Nether will be highlighted in this color (they explode when used).",
		new Color(255, 0, 0)); // Red color for Nether beds
	
	private final ColorSetting endBedColor = new ColorSetting("End bed color",
		"Beds in the End will be highlighted in this color (they explode when used).",
		new Color(128, 0, 128)); // Purple color for End beds
	
	private final ColorSetting overworldBedColor =
		new ColorSetting("Overworld bed color",
			"Beds in the Overworld will be highlighted in this color.",
			new Color(0, 0, 255)); // Blue color for normal beds
	
	private final SliderSetting limit = new SliderSetting("Limit",
		"Maximum number of beds to display.\n"
			+ "Higher values require more processing power.\n"
			+ "Set to 0 for unlimited.",
		100, 0, 1000, 10, ValueDisplay.INTEGER.withLabel(0, "unlimited"));
	
	private final CheckboxSetting showInOverworld = new CheckboxSetting(
		"Show in Overworld",
		"Whether to show beds in the Overworld. Disable to only show beds in Nether/End.",
		true);
	
	private final ArrayList<Box> beds = new ArrayList<>();
	private final ArrayList<Integer> bedsColors = new ArrayList<>();
	
	public BedEspHack()
	{
		super("BedESP");
		setCategory(Category.RENDER);
		
		addSetting(style);
		addSetting(boxSize);
		addSetting(netherBedColor);
		addSetting(endBedColor);
		addSetting(overworldBedColor);
		addSetting(limit);
		addSetting(showInOverworld);
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
		
		beds.clear();
		bedsColors.clear();
	}
	
	@Override
	public void onUpdate()
	{
		beds.clear();
		bedsColors.clear();
		
		// Check if we're in overworld and beds should be shown
		boolean inOverworld = !MC.world.getDimension().bedWorks();
		if(inOverworld && !showInOverworld.isChecked())
			return;
		
		// Get all block entities
		List<BlockEntity> blockEntities = ChunkUtils.getLoadedBlockEntities()
			.collect(Collectors.toCollection(ArrayList::new));
		
		// Collect beds
		int count = 0;
		for(BlockEntity blockEntity : blockEntities)
		{
			// Apply limit if set
			int limitValue = limit.getValueI();
			if(limitValue > 0 && count >= limitValue)
				break;
			
			// Look for bed block entities
			if(blockEntity instanceof BedBlockEntity)
			{
				// Get bed position and state
				BlockPos pos = blockEntity.getPos();
				BlockState state = MC.world.getBlockState(pos);
				
				// Skip if the block isn't a bed (should never happen)
				if(!(state.getBlock() instanceof BedBlock))
					continue;
					
				// Skip if it's not the pillow part of the bed (to avoid
				// duplicate boxes)
				if(state.get(BedBlock.PART) != BedPart.HEAD)
					continue;
				
				// Get the box for the bed
				Box box = getBedBox(pos, state);
				if(box == null)
					continue;
				
				// Add bed to our list
				beds.add(box);
				
				// Determine color based on dimension
				boolean bedExplodes = MC.world.getDimension().bedWorks();
				boolean isNether = MC.world.getRegistryKey().getValue()
					.getPath().equals("the_nether");
				boolean isEnd = MC.world.getRegistryKey().getValue().getPath()
					.equals("the_end");
				
				int color;
				if(bedExplodes)
				{
					color = isNether ? netherBedColor.getColorI()
						: endBedColor.getColorI();
				}else
				{
					color = overworldBedColor.getColorI();
				}
				
				bedsColors.add(color);
				count++;
			}
		}
	}
	
	private Box getBedBox(BlockPos headPos, BlockState state)
	{
		// Try to get the box for both parts of the bed
		Box headBox = BlockUtils.getBoundingBox(headPos);
		if(headBox == null)
			return null;
		
		// Get foot part position
		BlockPos footPos =
			headPos.offset(state.get(BedBlock.FACING).getOpposite());
		Box footBox = BlockUtils.getBoundingBox(footPos);
		if(footBox == null)
			return footBox;
		
		// Combine the boxes
		return headBox.union(footBox);
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
		// Skip if no beds found
		if(beds.isEmpty())
			return;
		
		// Get extra size for boxes
		double extraSize = boxSize.getExtraSize() / 2;
		
		if(style.hasBoxes())
		{
			// Draw individual colored boxes for each bed
			for(int i = 0; i < beds.size(); i++)
			{
				Box box = beds.get(i).expand(extraSize);
				int color = bedsColors.get(i);
				
				// Transparent fill color (0x40 alpha)
				int fillColor = color & 0x00FFFFFF | 0x40000000;
				RenderUtils.drawSolidBox(matrixStack, box, fillColor, false);
				
				// More opaque outline (0x80 alpha)
				int outlineColor = color & 0x00FFFFFF | 0x80000000;
				RenderUtils.drawOutlinedBox(matrixStack, box, outlineColor,
					false);
			}
		}
		
		if(style.hasLines())
		{
			// Create colored points for tracers
			ArrayList<ColoredPoint> tracerPoints = new ArrayList<>(beds.size());
			
			for(int i = 0; i < beds.size(); i++)
			{
				Vec3d center = beds.get(i).getCenter();
				int color = bedsColors.get(i) & 0x00FFFFFF | 0x80000000;
				tracerPoints.add(new ColoredPoint(center, color));
			}
			
			// Draw all tracer lines with the ColoredPoint method
			RenderUtils.drawTracers(matrixStack, partialTicks, tracerPoints,
				false);
		}
	}
}
