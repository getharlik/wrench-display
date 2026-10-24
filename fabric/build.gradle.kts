// Build script for every Minecraft version of the Fabric mod (:fabric:<version>), run by Stonecutter.
// Per-version values come from stonecutter.properties.toml.
plugins {
	// Applies the right Loom variant for obfuscated (1.21.x) and unobfuscated (26.x) Minecraft
	id("dev.kikugie.loom-back-compat")
}

val modVersion = rootProject.property("version") as String
version = "$modVersion+${sc.current.version}"
base.archivesName = "wrench-display-fabric"

val requiredJava: JavaVersion = if (sc.current.parsed >= "26.1") JavaVersion.VERSION_25 else JavaVersion.VERSION_21

repositories {
	// Common Protection API (claim/protection mod bridge)
	maven("https://maven.nucleoid.xyz/") { name = "Nucleoid" }
}

loom {
	splitEnvironmentSourceSets()

	mods {
		register("wrench-display") {
			sourceSet(sourceSets["main"])
			sourceSet(sourceSets["client"])
		}
	}

	runConfigs.configureEach {
		// One run folder for all versions (fabric/run)
		runDirectory = file("../../run")
	}
}

fabricApi {
	// In-game tests (./gradlew :fabric:<version>:runGameTest) in their own source set, not shipped in the mod jar.
	configureTests {
		createSourceSet = true
		modId = "wrench-display-gametest"
		eula = true
	}
}

dependencies {
	minecraft("com.mojang:minecraft:${sc.current.version}")
	// Mojang's names on obfuscated versions; nothing to do on 26.x
	loomx.applyMojangMappings()

	modImplementation("net.fabricmc:fabric-loader:${sc.properties.get<String>("deps.fabric_loader")}")
	modImplementation("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")

	// Shared core (parser, edit math, limits), nested into the mod jar.
	implementation(project(":common"))
	include(project(":common"))

	// One API for claim mods (FTB Chunks, Open Parties and Claims, Flan, …); tiny, so it is bundled.
	val protection = "eu.pb4:common-protection-api:${sc.properties.get<String>("deps.common_protection_api")}"
	modImplementation(protection)
	include(protection)

	// Before 26.1 Fabric API has no permission API; the common one (used by LuckPerms etc.) is bundled instead.
	sc.properties.getOrNull<String>("deps.fabric_permissions_api")?.let {
		val permissions = "me.lucko:fabric-permissions-api:$it"
		modImplementation(permissions)
		include(permissions)
	}
}

// The sample-model fixtures from :common's unit tests are reused by the game tests.
sourceSets.named("gametest") {
	resources.srcDir(project(":common").file("src/test/resources"))
}

java {
	withSourcesJar()
	sourceCompatibility = requiredJava
	targetCompatibility = requiredJava
}

tasks {
	withType<JavaCompile>().configureEach {
		options.release = requiredJava.majorVersion.toInt()
	}

	// Version-dependent values in fabric.mod.json and the mixin configs (main, client and gametest resources).
	withType<ProcessResources>().configureEach {
		val props = mapOf(
			"version" to version.toString(),
			"minecraft" to sc.properties.get<String>("mod.mc_compat"),
			"java" to ">=${requiredJava.majorVersion}",
			"mixin_java" to "JAVA_${requiredJava.majorVersion}",
		)
		inputs.properties(props)
		filesMatching(listOf("fabric.mod.json", "*.mixins.json")) { expand(props) }
	}

	processResources {
		// Shared assets (item models, textures, lang) live in :common so the resource pack can reuse them.
		from(project(":common").file("src/main/resources"))

		// The cooking_fuel item component only exists since 26.3; older versions block fuel with FuelValuesMixin.
		val dropCookingFuel = sc.current.parsed < "26.3"
		inputs.property("dropCookingFuel", dropCookingFuel) // so changing the cut-off re-processes the files
		if (dropCookingFuel) {
			filesMatching("data/wrench-display/recipe/*.json") {
				filter { line -> if (line.contains("\"!minecraft:cooking_fuel\"")) "" else line }
			}
		}
	}

	withType<Jar>().configureEach {
		from(rootProject.file("LICENSE")) { rename { "${it}_wrench-display" } }
	}
}

/**
 * Each Minecraft test server needs a lot of memory, and with parallel builds Gradle would start one per version at
 * once. This shared slot lets only one run at a time (compiling still runs in parallel).
 */
abstract class MinecraftServerSlot : BuildService<BuildServiceParameters.None>

val minecraftServerSlot = gradle.sharedServices.registerIfAbsent("wrenchDisplayMinecraftServer", MinecraftServerSlot::class) {
	maxParallelUsages = 1
}

tasks.matching { it.name == "runGameTest" || it.name == "prodServer" }.configureEach {
	usesService(minecraftServerSlot)
}

tasks.matching { it.name == "runGameTest" }.configureEach {
	(this as JavaExec).maxHeapSize = "1536m"
}

/**
 * Starts a normal Fabric server with only the built mod jar and Fabric API's release jar (on 1.21.x that is the
 * remapped jar players get), to check the real artifact loads: ./gradlew :fabric:<version>:prodServer
 */
tasks.register<net.fabricmc.loom.task.prod.ServerProductionRunTask>("prodServer") {
	group = "fabric"
	description = "Runs a real Fabric server with the release jar of this version."
	minecraftVersion = sc.current.version
	loaderVersion = sc.properties.get<String>("deps.fabric_loader")
	installerVersion = "1.1.2"
	val fabricApiJar = configurations.detachedConfiguration(
			dependencies.create("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}"))
			.apply { isTransitive = false }
	mods.from(loomx.modJar.flatMap { it.archiveFile }, fabricApiJar)
	val dir = layout.buildDirectory.dir("prod-server")
	runDir = dir
	programArgs.add("nogui")
	jvmArgs.add("-Xmx1536m")
	doFirst {
		dir.get().asFile.mkdirs()
		dir.get().file("eula.txt").asFile.writeText("eula=true\n")
		dir.get().file("server.properties").asFile.writeText("online-mode=false\nlevel-type=minecraft\\:flat\n")
	}
}

