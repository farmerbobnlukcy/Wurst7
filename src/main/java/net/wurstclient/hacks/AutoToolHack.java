/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry.Reference;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstClient;
import net.wurstclient.events.BlockBreakingProgressListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IClientPlayerInteractionManager;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.InventoryUtils;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;

@SearchTags({"tool", "AutoSwitch", "auto switch", "auto tool"})
public final class AutoToolHack extends Hack
	implements BlockBreakingProgressListener, UpdateListener
{
	private final CheckboxSetting useSwords = new CheckboxSetting("Use swords",
		"Uses swords to break leaves, cobwebs, etc.", false);
	
	private final CheckboxSetting useHands = new CheckboxSetting("Use hands",
		"Uses an empty hand or a non-damageable item when no applicable tool is"
			+ " found.",
		true);
	
	private final SliderSetting repairMode = new SliderSetting("Repair mode",
		"Prevents tools from being used when their durability reaches the given"
			+ " threshold, so you can repair them before they break.\n"
			+ "Can be adjusted from 0 (off) to 100 remaining uses.",
		0, 0, 100, 1, ValueDisplay.INTEGER.withLabel(0, "off"));
	
	private final CheckboxSetting switchBack = new CheckboxSetting(
		"Switch back", "After using a tool, automatically switches back to the"
			+ " previously selected slot.",
		false);
	
	private final CheckboxSetting silkTouchForSpecialBlocks =
		new CheckboxSetting("Silk Touch for Special",
			"Always uses silk touch tools for special blocks like enchantment tables, "
				+ "beacons, ender chests, etc.",
			true);
	
	private final CheckboxSetting silkTouchForOres = new CheckboxSetting(
		"Silk Touch for Ores",
		"Always uses silk touch tools for valuable ores like diamond, emerald, etc.",
		true);
	
	private int prevSelectedSlot;
	
	// Blocks that should always be harvested with silk touch when possible
	private static final Block[] SPECIAL_SILK_TOUCH_BLOCKS =
		{Blocks.ENCHANTING_TABLE, Blocks.ENDER_CHEST, Blocks.BEACON,
			Blocks.SCULK_SHRIEKER, Blocks.SCULK_SENSOR, Blocks.SCULK_CATALYST};
	
	// Ores that should be harvested with silk touch when the setting is enabled
	private static final Block[] SILK_TOUCH_ORES =
		{Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.EMERALD_ORE,
			Blocks.DEEPSLATE_EMERALD_ORE, Blocks.LAPIS_ORE,
			Blocks.DEEPSLATE_LAPIS_ORE, Blocks.REDSTONE_ORE,
			Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.COAL_ORE,
			Blocks.DEEPSLATE_COAL_ORE};
	
	public AutoToolHack()
	{
		super("AutoTool");
		
		setCategory(Category.BLOCKS);
		addSetting(useSwords);
		addSetting(useHands);
		addSetting(repairMode);
		addSetting(switchBack);
		addSetting(silkTouchForSpecialBlocks);
		addSetting(silkTouchForOres);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(BlockBreakingProgressListener.class, this);
		EVENTS.add(UpdateListener.class, this);
		prevSelectedSlot = -1;
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(BlockBreakingProgressListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
	}
	
	@Override
	public void onBlockBreakingProgress(BlockBreakingProgressEvent event)
	{
		BlockPos pos = event.getBlockPos();
		if(!BlockUtils.canBeClicked(pos))
			return;
		
		if(prevSelectedSlot == -1)
			prevSelectedSlot = MC.player.getInventory().selectedSlot;
		
		equipBestTool(pos, useSwords.isChecked(), useHands.isChecked(),
			repairMode.getValueI());
	}
	
	@Override
	public void onUpdate()
	{
		if(prevSelectedSlot == -1 || MC.interactionManager.isBreakingBlock())
			return;
		
		HitResult hitResult = MC.crosshairTarget;
		if(hitResult != null && hitResult.getType() == HitResult.Type.BLOCK)
			return;
		
		if(switchBack.isChecked())
			MC.player.getInventory().selectedSlot = prevSelectedSlot;
		
		prevSelectedSlot = -1;
	}
	
	public void equipIfEnabled(BlockPos pos)
	{
		if(!isEnabled())
			return;
		
		equipBestTool(pos, useSwords.isChecked(), useHands.isChecked(),
			repairMode.getValueI());
	}
	
	public void equipBestTool(BlockPos pos, boolean useSwords, boolean useHands,
		int repairMode)
	{
		ClientPlayerEntity player = MC.player;
		if(player.getAbilities().creativeMode)
			return;
		
		ItemStack heldItem = player.getMainHandStack();
		boolean heldItemDamageable = isDamageable(heldItem);
		if(heldItemDamageable && isTooDamaged(heldItem, repairMode))
			putAwayDamagedTool(repairMode);
		
		BlockState state = BlockUtils.getState(pos);
		int bestSlot = getBestSlot(state, useSwords, repairMode);
		if(bestSlot == -1)
		{
			if(useHands && heldItemDamageable && isWrongTool(heldItem, state))
				selectFallbackSlot();
			
			return;
		}
		
		player.getInventory().selectedSlot = bestSlot;
	}
	
	private int getBestSlot(BlockState state, boolean useSwords, int repairMode)
	{
		ClientPlayerEntity player = MC.player;
		PlayerInventory inventory = player.getInventory();
		ItemStack heldItem = MC.player.getMainHandStack();
		Block block = state.getBlock();
		
		// Check if this block requires silk touch
		boolean needsSilkTouch =
			requiresSilkTouch(block) || isOreForSilkTouch(block);
		
		// Check if the held item has silk touch
		boolean heldItemHasSilkTouch = hasSilkTouch(heldItem);
		
		float bestSpeed = getMiningSpeed(heldItem, state);
		if(isTooDamaged(heldItem, repairMode))
			bestSpeed = 1;
		// If we need silk touch but held item doesn't have it, consider it
		// ineffective
		if(needsSilkTouch && !heldItemHasSilkTouch)
			bestSpeed = 1;
		
		int bestSlot = -1;
		
		// First scan for a tool with silk touch if needed
		if(needsSilkTouch)
		{
			for(int slot = 0; slot < 9; slot++)
			{
				if(slot == inventory.selectedSlot)
					continue;
				
				ItemStack stack = inventory.getStack(slot);
				
				// Skip if not silk touch
				if(!hasSilkTouch(stack))
					continue;
				
				float speed = getMiningSpeed(stack, state);
				if(speed <= bestSpeed)
					continue;
				
				if(!useSwords && stack.getItem() instanceof SwordItem)
					continue;
				
				if(isTooDamaged(stack, repairMode))
					continue;
				
				bestSpeed = speed;
				bestSlot = slot;
			}
			
			// If we found a silk touch tool, return it
			if(bestSlot != -1)
				return bestSlot;
		}
		
		// If we didn't need silk touch or couldn't find a silk touch tool,
		// proceed with normal logic
		for(int slot = 0; slot < 9; slot++)
		{
			if(slot == inventory.selectedSlot)
				continue;
			
			ItemStack stack = inventory.getStack(slot);
			
			float speed = getMiningSpeed(stack, state);
			if(speed <= bestSpeed)
				continue;
			
			if(!useSwords && stack.getItem() instanceof SwordItem)
				continue;
			
			if(isTooDamaged(stack, repairMode))
				continue;
			
			// If block needs silk touch, only consider tools with silk touch
			if(needsSilkTouch && !hasSilkTouch(stack))
				continue;
			
			bestSpeed = speed;
			bestSlot = slot;
		}
		
		return bestSlot;
	}
	
	private float getMiningSpeed(ItemStack stack, BlockState state)
	{
		float speed = stack.getMiningSpeedMultiplier(state);
		
		if(speed > 1)
		{
			DynamicRegistryManager drm =
				WurstClient.MC.world.getRegistryManager();
			Registry<Enchantment> registry =
				drm.getOrThrow(RegistryKeys.ENCHANTMENT);
			
			Optional<Reference<Enchantment>> efficiency =
				registry.getOptional(Enchantments.EFFICIENCY);
			int effLvl = efficiency
				.map(entry -> EnchantmentHelper.getLevel(entry, stack))
				.orElse(0);
			
			if(effLvl > 0 && !stack.isEmpty())
				speed += effLvl * effLvl + 1;
		}
		
		return speed;
	}
	
	private boolean isDamageable(ItemStack stack)
	{
		return !stack.isEmpty() && stack.isDamageable();
	}
	
	private boolean isTooDamaged(ItemStack stack, int repairMode)
	{
		return stack.getMaxDamage() - stack.getDamage() <= repairMode;
	}
	
	private void putAwayDamagedTool(int repairMode)
	{
		PlayerInventory inv = MC.player.getInventory();
		int selectedSlot = inv.selectedSlot;
		IClientPlayerInteractionManager im = IMC.getInteractionManager();
		
		// If there's an empty slot in the main inventory,
		// shift-click the damaged item out of the hotbar
		OptionalInt emptySlot = IntStream.range(9, 36)
			.filter(i -> !inv.getStack(i).isEmpty()).findFirst();
		if(emptySlot.isPresent())
		{
			im.windowClick_QUICK_MOVE(
				InventoryUtils.toNetworkSlot(selectedSlot));
			return;
		}
		
		// Failing that, swap with a non-damageable item
		OptionalInt nonDamageableSlot = IntStream.range(9, 36)
			.filter(i -> !isDamageable(inv.getStack(i))).findFirst();
		if(nonDamageableSlot.isPresent())
		{
			im.windowClick_SWAP(nonDamageableSlot.getAsInt(), selectedSlot);
			return;
		}
		
		// Failing that, swap with a less damaged item
		OptionalInt notTooDamagedSlot = IntStream.range(9, 36)
			.filter(i -> !isTooDamaged(inv.getStack(i), repairMode))
			.findFirst();
		if(notTooDamagedSlot.isPresent())
		{
			im.windowClick_SWAP(notTooDamagedSlot.getAsInt(), selectedSlot);
			return;
		}
		
		// Failing all of the above (whole inventory full of damaged tools),
		// just swap with the top-left slot
		im.windowClick_SWAP(0, selectedSlot);
	}
	
	private boolean isWrongTool(ItemStack heldItem, BlockState state)
	{
		return getMiningSpeed(heldItem, state) <= 1;
	}
	
	private void selectFallbackSlot()
	{
		int fallbackSlot = getFallbackSlot();
		PlayerInventory inventory = MC.player.getInventory();
		
		if(fallbackSlot == -1)
		{
			if(inventory.selectedSlot == 8)
				inventory.selectedSlot = 0;
			else
				inventory.selectedSlot++;
			
			return;
		}
		
		inventory.selectedSlot = fallbackSlot;
	}
	
	private int getFallbackSlot()
	{
		PlayerInventory inventory = MC.player.getInventory();
		
		for(int slot = 0; slot < 9; slot++)
		{
			if(slot == inventory.selectedSlot)
				continue;
			
			ItemStack stack = inventory.getStack(slot);
			
			if(!isDamageable(stack))
				return slot;
		}
		
		return -1;
	}
	
	/**
	 * Checks if a block should always be harvested with silk touch
	 */
	private boolean requiresSilkTouch(Block block)
	{
		if(!silkTouchForSpecialBlocks.isChecked())
			return false;
		
		for(Block specialBlock : SPECIAL_SILK_TOUCH_BLOCKS)
			if(block == specialBlock)
				return true;
			
		return false;
	}
	
	/**
	 * Checks if an ore block should be harvested with silk touch
	 */
	private boolean isOreForSilkTouch(Block block)
	{
		if(!silkTouchForOres.isChecked())
			return false;
		
		for(Block oreBlock : SILK_TOUCH_ORES)
			if(block == oreBlock)
				return true;
			
		return false;
	}
	
	/**
	 * Checks if a tool has the silk touch enchantment
	 */
	private boolean hasSilkTouch(ItemStack stack)
	{
		if(stack.isEmpty())
			return false;
		
		DynamicRegistryManager drm = WurstClient.MC.world.getRegistryManager();
		Registry<Enchantment> registry =
			drm.getOrThrow(RegistryKeys.ENCHANTMENT);
		
		Optional<Reference<Enchantment>> silkTouch =
			registry.getOptional(Enchantments.SILK_TOUCH);
		
		return silkTouch
			.map(entry -> EnchantmentHelper.getLevel(entry, stack) > 0)
			.orElse(false);
	}
}
