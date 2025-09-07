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
import java.util.HashMap;
import java.util.stream.Collectors;

import net.minecraft.block.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
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
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockPlacer.BlockPlacingParams;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.InteractionSimulator;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RotationUtils;

@SearchTags({"auto plant", "planter", "seed planter", "auto farmer"})
public final class AutoPlantHack extends Hack
	implements UpdateListener, RenderListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"How far AutoPlant will reach to plant seeds.", 4.5, 1, 6, 0.05,
		ValueDisplay.DECIMAL);
	
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"\u00a7lFast\u00a7r mode can plant multiple seeds at once.\n"
			+ "\u00a7lLegit\u00a7r mode can bypass NoCheat+.",
		Mode.values(), Mode.FAST);
	
	private final EnumSetting<AutomationLevel> automationLevel =
		new EnumSetting<>("Automation",
			"How much of the planting process to automate.\n"
				+ "\u00a7lRight Click\u00a7r simply right clicks blocks with the item in your hand.\n"
				+ "\u00a7lHotbar\u00a7r selects plantable items in your hotbar and then uses them.\n"
				+ "\u00a7lInventory\u00a7r finds plantable items in your inventory, moves them to your hotbar and then uses them.",
			AutomationLevel.values(), AutomationLevel.RIGHT_CLICK);
	
	private final CheckboxSetting saplings =
		new CheckboxSetting("Saplings", true);
	private final CheckboxSetting crops = new CheckboxSetting("Crops",
		"Wheat seeds, carrots, potatoes and beetroots.", true);
	private final CheckboxSetting stems =
		new CheckboxSetting("Stems", "Pumpkin and melon seeds.", true);
	private final CheckboxSetting netherWart =
		new CheckboxSetting("Nether Wart", true);
	private final CheckboxSetting other = new CheckboxSetting("Other",
		"Other plantable items not covered by the above categories.", false);
	
	private final HashMap<Item, Block> plantableSurfaces = new HashMap<>();
	private final ArrayList<BlockPos> highlightedBlocks = new ArrayList<>();
	
	public AutoPlantHack()
	{
		super("AutoPlant");
		setCategory(Category.BLOCKS);
		
		addSetting(range);
		addSetting(mode);
		addSetting(automationLevel);
		addSetting(saplings);
		addSetting(crops);
		addSetting(stems);
		addSetting(netherWart);
		addSetting(other);
		
		// Initialize the mapping of items to surfaces they can be planted on
		initPlantableSurfaces();
	}
	
	private void initPlantableSurfaces()
	{
		// Seeds on farmland
		plantableSurfaces.put(Items.WHEAT_SEEDS, Blocks.FARMLAND);
		plantableSurfaces.put(Items.BEETROOT_SEEDS, Blocks.FARMLAND);
		plantableSurfaces.put(Items.POTATO, Blocks.FARMLAND);
		plantableSurfaces.put(Items.CARROT, Blocks.FARMLAND);
		plantableSurfaces.put(Items.MELON_SEEDS, Blocks.FARMLAND);
		plantableSurfaces.put(Items.PUMPKIN_SEEDS, Blocks.FARMLAND);
		
		// Saplings on dirt/grass
		plantableSurfaces.put(Items.OAK_SAPLING, Blocks.DIRT);
		plantableSurfaces.put(Items.SPRUCE_SAPLING, Blocks.DIRT);
		plantableSurfaces.put(Items.BIRCH_SAPLING, Blocks.DIRT);
		plantableSurfaces.put(Items.JUNGLE_SAPLING, Blocks.DIRT);
		plantableSurfaces.put(Items.ACACIA_SAPLING, Blocks.DIRT);
		plantableSurfaces.put(Items.DARK_OAK_SAPLING, Blocks.DIRT);
		plantableSurfaces.put(Items.CHERRY_SAPLING, Blocks.DIRT);
		
		// Nether wart on soul sand
		plantableSurfaces.put(Items.NETHER_WART, Blocks.SOUL_SAND);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		highlightedBlocks.clear();
	}
	
	@Override
	public void onUpdate()
	{
		// Wait for right click timer
		if(MC.itemUseCooldown > 0)
			return;
		
		if(MC.interactionManager.isBreakingBlock() || MC.player.isRiding())
			return;
		
		// Get valid blocks to plant on
		ArrayList<BlockPos> validBlocks = getValidBlocks();
		
		// Update the highlighted blocks for rendering
		highlightedBlocks.clear();
		highlightedBlocks.addAll(validBlocks);
		
		if(validBlocks.isEmpty())
			return;
		
		// Wait for AutoFarm
		if(WURST.getHax().autoFarmHack.isBusy())
			return;
		
		// If not holding a plantable item
		if(!isHoldingPlantableItem())
		{
			// Try to select one from inventory based on automation level
			if(automationLevel.getSelected() != AutomationLevel.RIGHT_CLICK)
				selectPlantableItem();
			
			return;
		}
		
		// Get the currently held item
		ItemStack heldItem = MC.player.getMainHandStack();
		if(heldItem.isEmpty())
			heldItem = MC.player.getOffHandStack();
		
		if(heldItem.isEmpty())
			return;
		
		Item item = heldItem.getItem();
		
		// Plant according to selected mode
		if(mode.getSelected() == Mode.LEGIT)
		{
			// Legit mode
			for(BlockPos pos : validBlocks)
				if(canBePlantedOn(pos, item) && rightClickBlockLegit(pos))
					break;
		}else
		{
			// Fast mode
			boolean shouldSwing = false;
			
			for(BlockPos pos : validBlocks)
				if(canBePlantedOn(pos, item) && rightClickBlockSimple(pos))
					shouldSwing = true;
				
			// Swing arm
			if(shouldSwing)
				MC.player.swingHand(Hand.MAIN_HAND);
		}
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		// Render highlights for valid planting blocks
		for(BlockPos pos : highlightedBlocks)
		{
			RenderUtils.drawOutlinedBox(matrixStack,
				BlockUtils.getBoundingBox(pos), 0x7700FF00, true);
		}
	}
	
	private ArrayList<BlockPos> getValidBlocks()
	{
		Vec3d eyesVec = RotationUtils.getEyesPos();
		BlockPos eyesBlock = BlockPos.ofFloored(eyesVec);
		double rangeSq = range.getValueSq();
		int blockRange = range.getValueCeil();
		
		// As plants are placed, they will occupy blocks and prevent planting in
		// the same spot
		// Sort by closest so we plant close to us first
		Comparator<BlockPos> closestFirst =
			Comparator.comparingDouble(pos -> pos.getSquaredDistance(eyesVec));
		
		Item heldItem = null;
		if(MC.player.getMainHandStack().getItem() instanceof BlockItem
			|| MC.player.getMainHandStack().getItem() == Items.WHEAT_SEEDS
			|| MC.player.getMainHandStack().getItem() == Items.BEETROOT_SEEDS
			|| MC.player.getMainHandStack().getItem() == Items.MELON_SEEDS
			|| MC.player.getMainHandStack().getItem() == Items.PUMPKIN_SEEDS
			|| MC.player.getMainHandStack().getItem() == Items.NETHER_WART)
		{
			heldItem = MC.player.getMainHandStack().getItem();
		}else if(MC.player.getOffHandStack().getItem() instanceof BlockItem
			|| MC.player.getOffHandStack().getItem() == Items.WHEAT_SEEDS
			|| MC.player.getOffHandStack().getItem() == Items.BEETROOT_SEEDS
			|| MC.player.getOffHandStack().getItem() == Items.MELON_SEEDS
			|| MC.player.getOffHandStack().getItem() == Items.PUMPKIN_SEEDS
			|| MC.player.getOffHandStack().getItem() == Items.NETHER_WART)
		{
			heldItem = MC.player.getOffHandStack().getItem();
		}
		
		final Item finalHeldItem = heldItem;
		
		return BlockUtils.getAllInBoxStream(eyesBlock, blockRange)
			.filter(pos -> pos.getSquaredDistance(eyesVec) <= rangeSq)
			.filter(pos -> BlockUtils.getState(pos).isReplaceable())
			.filter(pos -> finalHeldItem == null
				|| canBePlantedOn(pos, finalHeldItem))
			.sorted(closestFirst)
			.collect(Collectors.toCollection(ArrayList::new));
	}
	
	private boolean isHoldingPlantableItem()
	{
		return isPlantableItem(MC.player.getMainHandStack().getItem())
			|| isPlantableItem(MC.player.getOffHandStack().getItem());
	}
	
	private boolean isPlantableItem(Item item)
	{
		// Check if the item is a sapling modified this tojust check if its a
		// sapling
		if(item.getName().toString().toLowerCase().contains("sapling"))
		{
			return saplings.isChecked();
		}
		
		// Check if the item is a crop seed
		if(item == Items.WHEAT_SEEDS || item == Items.BEETROOT_SEEDS
			|| item == Items.POTATO || item == Items.CARROT)
		{
			return crops.isChecked();
		}
		
		// Check if the item is a stem seed
		if(item == Items.PUMPKIN_SEEDS || item == Items.MELON_SEEDS)
		{
			return stems.isChecked();
		}
		
		// Check if the item is nether wart
		if(item == Items.NETHER_WART)
		{
			return netherWart.isChecked();
		}
		
		// Check for other plantable items
		if(item instanceof BlockItem blockItem
			&& (blockItem.getBlock() instanceof net.minecraft.block.PlantBlock
				|| blockItem.getBlock().getDefaultState()
					.isIn(BlockTags.FLOWERS)))
		{
			return other.isChecked();
		}
		
		return false;
	}
	
	private void selectPlantableItem()
	{
		int maxSlot =
			automationLevel.getSelected() == AutomationLevel.HOTBAR ? 9 : 36;
		
		// First check if we're holding a plantable item
		if(isPlantableItem(MC.player.getMainHandStack().getItem())
			|| isPlantableItem(MC.player.getOffHandStack().getItem()))
		{
			return;
		}
		
		// Try to find a plantable item in inventory
		for(int slot = 0; slot < maxSlot; slot++)
		{
			Item item = MC.player.getInventory().getStack(slot).getItem();
			if(isPlantableItem(item))
			{
				InventoryUtils.selectItem(item);
				return;
			}
		}
	}
	
	private boolean canBePlantedOn(BlockPos pos, Item item)
	{
		Block blockBelow = BlockUtils.getBlock(pos.down());
		
		// For saplings
		if(item instanceof BlockItem blockItem && BlockTags.SAPLINGS.toString()
			.contains(blockItem.getBlock().toString()) && saplings.isChecked())
		{
			return blockBelow == Blocks.DIRT || blockBelow == Blocks.GRASS_BLOCK
				|| blockBelow == Blocks.PODZOL;
		}
		
		// For crops
		if((item == Items.WHEAT_SEEDS || item == Items.BEETROOT_SEEDS
			|| item == Items.POTATO || item == Items.CARROT)
			&& crops.isChecked())
		{
			return blockBelow instanceof FarmlandBlock;
		}
		
		// For stems
		if((item == Items.PUMPKIN_SEEDS || item == Items.MELON_SEEDS)
			&& stems.isChecked())
		{
			return blockBelow instanceof FarmlandBlock;
		}
		
		// For nether wart
		if(item == Items.NETHER_WART && netherWart.isChecked())
		{
			return blockBelow instanceof SoulSandBlock;
		}
		
		// For other plantable blocks
		if(other.isChecked() && item instanceof BlockItem)
		{
			Block itemBlock = ((BlockItem)item).getBlock();
			
			// Check if it's a flower or plant
			if(itemBlock instanceof net.minecraft.block.PlantBlock
				|| itemBlock.getDefaultState().isIn(BlockTags.FLOWERS))
			{
				return blockBelow == Blocks.DIRT
					|| blockBelow == Blocks.GRASS_BLOCK
					|| blockBelow == Blocks.PODZOL;
			}
		}
		
		return false;
	}
	
	private boolean rightClickBlockLegit(BlockPos pos)
	{
		// If breaking or riding, stop and don't try other blocks
		if(MC.interactionManager.isBreakingBlock() || MC.player.isRiding())
			return true;
		
		// If this block is unreachable, try the next one
		BlockPlacingParams params = BlockPlacer.getBlockPlacingParams(pos);
		if(params == null || params.distanceSq() > range.getValueSq()
			|| !params.lineOfSight())
			return false;
		
		// Face and right click the block
		MC.itemUseCooldown = 4;
		WURST.getRotationFaker().faceVectorPacket(params.hitVec());
		
		Hand hand = MC.player.getMainHandStack().isEmpty() ? Hand.OFF_HAND
			: Hand.MAIN_HAND;
		
		ActionResult result = MC.interactionManager.interactBlock(MC.player,
			hand, params.toHitResult());
		
		// Swing hand
		if(result instanceof ActionResult.Success)
			SwingHand.SERVER.swing(hand);
		
		return true;
	}
	
	private boolean rightClickBlockSimple(BlockPos pos)
	{
		// If this block is unreachable, try the next one
		BlockPlacingParams params = BlockPlacer.getBlockPlacingParams(pos);
		if(params == null)
			return false;
		
		// Right click the block
		Hand hand = MC.player.getMainHandStack().isEmpty() ? Hand.OFF_HAND
			: Hand.MAIN_HAND;
		InteractionSimulator.rightClickBlock(params.toHitResult(),
			SwingHand.OFF);
		return true;
	}
	
	private enum Mode
	{
		FAST("Fast"),
		LEGIT("Legit");
		
		private final String name;
		
		private Mode(String name)
		{
			this.name = name;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
	
	private enum AutomationLevel
	{
		RIGHT_CLICK("Right Click", 0),
		HOTBAR("Hotbar", 9),
		INVENTORY("Inventory", 36);
		
		private final String name;
		private final int maxInvSlot;
		
		private AutomationLevel(String name, int maxInvSlot)
		{
			this.name = name;
			this.maxInvSlot = maxInvSlot;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
}
