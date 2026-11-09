package com.avrgknye.portalgun;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** The sound events. The audio files and the mapping are in assets/portalgun/sounds and sounds.json. */
public final class PortalSounds {
	public static final SoundEvent FIRE_BLUE = event("weapon.fire_blue");
	public static final SoundEvent FIRE_RED = event("weapon.fire_red");
	public static final SoundEvent POWERUP = event("weapon.powerup");
	public static final SoundEvent OPEN_BLUE = event("portal.open_blue");
	public static final SoundEvent OPEN_RED = event("portal.open_red");
	public static final SoundEvent CLOSE_BLUE = event("portal.close_blue");
	public static final SoundEvent CLOSE_RED = event("portal.close_red");
	public static final SoundEvent FIZZLE = event("portal.fizzle");
	public static final SoundEvent INVALID_SURFACE = event("portal.invalid_surface");
	public static final SoundEvent AMBIENT_LOOP = event("portal.ambient_loop");
	public static final SoundEvent ENTER_PORTAL = event("player.enter_portal");
	public static final SoundEvent EXIT_PORTAL = event("player.exit_portal");

	private PortalSounds() {
	}

	private static SoundEvent event(String path) {
		return SoundEvent.createVariableRangeEvent(PortalGunMod.id(path));
	}

	public static void register() {
		register("weapon.fire_blue", FIRE_BLUE);
		register("weapon.fire_red", FIRE_RED);
		register("weapon.powerup", POWERUP);
		register("portal.open_blue", OPEN_BLUE);
		register("portal.open_red", OPEN_RED);
		register("portal.close_blue", CLOSE_BLUE);
		register("portal.close_red", CLOSE_RED);
		register("portal.fizzle", FIZZLE);
		register("portal.invalid_surface", INVALID_SURFACE);
		register("portal.ambient_loop", AMBIENT_LOOP);
		register("player.enter_portal", ENTER_PORTAL);
		register("player.exit_portal", EXIT_PORTAL);
	}

	private static void register(String path, SoundEvent sound) {
		ResourceLocation id = PortalGunMod.id(path);
		Registry.register(BuiltInRegistries.SOUND_EVENT, id, sound);
	}
}
