/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.ChatInputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ChatUtils;

@SearchTags({"position history", "position logger", "teleport logger",
	"location tracker"})
public final class PositionHistoryHack extends Hack
	implements UpdateListener, ChatInputListener
{
	private final SliderSetting chunkThreshold = new SliderSetting(
		"Chunk threshold",
		"Number of chunks that must be crossed in a single tick to trigger logging.",
		3, 1, 10, 1, ValueDisplay.INTEGER);
	
	private final SliderSetting teleportThreshold = new SliderSetting(
		"Teleport threshold",
		"Minimum distance (in blocks) that must be traveled in a single tick to be considered a teleport.",
		16, 4, 100, 1, ValueDisplay.INTEGER);
	
	// Set of commands to monitor
	private final Set<String> monitoredCommands = new HashSet<>();
	
	// Track player's positions and timing
	private Vec3d lastPosition;
	private ChunkPos lastChunkPos;
	private String lastWorldId = "";
	private long lastLogTimestamp = 0;
	private static final long MIN_LOG_INTERVAL_MS = 1000; // Minimum 1 second
															// between logs
	
	// Command tracking
	private boolean commandExecuted = false;
	private String lastCommand = "";
	
	private static final DateTimeFormatter DATE_FORMAT =
		DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	
	public PositionHistoryHack()
	{
		super("PositionHistory");
		setCategory(Category.OTHER);
		addSetting(chunkThreshold);
		addSetting(teleportThreshold);
		
		// Initialize the set of commands to monitor
		monitoredCommands.add("/rs");
		monitoredCommands.add("/rsn");
		monitoredCommands.add("/rtp");
		monitoredCommands.add("/wild");
		monitoredCommands.add("/home");
		monitoredCommands.add("/warp");
	}
	
	@Override
	public void onEnable()
	{
		lastPosition = null;
		lastChunkPos = null;
		lastWorldId = getCurrentWorldId();
		lastLogTimestamp = 0;
		commandExecuted = false;
		
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(ChatInputListener.class, this);
		
		System.out.println("Position history logging started");
		ChatUtils.message("Position history logging started");
	}
	
	@Override
	public void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(ChatInputListener.class, this);
		
		System.out.println("Position history logging stopped");
		ChatUtils.message("Position history logging stopped");
	}
	
	@Override
	public void onReceivedMessage(ChatInputEvent event)
	{
		String message = event.getComponent().getString();
		
		// Check if the message is a command
		if(!message.startsWith("/"))
			return;
		
		// Split by space to get the command
		String[] parts = message.split(" ");
		String command = parts[0].toLowerCase();
		
		// Check if it's a monitored command
		if(monitoredCommands.contains(command))
		{
			commandExecuted = true;
			lastCommand = message;
			
			// Log the command usage immediately
			if(lastPosition != null)
			{
				System.out.println("[" + LocalDateTime.now().format(DATE_FORMAT)
					+ "] Command executed: " + message + " at position "
					+ String.format("(%.2f, %.2f, %.2f)", lastPosition.x,
						lastPosition.y, lastPosition.z));
			}
		}
	}
	
	@Override
	public void onUpdate()
	{
		ClientPlayerEntity player = MC.player;
		if(player == null)
			return;
		
		Vec3d currentPos = player.getPos();
		ChunkPos currentChunkPos = new ChunkPos(player.getBlockPos());
		String currentWorldId = getCurrentWorldId();
		
		// Initialize last position if null
		if(lastPosition == null)
		{
			lastPosition = currentPos;
			lastChunkPos = currentChunkPos;
			lastWorldId = currentWorldId;
			return;
		}
		
		boolean shouldLog = false;
		String reason = "";
		
		// Check if the world changed
		if(!lastWorldId.equals(currentWorldId))
		{
			shouldLog = true;
			reason =
				"world change from " + lastWorldId + " to " + currentWorldId;
		}
		// Check if moved more than the teleport threshold
		else if(currentPos.squaredDistanceTo(lastPosition) > teleportThreshold
			.getValue() * teleportThreshold.getValue())
		{
			shouldLog = true;
			
			// If a command was executed recently, attribute the teleport to it
			if(commandExecuted)
			{
				reason = "teleport via command: " + lastCommand;
				commandExecuted = false; // Reset the flag
			}else
			{
				reason =
					"teleport detected (distance: "
						+ String.format("%.2f",
							Math.sqrt(
								currentPos.squaredDistanceTo(lastPosition)))
						+ " blocks)";
			}
		}
		// Check if crossed multiple chunks in a single tick
		else if(Math.abs(currentChunkPos.x - lastChunkPos.x) >= chunkThreshold
			.getValueI()
			|| Math.abs(currentChunkPos.z - lastChunkPos.z) >= chunkThreshold
				.getValueI())
		{
			shouldLog = true;
			
			// If a command was executed recently, attribute the movement to it
			if(commandExecuted)
			{
				reason = "rapid movement via command: " + lastCommand;
				commandExecuted = false; // Reset the flag
			}else
			{
				reason = "rapid chunk movement ("
					+ Math.abs(currentChunkPos.x - lastChunkPos.x) + ","
					+ Math.abs(currentChunkPos.z - lastChunkPos.z) + " chunks)";
			}
		}
		
		// Log position if necessary and if enough time has passed since last
		// log
		if(shouldLog && (System.currentTimeMillis()
			- lastLogTimestamp >= MIN_LOG_INTERVAL_MS))
		{
			logPositionChange(reason, lastPosition, currentPos);
			lastLogTimestamp = System.currentTimeMillis();
		}
		
		lastPosition = currentPos;
		lastChunkPos = currentChunkPos;
		lastWorldId = currentWorldId;
	}
	
	/**
	 * Gets a string identifier for the current world/dimension
	 */
	private String getCurrentWorldId()
	{
		if(MC.world == null)
			return "unknown";
		
		return MC.world.getDimension().effects().toString();
	}
	
	private void logPositionChange(String reason, Vec3d from, Vec3d to)
	{
		if(MC.player == null)
			return;
		
		// Format current position into chunk coordinates as well
		ChunkPos fromChunk = new ChunkPos((int)from.x >> 4, (int)from.z >> 4);
		ChunkPos toChunk = new ChunkPos((int)to.x >> 4, (int)to.z >> 4);
		
		String entry = String.format(
			"[%s] %s: From (%.2f, %.2f, %.2f) [chunk %d,%d] to (%.2f, %.2f, %.2f) [chunk %d,%d] - %s",
			LocalDateTime.now().format(DATE_FORMAT),
			MC.player.getName().getString(), from.x, from.y, from.z,
			fromChunk.x, fromChunk.z, to.x, to.y, to.z, toChunk.x, toChunk.z,
			reason);
		
		// Log to the standard system log
		System.out.println(entry);
		
		// Notify player in chat
		ChatUtils.message("§8Position logged: §7"
			+ String.format("(%.1f, %.1f, %.1f) → (%.1f, %.1f, %.1f)", from.x,
				from.y, from.z, to.x, to.y, to.z));
	}
}
