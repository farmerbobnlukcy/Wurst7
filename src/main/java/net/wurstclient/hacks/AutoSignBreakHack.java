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
import net.minecraft.client.util.math.MatrixStack;
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
import java.util.Comparator;

@SearchTags({"auto sign", "sign breaker", "sign destroyer"})
public final class AutoSignBreakHack extends Hack
	implements UpdateListener, RenderListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"How far AutoSignBreak will reach to break signs.", 4, 3, 5, 0.5,
		ValueDisplay.DECIMAL);
	
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
	
	private BlockPos currentSign;
	private final ArrayList<BlockPos> signs = new ArrayList<>();
	private final OverlayRenderer overlay = new OverlayRenderer();
	
	public AutoSignBreakHack()
	{
		super("AutoSignBreak");
		setCategory(Category.BLOCKS);
		
		addSetting(range);
		addSetting(facing);
		addSetting(swingHand);
	}
	
	@Override
	public String getRenderName()
	{
		if(currentSign != null)
			return getName() + " [Breaking]";
		
		return getName();
	}
	
	@Override
	protected void onEnable()
	{
		signs.clear();
		currentSign = null;
		
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
	}
	
	@Override
	public void onUpdate()
	{
		// Reset current sign if it's no longer valid
		if(currentSign != null && !isSign(currentSign))
		{
			currentSign = null;
			overlay.resetProgress();
		}
		
		// Find nearby signs if our list is empty
		if(signs.isEmpty())
		{
			Vec3d playerPos = MC.player.getPos();
			BlockPos playerBlockPos = BlockPos.ofFloored(playerPos);
			int rangeI = (int)Math.ceil(range.getValue());
			double rangeSq = range.getValueSq();
			
			// Simple search through all blocks in range
			for(int x = -rangeI; x <= rangeI; x++)
			{
				for(int y = -rangeI; y <= rangeI; y++)
				{
					for(int z = -rangeI; z <= rangeI; z++)
					{
						BlockPos pos = playerBlockPos.add(x, y, z);
						
						// Check distance and if it's a sign
						if(playerPos
							.squaredDistanceTo(Vec3d.ofCenter(pos)) <= rangeSq
							&& isSign(pos))
						{
							signs.add(pos);
						}
					}
				}
			}
			
			// If no signs found, return
			if(signs.isEmpty())
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
