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
import net.minecraft.server.packs.PackType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.phys.Vec3;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class PortalGunClient implements ClientModInitializer {

    private static final Logger LOG = LoggerFactory.getLogger("portalgun");

    private static final List<Object> PENDING_PORTAL_DEBUG = new ArrayList<>();

    public static KeyMapping resetKey;
    private boolean wasHolding = false;

    @Override
    public void onInitializeClient() {

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(new PortalGunModelLoader());

        BuiltinItemRendererRegistry.INSTANCE.register(
                PortalGunMod.PORTAL_GUN,
                new PortalGunRenderer()
        );

        resetKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping(
                        "key.portalgun.reset",
                        GLFW.GLFW_KEY_R,
                        "key.categories.portalgun"
                )
        );

        registerImmersivePortalsDebug();

        UseItemCallback.EVENT.register((player, world, hand) -> {

            var stack = player.getItemInHand(hand);

            if (world.isClientSide
                    && hand == InteractionHand.MAIN_HAND
                    && stack.is(PortalGunMod.PORTAL_GUN)) {

                onSecondaryFire();
                return InteractionResultHolder.fail(stack);
            }

            return InteractionResultHolder.pass(stack);
        });

        AttackBlockCallback.EVENT.register(
                (player, world, hand, pos, direction) -> {

                    if (world.isClientSide
                            && player.getMainHandItem().is(PortalGunMod.PORTAL_GUN)) {

                        onPrimaryFire();
                        return InteractionResult.FAIL;
                    }

                    return InteractionResult.PASS;
                }
        );

        AttackEntityCallback.EVENT.register(
                (player, world, hand, entity, hitResult) -> {

                    if (world.isClientSide
                            && player.getMainHandItem().is(PortalGunMod.PORTAL_GUN)) {

                        onPrimaryFire();
                        return InteractionResult.FAIL;
                    }

                    return InteractionResult.PASS;
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(
                PortalNetworking.ADD,
                (client, handler, buf, sender) -> {

                    UUID owner = buf.readUUID();
                    int color = buf.readByte();

                    Vec3 center = new Vec3(
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readDouble()
                    );

                    Direction normal = Direction.from3DDataValue(buf.readByte());
                    Direction up = Direction.from3DDataValue(buf.readByte());

                    boolean linked = buf.readBoolean();

                    client.execute(() ->
                            ClientPortals.add(
                                    owner,
                                    color,
                                    center,
                                    normal,
                                    up,
                                    linked
                            )
                    );
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(
                PortalNetworking.REMOVE,
                (client, handler, buf, sender) -> {

                    UUID owner = buf.readUUID();
                    int color = buf.readByte();

                    client.execute(() ->
                            ClientPortals.remove(owner, color)
                    );
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(
                PortalNetworking.CLEAR,
                (client, handler, buf, sender) ->
                        client.execute(ClientPortals::clear)
        );

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientPortals.clear();
            PENDING_PORTAL_DEBUG.clear();
        });

        WorldRenderEvents.AFTER_ENTITIES.register(
                PortalWorldRenderer::render
        );

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    /**
     * Registers a debug listener for Immersive Portals.
     */
    private static void registerImmersivePortalsDebug() {

        try {

            Class<?> ipClass = Class.forName(
                    "qouteall.imm_ptl.core.network.IPNetworkingClient"
            );

            Object signal = ipClass
                    .getField("clientPortalSpawnSignal")
                    .get(null);

            Consumer<Object> listener = entity -> {

                try {

                    Vec3 camera = Minecraft.getInstance()
                            .gameRenderer
                            .getMainCamera()
                            .getPosition();

                    Class<?> cls = entity.getClass();

                    Object id = cls.getMethod("getId")
                            .invoke(entity);

                    Object position = cls.getMethod("position")
                            .invoke(entity);

                    Object normal = cls.getMethod("getNormal")
                            .invoke(entity);

                    Object valid = cls.getMethod("isPortalValid")
                            .invoke(entity);

                    Object visible = cls.getMethod("isVisible")
                            .invoke(entity);

                    Object inFront = cls.getMethod(
                            "isInFrontOfPortal",
                            Vec3.class
                    ).invoke(entity, camera);

                    Object distance = cls.getMethod(
                            "getDistanceToPlane",
                            Vec3.class
                    ).invoke(entity, camera);

                    Object axisW = cls.getField("axisW")
                            .get(entity);

                    Object axisH = cls.getField("axisH")
                            .get(entity);

                    Object destination = cls.getField("destination")
                            .get(entity);

                    Object dimension = cls.getField("dimensionTo")
                            .get(entity);

                    LOG.info(
                            "[IP DEBUG] id={}, valid={}, visible={}, inFront={}, distance={}, normal={}, axisW={}, axisH={}, camera={}, position={}, destination={}, dimension={}",
                            id,
                            valid,
                            visible,
                            inFront,
                            distance,
                            normal,
                            axisW,
                            axisH,
                            camera,
                            position,
                            destination,
                            dimension
                    );

                    PENDING_PORTAL_DEBUG.add(entity);

                } catch (Throwable t) {

                    LOG.error("[IP DEBUG] Could not inspect portal", t);
                }
            };

            Method connect = signal.getClass().getMethod(
                    "connect",
                    Consumer.class
            );

            connect.invoke(signal, listener);

            LOG.info("[IP DEBUG] Listener registered");

        } catch (Throwable t) {

            LOG.error("[IP DEBUG] Could not register listener", t);
        }
    }

    /**
     * Runs one tick after a portal is received.
     * Checks whether the entity is actually registered in the client world
     * and included in the entity rendering list.
     */
    private static void debugPendingPortals(Minecraft mc) {

        if (mc.level == null || PENDING_PORTAL_DEBUG.isEmpty()) {
            return;
        }

        List<Object> pending = new ArrayList<>(PENDING_PORTAL_DEBUG);
        PENDING_PORTAL_DEBUG.clear();

        for (Object entity : pending) {

            try {

                Class<?> cls = entity.getClass();

                Object id = cls.getMethod("getId")
                        .invoke(entity);

                Object alive = invokeOptional(
                        entity,
                        "isAlive"
                );

                Object removed = invokeOptional(
                        entity,
                        "isRemoved"
                );

                Object entityLevel = invokeOptional(
                        entity,
                        "level"
                );

                Object worldEntity = invokeOptional(
                        mc.level,
                        "getEntity",
                        new Class<?>[]{int.class},
                        ((Number) id).intValue()
                );

                boolean inWorld = worldEntity == entity;
                boolean sameLevel = entityLevel == mc.level;

                boolean inRenderList = false;
                Object renderListResult = "unavailable";

                try {

                    renderListResult = invokeOptional(
                            mc.level,
                            "entitiesForRendering"
                    );

                    if (renderListResult instanceof Iterable<?> iterable) {

                        for (Object worldEntityEntry : iterable) {

                            if (worldEntityEntry == entity) {
                                inRenderList = true;
                                break;
                            }
                        }
                    }

                } catch (Throwable ignored) {
                    renderListResult = "error";
                }

                Vec3 camera = mc.gameRenderer
                        .getMainCamera()
                        .getPosition();

                Object roughlyVisible = invokeOptional(
                        entity,
                        "isRoughlyVisibleTo",
                        new Class<?>[]{Vec3.class},
                        camera
                );

                Object distance = invokeOptional(
                        entity,
                        "getDistanceToPlane",
                        new Class<?>[]{Vec3.class},
                        camera
                );

                Object boundingBox = invokeOptional(
                        entity,
                        "getBoundingBox"
                );

                Object rotation = readField(cls, entity, "rotation");

                Object teleportable = readField(
                        cls,
                        entity,
                        "teleportable"
                );

                Object specialShape = readField(
                        cls,
                        entity,
                        "specialShape"
                );

                Object cameraLook = invokeOptional(
                        mc.gameRenderer.getMainCamera(),
                        "getLookVector"
                );

                LOG.info(
                        "[IP WORLD DEBUG] id={}, alive={}, removed={}, sameLevel={}, inWorld={}, inRenderList={}, roughlyVisible={}, distance={}, rotation={}, teleportable={}, specialShapePresent={}, boundingBox={}, camera={}, cameraLook={}, renderListResult={}",
                        id,
                        alive,
                        removed,
                        sameLevel,
                        inWorld,
                        inRenderList,
                        roughlyVisible,
                        distance,
                        rotation,
                        teleportable,
                        specialShape != null,
                        boundingBox,
                        camera,
                        cameraLook,
                        renderListResult
                );

            } catch (Throwable t) {

                LOG.error(
                        "[IP WORLD DEBUG] Could not inspect client world entity",
                        t
                );
            }
        }
    }

    private static Object invokeOptional(
            Object target,
            String methodName,
            Class<?>[] parameterTypes,
            Object... args
    ) {

        if (target == null) {
            return "null";
        }

        try {

            Method method = target.getClass().getMethod(
                    methodName,
                    parameterTypes
            );

            return method.invoke(target, args);

        } catch (Throwable t) {

            return "unavailable";
        }
    }

    private static Object invokeOptional(
            Object target,
            String methodName
    ) {
        return invokeOptional(
                target,
                methodName,
                new Class<?>[0]
        );
    }

    private static Object readField(
            Class<?> cls,
            Object target,
            String fieldName
    ) {

        try {
            return cls.getField(fieldName).get(target);
        } catch (Throwable t) {
            return "unavailable";
        }
    }

    private void tick(Minecraft mc) {

        debugPendingPortals(mc);

        boolean holding = mc.player != null
                && mc.player.getMainHandItem().is(PortalGunMod.PORTAL_GUN);

        if (holding && !wasHolding) {

            PortalGunAnimator.play("draw");

            mc.getSoundManager().play(
                    SimpleSoundInstance.forUI(
                            PortalSounds.POWERUP,
                            1.0f,
                            0.7f
                    )
            );
        }

        wasHolding = holding;

        while (resetKey.consumeClick()) {

            if (holding) {
                onReset();
            }
        }
    }

    /** Left click: blue portal. */
    public static void onPrimaryFire() {
        fire(0);
    }

    /** Right click: orange portal. */
    public static void onSecondaryFire() {
        fire(1);
    }

    private static void fire(int color) {

        LocalPlayer player = Minecraft.getInstance().player;

        if (player == null) {
            return;
        }

        if (player.getCooldowns().isOnCooldown(PortalGunMod.PORTAL_GUN)) {
            return;
        }

        player.getCooldowns().addCooldown(
                PortalGunMod.PORTAL_GUN,
                PortalNetworking.COOLDOWN_TICKS
        );

        PortalGunAnimator.play("fire1");

        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeByte(color);

        ClientPlayNetworking.send(
                PortalNetworking.FIRE,
                buf
        );
    }

    /** R key: remove both portals. */
    public static void onReset() {

        LocalPlayer player = Minecraft.getInstance().player;

        if (player != null
                && ClientPortals.hasOwn(player.getUUID())) {

            PortalGunAnimator.play("fizzle");
        }

        ClientPlayNetworking.send(
                PortalNetworking.RESET,
                PacketByteBufs.create()
        );
    }
}