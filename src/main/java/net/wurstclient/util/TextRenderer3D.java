/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
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
	 * @param matrices
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
	public static void renderText(MatrixStack matrices, Text text, double posX,
		double posY, double posZ, int color, boolean shadow, float scale)
	{
		TextRenderer textRenderer = MC.textRenderer;
		VertexConsumerProvider.Immediate immediate =
			MC.getBufferBuilders().getEntityVertexConsumers();
		
		matrices.push();
		matrices.translate(posX, posY, posZ);
		matrices.multiply(MC.gameRenderer.getCamera().getRotation());
		matrices.scale(-scale, -scale, scale);
		
		float opacity = (color >> 24 & 0xFF) / 255.0F;
		int textBackground = (int)(opacity * 255.0F) << 24;
		
		// Get width for centering
		float width = -textRenderer.getWidth(text) / 2.0F;
		
		// Draw the text - using Minecraft's built-in text rendering system
		textRenderer.draw(text, width, 0, color, shadow,
			matrices.peek().getPositionMatrix(), immediate,
			TextRenderer.TextLayerType.NORMAL, textBackground, 0xF000F0);
		
		immediate.draw();
		matrices.pop();
	}
	
	/**
	 * Renders text at the specified position in 3D space with default scale.
	 *
	 * @param matrices
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
	public static void renderText(MatrixStack matrices, Text text, double posX,
		double posY, double posZ, int color, boolean shadow)
	{
		renderText(matrices, text, posX, posY, posZ, color, shadow, 0.025F);
	}
	
	/**
	 * Renders text above a box (typically an entity's bounding box) in 3D
	 * space.
	 *
	 * @param matrices
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
	public static void renderTextAboveBox(MatrixStack matrices, Text text,
		Box box, double yOffset, int color, boolean shadow, float scale)
	{
		Vec3d center = box.getCenter();
		renderText(matrices, text, center.x, box.maxY + yOffset, center.z,
			color, shadow, scale);
	}
	
	/**
	 * Renders text above a box with default scale and offset.
	 *
	 * @param matrices
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
	public static void renderTextAboveBox(MatrixStack matrices, Text text,
		Box box, int color, boolean shadow)
	{
		renderTextAboveBox(matrices, text, box, 0.3, color, shadow, 0.025F);
	}
	
	/**
	 * Renders text above a box with specified offset.
	 *
	 * @param matrices
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
	public static void renderTextAboveBox(MatrixStack matrices, Text text,
		Box box, double yOffset, int color, boolean shadow)
	{
		renderTextAboveBox(matrices, text, box, yOffset, color, shadow, 0.025F);
	}
}
