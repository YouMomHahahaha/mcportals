package com.avrgknye.portalgun.client.portal;

import com.avrgknye.portalgun.PortalGunMod;
import com.avrgknye.portalgun.portal.PortalData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Iterator;

import static com.avrgknye.portalgun.portal.PortalData.vec;

/** Draws the portals in the world as glowing ovals that open and close with a short animation. */
public final class PortalWorldRenderer {
	private static final ResourceLocation TEX_BLUE = PortalGunMod.id("textures/portal/portal_blue.png");
	private static final ResourceLocation TEX_ORANGE = PortalGunMod.id("textures/portal/portal_orange.png");
	private static final float OPEN_MS = 260f;
	private static final float CLOSE_MS = 180f;
	/** lift off the wall a little so the quad does not z-fight with the block */
	private static final double OFFSET = 0.012;

	private PortalWorldRenderer() {
	}

	public static void render(WorldRenderContext ctx) {
		MultiBufferSource buffers = ctx.consumers();
		if (buffers == null) return;
		if (ClientPortals.ACTIVE.isEmpty() && ClientPortals.CLOSING.isEmpty()) return;

		PoseStack ms = ctx.matrixStack();
		Vec3 cam = ctx.camera().getPosition();
		long now = System.currentTimeMillis();

		for (ClientPortals.Portal p : ClientPortals.ACTIVE.values()) {
			float t = Math.min(1f, (now - p.spawnMs) / OPEN_MS);
			draw(ms, buffers, cam, p, easeOutBack(t));
		}
		Iterator<ClientPortals.Portal> it = ClientPortals.CLOSING.iterator();
		while (it.hasNext()) {
			ClientPortals.Portal p = it.next();
			float t = (now - p.removeMs) / CLOSE_MS;
			if (t >= 1f) {
				it.remove();
				continue;
			}
			draw(ms, buffers, cam, p, 1f - t);
		}
	}

	private static float easeOutBack(float t) {
		float c1 = 1.70158f, c3 = c1 + 1f;
		float x = t - 1f;
		return 1f + c3 * x * x * x + c1 * x * x;
	}

	private static void draw(PoseStack ms, MultiBufferSource buffers, Vec3 cam, ClientPortals.Portal p, float scale) {
		if (scale <= 0.001f) return;
		Vec3 n = vec(p.normal), r = vec(p.right), u = vec(p.up);
		Vec3 c = p.center.add(n.scale(OFFSET));
		double hw = PortalData.WIDTH / 2 * scale, hh = PortalData.HEIGHT / 2 * scale;

		ms.pushPose();
		ms.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);
		Matrix4f pm = ms.last().pose();
		Matrix3f nm = ms.last().normal();
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(p.color == 0 ? TEX_BLUE : TEX_ORANGE));

		// corners: top-left, top-right, bottom-right, bottom-left (as seen from the front)
		corner(vc, pm, nm, r.scale(-hw).add(u.scale(hh)), n, 0f, 0f);
		corner(vc, pm, nm, r.scale(hw).add(u.scale(hh)), n, 1f, 0f);
		corner(vc, pm, nm, r.scale(hw).add(u.scale(-hh)), n, 1f, 1f);
		corner(vc, pm, nm, r.scale(-hw).add(u.scale(-hh)), n, 0f, 1f);
		ms.popPose();
	}

	private static void corner(VertexConsumer vc, Matrix4f pm, Matrix3f nm, Vec3 pos, Vec3 n, float uTex, float vTex) {
		vc.vertex(pm, (float) pos.x, (float) pos.y, (float) pos.z)
				.color(255, 255, 255, 255)
				.uv(uTex, vTex)
				.overlayCoords(OverlayTexture.NO_OVERLAY)
				.uv2(LightTexture.FULL_BRIGHT)
				.normal(nm, (float) n.x, (float) n.y, (float) n.z)
				.endVertex();
	}
}
