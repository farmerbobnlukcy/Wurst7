/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import net.wurstclient.event.TitleEvents;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin
{
	
	@Inject(
		method = "onTitle(Lnet/minecraft/network/packet/s2c/play/TitleS2CPacket;)V",
		at = @At("HEAD"))
	private void yourmod$onTitle(TitleS2CPacket packet, CallbackInfo ci)
	{
		TitleEvents.fire(packet.text(), /* isSubtitle = */ false);
	}
	
	@Inject(
		method = "onSubtitle(Lnet/minecraft/network/packet/s2c/play/SubtitleS2CPacket;)V",
		at = @At("HEAD"))
	private void yourmod$onSubtitle(SubtitleS2CPacket packet, CallbackInfo ci)
	{
		TitleEvents.fire(packet.text(), /* isSubtitle = */ true);
	}
	
	/*
	 * // Optional: when the server clears titles, notify listeners with null
	 *
	 * @Inject(method =
	 * "onClearTitle(Lnet/minecraft/network/packet/s2c/play/ClearTitleS2CPacket;)V",
	 * at = @At("HEAD"))
	 * private void yourmod$onClearTitle(ClearTitleS2CPacket packet,
	 * CallbackInfo ci) {
	 * TitleEvents.fire(null, false);
	 * TitleEvents.fire(null, true);
	 * }
	 */
	// Optional: fade timings are available here if you want them later
	@Inject(
		method = "onTitleFade(Lnet/minecraft/network/packet/s2c/play/TitleFadeS2CPacket;)V",
		at = @At("HEAD"))
	private void yourmod$onTitleFade(TitleFadeS2CPacket packet, CallbackInfo ci)
	{
		// packet.fadeInTicks(), packet.stayTicks(), packet.fadeOutTicks()
		// (No event fired by default—add your own if you need timing)
	}
}
