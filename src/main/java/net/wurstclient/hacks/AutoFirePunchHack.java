/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

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
import net.wurstclient.settings.FacingSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.BlockBreaker;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.OverlayRenderer;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RotationUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;

@SearchTags({"auto fire punch", "fire extinguisher", "fire breaker"})
public final class AutoFirePunchHack extends Hack
	implements UpdateListener, RenderListener
{
	private final SliderSetting range =
		new SliderSetting("Range", 4.5, 1, 6, 0.05, ValueDisplay.DECIMAL);
	
	private final FacingSetting facing = FacingSetting.withoutPacketSpam(
		"How AutoFirePunch should face the fire blocks when extinguishing them.\n\n"
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
	
	private BlockPos currentFire;
	private final ArrayList<BlockPos> fireBlocks = new ArrayList<>();
	private final OverlayRenderer overlay = new OverlayRenderer();
	
	public AutoFirePunchHack()
	{
		super("AutoFirePunch");
		setCategory(Category.BLOCKS);
		
		addSetting(range);
		addSetting(facing);
		addSetting(swingHand);
	}
	
	@Override
	public String getRenderName()
	{
		if(currentFire != null)
			return getName() + " [Extinguishing]";
		
		return getName();
	}
	
	@Override
	protected void onEnable()
	{
		fireBlocks.clear();
		currentFire = null;
		
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		if(currentFire != null)
		{
			MC.interactionManager.breakingBlock = true;
			MC.interactionManager.cancelBlockBreaking();
			currentFire = null;
		}
		
		overlay.resetProgress();
	}
	
	@Override
	public void onUpdate()
	{
		// Reset current fire if it's no longer valid
		if(currentFire != null && !isFireBlock(currentFire))
		{
			currentFire = null;
			overlay.resetProgress();
		}
		
		// Find nearby fire blocks if our list is empty
		if(fireBlocks.isEmpty())
		{
			Vec3d eyesVec = RotationUtils.getEyesPos();
			BlockPos eyesBlock = BlockPos.ofFloored(eyesVec);
			double rangeSq = range.getValueSq();
			int blockRange = range.getValueCeil();
			
			// Get all blocks within range that are fire
			fireBlocks
				.addAll(BlockUtils.getAllInBoxStream(eyesBlock, blockRange)
					.filter(pos -> pos.getSquaredDistance(eyesVec) <= rangeSq)
					.filter(this::isFireBlock)
					.sorted(Comparator.comparingDouble(
						pos -> pos.getSquaredDistance(eyesVec)))
					.collect(Collectors.toList()));
			
			// If no fire blocks found, return
			if(fireBlocks.isEmpty())
				return;
		}
		
		// Clean up our fire list (remove any that are no longer fire)
		fireBlocks.removeIf(pos -> !isFireBlock(pos));
		
		// If we have no current fire or it's been extinguished, get a new one
		if(currentFire == null)
		{
			// Try to find the nearest fire block
			Vec3d playerPos = MC.player.getPos();
			
			if(!fireBlocks.isEmpty())
			{
				currentFire =
					fireBlocks.stream()
						.min(Comparator.comparingDouble(pos -> playerPos
							.squaredDistanceTo(Vec3d.ofCenter(pos))))
						.orElse(null);
				
				if(currentFire != null)
				{
					fireBlocks.remove(currentFire);
					return;
				}
			}
			
			// If no valid target found, clear lists and search again next tick
			fireBlocks.clear();
			return;
		}
		
		// Extinguish the current fire
		extinguishFire(currentFire);
	}
	
	private boolean isFireBlock(BlockPos pos)
	{
		if(pos == null)
			return false;
		
		return BlockUtils.getBlock(pos) == Blocks.FIRE
			|| BlockUtils.getBlock(pos) == Blocks.SOUL_FIRE;
	}
	
	private void extinguishFire(BlockPos pos)
	{
		// Get breaking parameters for the fire block
		BlockBreaker.BlockBreakingParams params =
			BlockBreaker.getBlockBreakingParams(pos);
		
		if(params == null || params.distanceSq() > range.getValueSq())
		{
			// If we can't reach this fire, remove it and try another one
			fireBlocks.remove(pos);
			currentFire = null;
			return;
		}
		
		// Face the fire block according to settings
		facing.getSelected().face(params.hitVec());
		
		// Break the fire block
		if(MC.interactionManager.updateBlockBreakingProgress(pos,
			params.side()))
			swingHand.swing(Hand.MAIN_HAND);
		
		// Update progress overlay
		overlay.updateProgress();
		
		// If the fire has been extinguished, move to the next one
		if(!isFireBlock(pos))
		{
			currentFire = null;
			overlay.resetProgress();
		}
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		// Don't render anything if we have no current fire
		if(currentFire == null && fireBlocks.isEmpty())
			return;
		
		// Render breaking progress
		if(currentFire != null)
		{
			// Highlight current target and show breaking progress
			overlay.render(matrixStack, partialTicks, currentFire);
			
			try
			{
				// Red color for the current fire being extinguished
				int currentColor = 0x80FF0000;
				
				RenderUtils.drawOutlinedBox(matrixStack,
					BlockUtils.getBoundingBox(currentFire), currentColor, true);
			}catch(Exception e)
			{
				// Skip rendering if there's an error
			}
		}
		
		// Highlight all fire blocks
		for(BlockPos pos : fireBlocks)
		{
			try
			{
				// Orange color for other fire blocks
				RenderUtils.drawOutlinedBox(matrixStack,
					BlockUtils.getBoundingBox(pos), 0x40FF8800, true);
			}catch(Exception e)
			{
				// Skip rendering this fire if there's an error
			}
		}
	}
}
