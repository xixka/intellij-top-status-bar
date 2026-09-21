# AGENTS.md

IntelliJ IDEA 插件：把 New UI 风格的紧凑状态栏作为官方 Action System 自定义组件（CustomComponentAction），经原生「自定义工具栏」机制放入 Main Toolbar。

## 命令（CI 实测）

- 编译打包：`./gradlew buildPlugin -PbuildVersion=0.1.<n> --no-daemon`（与 `.github/workflows/ci.yml` 同款，master 每次 push 由 CI 验证；产物 `build/distributions/*.zip`）
- 版本号必须显式传入（见硬约束）；仓库无独立 lint/test 任务，CI `buildPlugin` 通过即编译验证

## 待确认（未实测）

- `./gradlew runIde`：沙盒验证插件行为，从未执行过；行为验证目前靠人工

## 目录导览

- `src/main/resources/META-INF/plugin.xml` — 插件声明：根级 Action、projectService、设置页、通知组；**刻意不注册任何 statusBarWidgetFactory**（原因见硬约束）
- `src/main/java/com/xixka/topstatusbar/` — Action（CustomComponentAction 入口）、Manager（item 生命周期、镜像项原生菜单开关读取、陈旧工厂残留检测）、Panel（顶栏渲染与自适应隐藏）
- `src/main/java/com/xixka/topstatusbar/items/` — 17 个状态项；`model/` — StatusItem 架构与 id→显示名注册表；`settings/` — 设置页（总开关 + 自有 6 项逐项开关 + 内容配置）；`ui/StatusCell.java` — 单元格绘制

## 条件路由

- 修改/新增状态项 → 先看 `model/StatusItems.java`（id 注册表）与 `items/` 同类实现；id 一经发布不可改
- 修改逐项显隐决策 → 看 `TopStatusBarManager.isDisplayEnabled`/`syncToNativeMenu`（决策快照比对、变化才 reload）/`installNativeToggleListener`（菜单动作即时同步）/`migrateLegacyOwnItemToggles`（历史勾选迁移）与 `TopStatusBarConfigurable`（自有 6 项 UI）
- 修改构建/发布流程 → 同步核对 `build.gradle.kts` 与 `.github/workflows/ci.yml`
- 平台 API 位置不确定 → 对照 intellij-community 241.14494 源码核实后再写（历史上 JBUI、BulkFileListener、setupAntialiasing、ActionButtonUtil 都曾出错）

## 完成的定义

- CI（GitHub Actions）`buildPlugin` 通过即编译完成，无其他自动化门槛
- 行为验证（runIde 沙盒把 Top Status Bar 拖入 Main Toolbar；原生「状态栏微件」菜单勾选/取消后镜像 11 项逐项跟随显隐；设置页自有 6 项开关与总开关、部署/CodeBuddy 配置；单元格点击弹出菜单与只读锁定切换；装旧副本时启动弹残留警告）由人工执行，结论写入提交说明
- 每个提交独立可编译：提交即推送，CI 即时反馈

## 平台已知坑（对照 intellij-community 233.14475/241.14494/master 源码核实）

- EDT 上调用 `GitRepositoryManager.getRepositoryForFile` 会触发平台断言 "Do not call synchronous repository update in EDT"（VcsRepositoryManager），弹 IDE 内部错误；必须在后台线程解析仓库再回 EDT 应用（参考 `items/GitBranchItem.java` 的异步模式）
- **禁止在普通 ReadAction.compute 里等待 VCS 仓库集合锁**（2026-09-18 用户线程转储死锁）：getRepositoryForFile 触发的集合更新持有仓库写锁并反向等读许可，普通读动作握着读许可等仓库锁，加上 EDT 阻塞写请求即成三环死锁、IDE 冻结。必须 ReadAction.nonBlocking（写请求到来让出读许可）+ getRepositoryForFileQuick（只读锁、绝不触发同步更新，233/241/master 均有）
- **EDT 禁止阻塞式 WriteAction.run 做磁盘/VFS 慢操作**（同一死锁的第三环）：写锁被争用时 SuvorovProgress 直接显示"无响应"。但 2026.1 起 WriteAction.run 仅允许 EDT（ThreadingAssertions，跨线程直接抛 RuntimeExceptionWithAttachments，2026-09-21 用户堆栈实证）——快写操作（如只读属性翻转）与原生 ToggleReadOnlyAttributePanel 一致在 EDT 执行 WriteAction（见 `items/ReadOnlyItem.java`）
- 涉及 VCS/仓库映射、VFS 扫描类 API（getAllVersionedRoots、checkAndUpdateRepositoryCollection 等）一律不放 EDT
- EDT 的 Swing 回调（mouseClicked 等）不持有 write-intent 锁：`FileDocumentManager.saveAllDocuments` 等模型访问必须包 `WriteIntentReadAction.run`（且必须在 EDT，平台 assertEventDispatchThread；见 `items/ReadOnlyItem.java`）；注意 `run` 有 Runnable/ThrowableRunnable 两个重载，lambda 必须显式 `(Runnable)` 转型，否则编译歧义（run 21 编译事故）；`ReadAction.nonBlocking` 同理有 Runnable/Callable 重载，块式 lambda 显式 return 消歧
- **2026.x 平台微件前端化**：Git 分支已迁移为主工具栏动作 `main.toolbar.git.Branches`（GitToolbarWidgetAction），多数原生微件不再挂在底栏（PowerSaveMode/Encoding 等 id 未变但底栏常无实例）——`StatusBar.getWidget(id)` 的返回值不再可靠反映用户意图，**判断原生菜单勾选状态必须读持久化的 `com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetSettings`**（`isEnabled(factory)`，工厂经 `StatusBarWidgetFactory.EP_NAME.getExtensionList()` 按 id 解析；233.14475/241.14494/262 三版核实 FQN 与签名一致，存储在 ide.general.xml）
- **2026.x 会为每个经典 statusBarWidgetFactory 生成第二套菜单条目（独立存储）**（用户 2026-09-20 截图实证：插件 6 项全部成对出现、两行勾选状态互不相同、11 个原生项仅一次；对照 master StatusBarWidgetsActionGroup/StatusBarWidgetSettings 源码——同 id 工厂必同状态，状态相异证明另一套条目读的是另一套存储，插件无法读取）：给插件自有项注册经典工厂必然导致菜单成双 + 一半开关失灵，此路不通；自有项显隐只能走插件设置页
- **2026.x 前端化微件的菜单勾选不落经典存储，不可镜像**（idea.log 2026-09-21 09:01 实证，IU-261.25134.95）：VfsRefresh 的 ToggleWidgetAction 两次触发后经典 `StatusBarWidgetSettings` 决策快照纹丝不动（勾选写入前端独立存储，插件不可读），同日 PowerSaveMode 勾选却能即时同步——经典存储通道本身可用，仅前端化微件例外。诊断信号：监听器日志「…未改变经典存储决策…前端独立存储…」。**此类微件（VfsRefresh 等）不要设为镜像对象**，对应状态项只能走设置页自有项

## 硬约束

- **每次发布新版本，版本号必须加 1**：dev 构建由 CI 自动注入 `0.1.<run_number>`；正式构建必须 `-PbuildVersion=x.y.z` 且大于上一正式版。版本号重复会导致 IDE 视为"已安装"不升级、旧构建残留，表现为修复无效/幽灵条目（2026-09-17 双条目事故根因）
- **已发布的动作 id、状态项 id 与显示文本永不改动/删除**：`TopStatusBar.Widget`（工具栏自定义按 id 持久化 ActionUrl）、设置页 `topStatusBar`、各状态项 id（statusText/fileSystemSync/codeBuddy/aggregator/networkLocation/deployServer…）。六个同名微件工厂注册经历了三轮反复：2026-09-18 因双插件副本致菜单成双而移除 → 2026-09-20 为让原生菜单接管逐项显示而恢复（id 与旧版一致）→ 同日晚因 2026.x 前端桥接为每个经典工厂生成第二套独立存储的菜单条目（成对且状态互不相通、插件读不到另一套存储）而再次移除，自有 6 项改由插件设置页控制。fileSystemSync 另有第四轮反复：2026-09-21 上午改为镜像平台 VfsRefresh 工厂（试图让原生菜单直接控制），当日实测 2026.1 把该微件勾选写入前端独立存储（经典存储快照不变、插件不可读）而立即回退设置页。ide.general.xml 中遗留的同 id 开关状态按平台规则复用，无害；**今后不要再为插件自有项注册 statusBarWidgetFactory，也不要把自有项镜像到前端化微件**
- **顶栏显示采用混合真源（2026-09-20，跨版本鲁棒）**：镜像平台原生微件的 11 项跟随原生「状态栏微件」菜单（View → Appearance → Status Bar Widgets，233+ 各版均有该菜单；机制：`TopStatusBarManager.isDisplayEnabled` 经 `StatusBarWidgetFactory.EP_NAME` 解析工厂后读持久化 `StatusBarWidgetSettings.isEnabled(factory)`，**并叠加工厂自带显隐逻辑 `isAvailable(project)`**——与原生 View 菜单展示条目前的判定一致（241/2026.x ToggleWidgetAction.update 源码核实），可用性变化经决策快照轮询捕获——**绝不读底栏微件实例**；工厂缺失回退设置页 itemEnabled）；插件自有 6 项由插件设置页（`TopStatusBarConfigurable`，持久化 itemEnabled）控制，**全版本行为一致**；设置页显示名必须与顶栏观感一致（statusText=当前项目、fileSystemSync=文件同步，2026-09-20 用户按顶栏文案找不到设置条目的教训）。变化检测：平台无变更 topic → `AnActionListener` 监听 `StatusBarWidgets.Toggle.*` 动作即时同步（动作触发但快照未变 = 勾选落入 2026.x 前端独立存储，日志留证据）+ 5s 轮询决策快照兜底（变化才 reload，items 全空也不断轮询）；读取异常 fail-open 按启用处理。启动时 `detectStaleWidgetFactories` 扫描 EP 中六个遗留 id——命中即旧副本残留（重复菜单的另一来源），日志（含 classloader 证据）+ 每会话一次气球通知指引用户清理
- **自有 6 项历史勾选一次性迁移（2026-09-20 晚）**：注册工厂时代的用户勾选持久化在平台经典存储（ide.general.xml，同 id）；`migrateLegacyOwnItemToggles` 在启动时把「经典存储显式关闭 + 设置页无显式选择」的项迁入 itemEnabled=false（旧工厂未覆盖 isEnabledByDefault=true → 持久化值只可能是显式关闭；设置页选择优先 → 幂等）。不清理平台遗留条目（无工厂应答后无读取方，无害）。无此迁移，用户在旧版取消勾选的项升级后全部重现
- 本地不执行任何 gradle 构建（含 runIde）：编译验证一律以 CI 结果为准，沙盒验证留给人工
- 不修改 IDEA 内部 UI：不创建第二行 Toolbar、不反射内部实现、不碰 MainFrame；只用官方 Action System 公共 API
- 绝不提交凭据/令牌（GitHub PAT 仅用于推送鉴权）；每完成一个改动立即 commit 并 push，提交身份固定为 `xaxka`
- 目标平台 2024.1（sinceBuild 233）：升级平台前先复查上述已知坑清单中的 API 是否迁移

## 协作约定（仓库所有者已确认）

- 发布仅走 GitHub Releases：master 每次 CI 通过后自动更新 dev 预构建（tag `dev`，版本号自动递增）；正式版人工上传 `build/distributions/*.zip`；不上架 JetBrains Marketplace
- 分支模型：单人直推 master，CI 绿灯是唯一合入门槛，不建 PR
- 出问题一律 fix-forward：追加修复提交推进，不 revert、不改写已推送历史
