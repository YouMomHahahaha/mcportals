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

import java.lang.reflect.Method;
import java.util.Iterator;

import static com.avrgknye.portalgun.portal.PortalData.vec;

/** Renders the custom Portal Gun surface and rim. */
public final class PortalWorldRenderer {

    private static final ResourceLocation TEX_BLUE =
            PortalGunMod.id("textures/portal/portal_blue.png");

    private static final ResourceLocation TEX_ORANGE =
            PortalGunMod.id("textures/portal/portal_orange.png");

    /** White texel used with vertex colors for a normal, depth-tested translucent rim. */
    private static final ResourceLocation SOLID_WHITE =
            PortalGunMod.id("textures/misc/solid_white.png");

    private static final float OPEN_MS = 260f;
    private static final float CLOSE_MS = 180f;

    /** Keep the custom rim almost coplanar with the real Immersive Portals plane (+0.01). */
    private static final double RENDER_OFFSET = 0.014;

    /** The rim is inset slightly so it stays inside the original oval texture footprint. */
    private static final double RIM_OUTER = 0.95;
    private static final int RING_SEGMENTS = 96;

    /**
     * Immersive Portals renders the destination world recursively. The custom rim
     * must not be drawn again inside that view, otherwise the opposite portal's
     * color appears nested over the portal being viewed.
     */
    private static final Method IP_IS_RENDERING_METHOD = findIpIsRenderingMethod();

    private PortalWorldRenderer() {
    }

    public static void render(WorldRenderContext ctx) {

        if (isInsideImmersivePortalView()) return;

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
                    easeOutCubic(t),
                    now
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
                    1f - t,
                    now
            );
        }
    }

    private static Method findIpIsRenderingMethod() {
        try {
            Class<?> worldRenderInfo = Class.forName(
                    "qouteall.imm_ptl.core.render.context_management.WorldRenderInfo"
            );
            return worldRenderInfo.getMethod("isRendering");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isInsideImmersivePortalView() {
        if (IP_IS_RENDERING_METHOD == null) return false;
        try {
            return Boolean.TRUE.equals(IP_IS_RENDERING_METHOD.invoke(null));
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Smooth opening without the overshoot that made the portal grow into nearby blocks. */
    private static float easeOutCubic(float t) {
        float x = 1f - t;
        return 1f - x * x * x;
    }

    private static ResourceLocation texture(ClientPortals.Portal p) {
        return p.color == 0 ? TEX_BLUE : TEX_ORANGE;
    }

    private static void draw(
            PoseStack ms,
            MultiBufferSource buffers,
            Vec3 cam,
            ClientPortals.Portal p,
            float scale,
            long now
    ) {

        if (scale <= 0.001f) return;

        // The opening animation is never allowed to grow past the physical 1 x 2 portal.
        scale = Math.min(1f, Math.max(0f, scale));

        if (p.linked) {
            drawLinkedRim(ms, buffers, cam, p, scale, now);
        } else {
            drawFullPortal(ms, buffers, cam, p, scale);
        }
    }

    /** Draws the original portal texture while the portal has no destination yet. */
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
        Vec3 c = p.center.add(n.scale(RENDER_OFFSET));

        double hw = PortalData.WIDTH / 2.0 * scale;
        double hh = PortalData.HEIGHT / 2.0 * scale;

        ms.pushPose();
        ms.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);

        Matrix4f pose = ms.last().pose();
        Matrix3f normalMatrix = ms.last().normal();

        VertexConsumer vc = buffers.getBuffer(
                RenderType.entityTranslucent(texture(p))
        );

        texturedCorner(vc, pose, normalMatrix,
                r.scale(-hw).add(u.scale(hh)), n, 0f, 0f);
        texturedCorner(vc, pose, normalMatrix,
                r.scale(hw).add(u.scale(hh)), n, 1f, 0f);
        texturedCorner(vc, pose, normalMatrix,
                r.scale(hw).add(u.scale(-hh)), n, 1f, 1f);
        texturedCorner(vc, pose, normalMatrix,
                r.scale(-hw).add(u.scale(-hh)), n, 0f, 1f);

        ms.popPose();
    }

    /**
     * Immersive Portals renders the connected world in the center.
     * This renderer adds a colored border inside the same 1 x 2 bounds.
     * The rim uses a white texel and vertex colors, never the portal texture's UVs,
     * so the texture cannot smear across the narrow ring segments.
     */
    private static void drawLinkedRim(
            PoseStack ms,
            MultiBufferSource buffers,
            Vec3 cam,
            ClientPortals.Portal p,
            float scale,
            long now
    ) {

        Vec3 n = vec(p.normal);
        Vec3 r = vec(p.right);
        Vec3 u = vec(p.up);
        Vec3 c = p.center.add(n.scale(RENDER_OFFSET));

        double hw = PortalData.WIDTH / 2.0 * scale;
        double hh = PortalData.HEIGHT / 2.0 * scale;

        // A subtle brightness pulse; it does not alter the rim's size or position.
        double pulse = 0.96 + (Math.sin(now / 520.0) + 1.0) * 0.02;

        int red;
        int green;
        int blue;
        int highlightRed;
        int highlightGreen;
        int highlightBlue;

        if (p.color == 0) {
            red = 34;
            green = 142;
            blue = 245;
            highlightRed = 150;
            highlightGreen = 215;
            highlightBlue = 255;
        } else {
            red = 245;
            green = 116;
            blue = 24;
            highlightRed = 255;
            highlightGreen = 205;
            highlightBlue = 130;
        }

        ms.pushPose();
        ms.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);

        Matrix4f pose = ms.last().pose();
        Matrix3f normalMatrix = ms.last().normal();

        // Standard translucent entity rendering keeps normal depth testing against the wall.
        VertexConsumer vc = buffers.getBuffer(
                RenderType.entityTranslucent(SOLID_WHITE)
        );

        // A narrow, softly tinted outer edge, inset to match the original oval texture.
        drawRing(vc, pose, normalMatrix, r, u, n, hw, hh,
                RIM_OUTER, 0.925,
                red, green, blue,
                (int) (34 * pulse));

        // Main rim: thick enough to read clearly, but kept inside the wood-face outline.
        drawRing(vc, pose, normalMatrix, r, u, n, hw, hh,
                RIM_OUTER, 0.86,
                red, green, blue,
                (int) (205 * pulse));

        // Small inner highlight for the characteristic Portal edge.
        drawRing(vc, pose, normalMatrix, r, u, n, hw, hh,
                0.905, 0.89,
                highlightRed, highlightGreen, highlightBlue,
                (int) (105 * pulse));

        ms.popPose();
    }

    private static void drawRing(
            VertexConsumer vc,
            Matrix4f pose,
            Matrix3f normalMatrix,
            Vec3 right,
            Vec3 up,
            Vec3 normal,
            double halfWidth,
            double halfHeight,
            double outerScale,
            double innerScale,
            int red,
            int green,
            int blue,
            int alpha
    ) {

        for (int i = 0; i < RING_SEGMENTS; i++) {

            double a0 = Math.PI * 2.0 * i / RING_SEGMENTS;
            double a1 = Math.PI * 2.0 * (i + 1) / RING_SEGMENTS;

            float cos0 = (float) Math.cos(a0);
            float sin0 = (float) Math.sin(a0);
            float cos1 = (float) Math.cos(a1);
            float sin1 = (float) Math.sin(a1);

            Vec3 outer0 = ringPoint(right, up, halfWidth, halfHeight,
                    cos0, sin0, outerScale);
            Vec3 outer1 = ringPoint(right, up, halfWidth, halfHeight,
                    cos1, sin1, outerScale);
            Vec3 inner1 = ringPoint(right, up, halfWidth, halfHeight,
                    cos1, sin1, innerScale);
            Vec3 inner0 = ringPoint(right, up, halfWidth, halfHeight,
                    cos0, sin0, innerScale);

            colorCorner(vc, pose, normalMatrix, outer0, normal, red, green, blue, alpha);
            colorCorner(vc, pose, normalMatrix, outer1, normal, red, green, blue, alpha);
            colorCorner(vc, pose, normalMatrix, inner1, normal, red, green, blue, alpha);
            colorCorner(vc, pose, normalMatrix, inner0, normal, red, green, blue, alpha);
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

    private static void colorCorner(
            VertexConsumer vc,
            Matrix4f pose,
            Matrix3f normalMatrix,
            Vec3 pos,
            Vec3 normal,
            int red,
            int green,
            int blue,
            int alpha
    ) {

        vc.vertex(
                pose,
                (float) pos.x,
                (float) pos.y,
                (float) pos.z
        )
                .color(red, green, blue, alpha)
                .uv(0.5f, 0.5f)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(
                        normalMatrix,
                        (float) normal.x,
                        (float) normal.y,
                        (float) normal.z
                )
                .endVertex();
    }

    private static void texturedCorner(
            VertexConsumer vc,
            Matrix4f pose,
            Matrix3f normalMatrix,
            Vec3 pos,
            Vec3 normal,
            float u,
            float v
    ) {

        vc.vertex(
                pose,
                (float) pos.x,
                (float) pos.y,
                (float) pos.z
        )
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(
                        normalMatrix,
                        (float) normal.x,
                        (float) normal.y,
                        (float) normal.z
                )
                .endVertex();
    }
}
