plugins {
    id("net.fabricmc.fabric-loom") version "1.18.4"
    `maven-publish`
}

group = "dev.ryanhcode.sable-companion"
version = "1.6.0-port.1+mc26.3"
base.archivesName = "sable-companion-fabric-26.3"

repositories { mavenCentral() }

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
}
tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}

sourceSets {
    main {
        java.setSrcDirs(listOf("common/src/main/java"))
        resources.setSrcDirs(listOf("common/src/main/resources", "fabric/src/main/resources"))
    }
}

dependencies {
    minecraft("com.mojang:minecraft:26.3")
    implementation("net.fabricmc:fabric-loader:0.19.5")
    implementation("net.fabricmc.fabric-api:fabric-api:0.165.0+26.3")
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") { expand("version" to project.version) }
}
tasks.jar { from("LICENSE") }
tasks.test { useJUnitPlatform() }

publishing {
    publications {
        create<MavenPublication>("mavenJava") { from(components["java"]) }
    }
}
