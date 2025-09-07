/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.Comparator;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.flowerbot.FlowerPickerUtils;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.FacingSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.BlockBreaker;
import net.wurstclient.util.BlockBreaker.BlockBreakingParams;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.OverlayRenderer;
import net.wurstclient.util.RenderUtils;

@SearchTags({"auto flower", "flower harvester", "flower picker"})
@DontSaveState
public final class AutoFlowerHack extends Hack
	implements UpdateListener, RenderListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"How far AutoFlower will reach to break flowers.", 4.5, 1, 6, 0.05,
		ValueDisplay.DECIMAL);
	
	private final SliderSetting flowerSearchRadius = new SliderSetting(
		"Search Radius", "How far AutoFlower will search for flowers.", 16, 4,
		32, 1, ValueDisplay.INTEGER);
	
	private final FacingSetting facing = FacingSetting.withoutPacketSpam(
		"How AutoFlower should face the flowers when breaking them.\n\n"
			+ "\u00a7lOff\u00a7r - Don't face the blocks at all. Will be"
			+ " detected by anti-cheat plugins.\n\n"
			+ "\u00a7lServer-side\u00a7r - Face the blocks on the"
			+ " server-side, while still letting you move the camera freely on"
			+ " the client-side.\n\n"
			+ "\u00a7lClient-side\u00a7r - Face the blocks by moving your"
			+ " camera on the client-side. This is the most legit option, but"
			+ " can be disorienting to look at.");
	
	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.SERVER);
	
	private final CheckboxSetting harvestGrass =
		new CheckboxSetting("Harvest Grass for Seeds",
			"Also break tall grass to obtain seeds.", false);
	
	private BlockPos currentFlower;
	private final ArrayList<BlockPos> flowers = new ArrayList<>();
	private final ArrayList<BlockPos> grassBlocks = new ArrayList<>();
	private final OverlayRenderer overlay = new OverlayRenderer();
	
	// For incremental search
	private boolean isSearching = false;
	private int searchTimeoutCounter = 0;
	private static final int SEARCH_TIMEOUT = 60; // 3 seconds timeout
	private BlockPos searchMin;
	private BlockPos searchMax;
	private int searchX, searchY, searchZ;
	
	public AutoFlowerHack()
	{
		super("AutoFlower");
		setCategory(Category.BLOCKS);
		
		addSetting(range);
		addSetting(flowerSearchRadius);
		addSetting(facing);
		addSetting(swingHand);
		addSetting(harvestGrass);
	}
	
	@Override
	public String getRenderName()
	{
		if(isSearching)
			return getName() + " [Searching...]";
		
		if(currentFlower == null)
		{
			if(flowers.isEmpty()
				&& (!harvestGrass.isChecked() || grassBlocks.isEmpty()))
				return getName() + " [Searching]";
			else
				return getName();
		}
		
		// Check if current block is a flower or grass
		Block currentBlock = BlockUtils.getBlock(currentFlower);
		if(currentBlock == Blocks.SHORT_GRASS
			|| currentBlock == Blocks.TALL_GRASS)
			return getName() + " [Grass]";
		else
			return getName() + " [Flower]";
	}
	
	@Override
	protected void onEnable()
	{
		flowers.clear();
		grassBlocks.clear();
		currentFlower = null;
		isSearching = false;
		searchTimeoutCounter = 0;
		
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		if(currentFlower != null)
		{
			MC.interactionManager.breakingBlock = true;
			MC.interactionManager.cancelBlockBreaking();
			currentFlower = null;
		}
		
		overlay.resetProgress();
		isSearching = false;
	}
	
	@Override
	public void onUpdate()
	{
		// Check for search timeout
		if(isSearching)
		{
			searchTimeoutCounter++;
			if(searchTimeoutCounter > SEARCH_TIMEOUT)
			{
				// If search takes too long, stop it
				isSearching = false;
				searchTimeoutCounter = 0;
			}
		}
		
		// Reset current flower if it's no longer valid
		if(currentFlower != null)
		{
			boolean isValidFlower = FlowerPickerUtils.isFlower(currentFlower);
			boolean isGrass = harvestGrass.isChecked()
				&& (BlockUtils.getBlock(currentFlower) == Blocks.SHORT_GRASS
					|| BlockUtils.getBlock(currentFlower) == Blocks.TALL_GRASS);
			
			if(!isValidFlower && !isGrass)
			{
				currentFlower = null;
				overlay.resetProgress();
			}
		}
		
		// Find nearby flowers/grass if our lists are empty
		if(flowers.isEmpty()
			&& (grassBlocks.isEmpty() || !harvestGrass.isChecked()))
		{
			if(isSearching)
			{
				// Continue incremental search
				continueFlowerSearch();
			}else
			{
				// Start a new search
				startFlowerSearch();
			}
			
			// Check if we found any flowers or grass
			if(flowers.isEmpty()
				&& (grassBlocks.isEmpty() || !harvestGrass.isChecked())
				&& !isSearching)
				return;
		}
		
		// Clean up our flower list (remove any that are no longer flowers)
		flowers.removeIf(pos -> !FlowerPickerUtils.isFlower(pos));
		
		// Clean up our grass list (remove any that are no longer grass)
		if(harvestGrass.isChecked())
			grassBlocks
				.removeIf(pos -> BlockUtils.getBlock(pos) != Blocks.SHORT_GRASS
					&& BlockUtils.getBlock(pos) != Blocks.TALL_GRASS);
		else
			grassBlocks.clear();
		
		// If we have no current flower or it's been harvested, get a new one
		if(currentFlower == null)
		{
			// Try to find the nearest flower or grass
			Vec3d playerPos = MC.player.getPos();
			
			// Prioritize flowers over grass
			if(!flowers.isEmpty())
			{
				currentFlower = flowers.stream().min(Comparator.comparingDouble(
					pos -> playerPos.squaredDistanceTo(Vec3d.ofCenter(pos))))
					.orElse(null);
				
				if(currentFlower != null)
				{
					flowers.remove(currentFlower);
					return;
				}
			}
			
			// If no flowers available and grass harvesting is enabled, try
			// grass
			if(harvestGrass.isChecked() && !grassBlocks.isEmpty())
			{
				currentFlower =
					grassBlocks.stream()
						.min(Comparator.comparingDouble(pos -> playerPos
							.squaredDistanceTo(Vec3d.ofCenter(pos))))
						.orElse(null);
				
				if(currentFlower != null)
				{
					grassBlocks.remove(currentFlower);
					return;
				}
			}
			
			// If no valid target found, clear lists and search again next tick
			flowers.clear();
			grassBlocks.clear();
			return;
		}
		
		// Break the current flower or grass
		harvestBlock(currentFlower);
	}
	
	private void startFlowerSearch()
	{
		int searchRadius = flowerSearchRadius.getValueI();
		
		BlockPos playerPos = BlockPos.ofFloored(MC.player.getPos());
		searchMin = playerPos.add(-searchRadius, -3, -searchRadius);
		searchMax = playerPos.add(searchRadius, 3, searchRadius);
		
		// Initialize search state
		isSearching = true;
		searchTimeoutCounter = 0;
		searchX = searchMin.getX();
		searchY = searchMin.getY();
		searchZ = searchMin.getZ();
	}
	
	private void continueFlowerSearch()
	{
		if(!isSearching)
			return;
		
		Vec3d playerPos = MC.player.getPos();
		double rangeSq = range.getValueSq() * 2;
		
		// Process a limited number of blocks per tick
		final int BLOCKS_PER_TICK = 200;
		int processed = 0;
		
		while(processed < BLOCKS_PER_TICK)
		{
			// Check if we've finished the search
			if(searchZ > searchMax.getZ())
			{
				isSearching = false;
				break;
			}
			
			BlockPos pos = new BlockPos(searchX, searchY, searchZ);
			
			// Fast distance check before doing expensive operations
			double dx = searchX + 0.5 - playerPos.x;
			double dy = searchY + 0.5 - playerPos.y;
			double dz = searchZ + 0.5 - playerPos.z;
			double fastDistSq = dx * dx + dy * dy + dz * dz;
			
			// Only do further checks if roughly in range
			if(fastDistSq <= rangeSq * 1.5)
			{
				try
				{
					// Check if it's a flower or grass
					if(FlowerPickerUtils.isFlower(pos))
					{
						flowers.add(pos);
					}else if(harvestGrass.isChecked()
						&& (BlockUtils.getBlock(pos) == Blocks.SHORT_GRASS
							|| BlockUtils.getBlock(pos) == Blocks.TALL_GRASS))
					{
						grassBlocks.add(pos);
					}
				}catch(Exception e)
				{
					// Skip this block if there's an error
				}
			}
			
			// Move to the next position
			searchX++;
			if(searchX > searchMax.getX())
			{
				searchX = searchMin.getX();
				searchY++;
				
				if(searchY > searchMax.getY())
				{
					searchY = searchMin.getY();
					searchZ++;
				}
			}
			
			processed++;
		}
		
		// If we found enough flowers/grass, we can stop searching
		if((!flowers.isEmpty() && flowers.size() >= 5)
			|| (harvestGrass.isChecked() && !grassBlocks.isEmpty()
				&& grassBlocks.size() >= 5))
		{
			isSearching = false;
		}
	}
	
	private void harvestBlock(BlockPos pos)
	{
		BlockBreakingParams params = BlockBreaker.getBlockBreakingParams(pos);
		if(params == null || !params.lineOfSight()
			|| params.distanceSq() > range.getValueSq())
		{
			// If we can't break this block, remove it and try another one
			flowers.remove(pos);
			grassBlocks.remove(pos);
			currentFlower = null;
			return;
		}
		
		// Select the best tool for breaking the block
		WURST.getHax().autoToolHack.equipBestTool(pos, false, true, 0);
		
		// Face the block according to settings
		facing.getSelected().face(params.hitVec());
		
		// Break the block
		if(MC.interactionManager.updateBlockBreakingProgress(pos,
			params.side()))
			swingHand.swing(Hand.MAIN_HAND);
		
		// Update progress overlay
		overlay.updateProgress();
		
		// If the block has been broken, move to the next one
		Block block = BlockUtils.getBlock(pos);
		boolean isFlower = FlowerPickerUtils.isFlower(pos);
		boolean isGrass =
			block == Blocks.SHORT_GRASS || block == Blocks.TALL_GRASS;
		
		if((!isFlower && !isGrass) || block == Blocks.AIR)
		{
			currentFlower = null;
			overlay.resetProgress();
		}
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		// Don't render anything if we have no current flower
		if(currentFlower == null && flowers.isEmpty() && grassBlocks.isEmpty())
			return;
		
		// Render breaking progress
		if(currentFlower != null)
		{
			// Highlight current target and show breaking progress
			overlay.render(matrixStack, partialTicks, currentFlower);
			
			try
			{
				// Different colors for flowers vs grass
				int currentColor = FlowerPickerUtils.isFlower(currentFlower)
					? 0x80FF00FF : 0x8000FF00;
				
				RenderUtils.drawOutlinedBox(matrixStack,
					BlockUtils.getBoundingBox(currentFlower), currentColor,
					true);
			}catch(Exception e)
			{
				// Skip rendering if there's an error
			}
		}
		
		// Highlight all flowers
		for(BlockPos pos : flowers)
		{
			try
			{
				RenderUtils.drawOutlinedBox(matrixStack,
					BlockUtils.getBoundingBox(pos), 0x4000FFFF, true);
			}catch(Exception e)
			{
				// Skip rendering this flower if there's an error
			}
		}
		
		// Highlight all grass blocks if grass harvesting is enabled
		if(harvestGrass.isChecked())
		{
			for(BlockPos pos : grassBlocks)
			{
				try
				{
					RenderUtils.drawOutlinedBox(matrixStack,
						BlockUtils.getBoundingBox(pos), 0x4000FF00, true);
				}catch(Exception e)
				{
					// Skip rendering this grass if there's an error
				}
			}
		}
	}
}
