plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "io.ch.java.opt.chain"
version = "0.1.2-SNAPSHOT"

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
        instrumentationTools()
        pluginVerifier()
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

intellijPlatform {
    pluginConfiguration {
        id = "io.ch.java.opt.chain.idea"
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
