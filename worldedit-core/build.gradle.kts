import org.gradle.plugins.ide.idea.model.IdeaModel

plugins {
    `java-library`
    antlr
}

project.description = "Core"

applyPlatformAndCoreConfiguration()

dependencies {
    constraints {
        implementation( "org.yaml:snakeyaml") {
            version { strictly("1.28") }
            because("Bukkit provides SnakeYaml")
        }
    }

    api(project(":worldedit-libs:core"))
    implementation("de.schlichtherle:truezip:6.8.4")
    implementation("org.mozilla:rhino-runtime:1.7.13")
    implementation("org.yaml:snakeyaml")
    implementation("com.google.guava:guava")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("com.google.code.gson:gson")
    implementation("org.apache.logging.log4j:log4j-api:2.14.1") {
        because("Mojang provides Log4J 2.14.1")
    }
    implementation("it.unimi.dsi:fastutil")
    compileOnly("net.kyori:adventure-nbt:4.8.1")
    testImplementation("net.kyori:adventure-nbt:4.7.0")

    val antlrVersion = "4.9.1"
    antlr("org.antlr:antlr4:$antlrVersion")
    implementation("org.antlr:antlr4-runtime:$antlrVersion")
    implementation("com.googlecode.json-simple:json-simple:1.1.1") { isTransitive = false }
    compileOnly(project(":worldedit-libs:core:ap"))
    annotationProcessor(project(":worldedit-libs:core:ap"))
    // ensure this is on the classpath for the AP
    annotationProcessor("com.google.guava:guava:21.0")
    compileOnly("com.google.auto.value:auto-value-annotations:${Versions.AUTO_VALUE}")
    annotationProcessor("com.google.auto.value:auto-value:${Versions.AUTO_VALUE}")
    testRuntimeOnly("org.apache.logging.log4j:log4j-core:2.14.1")
    implementation("com.github.luben:zstd-jni:1.5.0-2")
    compileOnly("net.fabiozumbi12:redprotect:1.9.6")
    api("com.github.intellectualsites.plotsquared:PlotSquared-API:4.514") { isTransitive = false }
    api("com.plotsquared:PlotSquared-Core:6.0.6-SNAPSHOT")
    compileOnlyApi("net.kyori:adventure-api:4.8.0")
    compileOnlyApi("net.kyori:adventure-text-minimessage:4.1.0-SNAPSHOT")
    api("com.intellectualsites.paster:Paster:1.0.1-SNAPSHOT")
    compileOnly("net.jpountz:lz4-java-stream:1.0.0") { isTransitive = false }
    compileOnly("org.lz4:lz4-java:1.8.0")
}

tasks.named<Test>("test") {
    maxHeapSize = "1G"
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn(":worldedit-libs:build")
    options.compilerArgs.add("-Aarg.name.key.prefix=")
}

tasks.named<AntlrTask>("generateGrammarSource").configure {
    val pkg = "com.sk89q.worldedit.antlr"
    outputDirectory = file("build/generated-src/antlr/main/${pkg.replace('.', '/')}")
    arguments = listOf(
            "-visitor", "-package", pkg,
            "-Xexact-output-dir"
    )
}

tasks.named("sourcesJar") {
    mustRunAfter("generateGrammarSource")
}

// Give intellij info about where ANTLR code comes from
plugins.withId("idea") {
    configure<IdeaModel> {
        afterEvaluate {
            module.sourceDirs.add(file("src/main/antlr"))
            module.sourceDirs.add(file("build/generated-src/antlr/main"))
            module.generatedSourceDirs.add(file("build/generated-src/antlr/main"))
        }
    }
}

sourceSets.named("main") {
    java {
        srcDir("src/legacy/java")
    }
}

tasks.named<Copy>("processResources") {
    filesMatching("fawe.properties") {
        expand("version" to "$version",
                "commit" to "${rootProject.ext["revision"]}",
                "date" to "${rootProject.ext["date"]}")
    }
}
