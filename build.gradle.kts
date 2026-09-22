import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

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

    // 正式版发布（release.yml）执行 verifyPlugin：覆盖最低支持版本 2023.3、
    // 编译基线 2024.1 与当前推荐版本（recommended() 按 sinceBuild..untilBuild
    // 区间解析最新推荐 IDE）。Marketplace 上传时官方也会跑 Plugin Verifier，
    // 这里提前在同一构建里把关。DSL 对照 intellij-platform-gradle-plugin
    // v2.0.1 源码核实（2026-09-22）
    pluginVerification {
        ides {
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2023.3")
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2024.1")
            recommended()
        }
    }

    // Marketplace 自动上传（release.yml 的 publishPlugin 步骤）：
    // token 从 -PmarketplaceToken 注入（CI 里来自 MARKETPLACE_PUBLISH_TOKEN
    // secret）；未传时仅当 publishPlugin 任务被调用才会报缺 token，
    // 不影响 buildPlugin/verifyPlugin 等常规构建
    publishing {
        token = providers.gradleProperty("marketplaceToken")
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
