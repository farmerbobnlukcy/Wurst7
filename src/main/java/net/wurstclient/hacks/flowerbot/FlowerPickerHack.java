
/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.flowerbot;

import java.util.ArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
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
import net.wurstclient.hacks.treebot.Flower;
import net.wurstclient.hacks.treebot.FlowerPickerUtils;
import net.wurstclient.settings.FacingSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.BlockBreaker;
import net.wurstclient.util.BlockBreaker.BlockBreakingParams;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.OverlayRenderer;

@SearchTags({"flower picker", "flower bot"})
@DontSaveState
public final class FlowerPickerHack extends Hack
		implements UpdateListener, RenderListener
{
	private final SliderSetting range = new SliderSetting("Range",
			"How far FlowerPicker will reach to break flowers.", 4.5, 1, 6, 0.05,
			ValueDisplay.DECIMAL);
	
	private final SliderSetting searchRadius = new SliderSetting("Search Radius",
			"How far FlowerPicker will search for flowers.", 16, 4, 32, 1,
			ValueDisplay.INTEGER);
	
	private final FacingSetting facing = FacingSetting.withoutPacketSpam(
			"How FlowerPicker should face the flowers when breaking them.\n\n"
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
	
	private FlowerFinder flowerFinder;
	private AngleFinder angleFinder;
	private FlowerBotPathProcessor processor;
	private Flower flower;
	
	private BlockPos currentBlock;
	private final OverlayRenderer overlay = new OverlayRenderer();
	
	public FlowerPickerHack()
	{
		super("FlowerPicker");
		setCategory(Category.BLOCKS);
		addSetting(range);
		addSetting(searchRadius);
		addSetting(facing);
		addSetting(swingHand);
	}
	
	@Override
	public String getRenderName()
	{
		if(flowerFinder != null && !flowerFinder.isDone() && !flowerFinder.isFailed())
			return getName() + " [Searching]";
		
		if(processor != null && !processor.isDone())
			return getName() + " [Going]";
		
		if(flower != null && !flower.getFlowers().isEmpty())
			return getName() + " [Picking]";
		
		return getName();
	}
	
	@Override
	protected void onEnable()
	{
		flowerFinder = new FlowerFinder();
		
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		PathProcessor.releaseControls();
		flowerFinder = null;
		angleFinder = null;
		processor = null;
		flower = null;
		
		if(currentBlock != null)
		{
			MC.interactionManager.breakingBlock = true;
			MC.interactionManager.cancelBlockBreaking();
			currentBlock = null;
		}
		
		overlay.resetProgress();
	}
	
	@Override
	public void onUpdate()
	{
		if(flowerFinder != null)
		{
			goToFlower();
			return;
		}
		
		if(flower == null)
		{
			flowerFinder = new FlowerFinder();
			return;
		}
		
		flower.getFlowers().removeIf(Predicate.not(FlowerPickerUtils::isFlower));
		
		if(flower.getFlowers().isEmpty())
		{
			flower = null;
			return;
		}
		
		if(angleFinder != null)
		{
			goToAngle();
			return;
		}
		
		if(breakBlocks(flower.getFlowers()))
			return;
		
		if(angleFinder == null)
			angleFinder = new AngleFinder();
	}
	
	private void goToFlower()
	{
		// find path
		if(!flowerFinder.isDoneOrFailed())
		{
			PathProcessor.lockControls();
			flowerFinder.findPath();
			return;
		}
		
		// process path
		if(processor != null && !processor.isDone())
		{
			processor.goToGoal();
			return;
		}
		
		PathProcessor.releaseControls();
		flowerFinder = null;
	}
	
	private void goToAngle()
	{
		// find path
		if(!angleFinder.isDone() && !angleFinder.isFailed())
		{
			PathProcessor.lockControls();
			angleFinder.findPath();
			return;
		}
		
		// process path
		if(processor != null && !processor.isDone())
		{
			processor.goToGoal();
			return;
		}
		
		PathProcessor.releaseControls();
		angleFinder = null;
	}
	
	private boolean breakBlocks(ArrayList<BlockPos> blocks)
	{
		for(BlockPos pos : blocks)
			if(breakBlock(pos))
			{
				currentBlock = pos;
				return true;
			}
		
		return false;
	}
	
	private boolean breakBlock(BlockPos pos)
	{
		BlockBreakingParams params = BlockBreaker.getBlockBreakingParams(pos);
		if(params == null || !params.lineOfSight()
				|| params.distanceSq() > range.getValueSq())
			return false;
		
		// select tool
		WURST.getHax().autoToolHack.equipBestTool(pos, false, true, 0);
		
		// face block
		facing.getSelected().face(params.hitVec());
		
		// damage block and swing hand
		if(MC.interactionManager.updateBlockBreakingProgress(pos,
				params.side()))
			swingHand.swing(Hand.MAIN_HAND);
		
		// update progress
		overlay.updateProgress();
		
		return true;
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		PathCmd pathCmd = WURST.getCmds().pathCmd;
		
		if(flowerFinder != null)
			flowerFinder.renderPath(matrixStack, pathCmd.isDebugMode(),
					pathCmd.isDepthTest());
		
		if(angleFinder != null)
			angleFinder.renderPath(matrixStack, pathCmd.isDebugMode(),
					pathCmd.isDepthTest());
		
		if(flower != null)
			flower.draw(matrixStack);
		
		overlay.render(matrixStack, partialTicks, currentBlock);
	}
	
	private abstract class FlowerBotPathFinder extends PathFinder
	{
		public FlowerBotPathFinder(BlockPos goal)
		{
			super(goal);
		}
		
		public FlowerBotPathFinder(FlowerBotPathFinder pathFinder)
		{
			super(pathFinder);
		}
		
		public void findPath()
		{
			think();
			
			if(isDoneOrFailed())
			{
				// set processor
				formatPath();
				processor = new FlowerBotPathProcessor(this);
			}
		}
		
		public boolean isDoneOrFailed()
		{
			return isDone() || isFailed();
		}
		
		public abstract void reset();
	}
	
	private class FlowerBotPathProcessor
	{
		private final FlowerBotPathFinder pathFinder;
		private final PathProcessor processor;
		
		public FlowerBotPathProcessor(FlowerBotPathFinder pathFinder)
		{
			this.pathFinder = pathFinder;
			processor = pathFinder.getProcessor();
		}
		
		public void goToGoal()
		{
			if(!pathFinder.isPathStillValid(processor.getIndex())
					|| processor.getTicksOffPath() > 20)
			{
				pathFinder.reset();
				return;
			}
			
			processor.process();
		}
		
		public final boolean isDone()
		{
			return processor.isDone();
		}
	}
	
	private class FlowerFinder extends FlowerBotPathFinder
	{
		private final int searchRadiusValue;
		
		public FlowerFinder()
		{
			super(BlockPos.ofFloored(WurstClient.MC.player.getPos()));
			searchRadiusValue = searchRadius.getValueI();
			setThinkTime(1);
		}
		
		public FlowerFinder(FlowerBotPathFinder pathFinder)
		{
			super(pathFinder);
			searchRadiusValue = searchRadius.getValueI();
		}
		
		@Override
		protected boolean isMineable(BlockPos pos)
		{
			return false; // We don't want to break anything while pathfinding to flowers
		}
		
		@Override
		protected boolean checkDone()
		{
			return done = isNextToFlower(current);
		}
		
		private boolean isNextToFlower(PathPos pos)
		{
			BlockPos playerPos = pos.getBlockPos();
			
			// Search for flowers in a radius around the player
			ArrayList<BlockPos> nearbyFlowers = new ArrayList<>();
			
			BlockPos min = playerPos.add(-searchRadiusValue, -3, -searchRadiusValue);
			BlockPos max = playerPos.add(searchRadiusValue, 3, searchRadiusValue);
			BlockUtils.getAllInBoxStream(min, max)
					.filter(FlowerPickerUtils::isFlower)
					.forEach(nearbyFlowers::add);
			
			if(nearbyFlowers.isEmpty())
				return false;
			
			flower = new Flower(nearbyFlowers);
			
			// Check if we're directly next to any flower
			for(BlockPos flowerPos : nearbyFlowers)
			{
				BlockPos diff = playerPos.subtract(flowerPos);
				if(Math.abs(diff.getX()) <= 1 && Math.abs(diff.getY()) <= 1
						&& Math.abs(diff.getZ()) <= 1)
					return true;
			}
			
			// If we found flowers but aren't next to them, set the goal to the nearest one
			BlockPos nearestFlower = nearbyFlowers.get(0);
			double nearestDist = Double.MAX_VALUE;
			for(BlockPos flowerPos : nearbyFlowers)
			{
				double dist = playerPos.getSquaredDistance(flowerPos);
				if(dist < nearestDist)
				{
					nearestDist = dist;
					nearestFlower = flowerPos;
				}
			}
			
			goal = nearestFlower;
			return false;
		}
		
		@Override
		public void reset()
		{
			flowerFinder = new FlowerFinder(flowerFinder);
		}
	}
	
	private class AngleFinder extends FlowerBotPathFinder
	{
		public AngleFinder()
		{
			super(BlockPos.ofFloored(WurstClient.MC.player.getPos()));
			setThinkSpeed(512);
			setThinkTime(1);
		}
		
		public AngleFinder(FlowerBotPathFinder pathFinder)
		{
			super(pathFinder);
		}
		
		@Override
		protected boolean isMineable(BlockPos pos)
		{
			return false;
		}
		
		@Override
		protected boolean checkDone()
		{
			return done = hasAngle(current);
		}
		
		private boolean hasAngle(PathPos pos)
		{
			double rangeSq = range.getValueSq();
			ClientPlayerEntity player = WurstClient.MC.player;
			Vec3d eyes = Vec3d.ofBottomCenter(pos).add(0,
					player.getEyeHeight(player.getPose()), 0);
			
			for(BlockPos flowerPos : flower.getFlowers())
			{
				BlockBreakingParams params =
						BlockBreaker.getBlockBreakingParams(eyes, flowerPos);
				
				if(params != null && params.lineOfSight()
						&& params.distanceSq() <= rangeSq)
					return true;
			}
			
			return false;
		}
		
		@Override
		public void reset()
		{
			angleFinder = new AngleFinder(angleFinder);
		}
	}
}