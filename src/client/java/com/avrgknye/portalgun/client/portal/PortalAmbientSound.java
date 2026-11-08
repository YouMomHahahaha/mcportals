package com.avrgknye.portalgun.client.portal;

import com.avrgknye.portalgun.PortalSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** The soft looping hum next to an open portal. Stops when the portal closes. */
public class PortalAmbientSound extends AbstractTickableSoundInstance {
	private final ClientPortals.Portal portal;

	public PortalAmbientSound(ClientPortals.Portal portal) {
		super(PortalSounds.AMBIENT_LOOP, SoundSource.BLOCKS, RandomSource.create());
		this.portal = portal;
		this.looping = true;
		this.delay = 0;
		this.volume = 0.45f;
		this.pitch = 1.1f;
		this.x = portal.center.x;
		this.y = portal.center.y;
		this.z = portal.center.z;
	}

	@Override
	public void tick() {
		if (portal.removeMs != 0 || Minecraft.getInstance().level == null) {
			stop();
		}
	}
}
