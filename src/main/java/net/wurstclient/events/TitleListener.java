/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.events;

import net.minecraft.text.Text;
import net.wurstclient.event.ClientState;
import net.wurstclient.event.Listener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface TitleListener extends Listener
{
	static final Logger LOG = LoggerFactory.getLogger("yourmod-afk");
	
	public default void onTitle(Text text, boolean isSubtitle)
	{
		if(text == null)
			return;
		
		String s = text.getString().toLowerCase();
		if(s.contains("afk"))
		{
			ClientState.setAfk(true);
		}else
		{
			// if you want to clear AFK whenever any other title is sent:
			ClientState.setAfk(false);
		}
	}
}
