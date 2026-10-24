pluginManagement {
	repositories {
		maven("https://maven.fabricmc.net/") { name = "Fabric" }
		maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
		mavenCentral()
		gradlePluginPortal()
	}
}

plugins {
	// One Fabric source tree for several Minecraft versions: https://stonecutter.kikugie.dev
	id("dev.kikugie.stonecutter") version "0.9.8"
	// Picks the right Loom for obfuscated (1.21.x) and unobfuscated (26.x) Minecraft from one build script
	id("dev.kikugie.loom-back-compat") version "0.4.2"
}

rootProject.name = "wrench-display"

include("common")
include("fabric")
include("paper")
include("resourcepack")

// Each version becomes :fabric:<version>, built from fabric/src with fabric/versions/<version>.
stonecutter {
	create(":fabric") {
		versions("1.21.8", "1.21.10", "1.21.11", "26.1.2", "26.2", "26.3")
		vcsVersion = "26.3"
	}
}
