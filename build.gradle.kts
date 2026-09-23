import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    id("java")
    // 2.0.1 → 2.19.0：IC 自 2025.3 起并入统一发行版（idea-2025.3.tar.gz，不再
    // 单独分发 ideaIC），2.0.1 的 recommended() 仍按 ideaIC:2025.3 解析导致
    // verifyPlugin 依赖解析失败（v1.0.0 release 失败原因）；2.19.0 起 IC 目标
    // 默认验证集自动纳入统一 IDEA 2025.3+ 发行版（官方 release notes 核实）
    id("org.jetbrains.intellij.platform") version "2.19.0"
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
        // 2025.3 之前的 IC 发行版仍用此助手（2.19.0 保留 legacy 支持）
        intellijIdeaCommunity("2024.1")
        bundledPlugin("Git4Idea")
        // instrumentationTools() 助手已在插件 2.19.0 移除：javaCompiler()
        // 依赖默认生效，无需显式声明（官方 DependenciesExtension 文档核实）
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

intellijPlatform {
    // 设置页无自定义可搜索关键词，跳过 buildSearchableOptions
    // （该任务需启动 IDE 建搜索索引，拖慢 CI；原以任务名匹配禁用，现改用扩展属性）
    buildSearchableOptions = false

    pluginConfiguration {
        id = "com.xixka.topstatusbar"
        name = "Top Status Bar"
        version = project.version.toString()

        ideaVersion {
            // 241 起：语言服务微件（com.intellij.platform.lang.lsWidget 包、
            // LangBundle 的 language.services.widget.* 键）与
            // IOUtil.directBuffersTotalAllocatedSize 均为 241+ 才有（v1.0.1
            // Verifier 对 233 报 6-7 项兼容性错误），233 无法真正运行
            sinceBuild = "241"
            untilBuild = "299.*"
        }
    }

    // 正式版发布（release.yml）执行 verifyPlugin：覆盖最低支持版本 2024.1
    // 与当前推荐版本（recommended() 按 sinceBuild..untilBuild
    // 区间解析最新推荐 IDE；2.19.0 起 IC 目标自动纳入统一 IDEA 2025.3+）。
    // Marketplace 上传时官方也会跑 Plugin Verifier，这里提前在同一构建里把关。
    // DSL 对照 intellij-platform-gradle-plugin v2.19.0 源码核实（2026-09-22）
    pluginVerification {
        // 显式固定为仅 COMPATIBILITY_PROBLEMS（= 2.0.1 默认值）：2.19.0 默认
        // 收紧为 COMPATIBILITY_PROBLEMS + INTERNAL_API_USAGES +
        // OVERRIDE_ONLY_API_USAGES；本插件镜像原生语言服务微件需触碰
        // lsWidget 内部 API，收紧默认值会误杀发布，维持 2.0.1 判定口径
        failureLevel = listOf(VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS)
        ides {
            // 2.19.0 中 ide() 助手已改名 create()（源码核实：
            // IntelliJPlatformExtension.PluginVerification.Ides 接口）
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2024.1")
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
}
