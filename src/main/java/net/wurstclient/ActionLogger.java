/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ToolItem;
import net.minecraft.util.math.BlockPos;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.LeftClickListener;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.events.RightClickListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.ChatUtils;

@SearchTags({"action logger", "item logger", "block logger"})
public final class ActionLogger extends Hack implements UpdateListener,
		PacketInputListener, PacketOutputListener, RightClickListener, LeftClickListener
{
	private final CheckboxSetting logItemDrops =
			new CheckboxSetting("Log Item Drops", "Logs when important items are dropped.", true);
	
	private final CheckboxSetting logBlockPlacing =
			new CheckboxSetting("Log Block Placing", "Logs when important blocks are placed.", true);
	
	private final CheckboxSetting logBlockBreaking =
			new CheckboxSetting("Log Block Breaking", "Logs when important blocks are broken.", true);
	
	private static final SimpleDateFormat DATE_FORMAT =
			new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	private static final Path LOG_FOLDER = Paths.get("wurst/actionlog");
	private static final Path LOG_FILE = LOG_FOLDER.resolve("actionlog.txt");
	
	// Track player-placed shulker boxes
	private final Map<BlockPos, Long> placedShulkerBoxes = new HashMap<>();
	private final Map<BlockPos, Long> placedPortals = new HashMap<>();
	private final Map<BlockPos, Long> placedEnderChests = new HashMap<>();
	
	private long lastLogTime = 0;
	private int logCooldown = 20; // ticks
	
	public ActionLogger()
	{
		super("ActionLogger");
		setCategory(Category.OTHER);
		
		addSetting(logItemDrops);
		addSetting(logBlockPlacing);
		addSetting(logBlockBreaking);
		
		try
		{
			Files.createDirectories(LOG_FOLDER);
		}catch(IOException e)
		{
			System.out.println("Couldn't create ActionLogger directory: " + e);
		}
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
		EVENTS.add(PacketOutputListener.class, this);
		EVENTS.add(RightClickListener.class, this);
		EVENTS.add(LeftClickListener.class, this);
		
		writeToLog("ActionLogger enabled");
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);
		EVENTS.remove(PacketOutputListener.class, this);
		EVENTS.remove(RightClickListener.class, this);
		EVENTS.remove(LeftClickListener.class, this);
		
		writeToLog("ActionLogger disabled");
	}
	
	@Override
	public void onUpdate()
	{
		// Check for dropped items if enabled
		if(logItemDrops.isChecked() && MC.world != null)
		{
			for(Entity entity : MC.world.getEntities())
			{
				if(entity instanceof ItemEntity itemEntity)
				{
					ItemStack stack = itemEntity.getStack();
					if(isImportantItem(stack))
					{
						// Log if the item was just dropped (entity age is low)
						if(itemEntity.age < 5)
						{
							logItemDrop(itemEntity);
						}
					}
				}
			}
		}
		
		// Cooldown to prevent log spam
		if(lastLogTime > 0)
		{
			lastLogTime--;
		}
	}
	
	@Override
	public void onRightClick(RightClickEvent event)
	{
		if(!logBlockPlacing.isChecked() || MC.world == null || MC.player == null)
			return;
		
		ClientPlayerEntity player = MC.player;
		ItemStack heldItem = player.getMainHandStack();
		
		// Check if player is placing a block
		if(heldItem.getItem() instanceof BlockItem blockItem)
		{
			Block block = blockItem.getBlock();
			BlockPos playerPos = player.getBlockPos();
			
			// Log shulker box placement
			if(block instanceof ShulkerBoxBlock)
			{
				BlockPos placePos = findPlacementPos(playerPos);
				if(placePos != null)
				{
					placedShulkerBoxes.put(placePos, System.currentTimeMillis());
					writeToLog("Player placed a shulker box at " + formatBlockPos(placePos));
				}
			}
			// Log ender chest placement
			else if(block == Blocks.ENDER_CHEST)
			{
				BlockPos placePos = findPlacementPos(playerPos);
				if(placePos != null)
				{
					placedEnderChests.put(placePos, System.currentTimeMillis());
					writeToLog("Player placed an ender chest at " + formatBlockPos(placePos));
				}
			}
			// Log obsidian (potential portal)
			else if(block == Blocks.OBSIDIAN)
			{
				BlockPos placePos = findPlacementPos(playerPos);
				if(placePos != null)
				{
					checkPortalCreation(placePos);
				}
			}
		}
	}
	
	@Override
	public void onLeftClick(LeftClickEvent event)
	{
		if(!logBlockBreaking.isChecked() || MC.world == null || MC.player == null)
			return;
		
		if(MC.crosshairTarget != null)
		{
			BlockPos pos = BlockPos.fromLong(MC.crosshairTarget.getPos().toLong());
			
			// Check if breaking a shulker box that was placed by the player
			if(placedShulkerBoxes.containsKey(pos))
			{
				writeToLog("Player broke a previously placed shulker box at " + formatBlockPos(pos));
				placedShulkerBoxes.remove(pos);
			}
			
			// Check if breaking an ender chest that was placed by the player
			if(placedEnderChests.containsKey(pos))
			{
				writeToLog("Player broke a previously placed ender chest at " + formatBlockPos(pos));
				placedEnderChests.remove(pos);
			}
			
			// Check if breaking a portal that was created by the player
			if(placedPortals.containsKey(pos))
			{
				writeToLog("Player broke a previously created portal at " + formatBlockPos(pos));
				placedPortals.remove(pos);
			}
		}
	}
	
	public void listUnbrokenShulkerBoxes()
	{
		if(placedShulkerBoxes.isEmpty())
		{
			ChatUtils.message("No unbroken shulker boxes found.");
			return;
		}
		
		ChatUtils.message("Unbroken shulker boxes:");
		for(Map.Entry<BlockPos, Long> entry : placedShulkerBoxes.entrySet())
		{
			BlockPos pos = entry.getKey();
			long time = entry.getValue();
			Date date = new Date(time);
			
			ChatUtils.message(formatBlockPos(pos) + " - placed on " + DATE_FORMAT.format(date));
		}
	}
	
	private void logItemDrop(ItemEntity itemEntity)
	{
		if(lastLogTime <= 0)
		{
			ItemStack stack = itemEntity.getStack();
			Item item = stack.getItem();
			String itemName = stack.getName().getString();
			int count = stack.getCount();
			
			String message = "Player dropped: " + count + "x " + itemName;
			
			// Add extra information for enchanted items
			if(stack.hasEnchantments())
			{
				message += " (Enchanted)";
			}
			
			// Log the position
			BlockPos pos = itemEntity.getBlockPos();
			message += " at " + formatBlockPos(pos);
			
			writeToLog(message);
			lastLogTime = logCooldown;
		}
	}
	
	private void checkPortalCreation(BlockPos obsidianPos)
	{
		// Simple check to see if this might be part of a portal
		// (A more sophisticated check would look for portal structures)
		if(MC.world.getBlockState(obsidianPos.up()).getBlock() == Blocks.NETHER_PORTAL)
		{
			writeToLog("Player created a portal at " + formatBlockPos(obsidianPos));
			placedPortals.put(obsidianPos, System.currentTimeMillis());
		}
	}
	
	private BlockPos findPlacementPos(BlockPos playerPos)
	{
		// Simple estimation of where a block might be placed
		// A more sophisticated approach would use raycasting
		for(int x = -2; x <= 2; x++)
			for(int y = -2; y <= 2; y++)
				for(int z = -2; z <= 2; z++)
				{
					BlockPos pos = playerPos.add(x, y, z);
					if(MC.world.getBlockState(pos).isAir())
						return pos;
				}
		
		return null;
	}
	
	private boolean isImportantItem(ItemStack stack)
	{
		Item item = stack.getItem();
		
		// Check for important items as specified in the requirements
		return item instanceof ShulkerBoxBlock ||
				item == Items.SHULKER_SHELL ||
				stack.hasEnchantments() ||
				item == Items.VILLAGER_SPAWN_EGG ||
				item == Items.SPAWNER ||
				item == Items.FIREWORK_ROCKET ||
				item == Items.ENDER_CHEST ||
				item == Items.OBSIDIAN ||
				item instanceof ToolItem ||
				item == Items.ELYTRA ||
				item == Items.DIAMOND ||
				item == Items.EMERALD ||
				item instanceof EnchantedBookItem;
	}
	
	private String formatBlockPos(BlockPos pos)
	{
		return "X:" + pos.getX() + ", Y:" + pos.getY() + ", Z:" + pos.getZ();
	}
	
	private void writeToLog(String message)
	{
		try
		{
			String timestamp = DATE_FORMAT.format(new Date());
			String fullMessage = "[" + timestamp + "] " + message;
			
			Files.write(LOG_FILE,
					(fullMessage + System.lineSeparator()).getBytes(),
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			
		}catch(IOException e)
		{
			System.out.println("Failed to write to ActionLogger log file: " + e);
		}
	}
	
	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		// Handle incoming packets if needed
	}
	
	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		// Handle outgoing packets if needed
	}
	
	// Method to add a button to the GUI to list unbroken shulker boxes
	@Override
	public String getPrimaryAction()
	{
		return "List Unbroken Shulkers";
	}
	
	@Override
	public void doPrimaryAction()
	{
		listUnbrokenShulkerBoxes();
	}
}