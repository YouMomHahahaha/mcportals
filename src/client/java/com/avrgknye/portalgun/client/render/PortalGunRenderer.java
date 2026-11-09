package com.avrgknye.portalgun.client.render;

import com.avrgknye.portalgun.PortalGunMod;
import com.avrgknye.portalgun.client.model.PortalGunModel;
import com.avrgknye.portalgun.client.model.PortalGunModelLoader;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public class PortalGunRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    private static final float FIRST_PERSON_SCALE = 0.03f;
    private static final float ANCHOR_X = 0.40f;
    private static final float ANCHOR_Y = -0.42f;
    private static final float ANCHOR_Z = -0.70f;
    private static final float OTHER_SCALE = 0.03f;

    private static final Logger LOG = LoggerFactory.getLogger("portalgun");
    private final Set<ItemDisplayContext> logged = EnumSet.noneOf(ItemDisplayContext.class);
    private final Set<String> statsLogged = new HashSet<>();

    public PortalGunRenderer() {
        LOG.info("[debug] PortalGunRenderer created and registered");
    }

    private static final ResourceLocation TEX_GUN =
            PortalGunMod.id("textures/gun/v_portalgun.png");

    private static final ResourceLocation TEX_GLASS =
            PortalGunMod.id("textures/gun/v_portalgun_glass.png");

    @Override
    public void render(
            ItemStack stack,
            ItemDisplayContext mode,
            PoseStack matrices,
            MultiBufferSource buffers,
            int light,
            int overlay
    ) {
        PortalGunModel model = PortalGunModelLoader.model;

        if (logged.add(mode)) {
            LOG.info("[debug] render() called, mode={}, model loaded={}",
                    mode, model != null);
        }

        if (model == null) return;

        boolean firstPerson = mode == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;

        if (mode == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) return;

        float[] skin = firstPerson
                ? PortalGunAnimator.sample(model)
                : PortalGunAnimator.idlePose(model);

        matrices.pushPose();
        matrices.translate(0.5, 0.5, 0.5);

        if (firstPerson) {
            matrices.translate(
                    ANCHOR_X - 0.56,
                    ANCHOR_Y + 0.52,
                    ANCHOR_Z + 0.72
            );
        }

        if (mode == ItemDisplayContext.GUI) {
            matrices.mulPose(Axis.XP.rotationDegrees(15f));
            matrices.mulPose(Axis.YP.rotationDegrees(-65f));
        }

        float s = firstPerson ? FIRST_PERSON_SCALE : OTHER_SCALE;
        matrices.scale(s, s, s);

        Matrix4f pm = matrices.last().pose();
        Matrix3f nm = matrices.last().normal();

        Minecraft mc = Minecraft.getInstance();

        ResourceLocation skinTex = mc.player != null
                ? mc.player.getSkinTextureLocation()
                : DefaultPlayerSkin.getDefaultSkin();

        boolean slim = mc.player != null
                && "slim".equals(mc.player.getModelName());

        for (PortalGunModel.Mesh mesh : model.meshes) {
            boolean arm = mesh.material.startsWith("steve_arm");

            if (arm) {
                if (!firstPerson) continue;
                if (mesh.material.endsWith("slim") != slim) continue;
            }

            boolean glass = mesh.material.equals("v_portalgun_glass");

            ResourceLocation tex = arm
                    ? skinTex
                    : glass ? TEX_GLASS : TEX_GUN;

            RenderType type = glass
                    ? RenderType.entityTranslucent(tex)
                    : RenderType.entityCutoutNoCull(tex);

            VertexConsumer vc = buffers.getBuffer(type);

            mesh.skin(skin);

            if (statsLogged.add(mode + "/" + mesh.material)) {
                logStats(mode, mesh, model, s);
            }

            float[] p = mesh.outPos;
            float[] n = mesh.outNrm;
            float[] uv = mesh.uvs;
            int[] idx = mesh.indices;

            for (int i = 0; i + 2 < idx.length; i += 3) {
                emit(vc, pm, nm, model, p, n, uv, idx[i], light);
                emit(vc, pm, nm, model, p, n, uv, idx[i + 1], light);
                emit(vc, pm, nm, model, p, n, uv, idx[i + 2], light);
                emit(vc, pm, nm, model, p, n, uv, idx[i + 2], light);
            }
        }

        matrices.popPose();
    }

    private static void emit(
            VertexConsumer vc,
            Matrix4f pm,
            Matrix3f nm,
            PortalGunModel model,
            float[] p,
            float[] n,
            float[] uv,
            int v,
            int light
    ) {
        float x = -(p[v * 3] - model.pivotX);
        float y = p[v * 3 + 2] - model.pivotZ;
        float z = p[v * 3 + 1] - model.pivotY;

        vc.vertex(pm, x, y, z)
                .color(255, 255, 255, 255)
                .uv(uv[v * 2], uv[v * 2 + 1])
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(
                        nm,
                        -n[v * 3],
                        n[v * 3 + 2],
                        n[v * 3 + 1]
                )
                .endVertex();
    }

    private static void logStats(
            ItemDisplayContext mode,
            PortalGunModel.Mesh mesh,
            PortalGunModel model,
            float scale
    ) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;

        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;

        int nan = 0;

        for (int v = 0; v < mesh.vertexCount(); v++) {
            float x = -(mesh.outPos[v * 3] - model.pivotX) * scale;
            float y = (mesh.outPos[v * 3 + 2] - model.pivotZ) * scale;
            float z = (mesh.outPos[v * 3 + 1] - model.pivotY) * scale;

            if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z)) {
                nan++;
                continue;
            }

            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);

            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);

            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }

        LOG.info(
                "[debug] mode={} mesh={} verts={} tris={} nanVerts={} pivot=({},{},{}) boundsBlocks=({},{},{})..({},{},{})",
                mode,
                mesh.material,
                mesh.vertexCount(),
                mesh.indices.length / 3,
                nan,
                model.pivotX,
                model.pivotY,
                model.pivotZ,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ
        );
    }
}