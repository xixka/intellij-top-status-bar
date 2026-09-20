# AGENTS.md

IntelliJ IDEA 插件：把 New UI 风格的紧凑状态栏作为官方 Action System 自定义组件（CustomComponentAction），经原生「自定义工具栏」机制放入 Main Toolbar。

## 命令（CI 实测）

- 编译打包：`./gradlew buildPlugin -PbuildVersion=0.1.<n> --no-daemon`（与 `.github/workflows/ci.yml` 同款，master 每次 push 由 CI 验证；产物 `build/distributions/*.zip`）
- 版本号必须显式传入（见硬约束）；仓库无独立 lint/test 任务，CI `buildPlugin` 通过即编译验证

## 待确认（未实测）

- `./gradlew runIde`：沙盒验证插件行为，从未执行过；行为验证目前靠人工

## 目录导览

- `src/main/resources/META-INF/plugin.xml` — 插件声明：根级 Action、projectService、设置页、通知组
- `src/main/java/com/xixka/topstatusbar/` — Action（CustomComponentAction 入口）、Manager（item 生命周期与原生「状态栏微件」菜单开关读取）、Panel（顶栏渲染与自适应隐藏）
- `src/main/java/com/xixka/topstatusbar/items/` — 17 个状态项；`model/` — StatusItem 架构与 id→显示名注册表；`settings/` — 设置页（总开关与内容配置）；`ui/StatusCell.java` — 单元格绘制；`widget/` — 插件自有项的原生微件工厂（零尺寸同步微件，只做即时通知）

## 条件路由

- 修改/新增状态项 → 先看 `model/StatusItems.java`（id 注册表）与 `items/` 同类实现；id 一经发布不可改
- 修改微件开关同步 → 看 `widget/TopBarSyncWidget.java` 与 `TopStatusBarManager.isDisplayEnabled`/`syncToNativeMenu`（决策快照比对、变化才 reload）
- 修改构建/发布流程 → 同步核对 `build.gradle.kts` 与 `.github/workflows/ci.yml`
- 平台 API 位置不确定 → 对照 intellij-community 241.14494 源码核实后再写（历史上 JBUI、BulkFileListener、setupAntialiasing、ActionButtonUtil 都曾出错）

## 完成的定义

- CI（GitHub Actions）`buildPlugin` 通过即编译完成，无其他自动化门槛
- 行为验证（runIde 沙盒把 Top Status Bar 拖入 Main Toolbar、原生「状态栏微件」菜单勾选/取消后顶栏逐项跟随显隐（含插件自有 6 项，取消后全部隐藏仍可恢复）、设置页总开关与部署/CodeBuddy 配置、单元格点击弹出菜单与只读锁定切换）由人工执行，结论写入提交说明
- 每个提交独立可编译：提交即推送，CI 即时反馈

## 平台已知坑（对照 intellij-community 233.14475/241.14494/master 源码核实）

- EDT 上调用 `GitRepositoryManager.getRepositoryForFile` 会触发平台断言 "Do not call synchronous repository update in EDT"（VcsRepositoryManager），弹 IDE 内部错误；必须在后台线程解析仓库再回 EDT 应用（参考 `items/GitBranchItem.java` 的异步模式）
- **禁止在普通 ReadAction.compute 里等待 VCS 仓库集合锁**（2026-09-18 用户线程转储死锁）：getRepositoryForFile 触发的集合更新持有仓库写锁并反向等读许可，普通读动作握着读许可等仓库锁，加上 EDT 阻塞写请求即成三环死锁、IDE 冻结。必须 ReadAction.nonBlocking（写请求到来让出读许可）+ getRepositoryForFileQuick（只读锁、绝不触发同步更新，233/241/master 均有）
- **EDT 禁止阻塞式 WriteAction.run 做磁盘/VFS 慢操作**（同一死锁的第三环）：写锁被争用时 SuvorovProgress 直接显示"无响应"。写动作跨线程合法： pooled 线程 WriteAction.run + invokeLater 回 EDT（见 `items/ReadOnlyItem.java`）
- 涉及 VCS/仓库映射、VFS 扫描类 API（getAllVersionedRoots、checkAndUpdateRepositoryCollection 等）一律不放 EDT
- EDT 的 Swing 回调（mouseClicked 等）不持有 write-intent 锁：`FileDocumentManager.saveAllDocuments` 等模型访问必须包 `WriteIntentReadAction.run`（且必须在 EDT，平台 assertEventDispatchThread；见 `items/ReadOnlyItem.java`）；注意 `run` 有 Runnable/ThrowableRunnable 两个重载，lambda 必须显式 `(Runnable)` 转型，否则编译歧义（run 21 编译事故）；`ReadAction.nonBlocking` 同理有 Runnable/Callable 重载，块式 lambda 显式 return 消歧
- **2026.x 平台微件前端化**：Git 分支已迁移为主工具栏动作 `main.toolbar.git.Branches`（GitToolbarWidgetAction），多数原生微件不再挂在底栏（PowerSaveMode/Encoding 等 id 未变但底栏常无实例）——`StatusBar.getWidget(id)` 的返回值不再可靠反映用户意图，**判断原生菜单勾选状态必须读持久化的 `com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetSettings`**（`isEnabled(factory)`，工厂经 `StatusBarWidgetFactory.EP_NAME.getExtensionList()` 按 id 解析；233.14475/241.14494/262 三版核实 FQN 与签名一致，存储在 ide.general.xml）

## 硬约束

- **每次发布新版本，版本号必须加 1**：dev 构建由 CI 自动注入 `0.1.<run_number>`；正式构建必须 `-PbuildVersion=x.y.z` 且大于上一正式版。版本号重复会导致 IDE 视为"已安装"不升级、旧构建残留，表现为修复无效/幽灵条目（2026-09-17 双条目事故根因）
- **已发布的动作 id、状态项 id 与显示文本永不改动/删除**：`TopStatusBar.Widget`（工具栏自定义按 id 持久化 ActionUrl）、设置页 `topStatusBar`、各状态项 id（statusText/fileSystemSync/codeBuddy/aggregator/networkLocation/deployServer…，状态项 id 同时是微件工厂 id，原生菜单勾选状态按它持久化）。六个微件工厂注册于 2026-09-18 按用户要求移除（当时双插件副本致菜单成双），2026-09-20 为让原生菜单接管逐项显示而恢复，id 与旧版一致，用户已持久化的勾选状态无缝衔接；ide.general.xml 中遗留状态按平台规则复用，无害
- **顶栏显示的唯一真源是原生「状态栏微件」菜单**（View → Appearance → Status Bar Widgets 的逐项勾选，2026-09-20 用户最终确认："系统设置选择了什么就显示什么"）：勾选即显示、取消即隐藏。机制：`TopStatusBarManager.isDisplayEnabled` 经 `StatusBarWidgetFactory.EP_NAME` 解析工厂后读持久化 `StatusBarWidgetSettings.isEnabled(factory)`（与菜单复选框同源）——**绝不读底栏微件实例**（`StatusBar.getWidget` 在 2026.x 恒 null，历史两轮"跟随原生菜单"实现都因此把勾选项永久隐藏，语义没错、机制错了；2026-09-18 曾短暂改为插件设置页为真源，用户次日否定"我不是要全部显示"）。插件自有 6 项以同名 id 注册 `statusBarWidgetFactory` 出现在同一菜单。工厂缺失（老平台/对应插件未装）回退设置页 itemEnabled 持久化映射（设置 UI 已移除逐项开关）。变化检测：平台无变更 topic → 5s 轮询决策快照（变化才 reload，items 全空也不断轮询）+ 同步微件被平台托管时 addNotify/removeNotify 即时通知；读取异常 fail-open 按启用处理
- 本地不执行任何 gradle 构建（含 runIde）：编译验证一律以 CI 结果为准，沙盒验证留给人工
- 不修改 IDEA 内部 UI：不创建第二行 Toolbar、不反射内部实现、不碰 MainFrame；只用官方 Action System 公共 API
- 绝不提交凭据/令牌（GitHub PAT 仅用于推送鉴权）；每完成一个改动立即 commit 并 push，提交身份固定为 `xaxka`
- 目标平台 2024.1（sinceBuild 233）：升级平台前先复查上述已知坑清单中的 API 是否迁移

## 协作约定（仓库所有者已确认）

- 发布仅走 GitHub Releases：master 每次 CI 通过后自动更新 dev 预构建（tag `dev`，版本号自动递增）；正式版人工上传 `build/distributions/*.zip`；不上架 JetBrains Marketplace
- 分支模型：单人直推 master，CI 绿灯是唯一合入门槛，不建 PR
- 出问题一律 fix-forward：追加修复提交推进，不 revert、不改写已推送历史
