/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.mob.WardenEntity;
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

import java.awt.*;
import java.util.ArrayList;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@SearchTags({"creeper esp", "CreeperTracers", "creeper tracers",
	"creeper finder", "phantom esp", "PhantomTracers", "phantom tracers"})
public final class CreeperEspHack extends Hack implements UpdateListener,
	CameraTransformViewBobbingListener, RenderListener
{
	private final EspStyleSetting style = new EspStyleSetting();
	
	private final EspBoxSizeSetting boxSize = new EspBoxSizeSetting(
		"\u00a7lAccurate\u00a7r mode shows the exact hitbox of each creeper.\n"
			+ "\u00a7lFancy\u00a7r mode shows slightly larger boxes that look better.");
	
	private final ColorSetting creeperColor = new ColorSetting("Creeper Color",
		"Creepers will be highlighted in this color.", new Color(0, 255, 0));
	
	private final ColorSetting phantomColor = new ColorSetting("Phantom Color",
		"Phantoms will be highlighted in this color.",
		new Color(100, 100, 255));
	
	private final ColorSetting wardenColor = new ColorSetting("Warden Color",
		"Warden will be highlighted in this color.", new Color(100, 100, 255));
	
	private final ColorSetting witherColor = new ColorSetting("Wither Color",
		"Withers will be highlighted in this color.", new Color(100, 100, 255));
	
	private final ColorSetting shulkerColor = new ColorSetting("Shulker Color",
		"Shulkers will be highlighted in this color.",
		new Color(100, 100, 255));
	
	private final SliderSetting distance = new SliderSetting("Distance",
		"Maximum distance in blocks that mobs will be highlighted.", 50, 10,
		200, 10, ValueDisplay.INTEGER);
	
	private final ArrayList<CreeperEntity> creepers = new ArrayList<>();
	private final ArrayList<PhantomEntity> phantoms = new ArrayList<>();
	private final ArrayList<WardenEntity> wardens = new ArrayList<>();
	private final ArrayList<WitherEntity> withers = new ArrayList<>();
	private final ArrayList<ShulkerEntity> shulkers = new ArrayList<>();
	
	public CreeperEspHack()
	{
		super("CreeperESP");
		setCategory(Category.RENDER);
		addSetting(style);
		addSetting(boxSize);
		addSetting(creeperColor);
		addSetting(phantomColor);
		addSetting(wardenColor);
		addSetting(witherColor);
		addSetting(shulkerColor);
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
		phantoms.clear();
		withers.clear();
		wardens.clear();
		shulkers.clear();
		
		// Get all entities
		Stream<Entity> stream =
			StreamSupport.stream(MC.world.getEntities().spliterator(), false);
		
		double maxDistSq = Math.pow(distance.getValue(), 2);
		
		// Find all creepers within range
		stream.filter(entity -> !entity.isRemoved())
			.filter(entity -> MC.player.squaredDistanceTo(entity) <= maxDistSq)
			.forEach(entity -> {
				if(entity instanceof CreeperEntity)
					creepers.add((CreeperEntity)entity);
				if(entity instanceof PhantomEntity)
					phantoms.add((PhantomEntity)entity);
				if(entity instanceof WardenEntity)
					wardens.add((WardenEntity)entity);
				if(entity instanceof ShulkerEntity)
					shulkers.add((ShulkerEntity)entity);
				else if(entity instanceof WitherEntity)
					withers.add((WitherEntity)entity);
			});
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
		// No need to render if no mobs were found
		if(creepers.isEmpty() && phantoms.isEmpty() && wardens.isEmpty()
			&& shulkers.isEmpty() && withers.isEmpty())
			return;
		
		// Render boxes
		if(style.hasBoxes())
		{
			double extraSize = boxSize.getExtraSize() / 2;
			
			ArrayList<ColoredBox> boxes = new ArrayList<>(creepers.size()
				+ phantoms.size() + wardens.size() + withers.size());
			
			// Add creeper boxes
			int creeperBoxColor = creeperColor.getColorI(0x80);
			for(CreeperEntity e : creepers)
			{
				Box box = EntityUtils.getLerpedBox(e, partialTicks)
					.offset(0, extraSize, 0).expand(extraSize);
				
				// Change color intensity based on creeper fuse time
				float fuseIntensity = e.getClientFuseTime(partialTicks);
				int adjustedColor = fuseIntensity > 0
					? getRawColorForFuse(fuseIntensity, creeperBoxColor)
					: creeperBoxColor;
				
				boxes.add(new ColoredBox(box, adjustedColor));
			}
			// Add warden boxes
			int wardenBoxColor = wardenColor.getColorI(0x80);
			for(WardenEntity e : wardens)
			{
				Box box = EntityUtils.getLerpedBox(e, partialTicks)
					.offset(0, extraSize, 0).expand(extraSize);
				boxes.add(new ColoredBox(box, wardenBoxColor));
			}
			// Add creeper boxes
			int witherBoxColor = witherColor.getColorI(0x80);
			for(WitherEntity e : withers)
			{
				Box box = EntityUtils.getLerpedBox(e, partialTicks)
					.offset(0, extraSize, 0).expand(extraSize);
				
				boxes.add(new ColoredBox(box, witherBoxColor));
			}
			
			// Add phantom boxes
			int phantomBoxColor = phantomColor.getColorI(0x80);
			for(PhantomEntity e : phantoms)
			{
				Box box = EntityUtils.getLerpedBox(e, partialTicks)
					.offset(0, extraSize, 0).expand(extraSize);
				boxes.add(new ColoredBox(box, phantomBoxColor));
			}
			
			int shulkerBoxColor = shulkerColor.getColorI(0x80);
			for(ShulkerEntity e : shulkers)
			{
				Box box = EntityUtils.getLerpedBox(e, partialTicks)
					.offset(0, extraSize, 0).expand(extraSize);
				boxes.add(new ColoredBox(box, shulkerBoxColor));
			}
			RenderUtils.drawOutlinedBoxes(matrixStack, boxes, false);
		}
		
		// Render tracers
		if(style.hasLines())
		{
			ArrayList<ColoredPoint> ends =
				new ArrayList<>(creepers.size() + phantoms.size());
			
			// Add creeper tracers
			int creeperLineColor = creeperColor.getColorI(0x80);
			for(CreeperEntity e : creepers)
			{
				Vec3d point =
					EntityUtils.getLerpedBox(e, partialTicks).getCenter();
				
				// Change color intensity based on creeper fuse time
				float fuseIntensity = e.getClientFuseTime(partialTicks);
				int adjustedColor = fuseIntensity > 0
					? getRawColorForFuse(fuseIntensity, creeperLineColor)
					: creeperLineColor;
				
				ends.add(new ColoredPoint(point, adjustedColor));
			}
			
			// Add phantom tracers
			int wardenLineColor = wardenColor.getColorI(0x80);
			for(WardenEntity e : wardens)
			{
				Vec3d point =
					EntityUtils.getLerpedBox(e, partialTicks).getCenter();
				ends.add(new ColoredPoint(point, wardenLineColor));
			}
			// Add shulker tracers
			int shulkerLineColor = shulkerColor.getColorI(0x80);
			for(ShulkerEntity e : shulkers)
			{
				Vec3d point =
					EntityUtils.getLerpedBox(e, partialTicks).getCenter();
				ends.add(new ColoredPoint(point, shulkerLineColor));
			}
			// Add wither tracers
			int witherLineColor = witherColor.getColorI(0x80);
			for(WitherEntity e : withers)
			{
				Vec3d point =
					EntityUtils.getLerpedBox(e, partialTicks).getCenter();
				ends.add(new ColoredPoint(point, witherLineColor));
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
