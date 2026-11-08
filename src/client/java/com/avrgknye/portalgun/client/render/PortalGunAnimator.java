package com.avrgknye.portalgun.client.render;

import com.avrgknye.portalgun.client.model.PortalGunModel;

/** Client-side animation state of the gun (which sequence is playing and since when). */
public final class PortalGunAnimator {
	private static String current = "idle";
	private static long startNs = System.nanoTime();

	private PortalGunAnimator() {
	}

	public static void play(String name) {
		current = name;
		startNs = System.nanoTime();
	}

	private static boolean loops(String name) {
		return name.equals("idle") || name.equals("lowidle");
	}

	/** Returns the skinning matrices for "now". */
	public static float[] sample(PortalGunModel model) {
		PortalGunModel.Anim anim = model.anims.get(current);
		if (anim == null) {
			current = "idle";
			anim = model.anims.get("idle");
		}
		if (anim == null) return model.computeSkin(null, 0f);

		float frame = (System.nanoTime() - startNs) / 1.0e9f * anim.fps;
		if (anim.frames <= 1) {
			frame = 0f;
		} else if (loops(current)) {
			frame = frame % anim.frames;
		} else if (frame >= anim.frames - 1) {
			// one-shot finished: go back to idle
			current = "idle";
			anim = model.anims.get("idle");
			frame = 0f;
			startNs = System.nanoTime();
			if (anim == null) return model.computeSkin(null, 0f);
		}
		return model.computeSkin(anim, frame);
	}

	/** Rest/idle pose for item icons, dropped items etc. */
	public static float[] idlePose(PortalGunModel model) {
		return model.computeSkin(model.anims.get("idle"), 0f);
	}
}
