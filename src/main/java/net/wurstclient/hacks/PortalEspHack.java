/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.events.CameraTransformViewBobbingListener;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.portalesp.PortalEspBlockGroup;
import net.wurstclient.settings.*;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.chunk.ChunkSearcher.Result;
import net.wurstclient.util.chunk.ChunkSearcherCoordinator;

import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;

public final class PortalEspHack extends Hack implements UpdateListener,
	CameraTransformViewBobbingListener, RenderListener
{
	private final EspStyleSetting style = new EspStyleSetting();
	
	private final PortalEspBlockGroup netherPortal =
		new PortalEspBlockGroup(Blocks.NETHER_PORTAL,
			new ColorSetting("Nether portal color",
				"Nether portals will be highlighted in this color.", Color.RED),
			new CheckboxSetting("Include nether portals", true));
	
	private final PortalEspBlockGroup endPortal =
		new PortalEspBlockGroup(Blocks.END_PORTAL,
			new ColorSetting("End portal color",
				"End portals will be highlighted in this color.", Color.GREEN),
			new CheckboxSetting("Include end portals", true));
	
	private final PortalEspBlockGroup endPortalFrame = new PortalEspBlockGroup(
		Blocks.END_PORTAL_FRAME,
		new ColorSetting("End portal frame color",
			"End portal frames will be highlighted in this color.", Color.BLUE),
		new CheckboxSetting("Include end portal frames", true));
	
	private final PortalEspBlockGroup endGateway = new PortalEspBlockGroup(
		Blocks.END_GATEWAY,
		new ColorSetting("End gateway color",
			"End gateways will be highlighted in this color.", Color.YELLOW),
		new CheckboxSetting("Include end gateways", true));
	
	private final List<PortalEspBlockGroup> groups =
		Arrays.asList(netherPortal, endPortal, endPortalFrame, endGateway);
	
	private final ChunkAreaSetting area = new ChunkAreaSetting("Area",
		"The area around the player to search in.\n"
			+ "Higher values require a faster computer.");
	
	private final BiPredicate<BlockPos, BlockState> query =
		(pos, state) -> state.getBlock() == Blocks.NETHER_PORTAL
			|| state.getBlock() == Blocks.END_PORTAL
			|| state.getBlock() == Blocks.END_PORTAL_FRAME
			|| state.getBlock() == Blocks.END_GATEWAY;
	
	private final ChunkSearcherCoordinator coordinator =
		new ChunkSearcherCoordinator(query, area);
	
	// Custom origin settings
	private final CheckboxSetting useCustomOrigins =
		new CheckboxSetting("Use Custom Origins",
			"Uses custom origin points for tracers instead of the crosshair.",
			true);
	
	private final SliderSetting portalOriginX =
		new SliderSetting("Portal Origin X", "X offset for portal tracers", 0.3,
			-1.0, 1.0, 0.05, SliderSetting.ValueDisplay.DECIMAL);
	
	private final SliderSetting portalOriginY =
		new SliderSetting("Portal Origin Y", "Y offset for portal tracers",
			-0.3, -1.0, 1.0, 0.05, SliderSetting.ValueDisplay.DECIMAL);
	
	private boolean groupsUpToDate;
	
	public PortalEspHack()
	{
		super("PortalESP");
		setCategory(Category.RENDER);
		
		addSetting(style);
		groups.stream().flatMap(PortalEspBlockGroup::getSettings)
			.forEach(this::addSetting);
		addSetting(area);
		
		// Add custom origin settings
		addSetting(useCustomOrigins);
		addSetting(portalOriginX);
		addSetting(portalOriginY);
	}
	
	/**
	 * Gets a custom origin point for tracers based on given offsets.
	 *
	 * @param xOffset
	 *            X offset from the center of the screen (-1 to 1)
	 * @param yOffset
	 *            Y offset from the center of the screen (-1 to 1)
	 * @return A Vec3d position for the custom origin
	 */
	private Vec3d getCustomOrigin(double xOffset, double yOffset)
	{
		// Get player's eye position and view vectors
		Vec3d eyePos = MC.player.getCameraPosVec(1.0F);
		Vec3d lookVec = MC.player.getRotationVec(1.0F).normalize();
		Vec3d upVec = new Vec3d(0, 1, 0);
		
		// Calculate right vector (perpendicular to look and up)
		Vec3d rightVec = lookVec.crossProduct(upVec).normalize();
		
		// Recalculate up vector to ensure orthogonality
		upVec = rightVec.crossProduct(lookVec).normalize();
		
		// Scale to move the point forward slightly in front of the player
		double forwardDist = 0.5;
		
		// Apply offsets to create a point relative to player's view
		return eyePos.add(
			lookVec.multiply(forwardDist).add(rightVec.multiply(xOffset * 0.5))
				.add(upVec.multiply(yOffset * 0.5)));
	}
	
	/**
	 * Draws tracers from a custom origin point instead of the center of the
	 * screen.
	 */
	private void drawCustomOriginTracers(MatrixStack matrixStack,
		float partialTicks, List<Vec3d> positions, int color,
		boolean throughWalls, Vec3d origin)
	{
		// Draw tracers from this origin to each position
		for(Vec3d end : positions)
		{
			RenderUtils.drawLine(matrixStack, origin, end, color, throughWalls);
		}
	}
	
	@Override
	protected void onEnable()
	{
		groupsUpToDate = false;
		
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketInputListener.class, coordinator);
		EVENTS.add(CameraTransformViewBobbingListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketInputListener.class, coordinator);
		EVENTS.remove(CameraTransformViewBobbingListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		coordinator.reset();
		groups.forEach(PortalEspBlockGroup::clear);
	}
	
	@Override
	public void onCameraTransformViewBobbing(
		CameraTransformViewBobbingEvent event)
	{
		if(style.getSelected().hasLines())
			event.cancel();
	}
	
	@Override
	public void onUpdate()
	{
		boolean searchersChanged = coordinator.update();
		if(searchersChanged)
			groupsUpToDate = false;
		
		if(!groupsUpToDate && coordinator.isDone())
			updateGroupBoxes();
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		if(style.getSelected().hasBoxes())
			renderBoxes(matrixStack);
		
		if(style.getSelected().hasLines())
			renderTracers(matrixStack, partialTicks);
	}
	
	private void renderBoxes(MatrixStack matrixStack)
	{
		for(PortalEspBlockGroup group : groups)
		{
			if(!group.isEnabled())
				return;
			
			List<Box> boxes = group.getBoxes();
			int quadsColor = group.getColorI(0x40);
			int linesColor = group.getColorI(0x80);
			
			RenderUtils.drawSolidBoxes(matrixStack, boxes, quadsColor, false);
			RenderUtils.drawOutlinedBoxes(matrixStack, boxes, linesColor,
				false);
		}
	}
	
	private void renderTracers(MatrixStack matrixStack, float partialTicks)
	{
		for(PortalEspBlockGroup group : groups)
		{
			if(!group.isEnabled())
				return;
			
			List<Box> boxes = group.getBoxes();
			List<Vec3d> ends = boxes.stream().map(Box::getCenter).toList();
			int color = group.getColorI(0x80);
			
			if(useCustomOrigins.isChecked())
			{
				Vec3d origin = getCustomOrigin(portalOriginX.getValue(),
					portalOriginY.getValue());
				
				drawCustomOriginTracers(matrixStack, partialTicks, ends, color,
					false, origin);
			}else
			{
				RenderUtils.drawTracers(matrixStack, partialTicks, ends, color,
					false);
			}
		}
	}
	
	private void updateGroupBoxes()
	{
		groups.forEach(PortalEspBlockGroup::clear);
		coordinator.getMatches().forEach(this::addToGroupBoxes);
		groupsUpToDate = true;
	}
	
	private void addToGroupBoxes(Result result)
	{
		for(PortalEspBlockGroup group : groups)
			if(result.state().getBlock() == group.getBlock())
			{
				group.add(result.pos());
				break;
			}
	}
}
