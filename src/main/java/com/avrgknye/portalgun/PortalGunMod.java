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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PortalGunMod implements ModInitializer {

    private static final Logger LOG =
            LoggerFactory.getLogger("portalgun");

    public static final String MOD_ID = "portalgun";

    public static final Item PORTAL_GUN =
            new PortalGunItem(new Item.Properties().stacksTo(1));

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    @Override
    public void onInitialize() {

        PortalSounds.register();

        Registry.register(
                BuiltInRegistries.ITEM,
                id("portal_gun"),
                PORTAL_GUN
        );

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(entries -> entries.accept(PORTAL_GUN));

        PortalNetworking.registerServerReceivers();

        ServerTickEvents.END_SERVER_TICK.register(PortalManager::tick);

        // Remove leftover portal entities from a previous session.
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {

            if (!entity.getTags().contains(PortalManager.IP_TAG)) {
                return;
            }

            java.util.UUID portalId = entity.getUUID();

            if (!PortalManager.isLiveIpPortal(portalId)) {

                LOG.warn(
                        "[IP LOAD CLEANUP] queued tagged entity because it is not in LIVE_IP: uuid={}, entityId={}, type={}, dimension={}, position={}",
                        portalId,
                        entity.getId(),
                        entity.getType(),
                        world.dimension(),
                        entity.position()
                );

                world.getServer().execute(() -> {

                    if (PortalManager.isLiveIpPortal(portalId)) {

                        LOG.warn(
                                "[IP LOAD CLEANUP] cancelled discard because the entity became live: uuid={}",
                                portalId
                        );

                        return;
                    }

                    if (!entity.isRemoved()) {

                        LOG.error(
                                "[IP LOAD CLEANUP] discarding tagged entity not present in LIVE_IP: uuid={}, entityId={}, dimension={}, position={}",
                                portalId,
                                entity.getId(),
                                world.dimension(),
                                entity.position()
                        );

                        entity.discard();
                    }
                });
            }
        });

        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) ->
                        PortalManager.syncTo(handler.player)
        );

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
                (player, origin, destination) ->
                        PortalManager.syncTo(player)
        );

        ServerPlayerEvents.AFTER_RESPAWN.register(
                (oldPlayer, newPlayer, alive) ->
                        PortalManager.syncTo(newPlayer)
        );
    }
}