package com.avrgknye.portalgun;

import com.avrgknye.portalgun.portal.PortalManager;
import com.avrgknye.portalgun.portal.PortalNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public class PortalGunMod implements ModInitializer {
	public static final String MOD_ID = "portalgun";

	public static final Item PORTAL_GUN = new PortalGunItem(new Item.Properties().stacksTo(1));

	public static ResourceLocation id(String path) {
		return new ResourceLocation(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		PortalSounds.register();
		Registry.register(BuiltInRegistries.ITEM, id("portal_gun"), PORTAL_GUN);
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(entries -> entries.accept(PORTAL_GUN));

		PortalNetworking.registerServerReceivers();
		ServerTickEvents.END_SERVER_TICK.register(PortalManager::tick);
		// remove leftover portal entities from a previous session (the gun's portals do not survive a restart)
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity.getTags().contains(PortalManager.IP_TAG) && !PortalManager.isLiveIpPortal(entity.getUUID())) {
				world.getServer().execute(entity::discard);
			}
		});
		// make sure every client knows the portals that already exist
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> PortalManager.syncTo(handler.player));
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> PortalManager.syncTo(player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> PortalManager.syncTo(newPlayer));
	}
}
