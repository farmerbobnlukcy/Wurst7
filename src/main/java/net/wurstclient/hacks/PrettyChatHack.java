/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.text.Text;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.ChatInputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SearchTags({"pretty chat", "chat colors", "colored chat"})
public final class PrettyChatHack extends Hack
	implements ChatInputListener, UpdateListener
{
	// Regular expression to match username patterns: <username> or username:
	private static final Pattern USERNAME_PATTERN =
		Pattern.compile("^<([^>]+)>|([^:]+):");
	
	// Colors
	private static final String USERNAME_COLOR = "§9"; // Blue
	private static final String MESSAGE_COLOR = "§7"; // Gray
	private static final String LIGHT_BACKGROUND = "§8"; // Dark Gray for
	// contrast
	private static final String DARK_BACKGROUND = "§0"; // Black for contrast
	
	// Track message count for alternating colors
	private int messageCounter = 0;
	
	// Last processed message to avoid duplication
	private String lastProcessedMessage = "";
	
	public PrettyChatHack()
	{
		super("PrettyChatHack");
		setCategory(Category.CHAT);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(ChatInputListener.class, this);
		EVENTS.add(UpdateListener.class, this);
		
		// Reset counter
		messageCounter = 0;
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(ChatInputListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
	}
	
	@Override
	public void onReceivedMessage(ChatInputEvent event)
	{
		Text message = event.getComponent();
		String messageText = message.getString();
		
		// Avoid processing the same message multiple times
		if(lastProcessedMessage.equals(messageText))
			return;
		
		lastProcessedMessage = messageText;
		
		// Format the message with username in blue and message in gray
		String formattedText = formatChatMessage(messageText);
		
		// Create new formatted text
		Text formattedMessage = Text.literal(formattedText);
		
		// Replace the original message with our formatted one
		event.setComponent(formattedMessage);
		messageCounter++;
	}
	
	@Override
	public void onUpdate()
	{
		// Reset the last processed message every few ticks to prevent issues
		// with spam filters
		if(MC.player != null && MC.player.age % 20 == 0)
			lastProcessedMessage = "";
	}
	
	/**
	 * Formats the chat message with colored username and alternating background
	 */
	private String formatChatMessage(String message)
	{
		// Determine background color based on message counter
		String backgroundColor =
			(messageCounter % 2 == 0) ? LIGHT_BACKGROUND : DARK_BACKGROUND;
		
		// Look for username pattern
		Matcher matcher = USERNAME_PATTERN.matcher(message);
		
		if(matcher.find())
		{
			// Extract username and message parts
			String username;
			String remainingText;
			
			if(matcher.group(1) != null)
			{
				// Format: <username> message
				username = matcher.group(1);
				remainingText = message.substring(matcher.end());
			}else
			{
				// Format: username: message
				username = matcher.group(2);
				remainingText = message.substring(matcher.end());
			}
			
			// Format with colors
			return backgroundColor + USERNAME_COLOR + "<" + username + "> "
				+ MESSAGE_COLOR + remainingText;
		}else
		{
			// If no username pattern found, just color the whole message gray
			return backgroundColor + MESSAGE_COLOR + message;
		}
	}
}
