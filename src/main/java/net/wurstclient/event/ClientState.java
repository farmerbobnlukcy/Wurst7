/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.event;

public class ClientState
{
	private static boolean afk = false;
	
	public static boolean isAfk()
	{
		return afk;
	}
	
	public static void setAfk(boolean value)
	{
		afk = value;
	}
	
}
