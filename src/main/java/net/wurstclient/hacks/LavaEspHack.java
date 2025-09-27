/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.awt.Color;
import java.util.ArrayList;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.block.Block;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.RenderUtils;

@SearchTags({"lava esp", "lava finder", "lavaesp", "magma finder"})
public final class LavaEspHack extends Hack
	implements UpdateListener, RenderListener
{
	private final EnumSetting<Style> style = new EnumSetting<>("Style",
		"How to render the lava highlights.", Style.values(), Style.BOXES);
	
	private final ColorSetting lavaBoxColor = new ColorSetting("Lava box color",
		"Color used to highlight lava pools.", new Color(255, 100, 0, 64));
	
	private final ColorSetting fireParticleColor =
		new ColorSetting("Fire particle color",
			"Color used to highlight fire particles near lava.",
			new Color(255, 255, 0, 128));
	
	private final SliderSetting range = new SliderSetting("Range",
		"Maximum distance in blocks to search for lava.", 32, 8, 128, 8,
		ValueDisplay.INTEGER);
	
	private ArrayList<BlockPos> lavaBlocks = new ArrayList<>();
	private ArrayList<FireParticleInfo> fireParticles = new ArrayList<>();
	
	public LavaEspHack()
	{
		super("LavaESP");
		setCategory(Category.RENDER);
		
		addSetting(style);
		addSetting(lavaBoxColor);
		addSetting(fireParticleColor);
		addSetting(range);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		lavaBlocks.clear();
		fireParticles.clear();
	}
	
	@Override
	public void onUpdate()
	{
		// Update lava block positions
		updateLavaBlocks();
		
		// Update fire particles positions
		updateFireParticles();
	}
	
	private void updateLavaBlocks()
	{
		// Clear old list
		lavaBlocks.clear();
		
		// Calculate range-based search box
		BlockPos center = BlockPos.ofFloored(MC.player.getEyePos());
		int rangeInt = range.getValueInt();
		BlockPos min = center.add(-rangeInt, -rangeInt, -rangeInt);
		BlockPos max = center.add(rangeInt, rangeInt, rangeInt);
		
		// Collect lava blocks in range
		for(BlockPos pos : BlockPos.iterate(min, max))
		{
			// Skip if out of range
			if(center.getSquaredDistance(pos) > rangeInt * rangeInt)
				continue;
			
			// Skip if not lava
			Block block = BlockUtils.getBlock(pos);
			if(!BlockUtils.isLava(block))
				continue;
			
			// Add to list
			lavaBlocks.add(new BlockPos(pos));
		}
	}
	
	private void updateFireParticles()
	{
		// Clear old list after a certain time to prevent stale data
		fireParticles.removeIf(info -> info.ageInTicks > 10);
		
		// Search for entities on fire
		for(Entity entity : MC.world.getEntities())
		{
			// Skip if not on fire
			if(!entity.isOnFire())
				continue;
			
			// Skip if out of range
			if(entity.squaredDistanceTo(MC.player) > range.getValueSq())
				continue;
			
			// Add fire particle info
			fireParticles.add(new FireParticleInfo(entity.getPos(), 0));
		}
		
		// Update age of existing particles
		for(FireParticleInfo info : fireParticles)
		{
			info.ageInTicks++;
		}
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		// Skip if no data
		if(lavaBlocks.isEmpty() && fireParticles.isEmpty())
			return;
		
		// Save GL settings
		RenderSystem.enableBlend();
		RenderSystem.setShaderColor(1, 1, 1, 1);
		
		// Render lava blocks
		if(!lavaBlocks.isEmpty())
		{
			// Get color settings
			Color color = lavaBoxColor.getColorWithAlpha();
			
			// Setup rendering
			if(style.getSelected() == Style.BOXES
				|| style.getSelected() == Style.BOTH)
			{
				// Draw boxes
				renderLavaBoxes(matrixStack, lavaBlocks, color.getRGB());
			}
			
			if(style.getSelected() == Style.OUTLINES
				|| style.getSelected() == Style.BOTH)
			{
				// Draw outlines
				renderLavaOutlines(matrixStack, lavaBlocks, color.getRGB());
			}
		}
		
		// Render fire particles
		if(!fireParticles.isEmpty())
		{
			// Get color settings
			Color color = fireParticleColor.getColorWithAlpha();
			
			// Draw fire particle markers
			renderFireParticles(matrixStack, partialTicks, color.getRGB());
		}
		
		// Reset GL settings
		RenderSystem.disableBlend();
	}
	
	private void renderLavaBoxes(MatrixStack matrixStack,
		ArrayList<BlockPos> blocks, int color)
	{
		RenderUtils.setBatchingEnabled(true);
		
		for(BlockPos pos : blocks)
		{
			Box box = new Box(pos);
			RenderUtils.drawSolidBox(matrixStack, box, color);
		}
		
		RenderUtils.setBatchingEnabled(false);
	}
	
	private void renderLavaOutlines(MatrixStack matrixStack,
		ArrayList<BlockPos> blocks, int color)
	{
		RenderUtils.setBatchingEnabled(true);
		
		for(BlockPos pos : blocks)
		{
			Box box = new Box(pos);
			RenderUtils.drawOutlinedBox(matrixStack, box, color);
		}
		
		RenderUtils.setBatchingEnabled(false);
	}
	
	private void renderFireParticles(MatrixStack matrixStack,
		float partialTicks, int color)
	{
		RenderSystem.disableDepthTest();
		
		matrixStack.push();
		
		// Use player's interpolated position for accurate rendering
		Vec3d camPos = MC.gameRenderer.getCamera().getPos();
		matrixStack.translate(-camPos.x, -camPos.y, -camPos.z);
		
		// Get matrix
		Matrix4f matrix = matrixStack.peek().getPositionMatrix();
		
		// Setup shader
		ShaderProgram shader = RenderSystem.getShader();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		
		// Start drawing
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		BufferBuilder bufferBuilder = tessellator.getBuffer();
		
		bufferBuilder.begin(VertexFormat.DrawMode.QUADS,
			VertexFormats.POSITION_COLOR);
		
		// Draw fire particle indicators
		for(FireParticleInfo info : fireParticles)
		{
			// Calculate size based on age (newer = larger)
			float size = 0.2f * (1.0f - (float)info.ageInTicks / 10f);
			
			// Calculate alpha based on age (newer = more visible)
			int alpha = color & 0xFF000000;
			int adjustedAlpha =
				(int)(alpha * (1.0f - (float)info.ageInTicks / 10f))
					& 0xFF000000;
			int adjustedColor = (color & 0x00FFFFFF) | adjustedAlpha;
			
			// Draw a square at particle position
			Vec3d pos = info.position;
			
			// Top face (Y+)
			bufferBuilder.vertex(matrix, (float)pos.x - size,
				(float)pos.y + size, (float)pos.z - size).color(adjustedColor)
				.next();
			bufferBuilder.vertex(matrix, (float)pos.x - size,
				(float)pos.y + size, (float)pos.z + size).color(adjustedColor)
				.next();
			bufferBuilder.vertex(matrix, (float)pos.x + size,
				(float)pos.y + size, (float)pos.z + size).color(adjustedColor)
				.next();
			bufferBuilder.vertex(matrix, (float)pos.x + size,
				(float)pos.y + size, (float)pos.z - size).color(adjustedColor)
				.next();
			
			// Bottom face (Y-)
			bufferBuilder.vertex(matrix, (float)pos.x - size,
				(float)pos.y - size, (float)pos.z - size).color(adjustedColor)
				.next();
			bufferBuilder.vertex(matrix, (float)pos.x + size,
				(float)pos.y - size, (float)pos.z - size).color(adjustedColor)
				.next();
			bufferBuilder.vertex(matrix, (float)pos.x + size,
				(float)pos.y - size, (float)pos.z + size).color(adjustedColor)
				.next();
			bufferBuilder.vertex(matrix, (float)pos.x - size,
				(float)pos.y - size, (float)pos.z + size).color(adjustedColor)
				.next();
			
			// East face (X+)
			bufferBuilder.vertex(matrix, (float)pos.x + size,
				(float)pos.y - size, (float)pos.z - size).color(adjustedColor)
				.next();
			bufferBuilder.vertex(matrix, (float)pos.x + size,
				(float)pos.y + size, (float)pos.z - size).color(adjustedColor)
				.next();
			bufferBuilder.vertex(matrix, (float)pos.x + size,
				(float)pos.y + size, (float)pos.z + size).color(adjustedColor)
				.next();
			bufferBuilder.vertex(matrix, (float)pos.x + size,
				(float)pos.y - size, (float)pos.z + size).color(adjustedColor)
				.next();
			
			// West face (X-)
			bufferBuilder.vertex(matrix, (float)pos.x - size,
				(float)pos.y - size, (float)pos.z - size).color(adjustedColor)
				.next();
			bufferBuilder.vertex(matrix, (float)pos.x - size,
				(float)pos.y - size, (float)pos.z + size).color(adjustedColor)
				.next();
			bufferBuilder.vertex(matrix, (float)pos.x - size,
				(float)pos.y + size, (float)pos.z + size).color(adjustedColor)
				.next();
			bufferBuilder.vertex(matrix, (float)pos.x - size,
				(float)pos.y + size, (float)pos.z - size).color(adjustedColor)
				.next();
			
			// South face (Z+)
			bufferBuilder.vertex(matrix, (float)pos.x - size,
				(float)pos.y - size, (float)pos.z + size).color(adjustedColor)
				.next();
			bufferBuilder.vertex(matrix, (float)pos.x + size,
				(float)pos.y - size, (float)pos.z + size).color(adjustedColor)
				.next();
			bufferBuilder.vertex(matrix, (float)pos.x + size,
				(float)pos.y + size, (float)pos.z + size).color(adjustedColor)
				.next();
			bufferBuilder.vertex(matrix, (float)pos.x - size,
				(float)pos.y + size, (float)pos.z + size).color(adjustedColor)
				.next();
			
			// North face (Z-)
			bufferBuilder.vertex(matrix, (float)pos.x - size,
				(float)pos.y - size, (float)pos.z - size).color(adjustedColor)
				.next();
			bufferBuilder.vertex(matrix, (float)pos.x - size,
				(float)pos.y + size, (float)pos.z - size).color(adjustedColor)
				.next();
			bufferBuilder.vertex(matrix, (float)pos.x + size,
				(float)pos.y + size, (float)pos.z - size).color(adjustedColor)
				.next();
			bufferBuilder.vertex(matrix, (float)pos.x + size,
				(float)pos.y - size, (float)pos.z - size).color(adjustedColor)
				.next();
		}
		
		// Draw all at once
		tessellator.draw();
		
		matrixStack.pop();
		
		RenderSystem.enableDepthTest();
		RenderSystem.setShader(() -> shader);
	}
	
	private static class FireParticleInfo
	{
		public Vec3d position;
		public int ageInTicks;
		
		public FireParticleInfo(Vec3d position, int ageInTicks)
		{
			this.position = position;
			this.ageInTicks = ageInTicks;
		}
	}
	
	private enum Style
	{
		BOXES("Boxes", "Renders lava as boxes."),
		OUTLINES("Outlines", "Renders outlines around lava."),
		BOTH("Both", "Renders both boxes and outlines.");
		
		private final String name;
		private final String description;
		
		private Style(String name, String description)
		{
			this.name = name;
			this.description = description;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
}
