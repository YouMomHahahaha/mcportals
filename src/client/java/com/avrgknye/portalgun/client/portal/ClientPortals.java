package com.avrgknye.portalgun.client.portal;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Portals the client knows about (sent by the server), plus the ones that are just closing. */
public final class ClientPortals {
	public static final class Portal {
		public final UUID owner;
		public final int color;
		public final Vec3 center;
		public final Direction normal, up, right;
		public final long spawnMs;
		public long removeMs = 0;
		/** both portals exist: the real see-through view is shown inside, so only the rim is drawn */
		public boolean linked = false;

		Portal(UUID owner, int color, Vec3 center, Direction normal, Direction up) {
			this.owner = owner;
			this.color = color;
			this.center = center;
			this.normal = normal;
			this.up = up;
			Vec3i c = up.getNormal().cross(normal.getNormal());
			this.right = Direction.fromDelta(c.getX(), c.getY(), c.getZ());
			this.spawnMs = System.currentTimeMillis();
		}
	}

	public static final Map<String, Portal> ACTIVE = new HashMap<>();
	public static final List<Portal> CLOSING = new ArrayList<>();

	private ClientPortals() {
	}

	private static String key(UUID owner, int color) {
		return owner + ":" + color;
	}

	public static void add(UUID owner, int color, Vec3 center, Direction normal, Direction up, boolean linked) {
		Portal existing = ACTIVE.get(key(owner, color));
		if (existing != null && existing.center.equals(center) && existing.normal == normal && existing.up == up) {
			existing.linked = linked; // same portal, only the link state changed: no new open animation
			return;
		}
		Portal fresh = new Portal(owner, color, center, normal, up);
		fresh.linked = linked;
		Portal old = ACTIVE.put(key(owner, color), fresh);
		Minecraft.getInstance().getSoundManager().play(new PortalAmbientSound(fresh));
		if (old != null) {
			old.removeMs = System.currentTimeMillis();
			CLOSING.add(old);
		}
	}

	/** color -1 removes both */
	public static void remove(UUID owner, int color) {
		for (int c = 0; c < 2; c++) {
			if (color != -1 && color != c) continue;
			Portal p = ACTIVE.remove(key(owner, c));
			if (p != null) {
				p.removeMs = System.currentTimeMillis();
				CLOSING.add(p);
			}
		}
	}

	/** does this player currently have at least one portal open? */
	public static boolean hasOwn(UUID owner) {
		return ACTIVE.containsKey(key(owner, 0)) || ACTIVE.containsKey(key(owner, 1));
	}

	public static void clear() {
		ACTIVE.clear();
		CLOSING.clear();
	}
}
