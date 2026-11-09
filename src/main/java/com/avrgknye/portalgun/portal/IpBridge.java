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

    /**
     * Moves the Immersive Portals plane slightly outward
     * from the supporting block face.
     *
     * This prevents the portal plane from being exactly
     * coplanar with the wall's collision and rendering surface.
     */
    private static final double PORTAL_SURFACE_OFFSET = 0.01;

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
     * Returns a portal position slightly outside its supporting wall.
     */
    private static Vec3 getSurfacePosition(PortalData portal) {

        return portal.center.add(
                vec(portal.normal).scale(PORTAL_SURFACE_OFFSET)
        );
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

        Entity portal = portalType.create(fromLevel);

        Entity destinationFrame = portalType.create(toLevel);

        if (portal == null || destinationFrame == null) {
            throw new IllegalStateException(
                    "Could not create Immersive Portals entities"
            );
        }

        Vec3 sourcePosition = getSurfacePosition(from);
        Vec3 destinationPosition = getSurfacePosition(to);

        // Configure the source portal.
        setOriginPos.invoke(
                portal,
                sourcePosition
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
                destinationPosition
        );

        // Configure the destination's orientation.
        setOrientationAndSize.invoke(
                destinationFrame,
                vec(to.right),
                vec(to.up),
                PortalData.WIDTH,
                PortalData.HEIGHT
        );

        // Calculate the rotation connecting both portal surfaces.
        adjustRotationToConnect.invoke(
                null,
                portal,
                destinationFrame
        );

        // Generate the circular portal shape.
        makePortalRound.invoke(
                null,
                portal,
                32
        );

        // Enable portal behavior.
        setTeleportable.invoke(portal, true);
        setInteractable.invoke(portal, true);
        setIsVisible.invoke(portal, true);

        portal.addTag(PortalManager.IP_TAG);

        // Create the reverse portal.
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
                "[IP OFFSET CHECK] surfaceOffset={}, source={}, destination={}, reverseSource={}, reverseDestination={}",
                PORTAL_SURFACE_OFFSET,
                sourcePosition,
                destinationPosition,
                invokePosition(reverse),
                invokeDestination(reverse)
        );

        LOG.info(
                "Created portal pair: {} -> {}",
                sourcePosition,
                destinationPosition
        );

        return new Entity[]{
                portal,
                reverse
        };
    }

    private static Object invokePosition(Entity entity) {
        try {
            return entity.getClass()
                    .getMethod("position")
                    .invoke(entity);
        } catch (Throwable ignored) {
            return "unavailable";
        }
    }

    private static Object invokeDestination(Entity entity) {
        try {
            return entity.getClass()
                    .getField("destination")
                    .get(entity);
        } catch (Throwable ignored) {
            return "unavailable";
        }
    }
}