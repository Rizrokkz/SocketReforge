plugins {
    id("java")
}

group = "irai.mod.reforge"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(files("libs/HytaleServer.jar"))
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation(files("libs/HytaleServer.jar"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar{
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    destinationDirectory.set(file("F:\\XboxGames\\Hytale\\UserData\\Mods"))
}

// Task to copy the built mod to the server mods directory
tasks.register<Copy>("copyToServerMods") {
    from(tasks.jar)
    into(file("server/mods"))
    rename { "irai.mod.reforge_SocketReforge.jar" }
}

// Task to copy server resources to server directory
tasks.register<Copy>("copyServerResources") {
    from("src/main/resources/Server")
    into(file("server/Server"))
}
