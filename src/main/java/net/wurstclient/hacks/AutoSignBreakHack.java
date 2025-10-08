/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.BlockBreaker;
import net.wurstclient.util.BlockBreaker.BlockBreakingParams;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.OverlayRenderer;
import net.wurstclient.util.RenderUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@SearchTags({"auto sign", "sign breaker", "sign destroyer"})
public final class AutoSignBreakHack extends Hack
	implements UpdateListener, RenderListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"How far AutoSignBreak will reach to break signs.", 4.5, 1, 6, 0.05,
		ValueDisplay.DECIMAL);
	
	private final SliderSetting searchRadius = new SliderSetting(
		"Search Radius", "How far AutoSignBreak will search for signs.", 16, 4,
		32, 1, ValueDisplay.INTEGER);
	
	private final FacingSetting facing = FacingSetting.withoutPacketSpam(
		"How AutoSignBreak should face the signs when breaking them.\n\n"
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
	
	private final CheckboxSetting filterSigns = new CheckboxSetting(
		"Filter Signs", "Only break signs with specific text", false);
	
	private final TextFieldSetting filterText =
		new TextFieldSetting("Filter Text",
			"Comma-separated text values to match (e.g. 'shop,buy,sell')",
			"_Lide_", v -> v != null); // Simple validator that just checks for
										// non-null
	
	private BlockPos currentSign;
	private final ArrayList<BlockPos> signs = new ArrayList<>();
	private final OverlayRenderer overlay = new OverlayRenderer();
	
	// For incremental search
	private boolean isSearching = false;
	private int searchTimeoutCounter = 0;
	private static final int SEARCH_TIMEOUT = 60; // 3 seconds timeout
	private BlockPos searchMin;
	private BlockPos searchMax;
	private int searchX, searchY, searchZ;
	
	public AutoSignBreakHack()
	{
		super("AutoSignBreak");
		setCategory(Category.BLOCKS);
		
		addSetting(range);
		addSetting(searchRadius);
		addSetting(facing);
		addSetting(swingHand);
		addSetting(filterSigns);
		addSetting(filterText);
	}
	
	@Override
	public String getRenderName()
	{
		if(isSearching)
			return getName() + " [Searching...]";
		
		if(currentSign == null)
		{
			if(signs.isEmpty())
				return getName() + " [Searching]";
			else
				return getName();
		}
		
		return getName() + " [Breaking]";
	}
	
	@Override
	protected void onEnable()
	{
		signs.clear();
		currentSign = null;
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
		
		if(currentSign != null)
		{
			MC.interactionManager.breakingBlock = true;
			MC.interactionManager.cancelBlockBreaking();
			currentSign = null;
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
		
		// Reset current sign if it's no longer valid
		if(currentSign != null)
		{
			boolean isValidSign = isSign(currentSign);
			
			if(!isValidSign)
			{
				currentSign = null;
				overlay.resetProgress();
			}
		}
		
		// Find nearby signs if our list is empty
		if(signs.isEmpty())
		{
			if(isSearching)
			{
				// Continue incremental search
				continueSignSearch();
			}else
			{
				// Start a new search
				startSignSearch();
			}
			
			// Check if we found any signs
			if(signs.isEmpty() && !isSearching)
				return;
		}
		
		// Clean up our sign list (remove any that are no longer signs)
		signs.removeIf(pos -> !isSign(pos));
		
		// If we have no current sign or it's been broken, get a new one
		if(currentSign == null)
		{
			// Try to find the nearest sign
			Vec3d playerPos = MC.player.getPos();
			
			if(!signs.isEmpty())
			{
				currentSign = signs.stream().min(Comparator.comparingDouble(
					pos -> playerPos.squaredDistanceTo(Vec3d.ofCenter(pos))))
					.orElse(null);
				
				if(currentSign != null)
				{
					// Check if the sign matches our filter criteria
					if(filterSigns.isChecked() && !matchesFilter(currentSign))
					{
						signs.remove(currentSign);
						currentSign = null;
						return;
					}
					
					signs.remove(currentSign);
					return;
				}
			}
			
			// If no valid target found, clear lists and search again next tick
			signs.clear();
			return;
		}
		
		// Break the current sign
		breakSign(currentSign);
	}
	
	private boolean isSign(BlockPos pos)
	{
		if(pos == null)
			return false;
		
		Block block = BlockUtils.getBlock(pos);
		return block == Blocks.OAK_SIGN || block == Blocks.SPRUCE_SIGN
			|| block == Blocks.BIRCH_SIGN || block == Blocks.JUNGLE_SIGN
			|| block == Blocks.ACACIA_SIGN || block == Blocks.DARK_OAK_SIGN
			|| block == Blocks.CRIMSON_SIGN || block == Blocks.WARPED_SIGN
			|| block == Blocks.OAK_WALL_SIGN || block == Blocks.SPRUCE_WALL_SIGN
			|| block == Blocks.BIRCH_WALL_SIGN
			|| block == Blocks.JUNGLE_WALL_SIGN
			|| block == Blocks.ACACIA_WALL_SIGN
			|| block == Blocks.DARK_OAK_WALL_SIGN
			|| block == Blocks.CRIMSON_WALL_SIGN
			|| block == Blocks.WARPED_WALL_SIGN || block == Blocks.MANGROVE_SIGN
			|| block == Blocks.MANGROVE_WALL_SIGN || block == Blocks.BAMBOO_SIGN
			|| block == Blocks.BAMBOO_WALL_SIGN || block == Blocks.CHERRY_SIGN
			|| block == Blocks.CHERRY_WALL_SIGN;
	}
	
	private boolean matchesFilter(BlockPos pos)
	{
		if(!filterSigns.isChecked() || filterText.getValue().isEmpty())
			return true;
		
		BlockEntity blockEntity = MC.world.getBlockEntity(pos);
		if(!(blockEntity instanceof SignBlockEntity))
			return false;
		
		SignBlockEntity sign = (SignBlockEntity)blockEntity;
		
		// Get filter terms
		List<String> filterTerms =
			Arrays.stream(filterText.getValue().split(",")).map(String::trim)
				.filter(s -> !s.isEmpty()).collect(Collectors.toList());
		
		if(filterTerms.isEmpty())
			return true;
		
		// Check front and back of sign for text matches
		for(int i = 0; i < 8; i++)
		{ // Check all 8 possible text lines (4 front, 4 back)
			Text lineText = i < 4 ? sign.getFrontText().getMessage(i, false)
				: sign.getBackText().getMessage(i - 4, false);
			String lineStr = lineText.getString().toLowerCase();
			
			for(String term : filterTerms)
			{
				if(lineStr.contains(term.toLowerCase()))
					return true;
			}
		}
		
		return false;
	}
	
	private void startSignSearch()
	{
		int searchRadius = this.searchRadius.getValueI();
		
		BlockPos playerPos = BlockPos.ofFloored(MC.player.getPos());
		searchMin = playerPos.add(-searchRadius, -searchRadius, -searchRadius);
		searchMax = playerPos.add(searchRadius, searchRadius, searchRadius);
		
		// Initialize search state
		isSearching = true;
		searchTimeoutCounter = 0;
		searchX = searchMin.getX();
		searchY = searchMin.getY();
		searchZ = searchMin.getZ();
	}
	
	private void continueSignSearch()
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
					// Check if it's a sign
					if(isSign(pos))
					{
						if(!filterSigns.isChecked() || matchesFilter(pos))
							signs.add(pos);
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
		
		// If we found enough signs, we can stop searching
		if(!signs.isEmpty() && signs.size() >= 5)
		{
			isSearching = false;
		}
	}
	
	private void breakSign(BlockPos pos)
	{
		BlockBreakingParams params = BlockBreaker.getBlockBreakingParams(pos);
		if(params == null || !params.lineOfSight()
			|| params.distanceSq() > range.getValueSq())
		{
			// If we can't break this sign, remove it and try another one
			signs.remove(pos);
			currentSign = null;
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
		if(!isSign(pos))
		{
			currentSign = null;
			overlay.resetProgress();
		}
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		// Don't render anything if we have no current sign
		if(currentSign == null && signs.isEmpty())
			return;
		
		// Render breaking progress
		if(currentSign != null)
		{
			// Highlight current target and show breaking progress
			overlay.render(matrixStack, partialTicks, currentSign);
			
			try
			{
				// Color for the current sign
				int currentColor = 0x80FF00FF;
				
				RenderUtils.drawOutlinedBox(matrixStack,
					BlockUtils.getBoundingBox(currentSign), currentColor, true);
			}catch(Exception e)
			{
				// Skip rendering if there's an error
			}
		}
		
		// Highlight all signs
		for(BlockPos pos : signs)
		{
			try
			{
				RenderUtils.drawOutlinedBox(matrixStack,
					BlockUtils.getBoundingBox(pos), 0x4000FFFF, true);
			}catch(Exception e)
			{
				// Skip rendering this sign if there's an error
			}
		}
	}
}
