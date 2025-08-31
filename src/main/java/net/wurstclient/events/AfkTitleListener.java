/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.events;

import net.minecraft.text.Text;
import net.wurstclient.util.ClientAfkState;

import java.util.Locale;

/**
 * Since your project already registers listeners, make an implementation that
 * flips AFK state and use it anywhere you need to “stop” something.
 *
 * src/main/java/net/wurstclient/events/listeners/AfkTitleListener.java
 */

public final class AfkTitleListener implements TitleScreenListener
{
	@Override
	public void onTitle(Text text, boolean isSubtitle)
	{
		if(text == null)
			return; // clear handled in onTitleClear
		String s = text.getString().trim().toLowerCase(Locale.ROOT);
		
		// Be forgiving: some servers send "You are AFK", "[AFK]", etc.
		boolean looksAfk = s.equals("afk") || s.contains(" afk")
			|| s.contains("[afk]") || s.contains("afk mode");
		if(looksAfk)
		{
			ClientAfkState.set(true);
		}else
		{
			ClientAfkState.set(false);
			// Optional policy: clear AFK on any *non*-AFK title
			// AfkState.setAfk(false);
		}
	}
	// tiny global flag you read elsewhere to pause features
	
	@Override
	public void onTitleClear()
	{
		ClientAfkState.set(false);
	}
}
