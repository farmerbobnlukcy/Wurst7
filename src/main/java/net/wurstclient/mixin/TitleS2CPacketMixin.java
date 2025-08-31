package net.wurstclient.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.wurstclient.events.TitleListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class TitleS2CPacketMixin {
	
	@Inject(method = "onTitle", at = @At("HEAD"))
	private void onTitlePacket(TitleS2CPacket packet, CallbackInfo ci) {
		// Handle different types of title packets
		switch (packet.getAction()) {
			case SET_TITLE:
				if (packet.getTitle() != null) {
					TitleListener.onTitleReceived(packet.getTitle());
				}
				break;
			case SET_SUBTITLE:
				if (packet.getSubtitle() != null) {
					TitleListener.onSubtitleReceived(packet.getSubtitle());
				}
				break;
			case SET_TIMES_AND_DISPLAY:
				TitleListener.onTitleTimesReceived(
						packet.getFadeInTicks(),
						packet.getStayTicks(),
						packet.getFadeOutTicks()
				);
				break;
			case CLEAR:
				System.out.println("[TITLE] Title cleared");
				break;
			case RESET:
				System.out.println("[TITLE] Title reset");
				break;
		}
	}
	}