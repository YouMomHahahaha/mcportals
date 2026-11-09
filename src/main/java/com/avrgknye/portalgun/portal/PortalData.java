package com.avrgknye.portalgun.portal;

import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * One portal: a 1 x 2 block window lying flat on a block face.
 * All three axes are cardinal directions: normal (out of the wall), up (the 2-block axis), right (= up x normal).
 */
public final class PortalData {
	public static final double WIDTH = 1.0;
	public static final double HEIGHT = 2.0;

	public final UUID owner;
	/** 0 = blue, 1 = orange */
	public final int color;
	public final ResourceKey<Level> dim;
	/** centre of the window, on the wall surface */
	public final Vec3 center;
	public final Direction normal, up, right;

	public PortalData(UUID owner, int color, ResourceKey<Level> dim, Vec3 center, Direction normal, Direction up) {
		this.owner = owner;
		this.color = color;
		this.dim = dim;
		this.center = center;
		this.normal = normal;
		this.up = up;
		Vec3i c = up.getNormal().cross(normal.getNormal());
		this.right = Direction.fromDelta(c.getX(), c.getY(), c.getZ());
	}

	public static Vec3 vec(Direction d) {
		return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeUUID(owner);
		buf.writeByte(color);
		buf.writeDouble(center.x);
		buf.writeDouble(center.y);
		buf.writeDouble(center.z);
		buf.writeByte(normal.get3DDataValue());
		buf.writeByte(up.get3DDataValue());
	}
}
