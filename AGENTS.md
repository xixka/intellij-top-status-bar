# AGENTS.md

IntelliJ IDEA 插件：把 New UI 风格的紧凑状态栏作为官方 Action System 自定义组件（CustomComponentAction），经原生「自定义工具栏」机制放入 Main Toolbar。

## 命令（CI 实测）

- 编译打包：`./gradlew buildPlugin -PbuildVersion=0.1.<n> --no-daemon`（与 `.github/workflows/ci.yml` 同款，master 每次 push 由 CI 验证；产物 `build/distributions/*.zip`）
- 版本号必须显式传入（见硬约束）；仓库无独立 lint/test 任务，CI `buildPlugin` 通过即编译验证

## 待确认（未实测）

- `./gradlew runIde`：沙盒验证插件行为，从未执行过；行为验证目前靠人工

## 目录导览

- `src/main/resources/META-INF/plugin.xml` — 插件声明：根级 Action、projectService、设置页、通知组
- `src/main/java/com/xixka/topstatusbar/` — Action（CustomComponentAction 入口）、Manager（item 生命周期与原生微件状态读取）、Panel（顶栏渲染与自适应隐藏）
- `src/main/java/com/xixka/topstatusbar/items/` — 17 个状态项；`model/` — StatusItem 架构与 id→显示名注册表；`settings/` — 设置页与持久化；`ui/StatusCell.java` — 单元格绘制

## 条件路由

- 修改/新增状态项 → 先看 `model/StatusItems.java`（id 注册表）与 `items/` 同类实现；id 一经发布不可改
- 修改微件开关同步 → 看 `widget/TopBarSyncWidget.java` 与 `TopStatusBarManager.isSuppressedByPlatformWidget`
- 修改构建/发布流程 → 同步核对 `build.gradle.kts` 与 `.github/workflows/ci.yml`
- 平台 API 位置不确定 → 对照 intellij-community 241.14494 源码核实后再写（历史上 JBUI、BulkFileListener、setupAntialiasing、ActionButtonUtil 都曾出错）

## 完成的定义

- CI（GitHub Actions）`buildPlugin` 通过即编译完成，无其他自动化门槛
- 行为验证（runIde 沙盒把 Top Status Bar 拖入 Main Toolbar、原生「状态栏微件」菜单开关后顶栏原生对应项跟随显隐、插件自有项在设置页开关、单元格点击弹出菜单与只读锁定切换、原生菜单中无插件注册的条目）由人工执行，结论写入提交说明
- 每个提交独立可编译：提交即推送，CI 即时反馈

## 平台已知坑（对照 intellij-community 241.14494 源码核实）

- EDT 上调用 `GitRepositoryManager.getRepositoryForFile` 会触发平台断言 "Do not call synchronous repository update in EDT"（VcsRepositoryManager），弹 IDE 内部错误；必须在后台线程 ReadAction 中解析仓库，再回 EDT 应用（参考 `items/GitBranchItem.java` 的异步模式）
- 涉及 VCS/仓库映射、VFS 扫描类 API（getAllVersionedRoots、checkAndUpdateRepositoryCollection 等）一律不放 EDT
- EDT 的 Swing 回调（mouseClicked 等）不持有 write-intent 锁：`FileDocumentManager.saveAllDocuments` 等模型访问必须包 `WriteIntentReadAction.run`（见 `items/ReadOnlyItem.java`）；注意 `run` 有 Runnable/ThrowableRunnable 两个重载，lambda 必须显式 `(Runnable)` 转型，否则编译歧义（run 21 编译事故）；`WriteAction.run` 自带写锁可用

## 硬约束

- **每次发布新版本，版本号必须加 1**：dev 构建由 CI 自动注入 `0.1.<run_number>`；正式构建必须 `-PbuildVersion=x.y.z` 且大于上一正式版。版本号重复会导致 IDE 视为"已安装"不升级、旧构建残留，表现为修复无效/幽灵条目（2026-09-17 双条目事故根因）
- **已发布的动作 id、状态项 id 与显示文本永不改动/删除**：`TopStatusBar.Widget`（工具栏自定义按 id 持久化 ActionUrl）、设置页 `topStatusBar`、各状态项 id（statusText/fileSystemSync/codeBuddy/aggregator/networkLocation/deployServer…，设置页按 id 持久化开关）。2026-09-18 按用户要求移除了六个微件工厂注册；ide.general.xml 中遗留的旧微件开关状态会被平台忽略，无害
- **顶栏只读取原生「状态栏微件」菜单，绝不向其注册插件条目**：原生对应项（Git 分支、行列、编码等）通过 `StatusBar.getWidget(id)` 动态跟随原生开关显隐；插件自有项的开关只在插件设置页（2026-09-18 用户明确；此前注册的条目还因用户 IDE 内加载了两份插件副本而在原生菜单中成双出现）
- 本地不执行任何 gradle 构建（含 runIde）：编译验证一律以 CI 结果为准，沙盒验证留给人工
- 不修改 IDEA 内部 UI：不创建第二行 Toolbar、不反射内部实现、不碰 MainFrame；只用官方 Action System 公共 API
- 绝不提交凭据/令牌（GitHub PAT 仅用于推送鉴权）；每完成一个改动立即 commit 并 push，提交身份固定为 `xaxka`
- 目标平台 2024.1（sinceBuild 233）：升级平台前先复查上述已知坑清单中的 API 是否迁移

## 协作约定（仓库所有者已确认）

- 发布仅走 GitHub Releases：master 每次 CI 通过后自动更新 dev 预构建（tag `dev`，版本号自动递增）；正式版人工上传 `build/distributions/*.zip`；不上架 JetBrains Marketplace
- 分支模型：单人直推 master，CI 绿灯是唯一合入门槛，不建 PR
- 出问题一律 fix-forward：追加修复提交推进，不 revert、不改写已推送历史
