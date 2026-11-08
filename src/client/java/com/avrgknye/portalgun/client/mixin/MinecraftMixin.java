package com.avrgknye.portalgun.client.mixin;

import com.avrgknye.portalgun.PortalGunMod;
import com.avrgknye.portalgun.client.PortalGunClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Shadow
	public LocalPlayer player;

	/** Left click while holding the portal gun: fire instead of swinging/attacking. */
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void portalgun$startAttack(CallbackInfoReturnable<Boolean> cir) {
		if (player != null && player.getMainHandItem().is(PortalGunMod.PORTAL_GUN)) {
			PortalGunClient.onPrimaryFire();
			cir.setReturnValue(false);
		}
	}
}
