package com.avrgknye.portalgun.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Skinned mesh + skeleton + animations, loaded from portalgun.json (made by smd_to_json.py).
 * Coordinates are in Source model space: muzzle points to -Y, up is +Z, right is -X.
 */
public final class PortalGunModel {
	public static final class Mesh {
		public final String material;
		public final float[] positions, normals, uvs, weights;
		public final int[] bones, indices;
		/** skinned output, rewritten by {@link #skin(float[])} */
		public final float[] outPos, outNrm;

		Mesh(String material, float[] positions, float[] normals, float[] uvs, int[] bones, float[] weights, int[] indices) {
			this.material = material;
			this.positions = positions;
			this.normals = normals;
			this.uvs = uvs;
			this.bones = bones;
			this.weights = weights;
			this.indices = indices;
			this.outPos = new float[positions.length];
			this.outNrm = new float[normals.length];
		}

		public int vertexCount() {
			return positions.length / 3;
		}

		/** CPU skinning with the flat 3x4 bone matrices from {@link PortalGunModel#computeSkin}. */
		public void skin(float[] sm) {
			int n = vertexCount();
			for (int v = 0; v < n; v++) {
				float px = positions[v * 3], py = positions[v * 3 + 1], pz = positions[v * 3 + 2];
				float nx = normals[v * 3], ny = normals[v * 3 + 1], nz = normals[v * 3 + 2];
				float ox = 0, oy = 0, oz = 0, onx = 0, ony = 0, onz = 0;
				for (int k = 0; k < 4; k++) {
					float w = weights[v * 4 + k];
					if (w == 0f) continue;
					int o = bones[v * 4 + k] * 12;
					ox += w * (sm[o] * px + sm[o + 1] * py + sm[o + 2] * pz + sm[o + 3]);
					oy += w * (sm[o + 4] * px + sm[o + 5] * py + sm[o + 6] * pz + sm[o + 7]);
					oz += w * (sm[o + 8] * px + sm[o + 9] * py + sm[o + 10] * pz + sm[o + 11]);
					onx += w * (sm[o] * nx + sm[o + 1] * ny + sm[o + 2] * nz);
					ony += w * (sm[o + 4] * nx + sm[o + 5] * ny + sm[o + 6] * nz);
					onz += w * (sm[o + 8] * nx + sm[o + 9] * ny + sm[o + 10] * nz);
				}
				float len = (float) Math.sqrt(onx * onx + ony * ony + onz * onz);
				if (len > 1e-6f) {
					onx /= len;
					ony /= len;
					onz /= len;
				}
				outPos[v * 3] = ox;
				outPos[v * 3 + 1] = oy;
				outPos[v * 3 + 2] = oz;
				outNrm[v * 3] = onx;
				outNrm[v * 3 + 1] = ony;
				outNrm[v * 3 + 2] = onz;
			}
		}
	}

	public static final class Anim {
		public final int fps, frames;
		/** [frame][bone*7 + (px,py,pz,qx,qy,qz,qw)] */
		public final float[][] data;

		Anim(int fps, int frames, float[][] data) {
			this.fps = fps;
			this.frames = frames;
			this.data = data;
		}
	}

	public final int boneCount;
	public final int[] parent;
	public final String[] boneNames;
	public final float[] restPos, restQuat;
	public final List<Mesh> meshes = new ArrayList<>();
	public final Map<String, Anim> anims = new HashMap<>();
	/** gun body position in the animated idle pose (used as the pivot when rendering) */
	public final float pivotX, pivotY, pivotZ;

	private final Matrix4f[] invBind;
	private final Matrix4f[] world, local;
	private final Matrix4f tmp = new Matrix4f();
	private final float[] skinFlat;

	private PortalGunModel(JsonObject root) {
		JsonArray ba = root.getAsJsonArray("bones");
		boneCount = ba.size();
		parent = new int[boneCount];
		boneNames = new String[boneCount];
		restPos = new float[boneCount * 3];
		restQuat = new float[boneCount * 4];
		for (int i = 0; i < boneCount; i++) {
			JsonObject b = ba.get(i).getAsJsonObject();
			boneNames[i] = b.get("name").getAsString();
			parent[i] = b.get("parent").getAsInt();
			float[] p = floats(b.getAsJsonArray("pos"));
			float[] q = floats(b.getAsJsonArray("quat"));
			System.arraycopy(p, 0, restPos, i * 3, 3);
			System.arraycopy(q, 0, restQuat, i * 4, 4);
		}
		world = new Matrix4f[boneCount];
		local = new Matrix4f[boneCount];
		invBind = new Matrix4f[boneCount];
		for (int i = 0; i < boneCount; i++) {
			world[i] = new Matrix4f();
			local[i] = new Matrix4f();
			invBind[i] = new Matrix4f();
		}
		skinFlat = new float[boneCount * 12];

		// bind pose -> inverse bind matrices
		buildWorld(null, 0f);
		for (int i = 0; i < boneCount; i++) {
			world[i].invert(invBind[i]);
		}

		for (JsonElement me : root.getAsJsonArray("meshes")) {
			JsonObject m = me.getAsJsonObject();
			meshes.add(new Mesh(
					m.get("material").getAsString(),
					floats(m.getAsJsonArray("positions")),
					floats(m.getAsJsonArray("normals")),
					floats(m.getAsJsonArray("uvs")),
					ints(m.getAsJsonArray("bones")),
					floats(m.getAsJsonArray("weights")),
					ints(m.getAsJsonArray("indices"))));
		}

		JsonObject anims = root.getAsJsonObject("animations");
		for (Map.Entry<String, JsonElement> e : anims.entrySet()) {
			JsonObject a = e.getValue().getAsJsonObject();
			int frames = a.get("frames").getAsInt();
			JsonArray data = a.getAsJsonArray("data");
			float[][] d = new float[frames][boneCount * 7];
			for (int f = 0; f < frames; f++) {
				JsonArray fr = data.get(f).getAsJsonArray();
				for (int b = 0; b < boneCount; b++) {
					JsonArray t = fr.get(b).getAsJsonArray();
					for (int k = 0; k < 7; k++) d[f][b * 7 + k] = t.get(k).getAsFloat();
				}
			}
			this.anims.put(e.getKey(), new Anim(a.get("fps").getAsInt(), frames, d));
		}

		// The animations place the whole model relative to the camera (camera = origin), which is
		// far away from the bind pose. So the pivot must come from the animated idle pose.
		buildWorld(this.anims.get("idle"), 0f);
		float px = 0, py = 0, pz = 0;
		for (int i = 0; i < boneCount; i++) {
			if (boneNames[i].equals("ValveBiped.Base")) {
				px = world[i].m30();
				py = world[i].m31();
				pz = world[i].m32();
			}
		}
		pivotX = px;
		pivotY = py;
		pivotZ = pz;
	}

	public static PortalGunModel parse(JsonObject root) {
		return new PortalGunModel(root);
	}

	private static float[] floats(JsonArray a) {
		float[] r = new float[a.size()];
		for (int i = 0; i < r.length; i++) r[i] = a.get(i).getAsFloat();
		return r;
	}

	private static int[] ints(JsonArray a) {
		int[] r = new int[a.size()];
		for (int i = 0; i < r.length; i++) r[i] = a.get(i).getAsInt();
		return r;
	}

	/** Fills local[] / world[] for the given animation sample (anim == null -> rest pose). */
	private void buildWorld(Anim anim, float frame) {
		int f0 = 0, f1 = 0;
		float t = 0f;
		if (anim != null) {
			float fc = Math.max(0f, Math.min(frame, anim.frames - 1));
			f0 = (int) Math.floor(fc);
			f1 = Math.min(f0 + 1, anim.frames - 1);
			t = fc - f0;
		}
		for (int i = 0; i < boneCount; i++) {
			float x, y, z, qx, qy, qz, qw;
			if (anim == null) {
				x = restPos[i * 3];
				y = restPos[i * 3 + 1];
				z = restPos[i * 3 + 2];
				qx = restQuat[i * 4];
				qy = restQuat[i * 4 + 1];
				qz = restQuat[i * 4 + 2];
				qw = restQuat[i * 4 + 3];
			} else {
				float[] a = anim.data[f0], b = anim.data[f1];
				int o = i * 7;
				x = a[o] + (b[o] - a[o]) * t;
				y = a[o + 1] + (b[o + 1] - a[o + 1]) * t;
				z = a[o + 2] + (b[o + 2] - a[o + 2]) * t;
				float bx = b[o + 3], by = b[o + 4], bz = b[o + 5], bw = b[o + 6];
				float dot = a[o + 3] * bx + a[o + 4] * by + a[o + 5] * bz + a[o + 6] * bw;
				if (dot < 0f) {
					bx = -bx;
					by = -by;
					bz = -bz;
					bw = -bw;
				}
				qx = a[o + 3] + (bx - a[o + 3]) * t;
				qy = a[o + 4] + (by - a[o + 4]) * t;
				qz = a[o + 5] + (bz - a[o + 5]) * t;
				qw = a[o + 6] + (bw - a[o + 6]) * t;
				float len = (float) Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
				qx /= len;
				qy /= len;
				qz /= len;
				qw /= len;
			}
			local[i].translation(x, y, z).rotate(new Quaternionf(qx, qy, qz, qw));
			if (parent[i] < 0) world[i].set(local[i]);
			else world[i].set(world[parent[i]]).mul(local[i]);
		}
	}

	/**
	 * Computes the flat 3x4 skinning matrices (row-major, 12 floats per bone) for an
	 * animation at a fractional frame. anim == null gives the bind pose (identity).
	 */
	public float[] computeSkin(Anim anim, float frame) {
		buildWorld(anim, frame);
		for (int i = 0; i < boneCount; i++) {
			world[i].mul(invBind[i], tmp);
			int o = i * 12;
			skinFlat[o] = tmp.m00();
			skinFlat[o + 1] = tmp.m10();
			skinFlat[o + 2] = tmp.m20();
			skinFlat[o + 3] = tmp.m30();
			skinFlat[o + 4] = tmp.m01();
			skinFlat[o + 5] = tmp.m11();
			skinFlat[o + 6] = tmp.m21();
			skinFlat[o + 7] = tmp.m31();
			skinFlat[o + 8] = tmp.m02();
			skinFlat[o + 9] = tmp.m12();
			skinFlat[o + 10] = tmp.m22();
			skinFlat[o + 11] = tmp.m32();
		}
		return skinFlat;
	}
}
