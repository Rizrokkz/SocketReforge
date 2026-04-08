plugins {
    id("java")
}

group = "irai.mod.reforge"
version = "1.3.7"

repositories {
    mavenCentral()
    maven {
        url = uri("https://www.cursemaven.com")
    }
}

dependencies {
    compileOnly(files("libs/HytaleServer.jar"))
    implementation(files("libs/HytaleServer.jar"))
    implementation(files("libs/HyUI-0.9.0-2026.02.19.jar"))
    // Optional dependency - DynamicTooltipsLib (https://github.com/Herolias/DynamicTooltipsLib)
    compileOnly(files("libs/DynamicTooltipsLib-1.5.2.jar"))
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    enabled = false
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // Output to local build directory first, then copy tasks handle distribution
    destinationDirectory.set(file("build/libs"))
    // Auto-copy to server mods directory for mdevtools auto-reload
    finalizedBy("copyToServerMods")
    finalizedBy("copyServerResources")
}

// Task to copy the built mod to the server mods directory
tasks.register<Copy>("copyToServerMods") {
    from(tasks.named<Jar>("jar").map { it.archiveFile })
    into(file("server/mods"))
    rename { "irai.mod.reforge_SocketReforge.jar" }
    // Disable up-to-date check to ensure copy always happens
    outputs.upToDateWhen { false }
    // Ensure this runs after compile tasks to avoid conflict with HyUI dependency
    mustRunAfter(tasks.compileJava)
    mustRunAfter(tasks.compileTestJava)
}

// Task to copy server resources to server directory
tasks.register<Copy>("copyServerResources") {
    from("src/main/resources/Server")
    into(file("server/Server"))
}

// Standalone DynamicFloatingDamageFormatter jars
val dynamicFormatterJar by tasks.registering(Jar::class) {
    archiveBaseName.set("DynamicFloatingDamageFormatter")
    archiveClassifier.set("core")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    destinationDirectory.set(file("build/libs"))
    from(sourceSets.main.get().output) {
        include("irai/mod/DynamicFloatingDamageFormatter/**")
    }
    from("DynamicFloatingDamageFormatter/manifest.json")
}

val dynamicFormatterAdapterJar by tasks.registering(Jar::class) {
    archiveBaseName.set("DynamicFloatingDamageFormatter")
    archiveClassifier.set("with-adapter")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    destinationDirectory.set(file("build/libs"))
    from(sourceSets.main.get().output) {
        include("irai/mod/DynamicFloatingDamageFormatter/**")
        include("irai/mod/reforge/Entity/Events/DamageNumberEST.class")
        include("irai/mod/reforge/Util/DamageNumberFormatter.class")
    }
    from("DynamicFloatingDamageFormatter/manifest.json")
    from("src/main/resources/Server/Config/DamageNumberConfig.json") {
        into("Server/Config")
    }
}

// Distribution folder with jars + manifest + examples
tasks.register<Copy>("dynamicFormatterDist") {
    dependsOn(dynamicFormatterJar, dynamicFormatterAdapterJar)
    from(dynamicFormatterJar.map { it.archiveFile })
    from(dynamicFormatterAdapterJar.map { it.archiveFile })
    from("DynamicFloatingDamageFormatter/manifest.json")
    from("examples") {
        into("examples")
    }
    from("src/main/resources/Server/Config/DamageNumberConfig.json") {
        into("examples/Server/Config")
    }
    into("dist/DynamicFloatingDamageFormatter")
}
