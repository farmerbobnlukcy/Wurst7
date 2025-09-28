/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.block.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.autofarm.AutoFarmRenderer;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.*;
import net.wurstclient.util.BlockPlacer.BlockPlacingParams;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SearchTags({"auto farm", "crop", "AutoHarvest", "auto harvest"})
public final class AutoFarmHack extends Hack
	implements UpdateListener, RenderListener
{
	private final SliderSetting range =
		new SliderSetting("Range", 5, 1, 6, 0.05, ValueDisplay.DECIMAL);
	
	private final CheckboxSetting replant =
		new CheckboxSetting("Replant", true);
	
	private final CheckboxSetting plantCrops =
		new CheckboxSetting("Plant Crops",
			"Plants crops when looking at tilled farmland\n"
				+ "while holding plantable items (seeds, potatoes, etc.)",
			true);
	
	private final CheckboxSetting autoSwitchToBonemeal = new CheckboxSetting(
		"Auto-switch to BonemealAura",
		"Automatically switches to BonemealAura when there are no more blocks to harvest or plant",
		true);
	
	private final CheckboxSetting ignoreSugarCane =
		new CheckboxSetting("Ignore Sugar Cane",
			"When enabled, AutoFarm will not harvest sugar cane.", false);
	
	private final HashMap<Block, Item> seeds = new HashMap<>();
	
	{
		seeds.put(Blocks.WHEAT, Items.WHEAT_SEEDS);
		seeds.put(Blocks.CARROTS, Items.CARROT);
		seeds.put(Blocks.POTATOES, Items.POTATO);
		seeds.put(Blocks.BEETROOTS, Items.BEETROOT_SEEDS);
		seeds.put(Blocks.PUMPKIN_STEM, Items.PUMPKIN_SEEDS);
		seeds.put(Blocks.MELON_STEM, Items.MELON_SEEDS);
		seeds.put(Blocks.NETHER_WART, Items.NETHER_WART);
		seeds.put(Blocks.COCOA, Items.COCOA_BEANS);
	}
	
	// Map of plantable items to the blocks they grow into
	private final HashMap<Item, Block> plantableItems = new HashMap<>();
	
	{
		plantableItems.put(Items.WHEAT_SEEDS, Blocks.WHEAT);
		plantableItems.put(Items.CARROT, Blocks.CARROTS);
		plantableItems.put(Items.POTATO, Blocks.POTATOES);
		plantableItems.put(Items.BEETROOT_SEEDS, Blocks.BEETROOTS);
		plantableItems.put(Items.PUMPKIN_SEEDS, Blocks.PUMPKIN_STEM);
		plantableItems.put(Items.MELON_SEEDS, Blocks.MELON_STEM);
		// Nether wart isn't planted on farmland, but we'll check for soul sand
		// separately
		plantableItems.put(Items.NETHER_WART, Blocks.NETHER_WART);
	}
	
	private final HashMap<BlockPos, Item> plants = new HashMap<>();
	private final BlockBreakingCache cache = new BlockBreakingCache();
	private BlockPos currentlyHarvesting;
	
	private final AutoFarmRenderer renderer = new AutoFarmRenderer();
	private final OverlayRenderer overlay = new OverlayRenderer();
	
	private boolean busy;
	private int emptyTickCounter = 0;
	
	public AutoFarmHack()
	{
		super("AutoFarm");
		
		setCategory(Category.BLOCKS);
		addSetting(range);
		addSetting(replant);
		addSetting(plantCrops);
		addSetting(autoSwitchToBonemeal);
		addSetting(ignoreSugarCane);
	}
	
	@Override
	protected void onEnable()
	{
		plants.clear();
		emptyTickCounter = 0;
		
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		if(currentlyHarvesting != null)
		{
			MC.interactionManager.breakingBlock = true;
			MC.interactionManager.cancelBlockBreaking();
			currentlyHarvesting = null;
		}
		
		cache.reset();
		overlay.resetProgress();
		busy = false;
		emptyTickCounter = 0;
		
		renderer.reset();
	}
	
	@Override
	public void onUpdate()
	{
		currentlyHarvesting = null;
		Vec3d eyesVec = RotationUtils.getEyesPos();
		BlockPos eyesBlock = BlockPos.ofFloored(eyesVec);
		double rangeSq = range.getValueSq();
		int blockRange = range.getValueCeil();
		
		// get nearby, non-empty blocks
		ArrayList<BlockPos> blocks =
			BlockUtils.getAllInBoxStream(eyesBlock, blockRange)
				.filter(pos -> pos.getSquaredDistance(eyesVec) <= rangeSq)
				.filter(BlockUtils::canBeClicked)
				.collect(Collectors.toCollection(ArrayList::new));
		
		// check for any new plants and add them to the map
		updatePlants(blocks);
		
		ArrayList<BlockPos> blocksToHarvest = new ArrayList<>();
		ArrayList<BlockPos> blocksToReplant = new ArrayList<>();
		
		// Check if the Plant Crops feature should be used
		if(plantCrops.isChecked() && !WURST.getHax().freecamHack.isEnabled())
		{
			// Try to plant crops when looking at farmland
			if(tryPlantCrop())
			{
				// If we planted something, skip other actions this tick
				busy = true;
				renderer.updateVertexBuffers(blocksToHarvest, plants.keySet(),
					blocksToReplant);
				return;
			}
		}
		
		// don't place or break any blocks while Freecam is enabled
		if(!WURST.getHax().freecamHack.isEnabled())
		{
			// check which of the nearby blocks can be harvested
			blocksToHarvest = getBlocksToHarvest(eyesVec, blocks);
			
			// do a new search to find empty blocks that can be replanted
			if(replant.isChecked())
				blocksToReplant =
					getBlocksToReplant(eyesVec, eyesBlock, rangeSq, blockRange);
		}
		
		// first, try to replant
		boolean replanting = replant(blocksToReplant);
		
		// if we can't replant, harvest instead
		if(!replanting)
			harvest(blocksToHarvest.stream());
		
		// update busy state
		busy = replanting || currentlyHarvesting != null;
		
		// Check if there's nothing to do
		if(!busy)
		{
			emptyTickCounter++;
			
			// If nothing to do for several ticks and auto-switch is enabled
			if(emptyTickCounter >= 10 && autoSwitchToBonemeal.isChecked())
			{
				// Check if player has bonemeal
				if(hasBonemealInInventory())
				{
					// Disable AutoFarm and enable BonemealAura
					setEnabled(false);
					WURST.getHax().bonemealAuraHack.setEnabled(true);
				}
			}
		}else
		{
			// Reset counter if we're doing something
			emptyTickCounter = 0;
		}
		
		// update renderer
		renderer.updateVertexBuffers(blocksToHarvest, plants.keySet(),
			blocksToReplant);
	}
	
	/**
	 * Checks if the player has bone meal in their inventory.
	 */
	private boolean hasBonemealInInventory()
	{
		// Check main hand
		if(MC.player.getMainHandStack().isOf(Items.BONE_MEAL))
			return true;
		
		// Check offhand
		if(MC.player.getOffHandStack().isOf(Items.BONE_MEAL))
			return true;
		
		// Check hotbar and inventory
		for(int slot = 0; slot < 36; slot++)
		{
			ItemStack stack = MC.player.getInventory().getStack(slot);
			if(stack.isOf(Items.BONE_MEAL))
				return true;
		}
		
		return false;
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		renderer.render(matrixStack);
		overlay.render(matrixStack, partialTicks, currentlyHarvesting);
	}
	
	/**
	 * Returns true if AutoFarm is currently harvesting or replanting something.
	 */
	public boolean isBusy()
	{
		return busy;
	}
	
	private void updatePlants(List<BlockPos> blocks)
	{
		for(BlockPos pos : blocks)
		{
			Item seed = seeds.get(BlockUtils.getBlock(pos));
			if(seed == null)
				continue;
			
			plants.put(pos, seed);
		}
	}
	
	private ArrayList<BlockPos> getBlocksToHarvest(Vec3d eyesVec,
		ArrayList<BlockPos> blocks)
	{
		return blocks.parallelStream().filter(this::shouldBeHarvested)
			.sorted(Comparator
				.comparingDouble(pos -> pos.getSquaredDistance(eyesVec)))
			.collect(Collectors.toCollection(ArrayList::new));
	}
	
	private boolean shouldBeHarvested(BlockPos pos)
	{
		Block block = BlockUtils.getBlock(pos);
		BlockState state = BlockUtils.getState(pos);
		
		if(block instanceof CropBlock)
			return ((CropBlock)block).isMature(state);
		
		if(block instanceof NetherWartBlock)
			return state.get(NetherWartBlock.AGE) >= 3;
		
		if(block instanceof CocoaBlock)
			return state.get(CocoaBlock.AGE) >= 2;
		
		if(block == Blocks.PUMPKIN || block == Blocks.MELON)
			return true;
		
		if(block instanceof SugarCaneBlock)
			return !ignoreSugarCane.isChecked()
				&& BlockUtils.getBlock(pos.down()) instanceof SugarCaneBlock
				&& !(BlockUtils
					.getBlock(pos.down(2)) instanceof SugarCaneBlock);
		
		if(block instanceof CactusBlock)
			return BlockUtils.getBlock(pos.down()) instanceof CactusBlock
				&& !(BlockUtils.getBlock(pos.down(2)) instanceof CactusBlock);
		
		if(block instanceof KelpPlantBlock)
			return BlockUtils.getBlock(pos.down()) instanceof KelpPlantBlock
				&& !(BlockUtils
					.getBlock(pos.down(2)) instanceof KelpPlantBlock);
		
		if(block instanceof BambooBlock)
			return BlockUtils.getBlock(pos.down()) instanceof BambooBlock
				&& !(BlockUtils.getBlock(pos.down(2)) instanceof BambooBlock);
		
		return false;
	}
	
	private ArrayList<BlockPos> getBlocksToReplant(Vec3d eyesVec,
		BlockPos eyesBlock, double rangeSq, int blockRange)
	{
		return BlockUtils.getAllInBoxStream(eyesBlock, blockRange)
			.filter(pos -> pos.getSquaredDistance(eyesVec) <= rangeSq)
			.filter(pos -> BlockUtils.getState(pos).isReplaceable())
			.filter(pos -> plants.containsKey(pos)).filter(this::canBeReplanted)
			.sorted(Comparator
				.comparingDouble(pos -> pos.getSquaredDistance(eyesVec)))
			.collect(Collectors.toCollection(ArrayList::new));
	}
	
	private boolean canBeReplanted(BlockPos pos)
	{
		Item item = plants.get(pos);
		
		if(item == Items.WHEAT_SEEDS || item == Items.CARROT
			|| item == Items.POTATO || item == Items.BEETROOT_SEEDS
			|| item == Items.PUMPKIN_SEEDS || item == Items.MELON_SEEDS)
			return BlockUtils.getBlock(pos.down()) instanceof FarmlandBlock;
		
		if(item == Items.NETHER_WART)
			return BlockUtils.getBlock(pos.down()) instanceof SoulSandBlock;
		
		if(item == Items.COCOA_BEANS)
			return BlockUtils.getState(pos.north()).isIn(BlockTags.JUNGLE_LOGS)
				|| BlockUtils.getState(pos.east()).isIn(BlockTags.JUNGLE_LOGS)
				|| BlockUtils.getState(pos.south()).isIn(BlockTags.JUNGLE_LOGS)
				|| BlockUtils.getState(pos.west()).isIn(BlockTags.JUNGLE_LOGS);
		
		return false;
	}
	
	private boolean replant(List<BlockPos> blocksToReplant)
	{
		// check cooldown
		if(MC.itemUseCooldown > 0)
			return false;
		
		// check if already holding one of the seeds needed for blocksToReplant
		Optional<Item> heldSeed = blocksToReplant.stream().map(plants::get)
			.distinct().filter(item -> MC.player.isHolding(item)).findFirst();
		
		// if so, try to replant the blocks that need that seed
		if(heldSeed.isPresent())
		{
			// get the seed and the hand that is holding it
			Item item = heldSeed.get();
			Hand hand = MC.player.getMainHandStack().isOf(item) ? Hand.MAIN_HAND
				: Hand.OFF_HAND;
			
			// filter out blocks that need a different seed
			ArrayList<BlockPos> blocksToReplantWithHeldSeed =
				blocksToReplant.stream().filter(pos -> plants.get(pos) == item)
					.collect(Collectors.toCollection(ArrayList::new));
			
			for(BlockPos pos : blocksToReplantWithHeldSeed)
			{
				// skip over blocks that we can't reach
				BlockPlacingParams params =
					BlockPlacer.getBlockPlacingParams(pos);
				if(params == null || params.distanceSq() > range.getValueSq())
					continue;
				
				// face block
				WURST.getRotationFaker().faceVectorPacket(params.hitVec());
				
				// place seed
				ActionResult result = MC.interactionManager
					.interactBlock(MC.player, hand, params.toHitResult());
				
				// swing arm
				// Note: All SwingHand types correspond to SwingSource.CLIENT
				if(result instanceof ActionResult.Success success
					&& success.swingSource() == ActionResult.SwingSource.CLIENT)
					SwingHand.SERVER.swing(hand); // intentional use of SERVER
					
				// reset cooldown
				MC.itemUseCooldown = 4;
				return true;
			}
		}
		
		// otherwise, find a block that we can reach and have seeds for
		for(BlockPos pos : blocksToReplant)
		{
			// skip over blocks that we can't reach
			BlockPlacingParams params = BlockPlacer.getBlockPlacingParams(pos);
			if(params == null || params.distanceSq() > range.getValueSq())
				continue;
			
			// try to select the seed (returns false if we don't have it)
			Item item = plants.get(pos);
			if(InventoryUtils.selectItem(item))
				return true;
		}
		
		// if we couldn't replant anything, return false
		return false;
	}
	
	/**
	 * Tries to plant a crop on farmland that the player is looking at.
	 *
	 * @return true if a crop was planted
	 */
	private boolean tryPlantCrop()
	{
		// Check cooldown
		if(MC.itemUseCooldown > 0)
			return false;
		
		// Get what the player is holding
		ItemStack mainHandStack = MC.player.getMainHandStack();
		ItemStack offHandStack = MC.player.getOffHandStack();
		
		// Check if either hand has a plantable item
		Item heldItem = null;
		Hand plantHand = null;
		
		if(plantableItems.containsKey(mainHandStack.getItem()))
		{
			heldItem = mainHandStack.getItem();
			plantHand = Hand.MAIN_HAND;
		}else if(plantableItems.containsKey(offHandStack.getItem()))
		{
			heldItem = offHandStack.getItem();
			plantHand = Hand.OFF_HAND;
		}
		
		// Return if player isn't holding a plantable item
		if(heldItem == null || plantHand == null)
			return false;
		
		// Special case for nether wart which needs soul sand, not farmland
		if(heldItem == Items.NETHER_WART)
			return tryPlantNetherWart(plantHand);
		
		// Check if player is looking at farmland
		net.minecraft.util.hit.HitResult hitResult = MC.crosshairTarget;
		if(hitResult.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK)
			return false;
		
		BlockPos pos =
			((net.minecraft.util.hit.BlockHitResult)hitResult).getBlockPos();
		Block block = BlockUtils.getBlock(pos);
		
		// Must be looking at farmland
		if(!(block instanceof FarmlandBlock))
			return false;
		
		// Check the block above the farmland
		BlockPos plantPos = pos.up();
		BlockState stateAbove = BlockUtils.getState(plantPos);
		
		// The block above must be replaceable (air, etc.)
		if(!stateAbove.isReplaceable())
			return false;
		
		// Get block placing parameters
		BlockPlacingParams params = BlockPlacer.getBlockPlacingParams(plantPos);
		if(params == null)
			return false;
		
		// Must be within range
		if(params.distanceSq() > range.getValueSq())
			return false;
		
		// Face the position
		WURST.getRotationFaker().faceVectorPacket(params.hitVec());
		
		// Plant the crop
		ActionResult result = MC.interactionManager.interactBlock(MC.player,
			plantHand, params.toHitResult());
		
		// Swing hand animation if successful
		if(result instanceof ActionResult.Success success
			&& success.swingSource() == ActionResult.SwingSource.CLIENT)
			SwingHand.SERVER.swing(plantHand);
		
		// Set cooldown
		MC.itemUseCooldown = 4;
		return true;
	}
	
	/**
	 * Special case for planting nether wart on soul sand
	 */
	private boolean tryPlantNetherWart(Hand plantHand)
	{
		// Check if player is looking at soul sand
		net.minecraft.util.hit.HitResult hitResult = MC.crosshairTarget;
		if(hitResult.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK)
			return false;
		
		BlockPos pos =
			((net.minecraft.util.hit.BlockHitResult)hitResult).getBlockPos();
		Block block = BlockUtils.getBlock(pos);
		
		// Must be looking at soul sand
		if(!(block instanceof SoulSandBlock))
			return false;
		
		// Check the block above the soul sand
		BlockPos plantPos = pos.up();
		BlockState stateAbove = BlockUtils.getState(plantPos);
		
		// The block above must be replaceable (air, etc.)
		if(!stateAbove.isReplaceable())
			return false;
		
		// Get block placing parameters
		BlockPlacingParams params = BlockPlacer.getBlockPlacingParams(plantPos);
		if(params == null)
			return false;
		
		// Must be within range
		if(params.distanceSq() > range.getValueSq())
			return false;
		
		// Face the position
		WURST.getRotationFaker().faceVectorPacket(params.hitVec());
		
		// Plant the nether wart
		ActionResult result = MC.interactionManager.interactBlock(MC.player,
			plantHand, params.toHitResult());
		
		// Swing hand animation if successful
		if(result instanceof ActionResult.Success success
			&& success.swingSource() == ActionResult.SwingSource.CLIENT)
			SwingHand.SERVER.swing(plantHand);
		
		// Set cooldown
		MC.itemUseCooldown = 4;
		return true;
	}
	
	private void harvest(Stream<BlockPos> stream)
	{
		// Break all blocks in creative mode
		if(MC.player.getAbilities().creativeMode)
		{
			MC.interactionManager.cancelBlockBreaking();
			overlay.resetProgress();
			
			ArrayList<BlockPos> blocks = cache.filterOutRecentBlocks(stream);
			if(blocks.isEmpty())
				return;
			
			currentlyHarvesting = blocks.get(0);
			BlockBreaker.breakBlocksWithPacketSpam(blocks);
			return;
		}
		
		// Break the first valid block in survival mode
		currentlyHarvesting =
			stream.filter(BlockBreaker::breakOneBlock).findFirst().orElse(null);
		
		if(currentlyHarvesting == null)
		{
			MC.interactionManager.cancelBlockBreaking();
			overlay.resetProgress();
			return;
		}
		
		overlay.updateProgress();
	}
}
