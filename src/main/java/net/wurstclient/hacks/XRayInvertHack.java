/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.util.math.BlockPos;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.*;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.ISimpleOption;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;

@SearchTags({"XRayInvert", "x ray invert", "xi", "reverse xray"})
public final class XRayInvertHack extends Hack implements UpdateListener,
	SetOpaqueCubeListener, GetAmbientOcclusionLightLevelListener,
	ShouldDrawSideListener, RenderBlockEntityListener
{
	private final SliderSetting opacity = new SliderSetting("Opacity",
		"Opacity of non-listed blocks when X-Ray Invert is enabled.\n\n"
			+ "Does not work when Sodium is installed.\n\n"
			+ "Remember to restart X-Ray Invert when changing this setting.",
		0, 0, 0.99, 0.01, ValueDisplay.PERCENTAGE.withLabel(0, "off"));
	
	private final String renderName =
		Math.random() < 0.01 ? "X-Wurst Invert" : getName();
	
	private XRayHack xray;
	
	public XRayInvertHack()
	{
		super("X-Ray Invert");
		setCategory(Category.RENDER);
		addSetting(opacity);
	}
	
	@Override
	public String getRenderName()
	{
		return renderName;
	}
	
	@Override
	protected void onEnable()
	{
		// Get reference to XRayHack
		xray = WURST.getHax().xRayHack;
		
		if(xray.isEnabled())
		{
			// If X-Ray is already enabled, disable it
			xray.setEnabled(false);
		}
		
		// add event listeners
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(SetOpaqueCubeListener.class, this);
		EVENTS.add(GetAmbientOcclusionLightLevelListener.class, this);
		EVENTS.add(ShouldDrawSideListener.class, this);
		EVENTS.add(RenderBlockEntityListener.class, this);
		
		// reload chunks
		MC.worldRenderer.reload();
	}
	
	@Override
	protected void onDisable()
	{
		// remove event listeners
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(SetOpaqueCubeListener.class, this);
		EVENTS.remove(GetAmbientOcclusionLightLevelListener.class, this);
		EVENTS.remove(ShouldDrawSideListener.class, this);
		EVENTS.remove(RenderBlockEntityListener.class, this);
		
		// reload chunks
		MC.worldRenderer.reload();
		
		// reset gamma
		FullbrightHack fullbright = WURST.getHax().fullbrightHack;
		if(!fullbright.isChangingGamma())
			ISimpleOption.get(MC.options.getGamma())
				.forceSetValue(fullbright.getDefaultGamma());
	}
	
	@Override
	public void onUpdate()
	{
		// force gamma to 16 so that blocks are bright enough to see
		ISimpleOption.get(MC.options.getGamma()).forceSetValue(16.0);
	}
	
	@Override
	public void onSetOpaqueCube(SetOpaqueCubeEvent event)
	{
		event.cancel();
	}
	
	@Override
	public void onGetAmbientOcclusionLightLevel(
		GetAmbientOcclusionLightLevelEvent event)
	{
		event.setLightLevel(1);
	}
	
	@Override
	public void onShouldDrawSide(ShouldDrawSideEvent event)
	{
		// Do the opposite of what XRayHack does
		boolean invisible =
			xray.isVisible(event.getState().getBlock(), event.getPos());
		boolean visible = !invisible;
		
		if(!visible && opacity.getValue() > 0)
			return;
		
		event.setRendered(visible);
	}
	
	@Override
	public void onRenderBlockEntity(RenderBlockEntityEvent event)
	{
		BlockPos pos = event.getBlockEntity().getPos();
		// Only hide if XRay would show it
		if(xray.isVisible(BlockUtils.getBlock(pos), pos))
			event.cancel();
	}
	
	public boolean isOpacityMode()
	{
		return isEnabled() && opacity.getValue() > 0;
	}
	
	public int getOpacityColorMask()
	{
		return (int)(opacity.getValue() * 255) << 24 | 0xFFFFFF;
	}
	
	public float getOpacityFloat()
	{
		return opacity.getValueF();
	}
	
	// See AbstractBlockRenderContextMixin, RenderLayersMixin
}
