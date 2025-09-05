/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.flowerbot;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.wurstclient.util.RenderUtils;

public class Flower
{
	private final ArrayList<BlockPos> flowers;
	
	public Flower(ArrayList<BlockPos> flowers)
	{
		this.flowers = flowers;
	}
	
	public void draw(MatrixStack matrixStack)
	{
		int color = 0x80FF00FF; // Purple color for flowers
		Box box = new Box(BlockPos.ORIGIN).contract(1 / 16.0);
		
		List<Box> flowerBoxes =
			flowers.stream().map(pos -> box.offset(pos)).toList();
		RenderUtils.drawOutlinedBoxes(matrixStack, flowerBoxes, color, false);
	}
	
	public ArrayList<BlockPos> getFlowers()
	{
		return flowers;
	}
}
