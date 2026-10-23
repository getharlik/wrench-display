package dev.harlik.wrenchdisplay.common;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * What this mod remembers about a display it placed. Stored as one JSON string (Fabric: data attachment,
 * Paper: persistent data container), so both platforms read the same data.
 *
 * @param owner     who placed it
 * @param home      the display's centre at placement time, relative to the entity origin (survival offset limit)
 * @param groupId   shared by all parts placed from one blueprint; null for single displays
 * @param refund    the item(s) given back on removal, serialized by the platform; null if nothing was consumed
 */
public record DisplayMeta(UUID owner, float[] home, UUID groupId, String refund) {
	private static final Gson GSON = new Gson();

	public Vector3f homeVector() {
		return home == null ? null : new Vector3f(home[0], home[1], home[2]);
	}

	public String serialize() {
		return GSON.toJson(this);
	}

	/** Returns null for missing or unreadable data, which callers treat as a foreign display. */
	public static DisplayMeta deserialize(String json) {
		if (json == null || json.isEmpty()) {
			return null;
		}
		try {
			DisplayMeta meta = GSON.fromJson(json, DisplayMeta.class);
			return meta != null && meta.owner() != null ? meta : null;
		} catch (JsonParseException e) {
			return null;
		}
	}
}
