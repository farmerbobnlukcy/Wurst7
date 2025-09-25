/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Utility class for rendering text labels in 3D space.
 * Used for displaying text above objects in the world.
 */
public final class TextRenderer3D
{
	private static final MinecraftClient MC = MinecraftClient.getInstance();
	
	/**
	 * Renders text at the specified position in 3D space.
	 *
	 * @param matrixStack
	 *            the matrix stack
	 * @param text
	 *            the text to render
	 * @param posX
	 *            the X position in the world
	 * @param posY
	 *            the Y position in the world
	 * @param posZ
	 *            the Z position in the world
	 * @param color
	 *            the text color
	 * @param shadow
	 *            whether to render a shadow
	 * @param scale
	 *            the text scale factor (default is 0.025)
	 */
	public static void renderText(MatrixStack matrixStack, Text text,
		double posX, double posY, double posZ, int color, boolean shadow,
		float scale)
	{
		// Save previous matrix state
		matrixStack.push();
		
		// Position text in 3D space
		matrixStack.translate(posX, posY, posZ);
		
		// Make text face the player
		matrixStack.multiply(MC.gameRenderer.getCamera().getRotation());
		
		// Flip text and scale it appropriately
		matrixStack.scale(-scale, -scale, scale);
		
		// Center the text
		int textWidth = MC.textRenderer.getWidth(text);
		float xOffset = -textWidth / 2f;
		
		// Render the text
		if(shadow)
			MC.textRenderer.drawWithShadow(matrixStack, text, xOffset, 0,
				color);
		else
			MC.textRenderer.draw(matrixStack, text, xOffset, 0, color);
		
		// Restore previous matrix state
		matrixStack.pop();
	}
	
	/**
	 * Renders text at the specified position in 3D space with default scale.
	 *
	 * @param matrixStack
	 *            the matrix stack
	 * @param text
	 *            the text to render
	 * @param posX
	 *            the X position in the world
	 * @param posY
	 *            the Y position in the world
	 * @param posZ
	 *            the Z position in the world
	 * @param color
	 *            the text color
	 * @param shadow
	 *            whether to render a shadow
	 */
	public static void renderText(MatrixStack matrixStack, Text text,
		double posX, double posY, double posZ, int color, boolean shadow)
	{
		renderText(matrixStack, text, posX, posY, posZ, color, shadow, 0.025f);
	}
	
	/**
	 * Renders text above a box (typically an entity's bounding box) in 3D
	 * space.
	 *
	 * @param matrixStack
	 *            the matrix stack
	 * @param text
	 *            the text to render
	 * @param box
	 *            the box above which to render the text
	 * @param yOffset
	 *            vertical offset above the box
	 * @param color
	 *            the text color
	 * @param shadow
	 *            whether to render a shadow
	 * @param scale
	 *            the text scale factor
	 */
	public static void renderTextAboveBox(MatrixStack matrixStack, Text text,
		Box box, double yOffset, int color, boolean shadow, float scale)
	{
		Vec3d center = box.getCenter();
		renderText(matrixStack, text, center.x, box.maxY + yOffset, center.z,
			color, shadow, scale);
	}
	
	/**
	 * Renders text above a box with default scale and offset.
	 *
	 * @param matrixStack
	 *            the matrix stack
	 * @param text
	 *            the text to render
	 * @param box
	 *            the box above which to render the text
	 * @param color
	 *            the text color
	 * @param shadow
	 *            whether to render a shadow
	 */
	public static void renderTextAboveBox(MatrixStack matrixStack, Text text,
		Box box, int color, boolean shadow)
	{
		renderTextAboveBox(matrixStack, text, box, 0.3, color, shadow, 0.025f);
	}
	
	/**
	 * Renders text above a box with specified offset.
	 *
	 * @param matrixStack
	 *            the matrix stack
	 * @param text
	 *            the text to render
	 * @param box
	 *            the box above which to render the text
	 * @param yOffset
	 *            vertical offset above the box
	 * @param color
	 *            the text color
	 * @param shadow
	 *            whether to render a shadow
	 */
	public static void renderTextAboveBox(MatrixStack matrixStack, Text text,
		Box box, double yOffset, int color, boolean shadow)
	{
		renderTextAboveBox(matrixStack, text, box, yOffset, color, shadow,
			0.025f);
	}
}
