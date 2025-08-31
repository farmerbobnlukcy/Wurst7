/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.events;

import java.util.ArrayList;

import net.minecraft.text.Text;
import net.wurstclient.event.Event;
import net.wurstclient.event.Listener;

public interface TitleScreenListener extends Listener
{
	/** Fired for both title & subtitle (text != null). */
	void onTitle(Text text, boolean isSubtitle);
	
	/** Fired when the server clears titles. */
	default void onTitleClear()
	{}
	
	// ---- Events ----
	
	final class TitleEvent extends Event<TitleScreenListener>
	{
		private final Text text;
		private final boolean subtitle;
		
		public TitleEvent(Text text, boolean subtitle)
		{
			this.text = text;
			this.subtitle = subtitle;
		}
		
		@Override
		public void fire(ArrayList<TitleScreenListener> ls)
		{
			for(var l : ls)
				l.onTitle(text, subtitle);
		}
		
		@Override
		public Class<TitleScreenListener> getListenerType()
		{
			return TitleScreenListener.class;
		}
	}
	
	final class TitleClearEvent extends Event<TitleScreenListener>
	{
		@Override
		public void fire(ArrayList<TitleScreenListener> ls)
		{
			for(var l : ls)
				l.onTitleClear();
		}
		
		@Override
		public Class<TitleScreenListener> getListenerType()
		{
			return TitleScreenListener.class;
		}
	}
}
