plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "io.github.chensirj"
version = "0.2.1-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    intellijPlatform {
        // Build against 2024.1 APIs; runtime supports through 2025.2 (252.*) via open untilBuild.
        intellijIdeaCommunity("2024.1.7")
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.idea.maven")
        instrumentationTools()
        pluginVerifier()
    }
    // Weaver bytecode rewrite. Packaged next to the plugin jar and added to the JPS classpath.
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.ow2.asm:asm-commons:9.7")
    implementation("org.ow2.asm:asm-tree:9.7")
    implementation("org.ow2.asm:asm-analysis:9.7")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

sourceSets {
    main {
        java {
            srcDir("../opt-chain-annotations/src/main/java")
            srcDir("../opt-chain-maven-plugin/src/main/java")
            exclude("**/OptChainPreprocessMojo.java")
            exclude("**/OptChainWeaveMojo.java")
        }
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "io.github.chensirj.optchain.idea"
        name = "Opt Chain"
        version = project.version.toString()
        ideaVersion {
            // Covers IU/IC 2024.1 (241) … 2025.2 (252) and newer until JetBrains bumps ABI.
            sinceBuild = "241"
            untilBuild = provider { null }
        }
    }
    instrumentCode.set(false)
}

tasks.test {
    useJUnitPlatform()
}

tasks {
    buildSearchableOptions {
        enabled = false
    }
}
