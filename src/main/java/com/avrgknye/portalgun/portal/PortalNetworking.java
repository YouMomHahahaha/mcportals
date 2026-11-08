package com.avrgknye.portalgun.portal;

import com.avrgknye.portalgun.PortalGunMod;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.UUID;

public final class PortalNetworking {
	/** delay between shots, in ticks (10 = half a second) */
	public static final int COOLDOWN_TICKS = 10;

	// client -> server
	public static final ResourceLocation FIRE = PortalGunMod.id("fire");
	public static final ResourceLocation RESET = PortalGunMod.id("reset");
	// server -> client
	public static final ResourceLocation ADD = PortalGunMod.id("portal_add");
	public static final ResourceLocation REMOVE = PortalGunMod.id("portal_remove");
	public static final ResourceLocation CLEAR = PortalGunMod.id("portal_clear");

	private PortalNetworking() {
	}

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(FIRE, (server, player, handler, buf, responseSender) -> {
			int color = buf.readByte();
			server.execute(() -> PortalManager.fire(player, color));
		});
		ServerPlayNetworking.registerGlobalReceiver(RESET, (server, player, handler, buf, responseSender) ->
				server.execute(() -> PortalManager.reset(player)));
	}

	public static void sendAdd(MinecraftServer server, PortalData p) {
		for (ServerPlayer pl : server.getPlayerList().getPlayers()) {
			if (pl.level().dimension().equals(p.dim)) sendAddTo(pl, p);
		}
	}

	public static void sendAddTo(ServerPlayer pl, PortalData p) {
		FriendlyByteBuf buf = PacketByteBufs.create();
		p.write(buf);
		ServerPlayNetworking.send(pl, ADD, buf);
	}

	/** color -1 removes both portals of that owner */
	public static void sendRemove(MinecraftServer server, ResourceKey<Level> dim, UUID owner, int color) {
		for (ServerPlayer pl : server.getPlayerList().getPlayers()) {
			if (!pl.level().dimension().equals(dim)) continue;
			FriendlyByteBuf buf = PacketByteBufs.create();
			buf.writeUUID(owner);
			buf.writeByte(color);
			ServerPlayNetworking.send(pl, REMOVE, buf);
		}
	}

	public static void sendClear(ServerPlayer pl) {
		ServerPlayNetworking.send(pl, CLEAR, PacketByteBufs.create());
	}
}
