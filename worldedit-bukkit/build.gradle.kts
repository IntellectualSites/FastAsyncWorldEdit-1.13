import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    `java-library`
}

project.description = "Bukkit"

applyPlatformAndCoreConfiguration()
applyShadowConfiguration()

repositories {
    maven {
        name = "SpigotMC"
        url = uri("https://hub.spigotmc.org/nexus/content/groups/public")
    }
    maven {
        name = "PaperMC"
        url = uri("https://papermc.io/repo/repository/maven-public/")
    }
    maven {
        name = "EngineHub"
        url = uri("https://maven.enginehub.org/repo/")
    }
    maven {
        name = "Athion"
        url = uri("https://ci.athion.net/plugin/repository/tools/")
    }
    maven {
        name = "JitPack"
        url = uri("https://jitpack.io")
    }
    maven {
        name = "ProtocolLib"
        url = uri("https://repo.dmulloy2.net/nexus/repository/public/")
    }
    maven {
        name = "Inventivetalent"
        url = uri("https://repo.inventivetalent.org/content/groups/public/")
    }
    maven {
        name = "OSS Sonatype Snapshots"
        url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
    }
    maven {
        name = "OSS Sonatype Releases"
        url = uri("https://oss.sonatype.org/content/repositories/releases/")
    }
    flatDir { dir(File("src/main/resources")) }
}

configurations.all {
    resolutionStrategy {
        force("com.google.guava:guava:21.0")
    }
}

dependencies {
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") { isTransitive = false }
    api(project(":worldedit-core"))
    api(project(":worldedit-libs:bukkit"))
    implementation(":worldedit-adapters:")
    // Paper-patched NMS jars
    compileOnly("com.destroystokyo.paperv1_15_r1:paperv1_15_r1:1_15_r1")
    compileOnly("com.destroystokyo.paperv1_16_r1:paperv1_16_r1:1_16_r1")
    compileOnly("com.destroystokyo.paperv1_16_r2:paperv1_16_r2:1_16_r2")
    compileOnly("com.destroystokyo.paperv1_16_r3:paperv1_16_r3:1_16_r3")
    compileOnly("org.spigotmcv1_15_r1:spigotmcv1_15_r1:1_15_r1")
    compileOnly("org.spigotmcv1_16_r1:spigotmcv1_16_r1:1_16_r1")
    compileOnly("org.spigotmcv1_16_r2:spigotmcv1_16_r2:1_16_r2")
    compileOnly("org.spigotmcv1_16_r3:spigotmcv1_16_r3:1_16_r3")
    implementation("it.unimi.dsi:fastutil")
    api("com.destroystokyo.paper:paper-api:1.16.5-R0.1-SNAPSHOT") {
        exclude("junit", "junit")
        isTransitive = false
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation(platform("org.apache.logging.log4j:log4j-bom:2.14.1") {
        because("Spigot provides Log4J (sort of, not in API, implicitly part of server)")
    })
    implementation("org.apache.logging.log4j:log4j-api")
    compileOnly("org.spigotmc:spigot:1.17-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:21.0.0")
    implementation("io.papermc:paperlib:1.0.6")
    compileOnly("com.sk89q:dummypermscompat:1.10") {
        exclude("com.github.MilkBowl", "VaultAPI")
    }
    testImplementation("org.mockito:mockito-core:3.11.2")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.5") {
        exclude("com.sk89q.worldedit", "worldedit-bukkit")
        exclude("com.sk89q.worldedit", "worldedit-core")
        exclude("com.sk89q.worldedit.worldedit-libs", "bukkit")
        exclude("com.sk89q.worldedit.worldedit-libs", "core")
    }
    compileOnly("net.kyori:adventure-api:4.8.1")
    testImplementation("net.kyori:adventure-api:4.8.1")
    testImplementation("org.checkerframework:checker-qual:3.15.0")
    testImplementation("org.spigotmc:spigot-api:1.17-R0.1-SNAPSHOT") { isTransitive = true }
    api("com.intellectualsites.paster:Paster:1.0.1-SNAPSHOT")
    api("org.lz4:lz4-java:1.8.0")
    api("net.jpountz:lz4-java-stream:1.0.0") { isTransitive = false }
    // Third party
    implementation("org.bstats:bstats-bukkit:2.2.1")
    implementation("org.bstats:bstats-base:2.2.1")
    compileOnlyApi("org.inventivetalent:mapmanager:1.7.10-SNAPSHOT") { isTransitive = false }
    implementation("com.github.TechFortress:GriefPrevention:16.17.1") { isTransitive = false }
    implementation("com.github.bloodmc:GriefDefenderApi:920a610") { isTransitive = false }
    implementation("com.flowpowered:flow-math:1.0.3") {
        because("This dependency is needed by GriefDefender but not exposed transitively.")
        isTransitive = false
    }
    implementation("com.massivecraft:mcore:7.0.1") { isTransitive = false }
    implementation("com.bekvon.bukkit.residence:Residence:4.5._13.1") { isTransitive = false }
    implementation("com.palmergames.bukkit:towny:0.84.0.9") { isTransitive = false }
    implementation("com.thevoxelbox.voxelsniper:voxelsniper:5.171.0") { isTransitive = false }
    implementation("com.comphenix.protocol:ProtocolLib:4.7.0") { isTransitive = false }
    implementation("org.incendo.serverlib:ServerLib:2.2.1")
    api("com.plotsquared:PlotSquared-Bukkit:6.0.6-SNAPSHOT")
}

tasks.named<Copy>("processResources") {
    val internalVersion = project.ext["internalVersion"]
    inputs.property("internalVersion", internalVersion)
    filesMatching("plugin.yml") {
        expand("internalVersion" to internalVersion)
    }
    // exclude adapters entirely from this JAR, they should only be in the shadow JAR
    exclude("**/worldedit-adapters.jar")
}

tasks.named<Jar>("jar") {
    manifest {
        attributes("Class-Path" to CLASSPATH,
                "WorldEdit-Version" to project.version)
    }
}

addJarManifest(WorldEditKind.Plugin, includeClasspath = true)

tasks.named<ShadowJar>("shadowJar") {
    from(zipTree("src/main/resources/worldedit-adapters.jar").matching {
        exclude("META-INF/")
    })
    archiveFileName.set("${rootProject.name}-Bukkit-${project.version}.${archiveExtension.getOrElse("jar")}")
    dependencies {
        // In tandem with not bundling log4j, we shouldn't relocate base package here.
        // relocate("org.apache.logging", "com.sk89q.worldedit.log4j")
        relocate("org.antlr.v4", "com.sk89q.worldedit.antlr4")
        include(dependency(":worldedit-core"))
        include(dependency(":worldedit-libs:bukkit"))
        // Purposefully not included, we assume (even though no API exposes it) that Log4J will be present at runtime
        // If it turns out not to be true for Spigot/Paper, our only two official platforms, this can be uncommented.
        // include(dependency("org.apache.logging.log4j:log4j-api"))
        include(dependency("org.antlr:antlr4-runtime"))
        relocate("org.bstats", "com.sk89q.worldedit.bstats") {
            include(dependency("org.bstats:"))
        }
        relocate("io.papermc.lib", "com.sk89q.worldedit.bukkit.paperlib") {
            include(dependency("io.papermc:paperlib"))
        }
        relocate("it.unimi.dsi.fastutil", "com.sk89q.worldedit.bukkit.fastutil") {
            include(dependency("it.unimi.dsi:fastutil"))
        }
        relocate("org.incendo.serverlib", "com.fastasyncworldedit.serverlib") {
            include(dependency("org.incendo.serverlib:ServerLib:2.2.1"))
        }
        relocate("com.intellectualsites.paster", "com.fastasyncworldedit.paster") {
            include(dependency("com.intellectualsites.paster:Paster:1.0.1-SNAPSHOT"))
        }
        relocate("com.github.luben", "com.fastasyncworldedit.core.zstd") {
            include(dependency("com.github.luben:zstd-jni:1.5.0-2"))
        }
        relocate("net.jpountz", "com.fastasyncworldedit.core.jpountz") {
            include(dependency("net.jpountz:lz4-java-stream:1.0.0"))
        }
        relocate("org.lz4", "com.fastasyncworldedit.core.lz4") {
            include(dependency("org.lz4:lz4-java:1.8.0"))
        }
        relocate("net.kyori", "com.fastasyncworldedit.core.adventure") {
            include(dependency("net.kyori:adventure-nbt:4.8.1"))
        }
    }
}

tasks.named("assemble").configure {
    dependsOn("shadowJar")
}
