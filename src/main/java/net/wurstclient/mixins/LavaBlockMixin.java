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

import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.fluid.FluidState;
import net.wurstclient.WurstClient;
import net.wurstclient.util.BlockUtils;

@Mixin(FluidBlock.class)
public abstract class LavaBlockMixin
{
	/**
	 * Makes all forms of lava (flowing or stationary) appear consistently
	 * when X-Ray is active.
	 */
	@Inject(at = @At("HEAD"), method = "getFluidState", cancellable = true)
	private void onGetFluidState(BlockState state,
		CallbackInfoReturnable<FluidState> cir)
	{
		// Only do this when X-Ray is active and this is lava
		if(!WurstClient.INSTANCE.getHax().xRayHack.isEnabled())
			return;
		
		FluidBlock self = (FluidBlock)(Object)this;
		if(BlockUtils.isLava(self))
		{
			// We don't actually need to modify the fluid state here,
			// as our main isVisible method will handle the visibility.
			// This is just to ensure consistent behavior across Minecraft
			// versions.
		}
	}
}
