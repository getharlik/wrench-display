package dev.harlik.wrenchdisplay.common.blueprint;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Extracts the skin of a player head so heads can be matched by what they look like.
 * The same skin is often exported with different timestamps, profile names or profile ids,
 * so the stable key is the texture hash at the end of the skin URL.
 */
public final class HeadTextures {
	private HeadTextures() {
	}

	/** @param profile the {@code minecraft:profile} component value */
	static String skinKey(Object profile) {
		if (profile instanceof Map<?, ?> map && map.get("properties") instanceof List<?> properties) {
			for (Object property : properties) {
				if (property instanceof Map<?, ?> p && "textures".equals(p.get("name")) && p.get("value") instanceof String value) {
					return skinKeyFromTextures(value);
				}
			}
		}
		return null;
	}

	/** @param owner legacy {@code SkullOwner} compound: {@code Properties:{textures:[{Value:"..."}]}} */
	static String legacySkinKey(Map<?, ?> owner) {
		if (owner.get("Properties") instanceof Map<?, ?> properties && properties.get("textures") instanceof List<?> textures) {
			for (Object texture : textures) {
				if (texture instanceof Map<?, ?> t && t.get("Value") instanceof String value) {
					return skinKeyFromTextures(value);
				}
			}
		}
		return null;
	}

	/** @param base64 the {@code textures} property value; returns the texture hash, or null if it can't be read */
	public static String skinKeyFromTextures(String base64) {
		try {
			String json = new String(Base64.getMimeDecoder().decode(base64), StandardCharsets.UTF_8);
			JsonElement root = JsonParser.parseString(json);
			JsonObject skin = root.getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("SKIN");
			String url = skin.get("url").getAsString();
			int slash = url.lastIndexOf('/');
			return slash >= 0 ? url.substring(slash + 1) : url;
		} catch (RuntimeException e) {
			return null;
		}
	}
}
