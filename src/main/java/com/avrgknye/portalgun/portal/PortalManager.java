package com.avrgknye.portalgun.portal;

import com.avrgknye.portalgun.PortalGunMod;
import com.avrgknye.portalgun.PortalSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.avrgknye.portalgun.portal.PortalData.vec;

/**
 * Server side: portal placement, validation and linking.
 * The see-through view, the walking through and the collisions are done by the real
 * Immersive Portals entities that {@link IpBridge} creates for every linked pair.
 */
public final class PortalManager {
	private static final Logger LOG = LoggerFactory.getLogger("portalgun");

	/** entity tag put on every Immersive Portals entity created by this mod */
	public static final String IP_TAG = "portalgun_portal";
	private static final double RANGE = 64.0;

	private static final class Owned {
		/** [blue, orange] */
		final PortalData[] data = new PortalData[2];
		/** the Immersive Portals entity standing at blue / at orange */
		final UUID[] ipIds = new UUID[2];

		boolean isLinked() {
			return ipIds[0] != null && ipIds[1] != null;
		}
	}

	private static final Map<UUID, Owned> OWNERS = new HashMap<>();
	private static final Set<UUID> LIVE_IP = new HashSet<>();

	private PortalManager() {
	}

	/** used to clean up leftover portal entities after a server restart */
	public static boolean isLiveIpPortal(UUID id) {
		return LIVE_IP.contains(id);
	}

	// ------------------------------------------------------------------ firing

	public static void fire(ServerPlayer player, int color) {
		if (color < 0 || color > 1) return;
		if (!player.getMainHandItem().is(PortalGunMod.PORTAL_GUN)) return;
		if (player.getCooldowns().isOnCooldown(PortalGunMod.PORTAL_GUN)) return;
		player.getCooldowns().addCooldown(PortalGunMod.PORTAL_GUN, PortalNetworking.COOLDOWN_TICKS);

		ServerLevel level = player.serverLevel();
		MinecraftServer server = level.getServer();
		playAt(level, player.getEyePosition(), color == 0 ? PortalSounds.FIRE_BLUE : PortalSounds.FIRE_RED, 0.7f);
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(RANGE));
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

		Owned owned = OWNERS.computeIfAbsent(player.getUUID(), k -> new Owned());
		PortalData placed = null;
		if (hit.getType() == HitResult.Type.BLOCK) {
			placed = findPlacement(player, level, hit, color, owned);
		}
		if (placed == null) {
			Vec3 at = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : player.position();
			playAt(level, at, PortalSounds.INVALID_SURFACE, 0.5f);
			return;
		}

		PortalData old = owned.data[color];
		if (old != null) { // the old portal of this color closes
			ServerLevel oldLevel = server.getLevel(old.dim);
			if (oldLevel != null) playAt(oldLevel, old.center, color == 0 ? PortalSounds.CLOSE_BLUE : PortalSounds.CLOSE_RED, 0.6f);
		}
		owned.data[color] = placed;
		link(server, owned, player); // also tells the clients about the new portal
		playAt(level, placed.center, color == 0 ? PortalSounds.OPEN_BLUE : PortalSounds.OPEN_RED, 0.6f);
	}

	public static void reset(ServerPlayer player) {
		Owned owned = OWNERS.remove(player.getUUID());
		if (owned == null) return;
		MinecraftServer server = player.getServer();
		removeIp(server, owned);
		for (PortalData p : owned.data) {
			if (p == null) continue;
			PortalNetworking.sendRemove(server, p.dim, p.owner, p.color);
			ServerLevel lvl = server.getLevel(p.dim);
			if (lvl != null) playAt(lvl, p.center, PortalSounds.FIZZLE, 0.5f);
		}
	}

	private static void removePortal(MinecraftServer server, Owned owned, PortalData p) {
		owned.data[p.color] = null;
		PortalNetworking.sendRemove(server, p.dim, p.owner, p.color);
		link(server, owned, null); // one side is gone, so the real portals are removed
		ServerLevel lvl = server.getLevel(p.dim);
		if (lvl != null) playAt(lvl, p.center, p.color == 0 ? PortalSounds.CLOSE_BLUE : PortalSounds.CLOSE_RED, 0.6f);
	}

	// ----------------------------------------------------------------- linking

	/** (Re)creates the real portals. They only exist while both colors are placed. */
	private static void link(MinecraftServer server, Owned owned, ServerPlayer notify) {
		removeIp(server, owned);

		if (owned.data[0] != null && owned.data[1] != null) {
			if (!IpBridge.isAvailable()) {
				if (notify != null) {
					notify.sendSystemMessage(Component.literal(
							"Portal Gun: the Immersive Portals mod is not installed, so portals cannot be see-through or walked through."));
				}
			} else {
				for (int c = 0; c < 2; c++) {
					PortalData from = owned.data[c], to = owned.data[1 - c];
					ServerLevel lvl = server.getLevel(from.dim);
					if (lvl == null) continue;
					try {
						Entity ip = IpBridge.create(lvl, from, to);
						LIVE_IP.add(ip.getUUID());
						lvl.addFreshEntity(ip);
						owned.ipIds[c] = ip.getUUID();
					} catch (Throwable t) {
						LOG.error("Could not create the portal entity", t);
					}
				}
			}
		}

		// tell the clients which portals exist and whether they are linked (see-through) or not
		boolean linked = owned.isLinked();
		for (PortalData p : owned.data) {
			if (p != null) PortalNetworking.sendAdd(server, p, linked);
		}
	}

	private static void removeIp(MinecraftServer server, Owned owned) {
		for (int c = 0; c < 2; c++) {
			UUID id = owned.ipIds[c];
			owned.ipIds[c] = null;
			if (id == null) continue;
			LIVE_IP.remove(id);
			for (ServerLevel lvl : server.getAllLevels()) {
				Entity e = lvl.getEntity(id);
				if (e != null) e.discard();
			}
		}
	}

	// --------------------------------------------------------------- placement

	private static PortalData findPlacement(ServerPlayer player, ServerLevel level, BlockHitResult hit, int color, Owned owned) {
		Direction n = hit.getDirection();
		BlockPos b = hit.getBlockPos();
		// on walls the portal stands upright; on floors/ceilings its top points the way the player faces
		Direction up = n.getAxis().isHorizontal() ? Direction.UP : player.getDirection();

		Vec3 blockCenter = Vec3.atCenterOf(b);
		double t = hit.getLocation().subtract(blockCenter).dot(vec(up));
		double[] offsets = t >= 0 ? new double[]{0.5, -0.5} : new double[]{-0.5, 0.5};

		PortalData other = owned.data[1 - color];

		for (double off : offsets) {
			Vec3 center = blockCenter.add(vec(n).scale(0.5)).add(vec(up).scale(off));
			if (!isValidSpot(level, center, n, up)) continue;
			if (other != null && other.dim.equals(level.dimension()) && other.normal == n
					&& other.center.distanceTo(center) < 1.9) continue; // would overlap the other portal
			return new PortalData(player.getUUID(), color, level.dimension(), center, n, up);
		}
		return null;
	}

	/** Both wall cells must be solid on that face, and the space in front of them must be free. */
	static boolean isValidSpot(Level level, Vec3 center, Direction n, Direction up) {
		Vec3 back = center.subtract(vec(n).scale(0.5));
		int depth = n.getAxis().isVertical() ? 2 : 1; // a standing player needs 2 free blocks above a floor portal
		for (int s = -1; s <= 1; s += 2) {
			BlockPos cell = BlockPos.containing(back.add(vec(up).scale(0.5 * s)));
			if (!level.getBlockState(cell).isFaceSturdy(level, cell, n)) return false;
			for (int k = 1; k <= depth; k++) {
				BlockPos front = cell.relative(n, k);
				if (!level.getBlockState(front).getCollisionShape(level, front).isEmpty()) return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------- sync

	public static void syncTo(ServerPlayer player) {
		PortalNetworking.sendClear(player);
		for (Owned owned : OWNERS.values()) {
			for (PortalData p : owned.data) {
				if (p != null && p.dim.equals(player.level().dimension())) PortalNetworking.sendAddTo(player, p, owned.isLinked());
			}
		}
	}

	// ------------------------------------------------------------------- tick

	/** Every half second, check that the walls behind the portals still exist. */
	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 10 != 0) return;

		List<Object[]> dead = new ArrayList<>();
		for (Owned owned : OWNERS.values()) {
			for (PortalData p : owned.data) {
				if (p == null) continue;
				ServerLevel lvl = server.getLevel(p.dim);
				if (lvl == null || !lvl.isLoaded(BlockPos.containing(p.center))) continue;
				if (!isValidSpot(lvl, p.center, p.normal, p.up)) dead.add(new Object[]{owned, p});
			}
		}
		for (Object[] d : dead) removePortal(server, (Owned) d[0], (PortalData) d[1]);
	}

	/** plays a sound with the small random pitch change (95%..105%) that the original game uses */
	private static void playAt(ServerLevel level, Vec3 pos, SoundEvent sound, float volume) {
		float pitch = 0.95f + level.getRandom().nextFloat() * 0.1f;
		level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
	}
}
