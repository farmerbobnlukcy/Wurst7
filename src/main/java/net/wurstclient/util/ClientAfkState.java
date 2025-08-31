/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

public final class ClientAfkState
{
	private static volatile boolean afk;
	
	public static boolean isAfk()
	{
		return afk;
	}
	
	public static void set(boolean v)
	{
		afk = v;
	}
	
	private ClientAfkState()
	{}
	
	public static void setAfk(boolean v)
	{
		afk = v;
	}
}
/**
 * import net.wurstclient.util.ClientAfkState;
 *
 * @Override
 *           public void onUpdate() // e.g., inside a hack or feature
 *           {
 *           if(ClientAfkState.isAfk())
 *           return; // pause this feature while AFK
 *			
 *           // ...existing logic...
 *           }
 */
