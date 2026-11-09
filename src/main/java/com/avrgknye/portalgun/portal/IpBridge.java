package com.avrgknye.portalgun.portal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

import static com.avrgknye.portalgun.portal.PortalData.vec;

final class IpBridge {

    private static final Logger LOG =
            LoggerFactory.getLogger("portalgun");

    private static boolean initDone;
    private static boolean available;

    private static EntityType<?> portalType;

    private static Method setOriginPos;
    private static Method setOrientationAndSize;
    private static Method setDestinationDimension;
    private static Method setDestination;
    private static Method setTeleportable;
    private static Method setInteractable;
    private static Method setIsVisible;

    private static Method adjustRotationToConnect;
    private static Method makePortalRound;
    private static Method createReversePortal;

    private IpBridge() {
    }

    static synchronized boolean isAvailable() {
        if (!initDone) {
            init();
        }

        return available;
    }

    private static void init() {
        initDone = true;

        try {
            Class<?> portalCls = Class.forName(
                    "qouteall.imm_ptl.core.portal.Portal"
            );

            Class<?> manipulationCls = Class.forName(
                    "qouteall.imm_ptl.core.portal.PortalManipulation"
            );

            portalType = (EntityType<?>) portalCls
                    .getField("entityType")
                    .get(null);

            setOriginPos = portalCls.getMethod(
                    "setOriginPos",
                    Vec3.class
            );

            setOrientationAndSize = portalCls.getMethod(
                    "setOrientationAndSize",
                    Vec3.class,
                    Vec3.class,
                    double.class,
                    double.class
            );

            setDestinationDimension = portalCls.getMethod(
                    "setDestinationDimension",
                    net.minecraft.resources.ResourceKey.class
            );

            setDestination = portalCls.getMethod(
                    "setDestination",
                    Vec3.class
            );

            setTeleportable = portalCls.getMethod(
                    "setTeleportable",
                    boolean.class
            );

            setInteractable = portalCls.getMethod(
                    "setInteractable",
                    boolean.class
            );

            setIsVisible = portalCls.getMethod(
                    "setIsVisible",
                    boolean.class
            );

            adjustRotationToConnect =
                    manipulationCls.getMethod(
                            "adjustRotationToConnect",
                            portalCls,
                            portalCls
                    );

            makePortalRound = manipulationCls.getMethod(
                    "makePortalRound",
                    portalCls,
                    int.class
            );

            createReversePortal =
                    manipulationCls.getMethod(
                            "createReversePortal",
                            portalCls,
                            EntityType.class
                    );

            available = true;

            LOG.info(
                    "Immersive Portals API initialized successfully"
            );

        } catch (Throwable t) {

            available = false;

            LOG.error(
                    "Could not initialize Immersive Portals integration",
                    t
            );
        }
    }

    /**
     * Creates both sides of a portal connection.
     *
     * Index 0: from -> to
     * Index 1: to -> from
     */
    static Entity[] createPair(
            ServerLevel fromLevel,
            ServerLevel toLevel,
            PortalData from,
            PortalData to
    ) throws ReflectiveOperationException {

        // Create the main portal.
        Entity portal = portalType.create(fromLevel);

        // Temporary portal used only for calculating the destination orientation.
        Entity destinationFrame = portalType.create(toLevel);

        if (portal == null || destinationFrame == null) {
            throw new IllegalStateException(
                    "Could not create Immersive Portals entities"
            );
        }

        // Configure the source.
        setOriginPos.invoke(
                portal,
                from.center
        );

        setOrientationAndSize.invoke(
                portal,
                vec(from.right),
                vec(from.up),
                PortalData.WIDTH,
                PortalData.HEIGHT
        );

        // Configure its destination.
        setDestinationDimension.invoke(
                portal,
                to.dim
        );

        setDestination.invoke(
                portal,
                to.center
        );

        // Configure the destination's orientation.
        setOrientationAndSize.invoke(
                destinationFrame,
                vec(to.right),
                vec(to.up),
                PortalData.WIDTH,
                PortalData.HEIGHT
        );

        // Let Immersive Portals calculate the rotation.
        adjustRotationToConnect.invoke(
                null,
                portal,
                destinationFrame
        );

        // Generate the circular shape using the official IP function.
        makePortalRound.invoke(
                null,
                portal,
                32
        );

        // Explicitly enable portal behavior.
        setTeleportable.invoke(portal, true);
        setInteractable.invoke(portal, true);
        setIsVisible.invoke(portal, true);

        portal.addTag(PortalManager.IP_TAG);

        // Create the actual reverse portal from the first one.
        // This copies the transformation and creates the opposite side.
        Entity reverse = (Entity) createReversePortal.invoke(
                null,
                portal,
                portalType
        );

        if (reverse == null) {
            throw new IllegalStateException(
                    "Immersive Portals could not create reverse portal"
            );
        }

        setTeleportable.invoke(reverse, true);
        setInteractable.invoke(reverse, true);
        setIsVisible.invoke(reverse, true);

        reverse.addTag(PortalManager.IP_TAG);

        LOG.info(
                "Created portal pair: {} -> {}",
                from.center,
                to.center
        );

        return new Entity[]{
                portal,
                reverse
        };
    }
}