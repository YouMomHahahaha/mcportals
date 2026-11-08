package com.avrgknye.portalgun.client;

import com.avrgknye.portalgun.PortalGunMod;
import com.avrgknye.portalgun.PortalSounds;
import com.avrgknye.portalgun.client.model.PortalGunModelLoader;
import com.avrgknye.portalgun.client.portal.ClientPortals;
import com.avrgknye.portalgun.client.portal.PortalWorldRenderer;
import com.avrgknye.portalgun.client.render.PortalGunAnimator;
import com.avrgknye.portalgun.client.render.PortalGunRenderer;
import com.avrgknye.portalgun.portal.PortalNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PortalGunClient implements ClientModInitializer {
	private static final Logger LOG = LoggerFactory.getLogger("portalgun");
	public static KeyMapping resetKey;
	private boolean wasHolding = false;

	@Override
	public void onInitializeClient() {
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new PortalGunModelLoader());
		BuiltinItemRendererRegistry.INSTANCE.register(PortalGunMod.PORTAL_GUN, new PortalGunRenderer());

		resetKey = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.portalgun.reset", GLFW.GLFW_KEY_R, "key.categories.portalgun"));

		// Right click = secondary fire (orange portal). Returning FAIL stops vanilla's swing/equip bob.
		UseItemCallback.EVENT.register((player, world, hand) -> {
			var stack = player.getItemInHand(hand);
			if (world.isClientSide && hand == InteractionHand.MAIN_HAND && stack.is(PortalGunMod.PORTAL_GUN)) {
				onSecondaryFire();
				return InteractionResultHolder.fail(stack);
			}
			return InteractionResultHolder.pass(stack);
		});

		// Left click fallbacks (the mixin handles clicks on air; these catch clicks aimed at blocks / mobs)
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (world.isClientSide && player.getMainHandItem().is(PortalGunMod.PORTAL_GUN)) {
				onPrimaryFire();
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClientSide && player.getMainHandItem().is(PortalGunMod.PORTAL_GUN)) {
				onPrimaryFire();
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});

		// portals sent by the server
		ClientPlayNetworking.registerGlobalReceiver(PortalNetworking.ADD, (client, handler, buf, sender) -> {
			UUID owner = buf.readUUID();
			int color = buf.readByte();
			Vec3 center = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
			Direction normal = Direction.from3DDataValue(buf.readByte());
			Direction up = Direction.from3DDataValue(buf.readByte());
			boolean linked = buf.readBoolean();
			client.execute(() -> ClientPortals.add(owner, color, center, normal, up, linked));
		});
		ClientPlayNetworking.registerGlobalReceiver(PortalNetworking.REMOVE, (client, handler, buf, sender) -> {
			UUID owner = buf.readUUID();
			int color = buf.readByte();
			client.execute(() -> ClientPortals.remove(owner, color));
		});
		ClientPlayNetworking.registerGlobalReceiver(PortalNetworking.CLEAR, (client, handler, buf, sender) ->
				client.execute(ClientPortals::clear));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientPortals.clear());

		WorldRenderEvents.AFTER_ENTITIES.register(PortalWorldRenderer::render);

		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private void tick(Minecraft mc) {
		boolean holding = mc.player != null && mc.player.getMainHandItem().is(PortalGunMod.PORTAL_GUN);
		if (holding && !wasHolding) {
			PortalGunAnimator.play("draw");
			mc.getSoundManager().play(SimpleSoundInstance.forUI(PortalSounds.POWERUP, 1.0f, 0.7f));
		}
		wasHolding = holding;
		while (resetKey.consumeClick()) {
			if (holding) onReset();
		}
	}

	/** Left click (called from the mixin): blue portal. */
	public static void onPrimaryFire() {
		fire(0);
	}

	/** Right click: orange portal. */
	public static void onSecondaryFire() {
		fire(1);
	}

	private static void fire(int color) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) return;
		// cooldown: ignore clicks while the gun is still recharging (the server enforces it too)
		if (player.getCooldowns().isOnCooldown(PortalGunMod.PORTAL_GUN)) return;
		player.getCooldowns().addCooldown(PortalGunMod.PORTAL_GUN, PortalNetworking.COOLDOWN_TICKS);

		PortalGunAnimator.play("fire1");
		FriendlyByteBuf buf = PacketByteBufs.create();
		buf.writeByte(color);
		ClientPlayNetworking.send(PortalNetworking.FIRE, buf);
	}

	/** R key: remove both portals. */
	public static void onReset() {
		// like the original: the fizzle animation only plays if there was a portal to remove
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && ClientPortals.hasOwn(player.getUUID())) PortalGunAnimator.play("fizzle");
		ClientPlayNetworking.send(PortalNetworking.RESET, PacketByteBufs.create());
	}
}
