/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.commands;

import net.wurstclient.command.CmdError;
import net.wurstclient.command.CmdException;
import net.wurstclient.command.CmdSyntaxError;
import net.wurstclient.command.Command;
import net.wurstclient.hacks.ItemEspHack;
import net.wurstclient.util.ChatUtils;

public final class ItemEspReloadCmd extends Command
{
	public ItemEspReloadCmd()
	{
		super("itemespreload",
			"Reloads the ItemESP filter and priority lists from files.",
			".itemespreload");
	}
	
	@Override
	public void call(String[] args) throws CmdException
	{
		if(args.length > 0)
			throw new CmdSyntaxError();
		
		ItemEspHack itemEsp = WURST.getHax().itemEspHack;
		
		if(!itemEsp.isEnabled())
			throw new CmdError("ItemESP is not enabled.");
		
		try
		{
			itemEsp.reloadLists();
			
			int filteredCount = itemEsp.getFilteredItemsCount();
			int priorityCount = itemEsp.getPriorityItemsCount();
			
			ChatUtils.message("ItemESP lists reloaded successfully.");
			ChatUtils.message("Filtered items: " + filteredCount);
			ChatUtils.message("Priority items: " + priorityCount);
			
		}catch(Exception e)
		{
			throw new CmdError("Failed to reload lists: " + e.getMessage());
		}
	}
}
