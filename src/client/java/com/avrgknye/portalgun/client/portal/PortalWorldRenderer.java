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

public final class PortalWorldRenderer {

    private static final ResourceLocation TEX_BLUE =
            PortalGunMod.id("textures/portal/portal_blue.png");

    private static final ResourceLocation TEX_ORANGE =
            PortalGunMod.id("textures/portal/portal_orange.png");

    private static final float OPEN_MS = 260f;
    private static final float CLOSE_MS = 180f;

    private static final double OFFSET = 0.025;

    private static final int RING_SEGMENTS = 64;

    private PortalWorldRenderer() {
    }

    public static void render(WorldRenderContext ctx) {

        MultiBufferSource buffers = ctx.consumers();

        if (buffers == null) return;

        if (ClientPortals.ACTIVE.isEmpty()
                && ClientPortals.CLOSING.isEmpty()) {
            return;
        }

        PoseStack ms = ctx.matrixStack();
        Vec3 cam = ctx.camera().getPosition();

        long now = System.currentTimeMillis();

        for (ClientPortals.Portal p : ClientPortals.ACTIVE.values()) {

            float t = Math.min(
                    1f,
                    (now - p.spawnMs) / OPEN_MS
            );

            draw(
                    ms,
                    buffers,
                    cam,
                    p,
                    easeOutBack(t)
            );
        }

        Iterator<ClientPortals.Portal> it =
                ClientPortals.CLOSING.iterator();

        while (it.hasNext()) {

            ClientPortals.Portal p = it.next();

            float t = (now - p.removeMs) / CLOSE_MS;

            if (t >= 1f) {
                it.remove();
                continue;
            }

            draw(
                    ms,
                    buffers,
                    cam,
                    p,
                    1f - t
            );
        }
    }

    private static float easeOutBack(float t) {

        float c1 = 1.70158f;
        float c3 = c1 + 1f;

        float x = t - 1f;

        return 1f + c3 * x * x * x + c1 * x * x;
    }

    private static ResourceLocation texture(ClientPortals.Portal p) {
        return p.color == 0 ? TEX_BLUE : TEX_ORANGE;
    }

    private static void draw(
            PoseStack ms,
            MultiBufferSource buffers,
            Vec3 cam,
            ClientPortals.Portal p,
            float scale
    ) {

        if (scale <= 0.001f) return;

        if (p.linked) {
            drawLinkedRim(ms, buffers, cam, p, scale);
        } else {
            drawFullPortal(ms, buffers, cam, p, scale);
        }
    }

    /**
     * Draws the complete portal texture when the portal
     * does not have a destination yet.
     */
    private static void drawFullPortal(
            PoseStack ms,
            MultiBufferSource buffers,
            Vec3 cam,
            ClientPortals.Portal p,
            float scale
    ) {

        Vec3 n = vec(p.normal);
        Vec3 r = vec(p.right);
        Vec3 u = vec(p.up);

        Vec3 c = p.center.add(n.scale(OFFSET));

        double hw = PortalData.WIDTH / 2 * scale;
        double hh = PortalData.HEIGHT / 2 * scale;

        ms.pushPose();

        ms.translate(
                c.x - cam.x,
                c.y - cam.y,
                c.z - cam.z
        );

        Matrix4f pm = ms.last().pose();
        Matrix3f nm = ms.last().normal();

        VertexConsumer vc = buffers.getBuffer(
                RenderType.entityTranslucent(texture(p))
        );

        corner(
                vc, pm, nm,
                r.scale(-hw).add(u.scale(hh)),
                n, 0f, 0f, 255
        );

        corner(
                vc, pm, nm,
                r.scale(hw).add(u.scale(hh)),
                n, 1f, 0f, 255
        );

        corner(
                vc, pm, nm,
                r.scale(hw).add(u.scale(-hh)),
                n, 1f, 1f, 255
        );

        corner(
                vc, pm, nm,
                r.scale(-hw).add(u.scale(-hh)),
                n, 0f, 1f, 255
        );

        ms.popPose();
    }

    /**
     * Draws only the glowing oval border.
     * The center remains transparent so the actual
     * Immersive Portals view is not covered.
     */
    private static void drawLinkedRim(
            PoseStack ms,
            MultiBufferSource buffers,
            Vec3 cam,
            ClientPortals.Portal p,
            float scale
    ) {

        Vec3 n = vec(p.normal);
        Vec3 r = vec(p.right);
        Vec3 u = vec(p.up);

        Vec3 c = p.center.add(n.scale(OFFSET));

        double hw = PortalData.WIDTH / 2 * scale;
        double hh = PortalData.HEIGHT / 2 * scale;

        ms.pushPose();

        ms.translate(
                c.x - cam.x,
                c.y - cam.y,
                c.z - cam.z
        );

        Matrix4f pm = ms.last().pose();
        Matrix3f nm = ms.last().normal();

        VertexConsumer vc = buffers.getBuffer(
                RenderType.entityTranslucent(texture(p))
        );

        // Wider, softer outer glow.
        drawRing(
                vc,
                pm,
                nm,
                r,
                u,
                n,
                hw,
                hh,
                1.20,
                0.80,
                0.50f,
                0.36f,
                75
        );

        // Main colored portal border.
        drawRing(
                vc,
                pm,
                nm,
                r,
                u,
                n,
                hw,
                hh,
                1.08,
                0.92,
                0.47f,
                0.42f,
                230
        );

        ms.popPose();
    }

    private static void drawRing(
            VertexConsumer vc,
            Matrix4f pm,
            Matrix3f nm,
            Vec3 right,
            Vec3 up,
            Vec3 normal,
            double halfWidth,
            double halfHeight,
            double outerScale,
            double innerScale,
            float outerUvRadius,
            float innerUvRadius,
            int alpha
    ) {

        for (int i = 0; i < RING_SEGMENTS; i++) {

            double a0 = Math.PI * 2.0 * i / RING_SEGMENTS;
            double a1 = Math.PI * 2.0 * (i + 1) / RING_SEGMENTS;

            float cos0 = (float) Math.cos(a0);
            float sin0 = (float) Math.sin(a0);

            float cos1 = (float) Math.cos(a1);
            float sin1 = (float) Math.sin(a1);

            Vec3 outer0 = ringPoint(
                    right, up, halfWidth, halfHeight,
                    cos0, sin0, outerScale
            );

            Vec3 outer1 = ringPoint(
                    right, up, halfWidth, halfHeight,
                    cos1, sin1, outerScale
            );

            Vec3 inner1 = ringPoint(
                    right, up, halfWidth, halfHeight,
                    cos1, sin1, innerScale
            );

            Vec3 inner0 = ringPoint(
                    right, up, halfWidth, halfHeight,
                    cos0, sin0, innerScale
            );

            corner(
                    vc, pm, nm,
                    outer0, normal,
                    0.5f + cos0 * outerUvRadius,
                    0.5f + sin0 * outerUvRadius,
                    alpha
            );

            corner(
                    vc, pm, nm,
                    outer1, normal,
                    0.5f + cos1 * outerUvRadius,
                    0.5f + sin1 * outerUvRadius,
                    alpha
            );

            corner(
                    vc, pm, nm,
                    inner1, normal,
                    0.5f + cos1 * innerUvRadius,
                    0.5f + sin1 * innerUvRadius,
                    alpha
            );

            corner(
                    vc, pm, nm,
                    inner0, normal,
                    0.5f + cos0 * innerUvRadius,
                    0.5f + sin0 * innerUvRadius,
                    alpha
            );
        }
    }

    private static Vec3 ringPoint(
            Vec3 right,
            Vec3 up,
            double halfWidth,
            double halfHeight,
            float cos,
            float sin,
            double scale
    ) {

        return right.scale(cos * halfWidth * scale)
                .add(up.scale(sin * halfHeight * scale));
    }

    private static void corner(
            VertexConsumer vc,
            Matrix4f pm,
            Matrix3f nm,
            Vec3 pos,
            Vec3 normal,
            float uTex,
            float vTex,
            int alpha
    ) {

        vc.vertex(
                pm,
                (float) pos.x,
                (float) pos.y,
                (float) pos.z
        )
                .color(255, 255, 255, alpha)
                .uv(uTex, vTex)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(
                        nm,
                        (float) normal.x,
                        (float) normal.y,
                        (float) normal.z
                )
                .endVertex();
    }
}