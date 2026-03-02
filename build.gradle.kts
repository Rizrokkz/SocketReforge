plugins {
    id("java")
}

group = "irai.mod.reforge"
version = "1.1.0"

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
    compileOnly(files("libs/DynamicTooltipsLib-1.5.0.jar"))
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    enabled = false
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    destinationDirectory.set(file("F:\\XboxGames\\Hytale\\UserData\\Mods"))
    // Auto-copy to server mods directory for mdevtools auto-reload
    finalizedBy("copyToServerMods")
    finalizedBy("copyServerResources")
}

// Task to copy the built mod to the server mods directory
tasks.register<Copy>("copyToServerMods") {
    from(tasks.jar)
    into(file("server/mods"))
    rename { "irai.mod.reforge_SocketReforge.jar" }
    // Ensure this runs after compile tasks to avoid conflict with HyUI dependency
    mustRunAfter(tasks.compileJava)
    mustRunAfter(tasks.compileTestJava)
}

// Task to copy server resources to server directory
tasks.register<Copy>("copyServerResources") {
    from("src/main/resources/Server")
    into(file("server/Server"))
}
