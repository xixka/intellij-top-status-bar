plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.0.1"
}

group = "com.xixka"
version = "0.1.0"

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.1")
        bundledPlugin("Git4Idea")
        instrumentationTools()
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.xixka.topstatusbar"
        name = "Top Status Bar"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "233"
            untilBuild = "299.*"
        }
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    matching { it.name == "buildSearchableOptions" }.configureEach {
        enabled = false
    }
}
