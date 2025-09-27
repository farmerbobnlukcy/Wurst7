/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.wurstclient.WurstClient;

@Mixin(AnvilBlock.class)
public abstract class AnvilBlockMixin
{
	/**
	 * Makes all anvil variants (regular, slightly damaged, very damaged)
	 * appear as regular anvils when X-Ray is active.
	 */
	@Inject(at = @At("HEAD"), method = "getOutlineShape", cancellable = true)
	private void onGetOutlineShape(BlockState state, BlockView world,
		BlockPos pos, net.minecraft.block.ShapeContext context,
		CallbackInfoReturnable<net.minecraft.util.shape.VoxelShape> cir)
	{
		// Only do this when X-Ray is active
		if(!WurstClient.INSTANCE.getHax().xRayHack.isEnabled())
			return;
		
		// Return the regular anvil shape for all anvil types
		cir.setReturnValue(
			Blocks.ANVIL.getOutlineShape(state, world, pos, context));
	}
}
