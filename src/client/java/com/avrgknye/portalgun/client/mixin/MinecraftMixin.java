package com.avrgknye.portalgun.client.mixin;

import com.avrgknye.portalgun.PortalGunMod;
import com.avrgknye.portalgun.client.PortalGunClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow
    public LocalPlayer player;

    @Unique
    private boolean portalgun$attackLatched;

    /** Left click with the portal gun fires once and never starts block mining. */
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void portalgun$startAttack(CallbackInfoReturnable<Boolean> cir) {
        if (player != null && player.getMainHandItem().is(PortalGunMod.PORTAL_GUN)) {
            if (!portalgun$attackLatched) {
                portalgun$attackLatched = true;
                PortalGunClient.onPrimaryFire();
            }
            cir.setReturnValue(false);
        }
    }

    /** Reset only after the mouse button is released, not while it is held to mine. */
    @Inject(method = "handleKeybinds", at = @At("TAIL"))
    private void portalgun$resetAttackLatch(CallbackInfo ci) {
        if (!Minecraft.getInstance().options.keyAttack.isDown()) {
            portalgun$attackLatched = false;
        }
    }
}
