plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.0.1"
}

group = "com.xixka"

// 版本说明（务必阅读 AGENTS.md 硬约束）：
// - CI 每次 dev 构建注入 -PbuildVersion=0.1.<run_number>，保证 IDE 能识别为升级
// - 本地构建/正式发布可显式指定：./gradlew buildPlugin -PbuildVersion=0.2.0
// - 绝不能让两次发布携带相同版本号，否则 "Install Plugin from Disk" 不触发升级，
//   旧构建继续生效，用户会看到已被删除/改名的旧条目（幽灵条目）
version = (project.findProperty("buildVersion") as String?) ?: "0.1.0"

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
