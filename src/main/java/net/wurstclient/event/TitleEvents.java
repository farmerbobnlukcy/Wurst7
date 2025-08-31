/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.text.Text;

public final class TitleEvents
{
	private TitleEvents()
	{}
	
	@FunctionalInterface
	public interface TitleListener
	{
		/**
		 * @param isSubtitle
		 *            true for subtitle, false for main title; text may be null
		 *            (e.g., clear)
		 */
		void onTitle(Text text, boolean isSubtitle);
	}
	
	public static final Event<TitleListener> TITLE_EVENT =
		EventFactory.createArrayBacked(TitleListener.class,
			listeners -> (text, isSubtitle) -> {
				for(TitleListener l : listeners)
					l.onTitle(text, isSubtitle);
			});
	
	public static void fire(Text text, boolean isSubtitle)
	{
		TITLE_EVENT.invoker().onTitle(text, isSubtitle);
	}
}
