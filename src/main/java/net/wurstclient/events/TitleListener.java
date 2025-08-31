package net.wurstclient.events;

import com.google.gson.internal.reflect.ReflectionHelper;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class TitleListener implements ClientModInitializer {
	
	@Override
	public void onInitializeClient() {
		System.out.println("Title Listener Mod initialized!");
	}
	
	// This method will be called by our mixin when a title is received
	public static void onTitleReceived(Text title) {
		if (title != null) {
			String titleText = title.getString();
			System.out.println("[TITLE] " + titleText);
			
			// Example: React to specific titles
			if (titleText.toLowerCase().contains("victory")) {
				sendClientMessage("Congratulations on your victory!", Formatting.GOLD);
			} else if (titleText.toLowerCase().contains("death")) {
				sendClientMessage("Better luck next time!", Formatting.RED);
			}
			
			// You can add your custom logic here
			handleTitle(titleText);
		}
	}
	
	// This method will be called by our mixin when a subtitle is received
	public static void onSubtitleReceived(Text subtitle) {
		if (subtitle != null) {
			String subtitleText = subtitle.getString();
			System.out.println("[SUBTITLE] " + subtitleText);
			
			// Example: React to specific subtitles
			if (subtitleText.toLowerCase().contains("level up")) {
				sendClientMessage("Nice level up!", Formatting.GREEN);
			}
			
			// You can add your custom logic here
			handleSubtitle(subtitleText);
		}
	}

	
	// This method will be called by our mixin when title times are set
	public static void onTitleTimesReceived(int fadeIn, int stay, int fadeOut) {
		System.out.println("[TITLE TIMES] FadeIn: " + fadeIn + ", Stay: " + stay + ", FadeOut: " + fadeOut);
		
		// You can react to timing changes here if needed
		handleTitleTimes(fadeIn, stay, fadeOut);
	}
	
	// Custom handler for title messages
	private static void handleTitle(String titleText) {
		// Add your custom title handling logic here
		// Examples:
		// - Log to file
		// - Send to external API
		// - Trigger other mod features
		// - Play sounds
		// - etc.
	}
	
	// Custom handler for subtitle messages
	private static void handleSubtitle(String subtitleText) {
		// Add your custom subtitle handling logic here
	}
	
	// Custom handler for title timing
	private static void handleTitleTimes(int fadeIn, int stay, int fadeOut) {
		// Add your custom timing handling logic here
	}
	
	// Utility method to send a message to the player
	private static void sendClientMessage(String message, Formatting color) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			client.player.sendMessage(
					Text.literal("[Title Listener] " + message).formatted(color),
					false
			);
		}
	}
}
