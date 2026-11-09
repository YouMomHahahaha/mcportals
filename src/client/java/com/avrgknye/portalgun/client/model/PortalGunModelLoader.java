package com.avrgknye.portalgun.client.model;

import com.avrgknye.portalgun.PortalGunMod;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Loads assets/portalgun/gun/portalgun.json on game start and on F3+T. */
public class PortalGunModelLoader implements SimpleSynchronousResourceReloadListener {
	private static final Logger LOG = LoggerFactory.getLogger("portalgun");

	/** null until loaded (or if the file is missing) */
	public static volatile PortalGunModel model;

	@Override
	public ResourceLocation getFabricId() {
		return PortalGunMod.id("gun_model");
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		ResourceLocation id = PortalGunMod.id("gun/portalgun.json");
		try (var in = manager.getResource(id).orElseThrow().open();
			 var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			model = PortalGunModel.parse(JsonParser.parseReader(reader).getAsJsonObject());
			LOG.info("Portal gun model loaded: {} bones, {} meshes, {} animations",
					model.boneCount, model.meshes.size(), model.anims.size());
		} catch (Exception e) {
			model = null;
			LOG.error("Could not load {} - did you copy portalgun.json into src/main/resources/assets/portalgun/gun/ ?", id, e);
		}
	}
}
