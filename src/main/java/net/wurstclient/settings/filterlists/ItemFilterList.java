/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.settings.filterlists;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import net.minecraft.entity.ItemEntity;
import net.wurstclient.settings.Setting;

public class ItemFilterList
{
	private final List<ItemFilter> itemFilters;
	
	public ItemFilterList(ItemFilter... filters)
	{
		this(Arrays.asList(filters));
	}
	
	public ItemFilterList(List<ItemFilter> filters)
	{
		itemFilters = Collections.unmodifiableList(filters);
	}
	
	public final void forEach(Consumer<? super Setting> action)
	{
		itemFilters.stream().map(ItemFilter::getSetting).forEach(action);
	}
	
	public final <T extends ItemEntity> Stream<T> applyTo(Stream<T> stream)
	{
		for(ItemFilter filter : itemFilters)
		{
			if(!filter.isFilterEnabled())
				continue;
			
			stream = stream.filter(filter);
		}
		
		return stream;
	}
	
	public final boolean testOne(ItemEntity entity)
	{
		for(ItemFilter filter : itemFilters)
			if(filter.isFilterEnabled() && !filter.test(entity))
				return false;
			
		return true;
	}
	
	public static interface ItemFilter extends Predicate<ItemEntity>
	{
		public boolean isFilterEnabled();
		
		public Setting getSetting();
	}
}
