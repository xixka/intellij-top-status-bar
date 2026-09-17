# AGENTS.md

IntelliJ IDEA 插件：把 New UI 风格的紧凑状态栏作为官方 Action System 自定义组件（CustomComponentAction），经原生「自定义工具栏」机制放入 Main Toolbar。

## 命令（均已实测）

- 构建/编译验证：`./gradlew buildPlugin --no-daemon`（与 `.github/workflows/ci.yml` 同款，CI 已通过；产物 `build/distributions/*.zip`）
- 仓库无独立 lint/test 配置；Java 源码强制 UTF-8（见 `build.gradle.kts` 的 JavaCompile 配置）

## 待确认（未实测）

- `./gradlew runIde`：沙盒 IDEA 验证插件行为（README 中作为人工验证步骤提及），从未实际执行过

## 目录导览

- `src/main/resources/META-INF/plugin.xml` — 插件声明：根级 Action 注册、projectService、设置页、通知组
- `src/main/java/com/xixka/topstatusbar/` — 两个入口类：Action（根级 CustomComponentAction，直接注册于 plugin.xml）、Panel（工具栏内渲染与自适应隐藏）
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
- **已发布动作的 id 与显示文本永不改动/删除**：动作 id `TopStatusBar.Widget`、文本 `Top Status Bar`、设置页 id `topStatusBar` 一经发布即冻结。依据（intellij-community 源码已核实）：`CustomActionsSchema`/`ActionUrl` 按 **id** 持久化用户工具栏自定义，Add Action 树、Menus and Toolbars、Find Action 的显示文本全部由 `ActionManager` 按 id **实时解析**（`CustomizationUtil.acceptObjectIconAndText`、`ActionsTreeUtil.createGroup`）；父组匹配用的是**显示名**而非 id。改名/删除动作 = 用户的持久化条目变成幽灵条目（显示原始 id）或被静默丢弃，且旧安装未升级前会同时看到新旧两套条目
- **每次发布版本号必须递增**：CI 已注入 `-PbuildVersion=0.1.<run_number>`（见 ci.yml），本地/正式构建需显式 `-PbuildVersion=x.y.z`。版本号重复会导致 IDE 把同 id 同版本 zip 视为"已安装"，不触发升级、旧构建继续生效——这正是"修了但用户还是看到旧条目（Top Status Bar / Top Status Bar Widget 双条目）"事故的根因

## 协作约定（仓库所有者已确认）

- 发布仅走 GitHub Releases：master 每次 CI 通过后自动更新 dev 预发布（tag `dev`，见 ci.yml 末步）；正式版人工上传 `build/distributions/*.zip`；不上架 JetBrains Marketplace
- 分支模型：单人直推 master，CI 绿灯是唯一合入门槛，不建 PR
- 出问题一律 fix-forward：追加修复提交推进，不 revert、不改写已推送历史
