plugins {
	id("dev.kikugie.stonecutter")
}

// The version the sources in fabric/src are written for (and committed with).
stonecutter active "26.3"

stonecutter parameters {
	replacements {
		// Mojang renamed ResourceLocation to Identifier in 1.21.11; the sources use the new name.
		string(current.parsed >= "1.21.11") {
			replace("ResourceLocation", "Identifier")
		}
	}
}
