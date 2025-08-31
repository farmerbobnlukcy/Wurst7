/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

public class DelayUtils
{
	// Common Minecraft time periods in ticks
	public static final int TICK = 1;
	public static final int SECOND = 20;
	public static final int MINUTE = SECOND * 60;
	
	private int ticks;
	private boolean active;
	
	public DelayUtils()
	{
		this.ticks = 0;
		this.active = false;
	}
	
	/**
	 * Set delay using milliseconds
	 *
	 * @param milliseconds
	 */
	
	public void setDelayMs(int milliseconds)
	{
		this.ticks = milliseconds / 50; // 50ms per tick
		this.active = true;
	}
	
	/**
	 * Set delay using predefined time periods
	 *
	 * @param seconds
	 *            Number of seconds to delay
	 */
	public void setDelaySeconds(int seconds)
	{
		this.ticks = seconds * SECOND;
		this.active = true;
		
	}
	
	/**
	 * Set delay using raw ticks
	 *
	 * @param ticks
	 *            Number of ticks to delay (20 ticks = 1 second)
	 */
	public void setDelay(int ticks)
	{
		this.ticks = ticks;
		this.active = true;
	}
	
	/**
	 * Update tick counter
	 */
	public void tick()
	{
		if(active && ticks > 0)
		{
			ticks--;
			if(ticks <= 0)
			{
				active = false;
			}
		}
	}
	
	/**
	 * Check if delay has completed
	 *
	 * @return true if delay is finished
	 */
	public boolean hasFinished()
	{
		return !active;
	}
	
	/**
	 * Get remaining ticks in delay
	 *
	 * @return number of ticks remaining
	 */
	public int getRemainingTicks()
	{
		return ticks;
	}
	
}
