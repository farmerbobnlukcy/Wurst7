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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.CameraTransformViewBobbingListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EspBoxSizeSetting;
import net.wurstclient.settings.EspStyleSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RenderUtils.ColoredBox;
import net.wurstclient.util.RenderUtils.ColoredPoint;

@SearchTags({"creeper esp", "CreeperTracers", "creeper tracers",
	"creeper finder"})
public final class CreeperEspHack extends Hack implements UpdateListener,
	CameraTransformViewBobbingListener, RenderListener
{
	private final EspStyleSetting style = new EspStyleSetting();
	
	private final EspBoxSizeSetting boxSize = new EspBoxSizeSetting(
		"\u00a7lAccurate\u00a7r mode shows the exact hitbox of each creeper.\n"
			+ "\u00a7lFancy\u00a7r mode shows slightly larger boxes that look better.");
	
	private final ColorSetting color = new ColorSetting("Color",
		"Creepers will be highlighted in this color.", new Color(0, 255, 0));
	
	private final SliderSetting distance = new SliderSetting("Distance",
		"Maximum distance in blocks that creepers will be highlighted.", 50, 10,
		200, 10, ValueDisplay.INTEGER);
	
	private final ArrayList<CreeperEntity> creepers = new ArrayList<>();
	
	public CreeperEspHack()
	{
		super("CreeperESP");
		setCategory(Category.RENDER);
		addSetting(style);
		addSetting(boxSize);
		addSetting(color);
		addSetting(distance);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(CameraTransformViewBobbingListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(CameraTransformViewBobbingListener.class, this);
		EVENTS.remove(RenderListener.class, this);
	}
	
	@Override
	public void onUpdate()
	{
		creepers.clear();
		
		// Get all entities and filter for creepers only
		Stream<Entity> stream =
			StreamSupport.stream(MC.world.getEntities().spliterator(), false);
		
		double maxDistSq = Math.pow(distance.getValue(), 2);
		
		// Find all creepers within range
		Stream<CreeperEntity> creeperStream = stream
			.filter(entity -> entity instanceof CreeperEntity)
			.map(entity -> (CreeperEntity)entity)
			.filter(entity -> !entity.isRemoved() && entity.getHealth() > 0)
			.filter(entity -> MC.player.squaredDistanceTo(entity) <= maxDistSq);
		
		creepers.addAll(creeperStream.collect(Collectors.toList()));
	}
	
	@Override
	public void onCameraTransformViewBobbing(
		CameraTransformViewBobbingEvent event)
	{
		if(style.hasLines())
			event.cancel();
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		// No need to render if no creepers were found
		if(creepers.isEmpty())
			return;
		
		// Get the base color from settings
		int boxColor = color.getColorI(0x80);
		
		if(style.hasBoxes())
		{
			double extraSize = boxSize.getExtraSize() / 2;
			
			ArrayList<ColoredBox> boxes = new ArrayList<>(creepers.size());
			for(CreeperEntity e : creepers)
			{
				Box box = EntityUtils.getLerpedBox(e, partialTicks)
					.offset(0, extraSize, 0).expand(extraSize);
				
				// Change color intensity based on creeper fuse time
				float fuseIntensity = e.getClientFuseTime(partialTicks);
				int adjustedColor = fuseIntensity > 0
					? getRawColorForFuse(fuseIntensity, boxColor) : boxColor;
				
				boxes.add(new ColoredBox(box, adjustedColor));
			}
			
			RenderUtils.drawOutlinedBoxes(matrixStack, boxes, false);
		}
		
		if(style.hasLines())
		{
			ArrayList<ColoredPoint> ends = new ArrayList<>(creepers.size());
			for(CreeperEntity e : creepers)
			{
				Vec3d point =
					EntityUtils.getLerpedBox(e, partialTicks).getCenter();
				
				// Change color intensity based on creeper fuse time
				float fuseIntensity = e.getClientFuseTime(partialTicks);
				int adjustedColor = fuseIntensity > 0
					? getRawColorForFuse(fuseIntensity, boxColor) : boxColor;
				
				ends.add(new ColoredPoint(point, adjustedColor));
			}
			
			RenderUtils.drawTracers(matrixStack, partialTicks, ends, false);
		}
	}
	
	/**
	 * Gets a color that intensifies (more red) as the creeper gets closer to
	 * exploding
	 */
	private int getRawColorForFuse(float fuseIntensity, int baseColor)
	{
		// As creeper is about to explode, shift color from green to red
		float r = MathHelper.clamp(fuseIntensity, 0, 1);
		float g = MathHelper.clamp(1 - fuseIntensity, 0, 1);
		float b = 0;
		float a = ((baseColor >> 24) & 0xFF) / 255F;
		
		return RenderUtils.toIntColor(new float[]{r, g, b}, a);
	}
}
