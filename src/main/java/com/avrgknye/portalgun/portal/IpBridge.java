package com.avrgknye.portalgun.portal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;

import static com.avrgknye.portalgun.portal.PortalData.vec;

/**
 * Talks to the Immersive Portals mod (qouteall) through reflection, so this mod compiles without it
 * and only needs the Immersive Portals jar at runtime. It creates the real portal entities that
 * give the see-through view, the seamless walking through and the collision handling.
 */
final class IpBridge {
	private static final Logger LOG = LoggerFactory.getLogger("portalgun");

	private static boolean initDone = false;
	private static boolean available = false;

	private static EntityType<?> portalType;
	private static Method setOriginPos, setOrientationAndSize, setDestinationDimension, setDestination, setRotation;
	private static Field specialShapeField, shapeNormalizedField, commandsField;
	private static Constructor<?> quatCtor, shapeCtor, triangleCtor;

	private IpBridge() {
	}

	static synchronized boolean isAvailable() {
		if (!initDone) init();
		return available;
	}

	private static void init() {
		initDone = true;
		try {
			Class<?> portalCls = Class.forName("qouteall.imm_ptl.core.portal.Portal");
			Class<?> quatCls = Class.forName("qouteall.q_misc_util.my_util.DQuaternion");
			Class<?> shapeCls = Class.forName("qouteall.imm_ptl.core.portal.GeometryPortalShape");
			Class<?> triCls = Class.forName("qouteall.imm_ptl.core.portal.GeometryPortalShape$TriangleInPlane");

			portalType = (EntityType<?>) portalCls.getField("entityType").get(null);
			setOriginPos = portalCls.getMethod("setOriginPos", Vec3.class);
			setOrientationAndSize = portalCls.getMethod("setOrientationAndSize", Vec3.class, Vec3.class, double.class, double.class);
			setDestinationDimension = portalCls.getMethod("setDestinationDimension", net.minecraft.resources.ResourceKey.class);
			setDestination = portalCls.getMethod("setDestination", Vec3.class);
			setRotation = portalCls.getMethod("setRotation", quatCls);
			specialShapeField = portalCls.getField("specialShape");
			shapeNormalizedField = shapeCls.getField("normalized");
			commandsField = portalCls.getField("commandsOnTeleported");
			quatCtor = quatCls.getConstructor(double.class, double.class, double.class, double.class);
			shapeCtor = shapeCls.getConstructor(List.class);
			triangleCtor = triCls.getConstructor(double.class, double.class, double.class, double.class, double.class, double.class);
			available = true;
			LOG.info("Immersive Portals found: portals will be see-through");
		} catch (Throwable t) {
			available = false;
			LOG.error("Immersive Portals is missing or has a different API, portals cannot be see-through", t);
		}
	}

	/** Creates (but does not spawn) the portal entity that sits at {@code from} and leads out of {@code to}. */
	static Entity create(ServerLevel level, PortalData from, PortalData to) throws ReflectiveOperationException {
		Entity portal = portalType.create(level);
		if (portal == null) throw new IllegalStateException("could not create a portal entity");

		setOriginPos.invoke(portal, from.center);
		setOrientationAndSize.invoke(portal, vec(from.right), vec(from.up), PortalData.WIDTH, PortalData.HEIGHT);
		setDestinationDimension.invoke(portal, to.dim);
		setDestination.invoke(portal, to.center);
		setRotation.invoke(portal, rotation(from, to));
		specialShapeField.set(portal, ovalShape());
		portal.addTag(PortalManager.IP_TAG);

		// Immersive Portals runs these commands (as the entity that went through) right after it teleports:
		// the enter sound at this portal and the exit sound where the entity comes out.
		List<String> commands = new ArrayList<>();
		if (from.dim.equals(to.dim)) {
			commands.add(String.format(Locale.ROOT, "playsound portalgun:player.enter_portal player @a %.3f %.3f %.3f 0.5 1",
					from.center.x, from.center.y, from.center.z));
		}
		commands.add("playsound portalgun:player.exit_portal player @a ~ ~ ~ 0.5 1");
		commandsField.set(portal, commands);
		return portal;
	}

	/** A circle in the portal's normalized plane (-1..1), which becomes the oval 1 x 2 blocks. */
	private static Object ovalShape() throws ReflectiveOperationException {
		int segments = 32;
		List<Object> triangles = new ArrayList<>();
		for (int i = 0; i < segments; i++) {
			double a0 = 2 * Math.PI * i / segments;
			double a1 = 2 * Math.PI * (i + 1) / segments;
			triangles.add(triangleCtor.newInstance(0.0, 0.0, Math.cos(a0), Math.sin(a0), Math.cos(a1), Math.sin(a1)));
		}
		Object shape = shapeCtor.newInstance(triangles);
		shapeNormalizedField.set(shape, true);
		return shape;
	}

	private static double c(net.minecraft.core.Direction d, int axis) {
		return axis == 0 ? d.getStepX() : axis == 1 ? d.getStepY() : d.getStepZ();
	}

	/**
	 * Rotation that maps the frame (right, up, normal) of portal A onto (-right, up, -normal) of portal B:
	 * walking into A (against its normal) comes out of B moving along B's normal.
	 */
	private static Object rotation(PortalData a, PortalData b) throws ReflectiveOperationException {
		double[][] m = new double[3][3];
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				m[i][j] = -c(b.right, i) * c(a.right, j) + c(b.up, i) * c(a.up, j) - c(b.normal, i) * c(a.normal, j);
			}
		}
		double x, y, z, w;
		double t = m[0][0] + m[1][1] + m[2][2];
		if (t > 0) {
			double s = Math.sqrt(t + 1) * 2;
			w = 0.25 * s;
			x = (m[2][1] - m[1][2]) / s;
			y = (m[0][2] - m[2][0]) / s;
			z = (m[1][0] - m[0][1]) / s;
		} else if (m[0][0] > m[1][1] && m[0][0] > m[2][2]) {
			double s = Math.sqrt(1 + m[0][0] - m[1][1] - m[2][2]) * 2;
			w = (m[2][1] - m[1][2]) / s;
			x = 0.25 * s;
			y = (m[0][1] + m[1][0]) / s;
			z = (m[0][2] + m[2][0]) / s;
		} else if (m[1][1] > m[2][2]) {
			double s = Math.sqrt(1 + m[1][1] - m[0][0] - m[2][2]) * 2;
			w = (m[0][2] - m[2][0]) / s;
			x = (m[0][1] + m[1][0]) / s;
			y = 0.25 * s;
			z = (m[1][2] + m[2][1]) / s;
		} else {
			double s = Math.sqrt(1 + m[2][2] - m[0][0] - m[1][1]) * 2;
			w = (m[1][0] - m[0][1]) / s;
			x = (m[0][2] + m[2][0]) / s;
			y = (m[1][2] + m[2][1]) / s;
			z = 0.25 * s;
		}
		double len = Math.sqrt(x * x + y * y + z * z + w * w);
		return quatCtor.newInstance(x / len, y / len, z / len, w / len);
	}
}
