/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.ChatInputListener;
import net.wurstclient.hack.Hack;

@SearchTags({"auto bep", "bep", "chat", "auto response"})
public final class AutoBepHack extends Hack implements ChatInputListener
{
	public AutoBepHack()
	{
		super("AutoBep");
		setCategory(Category.CHAT);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(ChatInputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(ChatInputListener.class, this);
	}

	@Override
	public void onReceivedMessage(ChatInputEvent event)
	{
		String message = event.getComponent().getString().toLowerCase();

		// Check if the message contains "bep"
		if(message.contains("bep"))
		{
			// Send "bep" as a response
			MC.player.networkHandler.sendChatMessage("bep");
		}
	}
}
