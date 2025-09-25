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

import net.minecraft.block.BlockState;
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

@SearchTags({"hole esp", "fall", "pit", "danger"})
public final class HoleEspHack extends Hack implements UpdateListener,
		CameraTransformViewBobbingListener, RenderListener
{
	private final EspStyleSetting style = new EspStyleSetting();
	
	private final EspBoxSizeSetting boxSize = new EspBoxSizeSetting(
			"\u00a7lAccurate\u00a7r mode shows the exact hitbox of each hole.\n"
					+ "\u00a7lFancy\u00a7r mode shows slightly larger boxes that look better.");
	
	private final ColorSetting color = new ColorSetting("Color",
			"Dangerous holes will be highlighted in this color.", new Color(255, 0, 0));
	
	private final SliderSetting minDepth = new SliderSetting("Min depth",
			"Minimum hole depth to be highlighted.",
			2, 1, 10, 1, ValueDisplay.INTEGER);
	
	private final CheckboxSetting checkPlayerFeet = new CheckboxSetting(
			"Check player feet",
			"Highlights holes that are one block below the player's feet.",
			true);
	
	private final SliderSetting range = new SliderSetting("Range",
			"Maximum distance in blocks to search for holes.",
			32, 8, 128, 8, ValueDisplay.INTEGER);
	
	private final SliderSetting maxHoles = new SliderSetting("Max holes",
			"Maximum number of holes to highlight.\n"
					+ "Higher values require more processing power.",
			128, 32, 1024, 32, ValueDisplay.INTEGER);
	
	private final ArrayList<Box> holes = new ArrayList<>();
	
	public HoleEspHack()
	{
		super("HoleESP");
		setCategory(Category.RENDER);
		
		addSetting(style);
		addSetting(boxSize);
		addSetting(color);
		addSetting(minDepth);
		addSetting(checkPlayerFeet);
		addSetting(range);
		addSetting(maxHoles);
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
		
		holes.clear();
	}
	
	@Override
	public void onUpdate()
	{
		holes.clear();
		
		if(MC.player == null || MC.world == null)
			return;
		
		// Get player position
		BlockPos playerPos = MC.player.getBlockPos();
		
		// Get reference Y level (one block below player's feet)
		int referenceY = playerPos.getY() - 1;
		
		// Calculate search range
		int searchRange = range.getValueI();
		
		// Find dangerous holes
		int count = 0;
		int maxCount = maxHoles.getValueI();
		int minHoleDepth = minDepth.getValueI();
		
		for(int x = -searchRange; x <= searchRange; x++)
		{
			for(int z = -searchRange; z <= searchRange; z++)
			{
				if(count >= maxCount)
					break;
				
				// Check if we should only look at the player's Y level
				if(checkPlayerFeet.isChecked())
				{
					BlockPos pos = new BlockPos(
							playerPos.getX() + x,
							referenceY,
							playerPos.getZ() + z);
					
					if(checkHole(pos, minHoleDepth))
					{
						holes.add(BlockUtils.getBoundingBox(pos));
						count++;
					}
				}
				else
				{
					// Search in a vertical range around the player
					for(int y = -searchRange; y <= searchRange; y++)
					{
						BlockPos pos = new BlockPos(
								playerPos.getX() + x,
								playerPos.getY() + y,
								playerPos.getZ() + z);
						
						if(checkHole(pos, minHoleDepth))
						{
							holes.add(BlockUtils.getBoundingBox(pos));
							count++;
							break; // Only count one hole per column
						}
					}
				}
			}
		}
	}
	
	private boolean checkHole(BlockPos pos, int minDepth)
	{
		// Check if the current block is air
		if(!isAir(pos))
			return false;
		
		// Check depth
		int depth = 1;
		BlockPos checkPos = pos;
		
		// Count consecutive air blocks below
		while(depth <= minDepth)
		{
			checkPos = checkPos.down();
			
			if(isAir(checkPos))
				depth++;
			else
				break;
		}
		
		// Only highlight if the hole is at least the minimum depth
		return depth >= minDepth;
	}
	
	private boolean isAir(BlockPos pos)
	{
		if(pos == null || MC.world == null)
			return false;
		
		BlockState state = MC.world.getBlockState(pos);
		return state.isAir();
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
		// Skip if no holes found
		if(holes.isEmpty())
			return;
		
		// Get extra size for boxes
		double extraSize = boxSize.getExtraSize() / 2;
		int boxColor = color.getColorI();
		
		if(style.hasBoxes())
		{
			// Draw filled boxes
			List<Box> expandedBoxes = new ArrayList<>();
			for(Box box : holes)
				expandedBoxes.add(box.expand(extraSize));
			
			// Fill color with 0x40 alpha (semi-transparent)
			int fillColor = boxColor & 0x00FFFFFF | 0x40000000;
			RenderUtils.drawSolidBoxes(matrixStack, expandedBoxes, fillColor, false);
			
			// Outline color with 0x80 alpha (more opaque)
			int outlineColor = boxColor & 0x00FFFFFF | 0x80000000;
			RenderUtils.drawOutlinedBoxes(matrixStack, expandedBoxes, outlineColor, false);
		}
		
		if(style.hasLines())
		{
			// Draw tracers
			List<Vec3d> centers = new ArrayList<>();
			for(Box box : holes)
				centers.add(box.getCenter());
			
			// Tracer color with 0x80 alpha
			int tracerColor = boxColor & 0x00FFFFFF | 0x80000000;
			RenderUtils.drawTracers(matrixStack, partialTicks, centers, tracerColor, false);
		}
	}
}