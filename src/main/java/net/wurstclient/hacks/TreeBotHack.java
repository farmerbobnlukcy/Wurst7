/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
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
import net.wurstclient.hacks.treebot.Tree;
import net.wurstclient.hacks.treebot.TreeBotUtils;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.BlockBreaker;
import net.wurstclient.util.BlockBreaker.BlockBreakingParams;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.OverlayRenderer;

@SearchTags({"tree bot"})
@DontSaveState
public final class TreeBotHack extends Hack
	implements UpdateListener, RenderListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"How far TreeBot will reach to break blocks.", 4.5, 1, 6, 0.05,
		ValueDisplay.DECIMAL);
	
	private final FacingSetting facing = FacingSetting.withoutPacketSpam(
		"How TreeBot should face the logs and leaves when breaking them.\n\n"
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
	
	private final CheckboxSetting collectDrops = new CheckboxSetting(
		"Collect Drops",
		"When enabled, TreeBot will collect dropped items after cutting a tree.",
		true);
	
	private final SliderSetting itemSearchRange = new SliderSetting(
		"Item Search Range",
		"How far TreeBot will search for dropped items after cutting a tree.",
		16, 4, 32, 1, ValueDisplay.INTEGER);
	
	private TreeFinder treeFinder;
	private AngleFinder angleFinder;
	private TreeBotPathProcessor processor;
	private Tree tree;
	
	private boolean collectingItems = false;
	private ItemGathererHack itemGatherer;
	
	private BlockPos currentBlock;
	private final OverlayRenderer overlay = new OverlayRenderer();
	
	public TreeBotHack()
	{
		super("TreeBot");
		setCategory(Category.BLOCKS);
		addSetting(range);
		addSetting(facing);
		addSetting(swingHand);
	}
	
	@Override
	public String getRenderName()
	{
		if(collectingItems)
			return getName() + " [Collecting]";
		
		if(treeFinder != null && !treeFinder.isDone() && !treeFinder.isFailed())
			return getName() + " [Searching]";
		
		if(processor != null && !processor.isDone())
			return getName() + " [Going]";
		
		if(tree != null && !tree.getLogs().isEmpty())
			return getName() + " [Chopping]";
		
		return getName();
	}
	
	@Override
	protected void onEnable()
	{
		treeFinder = new TreeFinder();
		collectingItems = false;
		
		// Get a reference to the ItemGathererHack
		
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		PathProcessor.releaseControls();
		treeFinder = null;
		angleFinder = null;
		processor = null;
		tree = null;
		collectingItems = false;
		
		// Make sure the ItemGathererHack is disabled when we disable TreeBot
		if(itemGatherer != null && itemGatherer.isEnabled())
			itemGatherer.setEnabled(false);
		
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
		// If we're in collecting mode, handle item gathering
		if(collectingItems)
		{
			// If the user has disabled item gathering, move on
			if(!collectDrops.isChecked())
			{
				finishCollectingItems();
				return;
			}
			
			// If there are no nearby items or ItemGatherer has finished
			if(!hasNearbyItems() || !itemGatherer.isEnabled())
			{
				finishCollectingItems();
				return;
			}
			
			// Otherwise, continue collecting
			return;
		}
		
		if(treeFinder != null)
		{
			goToTree();
			return;
		}
		
		if(tree == null)
		{
			treeFinder = new TreeFinder();
			return;
		}
		
		tree.getLogs().removeIf(Predicate.not(TreeBotUtils::isLog));
		
		if(tree.getLogs().isEmpty())
		{
			// If tree is done, try to collect drops if setting is enabled
			if(collectDrops.isChecked() && hasNearbyItems())
			{
				startCollectingItems();
			}else
			{
				tree = null;
			}
			return;
		}
		
		if(angleFinder != null)
		{
			goToAngle();
			return;
		}
		
		if(breakBlocks(tree.getLogs()))
			return;
		
		if(angleFinder == null)
			angleFinder = new AngleFinder();
	}
	
	private void startCollectingItems()
	{
		
		collectingItems = true;
		
		// Configure the ItemGathererHack's search range to match our configured
		// range
		final String RANGE_SETTING_NAME = "Range";
		Setting rangeSetting =
			itemGatherer.getSettings().get(RANGE_SETTING_NAME);
		if(rangeSetting != null)
		{
			// Set the range setting to our configured value
			// NOTE: Actual setting implementation needs to be added here
			// Example: rangeSetting.setValue(itemSearchRange.getValue());
		}
		
		// Enable ItemGatherer if it's not already enabled
		if(!itemGatherer.isEnabled())
			itemGatherer.setEnabled(true);
	}
	
	private void finishCollectingItems()
	{
		collectingItems = false;
		
		// Disable ItemGatherer if it's enabled
		if(itemGatherer.isEnabled())
			itemGatherer.setEnabled(false);
		
		// Reset tree to start looking for a new one
		tree = null;
	}
	
	private boolean hasNearbyItems()
	{
		ClientPlayerEntity player = MC.player;
		double rangeSq = itemSearchRange.getValueSq();
		Vec3d playerPos = player.getPos();
		
		// Check if there are any item entities within range
		for(Entity entity : MC.world.getEntities())
		{
			if(entity instanceof ItemEntity && entity.isAlive()
				&& playerPos.squaredDistanceTo(entity.getPos()) <= rangeSq)
			{
				return true;
			}
		}
		
		return false;
	}
	
	private void goToTree()
	{
		// find path
		if(!treeFinder.isDoneOrFailed())
		{
			PathProcessor.lockControls();
			treeFinder.findPath();
			return;
		}
		
		// process path
		if(processor != null && !processor.isDone())
		{
			processor.goToGoal();
			return;
		}
		
		PathProcessor.releaseControls();
		treeFinder = null;
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
		
		if(treeFinder != null)
			treeFinder.renderPath(matrixStack, pathCmd.isDebugMode(),
				pathCmd.isDepthTest());
		
		if(angleFinder != null)
			angleFinder.renderPath(matrixStack, pathCmd.isDebugMode(),
				pathCmd.isDepthTest());
		
		if(tree != null)
			tree.draw(matrixStack);
		
		overlay.render(matrixStack, partialTicks, currentBlock);
	}
	
	private ArrayList<BlockPos> getNeighbors(BlockPos pos)
	{
		return BlockUtils
			.getAllInBoxStream(pos.add(-1, -1, -1), pos.add(1, 1, 1))
			.filter(TreeBotUtils::isLog)
			.collect(Collectors.toCollection(ArrayList::new));
	}
	
	private abstract class TreeBotPathFinder extends PathFinder
	{
		public TreeBotPathFinder(BlockPos goal)
		{
			super(goal);
		}
		
		public TreeBotPathFinder(TreeBotPathFinder pathFinder)
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
				processor = new TreeBotPathProcessor(this);
			}
		}
		
		public boolean isDoneOrFailed()
		{
			return isDone() || isFailed();
		}
		
		public abstract void reset();
	}
	
	private class TreeBotPathProcessor
	{
		private final TreeBotPathFinder pathFinder;
		private final PathProcessor processor;
		
		public TreeBotPathProcessor(TreeBotPathFinder pathFinder)
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
			
			if(processor.canBreakBlocks() && breakBlocks(getLeavesOnPath()))
				return;
			
			processor.process();
		}
		
		private ArrayList<BlockPos> getLeavesOnPath()
		{
			List<PathPos> path = pathFinder.getPath();
			path = path.subList(processor.getIndex(), path.size());
			
			return path.stream().flatMap(pos -> Stream.of(pos, pos.up()))
				.distinct().filter(TreeBotUtils::isLeaves)
				.collect(Collectors.toCollection(ArrayList::new));
		}
		
		public final boolean isDone()
		{
			return processor.isDone();
		}
	}
	
	private class TreeFinder extends TreeBotPathFinder
	{
		public TreeFinder()
		{
			super(BlockPos.ofFloored(WurstClient.MC.player.getPos()));
		}
		
		public TreeFinder(TreeBotPathFinder pathFinder)
		{
			super(pathFinder);
		}
		
		@Override
		protected boolean isMineable(BlockPos pos)
		{
			return TreeBotUtils.isLeaves(pos);
		}
		
		@Override
		protected boolean checkDone()
		{
			return done = isNextToTreeStump(current);
		}
		
		private boolean isNextToTreeStump(PathPos pos)
		{
			return isTreeStump(pos.north()) || isTreeStump(pos.east())
				|| isTreeStump(pos.south()) || isTreeStump(pos.west());
		}
		
		private boolean isTreeStump(BlockPos pos)
		{
			if(!TreeBotUtils.isLog(pos))
				return false;
			
			if(TreeBotUtils.isLog(pos.down()))
				return false;
			
			analyzeTree(pos);
			
			// ignore large trees (for now)
			if(tree.getLogs().size() > 6)
				return false;
			
			return true;
		}
		
		private void analyzeTree(BlockPos stump)
		{
			ArrayList<BlockPos> logs = new ArrayList<>(Arrays.asList(stump));
			ArrayDeque<BlockPos> queue = new ArrayDeque<>(Arrays.asList(stump));
			
			for(int i = 0; i < 1024; i++)
			{
				if(queue.isEmpty())
					break;
				
				BlockPos current = queue.pollFirst();
				
				for(BlockPos next : getNeighbors(current))
				{
					if(logs.contains(next))
						continue;
					
					logs.add(next);
					queue.add(next);
				}
			}
			
			tree = new Tree(stump, logs);
		}
		
		@Override
		public void reset()
		{
			treeFinder = new TreeFinder(treeFinder);
		}
	}
	
	private class AngleFinder extends TreeBotPathFinder
	{
		public AngleFinder()
		{
			super(BlockPos.ofFloored(WurstClient.MC.player.getPos()));
			setThinkSpeed(512);
			setThinkTime(1);
		}
		
		public AngleFinder(TreeBotPathFinder pathFinder)
		{
			super(pathFinder);
		}
		
		@Override
		protected boolean isMineable(BlockPos pos)
		{
			return TreeBotUtils.isLeaves(pos);
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
			
			for(BlockPos log : tree.getLogs())
			{
				BlockBreakingParams params =
					BlockBreaker.getBlockBreakingParams(eyes, log);
				
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
