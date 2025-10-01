/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.CameraTransformViewBobbingListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EspBoxSizeSetting;
import net.wurstclient.settings.EspStyleSetting;
import net.wurstclient.settings.EspStyleSetting.EspStyle;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.settings.filters.FilterInvisibleSetting;
import net.wurstclient.settings.filters.FilterSleepingSetting;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.FakePlayerEntity;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RenderUtils.ColoredBox;
import net.wurstclient.util.RenderUtils.ColoredPoint;

import java.awt.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SearchTags({"player esp", "PlayerTracers", "player tracers"})
public final class PlayerEspHack extends Hack implements UpdateListener,
	CameraTransformViewBobbingListener, RenderListener
{
	private final EspStyleSetting style =
		new EspStyleSetting(EspStyle.LINES_AND_BOXES);
	
	private final EspBoxSizeSetting boxSize = new EspBoxSizeSetting(
		"\u00a7lAccurate\u00a7r mode shows the exact hitbox of each player.\n"
			+ "\u00a7lFancy\u00a7r mode shows slightly larger boxes that look better.");
	
	private final CheckboxSetting soundAlerts = new CheckboxSetting(
		"Sound Alerts",
		"Plays a system beep sound when players come within the alert distance.",
		true);
	
	private final CheckboxSetting titleAlerts = new CheckboxSetting(
		"Title Alerts",
		"Displays a title message when players come within the alert distance.",
		true);
	
	private final CheckboxSetting gameplaySounds =
		new CheckboxSetting("Gameplay Sounds",
			"Plays Minecraft sounds when players are detected.", true);
	
	private final SliderSetting alertDistance =
		new SliderSetting("Alert Distance",
			"Distance in blocks at which alerts will trigger for players.", 20,
			5, 100, 1, SliderSetting.ValueDisplay.DECIMAL);
	
	private final EntityFilterList entityFilters = new EntityFilterList(
		new FilterSleepingSetting("Won't show sleeping players.", false),
		new FilterInvisibleSetting("Won't show invisible players.", false));
	
	private final ArrayList<PlayerEntity> players = new ArrayList<>();
	
	// Track player UUIDs we've already alerted about
	private final Set<UUID> alertedPlayers = new HashSet<>();
	
	// Random number generator for varying sounds
	private final Random random = new Random();
	
	// Cooldown for title alerts (in milliseconds)
	private long lastTitleTime = 0;
	private static final long TITLE_COOLDOWN = 2000; // 2 seconds
	
	public PlayerEspHack()
	{
		super("PlayerESP");
		setCategory(Category.RENDER);
		addSetting(style);
		addSetting(boxSize);
		addSetting(soundAlerts);
		addSetting(titleAlerts);
		addSetting(gameplaySounds);
		addSetting(alertDistance);
		entityFilters.forEach(this::addSetting);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(CameraTransformViewBobbingListener.class, this);
		EVENTS.add(RenderListener.class, this);
		
		// Clear the alerted players set when the hack is enabled
		alertedPlayers.clear();
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
		players.clear();
		
		Stream<AbstractClientPlayerEntity> stream = MC.world.getPlayers()
			.parallelStream().filter(e -> !e.isRemoved() && e.getHealth() > 0)
			.filter(e -> e != MC.player)
			.filter(e -> !(e instanceof FakePlayerEntity))
			.filter(e -> Math.abs(e.getY() - MC.player.getY()) <= 1e6);
		
		stream = entityFilters.applyTo(stream);
		
		players.addAll(stream.collect(Collectors.toList()));
		
		// Alert logic
		boolean anySoundAlerts = soundAlerts.isChecked();
		boolean anyTitleAlerts = titleAlerts.isChecked();
		boolean anyGameplaySounds = gameplaySounds.isChecked();
		
		// Only process if any alert type is enabled
		if(anySoundAlerts || anyTitleAlerts || anyGameplaySounds)
		{
			// Process alerts for each player
			for(PlayerEntity player : players)
			{
				float distance = MC.player.distanceTo(player);
				
				// Check if player is within alert distance
				if(distance <= alertDistance.getValueF())
				{
					// Check if we haven't alerted about this player yet
					if(!alertedPlayers.contains(player.getUuid()))
					{
						// Play system beep if enabled
						if(anySoundAlerts)
							Toolkit.getDefaultToolkit().beep();
						
						// Show title alert if enabled
						if(anyTitleAlerts)
							showTitleAlert(player);
						
						// Play gameplay sounds if enabled
						if(anyGameplaySounds)
							playAlertSounds();
						
						// Add player to alerted set
						alertedPlayers.add(player.getUuid());
					}
				}else
				{
					// If the player moves out of range, remove them from the
					// alerted set so they'll trigger another alert if they come
					// back in
					// range
					alertedPlayers.remove(player.getUuid());
				}
			}
			
			// Remove UUIDs of players who are no longer present
			alertedPlayers.removeIf(uuid -> players.stream()
				.noneMatch(p -> p.getUuid().equals(uuid)));
		}
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
		if(style.hasBoxes())
		{
			double extraSize = boxSize.getExtraSize() / 2;
			
			ArrayList<ColoredBox> boxes = new ArrayList<>(players.size());
			for(PlayerEntity e : players)
			{
				Box box = EntityUtils.getLerpedBox(e, partialTicks)
					.offset(0, extraSize, 0).expand(extraSize);
				boxes.add(new ColoredBox(box, getColor(e)));
			}
			
			RenderUtils.drawOutlinedBoxes(matrixStack, boxes, false);
		}
		
		if(style.hasLines())
		{
			ArrayList<ColoredPoint> ends = new ArrayList<>(players.size());
			for(PlayerEntity e : players)
			{
				Vec3d point =
					EntityUtils.getLerpedBox(e, partialTicks).getCenter();
				ends.add(new ColoredPoint(point, getColor(e)));
			}
			
			RenderUtils.drawTracers(matrixStack, partialTicks, ends, false);
		}
	}
	
	private int getColor(PlayerEntity e)
	{
		if(WURST.getFriends().contains(e.getName().getString()))
			return 0x800000FF;
		
		float f = MC.player.distanceTo(e) / 20F;
		float r = MathHelper.clamp(2 - f, 0, 1);
		float g = MathHelper.clamp(f, 0, 1);
		float[] rgb = {r, g, 0};
		return RenderUtils.toIntColor(rgb, 0.5F);
	}
	
	/**
	 * Displays a title message on the player's screen
	 */
	private void showTitleAlert(PlayerEntity detectedPlayer)
	{
		// Check if it's been long enough since the last title message
		long currentTime = System.currentTimeMillis();
		if(currentTime - lastTitleTime < TITLE_COOLDOWN)
			return;
		
		lastTitleTime = currentTime;
		
		String playerName = detectedPlayer.getName().getString();
		String distance =
			String.format("%.1f", MC.player.distanceTo(detectedPlayer));
		
		// Show title and subtitle
		MC.inGameHud.setTitle(Text.literal("§c§lPLAYER DETECTED"));
		MC.inGameHud.setSubtitle(Text.literal(
			"§e" + playerName + " §f- §e" + distance + " blocks away"));
		MC.inGameHud.setTitleTicks(10, 30, 10); // fade in, stay, fade out
	}
	
	/**
	 * Plays multiple Minecraft sounds in different categories
	 */
	private void playAlertSounds()
	{
		if(MC.player == null || MC.world == null)
			return;
			
		// Simply use the player's built-in playSound method which is designed
		// for client use
		MC.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F,
			1.0F);
		MC.player.playSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 0.3F, 1.0F);
		MC.player.playSound(SoundEvents.BLOCK_ANVIL_LAND, 0.3F, 2.0F);
		
		// Play a random additional sound for variety
		if(random.nextBoolean())
			MC.player.playSound(SoundEvents.ENTITY_WITHER_SPAWN, 0.15F, 0.5F);
		else
			MC.player.playSound(SoundEvents.ENTITY_BLAZE_AMBIENT, 0.3F, 0.5F);
	}
}
