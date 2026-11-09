package com.avrgknye.portalgun.client.mixin;

import com.avrgknye.portalgun.PortalSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
        targets = "qouteall.imm_ptl.core.teleportation.ClientTeleportationManager",
        remap = false
)
public abstract class PortalTeleportSoundMixin {

    @Unique
    private static Object portalgun$pendingPortal;

    @Inject(
            method = "teleportPlayer",
            at = @At("HEAD"),
            remap = false
    )
    private static void portalgun$resetPendingPortal(
            @Coerce Object teleportation,
            float partialTicks,
            CallbackInfo ci
    ) {
        portalgun$pendingPortal = null;
    }

    @Inject(
            method = "teleportPlayer",
            at = @At(
                    value = "FIELD",
                    target = "Lqouteall/imm_ptl/core/teleportation/ClientTeleportationManager;lastTeleportGameTime:J",
                    opcode = Opcodes.PUTSTATIC,
                    shift = At.Shift.AFTER
            ),
            remap = false
    )
    private static void portalgun$playEnterSound(
            @Coerce Object teleportation,
            float partialTicks,
            CallbackInfo ci
    ) {
        Object portal = invoke(teleportation, "portal");

        if (portal == null) {
            return;
        }

        portalgun$pendingPortal = portal;

        Vec3 origin = readPosition(portal, "getOriginPos");

        if (origin != null) {
            playAt(PortalSounds.ENTER_PORTAL, origin);
        }
    }

    @Inject(
            method = "teleportPlayer",
            at = @At("TAIL"),
            remap = false
    )
    private static void portalgun$playExitSound(
            @Coerce Object teleportation,
            float partialTicks,
            CallbackInfo ci
    ) {
        Object portal = portalgun$pendingPortal;
        portalgun$pendingPortal = null;

        if (portal == null) {
            return;
        }

        Vec3 destination = readPosition(portal, "getDestPos");

        if (destination != null) {
            playAt(PortalSounds.EXIT_PORTAL, destination);
        }
    }

    private static void playAt(SoundEvent sound, Vec3 position) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.level == null) {
            return;
        }

        mc.getSoundManager().play(
                new SimpleSoundInstance(
                        sound,
                        SoundSource.PLAYERS,
                        0.9f,
                        1.0f,
                        RandomSource.create(),
                        position.x,
                        position.y,
                        position.z
                )
        );
    }

    private static Object invoke(Object target, String methodName) {
        if (target == null) {
            return null;
        }

        try {
            return target.getClass()
                    .getMethod(methodName)
                    .invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Vec3 readPosition(Object portal, String methodName) {
        Object value = invoke(portal, methodName);
        return value instanceof Vec3 position ? position : null;
    }
}
