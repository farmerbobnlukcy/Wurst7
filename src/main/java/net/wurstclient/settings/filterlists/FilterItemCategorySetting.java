/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.settings.filters;

import java.util.function.Predicate;

import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.wurstclient.hacks.ItemEspHack.ItemCategory;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.filterlists.ItemFilterList.ItemFilter;

public final class FilterItemCategorySetting implements ItemFilter
{
	private final CheckboxSetting setting;
	private final ItemCategory category;
	private final Predicate<ItemStack> categorizer;
	
	public FilterItemCategorySetting(String description, ItemCategory category,
		Predicate<ItemStack> categorizer, boolean checked)
	{
		setting =
			new CheckboxSetting(category.toString(), description, checked);
		this.category = category;
		this.categorizer = categorizer;
	}
	
	public static FilterItemCategorySetting create(ItemCategory category,
		Predicate<ItemStack> categorizer, boolean checked)
	{
		return new FilterItemCategorySetting(category.getDescription(),
			category, categorizer, checked);
	}
	
	@Override
	public boolean isFilterEnabled()
	{
		return !setting.isChecked();
	}
	
	@Override
	public CheckboxSetting getSetting()
	{
		return setting;
	}
	
	@Override
	public boolean test(ItemEntity entity)
	{
		ItemStack stack = entity.getStack();
		return !categorizer.test(stack);
	}
}
