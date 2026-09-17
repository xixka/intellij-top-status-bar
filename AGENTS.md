# AGENTS.md

IntelliJ IDEA 插件：把 New UI 风格的紧凑状态栏作为官方 Action System 自定义组件（CustomComponentAction），经原生「自定义工具栏」机制放入 Main Toolbar。

## 命令（均已实测）

- 构建/编译验证：`./gradlew buildPlugin --no-daemon`（与 `.github/workflows/ci.yml` 同款，CI 已通过；产物 `build/distributions/*.zip`）
- 仓库无独立 lint/test 配置；Java 源码强制 UTF-8（见 `build.gradle.kts` 的 JavaCompile 配置）

## 待确认（未实测）

- `./gradlew runIde`：沙盒 IDEA 验证插件行为（README 中作为人工验证步骤提及），从未实际执行过

## 目录导览

- `src/main/resources/META-INF/plugin.xml` — 插件声明：ActionGroup 注册、projectService、设置页、通知组
- `src/main/java/com/xixka/topstatusbar/` — 三个入口类：Group（根级动作分组）、Action（CustomComponentAction）、Panel（工具栏内渲染与自适应隐藏）
- `src/main/java/com/xixka/topstatusbar/model/` — StatusItem 架构与状态项 id 注册表
- `src/main/java/com/xixka/topstatusbar/items/` — 17 个状态项实现与 CurrentFileItem 基类
- `src/main/java/com/xixka/topstatusbar/ui/StatusCell.java` — New UI 风格单元格绘制
- `src/main/java/com/xixka/topstatusbar/settings/` — 持久化设置与 Configurable
- `build.gradle.kts` / `.github/workflows/ci.yml` — 构建与 CI（IntelliJ Platform Gradle Plugin 2.0.1、IDEA 2024.1、Java 17）

## 条件路由

- 新增/修改状态项 → 先读 `src/main/java/com/xixka/topstatusbar/model/StatusItem.java` 契约；文件类项继承 `items/CurrentFileItem.java`；必须同步登记 `TopStatusBarManager.createItems()` 与 `model/StatusItems.java`（漏登记则不出现在面板和设置页）
- 平台 API 拿不准 → 对照 intellij-community `241.14494` 分支源码核实后再写。已知坑：`BulkFileListener` 在 `com.intellij.openapi.vfs.newvfs`；`isColumnMode()` 在 `Editor` 接口而非 EditorSettings；缩放用 `com.intellij.util.ui.JBUI.scale`；光标监听走 `EditorFactory.getEventMulticaster()`
- 构建/依赖调整 → 同步核对 `build.gradle.kts` 与 CI 命令一致；Gradle 版本在 `gradle/wrapper/gradle-wrapper.properties`

## 完成的定义

- CI（GitHub Actions）`buildPlugin` 通过即编译完成，无其他自动化门槛
- 行为验证（runIde 沙盒中把 Top Status Bar 拖入 Main Toolbar 搜索按钮左侧）由人工执行，结论写入提交说明
- 每个提交独立可编译：提交即推送，CI 即时反馈

## 硬约束

- 本地不执行任何 gradle 构建/编译任务（含 runIde）：编译验证一律以 CI 结果为准，沙盒验证留给人工
- 每完成一个改动立即 commit 并 push；提交身份固定为 `xaxka`
- 绝不提交凭据/令牌（GitHub PAT 仅用于推送鉴权，不得出现在任何文件或提交中）
- 不修改 IDEA 内部 UI：不创建第二行 Toolbar、不反射访问内部实现、不碰 MainFrame；只用官方 Action System 机制
- 目标平台 2024.1（sinceBuild 233）：升级平台前先复查上面「已知坑」清单中的 API 是否迁移
