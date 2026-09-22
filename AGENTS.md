# AGENTS.md

IntelliJ IDEA 插件：把 New UI 风格的紧凑状态栏作为官方 Action System 自定义组件（CustomComponentAction），经原生「自定义工具栏」机制放入 Main Toolbar。

## 命令（CI 实测）

- 编译打包：`./gradlew buildPlugin -PbuildVersion=0.1.<n> --no-daemon`（与 `.github/workflows/ci.yml` 同款，master 每次 push 由 CI 验证；产物 `build/distributions/*.zip`）
- 版本号必须显式传入（见硬约束）；仓库无独立 lint/test 任务，CI `buildPlugin` 通过即编译验证
- 正式发布：`git tag v1.0.0 && git push origin v1.0.0`（触发 `.github/workflows/release.yml`：构建 + Plugin Verifier + GitHub Release，secret 在位时自动传 Marketplace）；也可在 Actions 页手动 Run workflow 输入版本号对当前 HEAD 发布

## 待确认（未实测）

- `./gradlew runIde`：沙盒验证插件行为，从未执行过；行为验证目前靠人工

## 目录导览

- `src/main/resources/META-INF/plugin.xml` — 插件声明：根级 Action、projectService、设置页、通知组；**刻意不注册任何 statusBarWidgetFactory**（原因见硬约束）
- `src/main/java/com/xixka/topstatusbar/` — Action（CustomComponentAction 入口）、Manager（item 生命周期、镜像项原生菜单开关读取、陈旧工厂残留检测）、Panel（顶栏渲染与自适应隐藏）
- `src/main/java/com/xixka/topstatusbar/items/` — 17 个状态项；`model/` — StatusItem 架构与 id→显示名注册表；`settings/` — 设置页（总开关 + 自有 6 项逐项开关 + 内容配置）；`ui/StatusCell.java` — 单元格绘制
- `README.md`（英文主文档，面向 Marketplace/国际用户）/ `README.zh-CN.md`（中文版）——两份内容同步维护：改一项必须同时核对另一份（状态项列表、默认显隐、发布模型等事实性内容）

## 条件路由

- 修改/新增状态项 → 先看 `model/StatusItems.java`（id 注册表 + 按项默认显隐 `DEFAULT_DISABLED_IDS`）与 `items/` 同类实现；id 一经发布不可改
- 修改状态项点击菜单/悬停详情 → 一律复用平台原生实现（见硬约束 2026-09-22 定案）：菜单上下文用 `items/EditorContext.popupContext`；缩进项解析链看 `items/IndentItem.findUiContributor`（瞬态优先 + provider 回退）；参考样板 `LineSeparatorItem`（动作组）与 `EncodingItem`（ChangeFileEncodingAction）
- 修改逐项显隐决策 → 看 `TopStatusBarManager.isDisplayEnabled`/`syncToNativeMenu`（决策快照比对、变化才 reload）/`installNativeToggleListener`（菜单动作即时同步）/`migrateLegacyOwnItemToggles`（历史勾选迁移）与 `TopStatusBarConfigurable`（自有 6 项 UI）；改默认显隐/调整默认关闭集合 → 看 `StatusItems.DEFAULT_DISABLED_IDS` + `TopStatusBarSettings.applyDefaultOffPolicy`（遗留显式值一次性清除，按 `defaultOffPolicyApplied` 记账幂等）
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

- **每次发布新版本，版本号必须加 1**：dev 构建由 CI 自动注入 `0.1.<run_number>`；正式构建必须 `-PbuildVersion=x.y.z` 且大于上一正式版（2026-09-22 起由 `release.yml` 从 tag `v*` 注入，不再人工本地构建）。版本号重复会导致 IDE 视为"已安装"不升级、旧构建残留，表现为修复无效/幽灵条目（2026-09-17 双条目事故根因）
- **已发布的动作 id、状态项 id 与显示文本永不改动/删除**：`TopStatusBar.Widget`（工具栏自定义按 id 持久化 ActionUrl）、设置页 `topStatusBar`、各状态项 id（statusText/fileSystemSync/codeBuddy/aggregator/networkLocation/deployServer…）。六个同名微件工厂注册经历了三轮反复：2026-09-18 因双插件副本致菜单成双而移除 → 2026-09-20 为让原生菜单接管逐项显示而恢复（id 与旧版一致）→ 同日晚因 2026.x 前端桥接为每个经典工厂生成第二套独立存储的菜单条目（成对且状态互不相通、插件读不到另一套存储）而再次移除，自有 6 项改由插件设置页控制。fileSystemSync 另有第四轮反复：2026-09-21 上午改为镜像平台 VfsRefresh 工厂（试图让原生菜单直接控制），当日实测 2026.1 把该微件勾选写入前端独立存储（经典存储快照不变、插件不可读）而立即回退设置页。ide.general.xml 中遗留的同 id 开关状态按平台规则复用，无害；**今后不要再为插件自有项注册 statusBarWidgetFactory，也不要把自有项镜像到前端化微件**
- **顶栏显示采用混合真源（2026-09-20，跨版本鲁棒）**：镜像平台原生微件的 11 项跟随原生「状态栏微件」菜单（View → Appearance → Status Bar Widgets，233+ 各版均有该菜单；机制：`TopStatusBarManager.isDisplayEnabled` 经 `StatusBarWidgetFactory.EP_NAME` 解析工厂后读持久化 `StatusBarWidgetSettings.isEnabled(factory)`，**并叠加工厂自带显隐逻辑 `isAvailable(project)`**——与原生 View 菜单展示条目前的判定一致（241/2026.x ToggleWidgetAction.update 源码核实），可用性变化经决策快照轮询捕获——**绝不读底栏微件实例**；工厂缺失回退设置页 itemEnabled）；插件自有 6 项由插件设置页（`TopStatusBarConfigurable`，持久化 itemEnabled）控制，**全版本行为一致**；设置页显示名必须与顶栏观感一致（statusText=当前项目、fileSystemSync=文件同步，2026-09-20 用户按顶栏文案找不到设置条目的教训）。变化检测：平台无变更 topic → `AnActionListener` 监听 `StatusBarWidgets.Toggle.*` 动作即时同步（动作触发但快照未变 = 勾选落入 2026.x 前端独立存储，日志留证据）+ 5s 轮询决策快照兜底（变化才 reload，items 全空也不断轮询）；读取异常 fail-open 按启用处理。启动时 `detectStaleWidgetFactories` 扫描 EP 中六个遗留 id——命中即旧副本残留（重复菜单的另一来源），日志（含 classloader 证据）+ 每会话一次气球通知指引用户清理
- **自有 6 项历史勾选一次性迁移（2026-09-20 晚）**：注册工厂时代的用户勾选持久化在平台经典存储（ide.general.xml，同 id）；`migrateLegacyOwnItemToggles` 在启动时把「经典存储显式关闭 + 设置页无显式选择」的项迁入 itemEnabled=false（旧工厂未覆盖 isEnabledByDefault=true → 持久化值只可能是显式关闭；设置页选择优先 → 幂等）。不清理平台遗留条目（无工厂应答后无读取方，无害）。无此迁移，用户在旧版取消勾选的项升级后全部重现
- **当前项目/文件同步默认关闭 + 遗留显式值一次性清除（2026-09-21 第五轮「还是会显示」定案）**：用户连续五轮要求这两项不再显示，但自有项仅受设置页控制——依赖用户逐项目手动关不可靠，且早期构建的设置页 Apply 把整页勾选写成显式值，显式值优先于默认值，仅改默认压不过遗留的显式 true。机制：`StatusItems.isEnabledByDefault`（`DEFAULT_DISABLED_IDS`：statusText、fileSystemSync、codeBuddy——第三项为 2026-09-22 CodeBuddy 定案：CodeBuddy 是第三方插件微件，本插件不处理任何第三方插件添加的微件，顶栏模板默认不显示；原生底栏第三方微件显隐不受本插件影响，镜像开关只读原生存储）为按项默认；`TopStatusBarSettings.applyDefaultOffPolicy`（Manager 构造时、先于遗留勾选迁移执行）对每个新加入集合的 id 一次性清除其显式值并记入 `defaultOffPolicyApplied` 持久化（幂等；之后用户重新勾选写回的显式值不再被触碰）。教训：**对「用户反复要求隐藏」的自有项，正确终局是默认关闭 + 清遗留显式值，而非继续换控制真源**（原生菜单对自有项不可行已被 09-20/09-21 两次实证否决）
- **点击菜单/悬停详情必须复用平台原生实现（2026-09-22 定案，c3764fc/b92c766）**：自拼菜单与原生双重漂移（视觉：短标签/全量平铺 vs 原生完整描述/分组子菜单；行为：写入无 undo、缺禁用动作、无标题）。现四项均已对齐：行分隔符=平台 `ChangeLineSeparators` 动作组 + `UIBundle` 标题（原生 LineSeparatorPanel.createPopup 同款）；文件编码=`ChangeFileEncodingAction.createPopup(context, EncodingPanelActions)`（原生 EncodingPanel 同款，233→master 公开 API）；缩进=contributor 解析链对齐 master getWidgetState——**编辑器瞬态设置优先**（按键名 `editor.code.style.settings` 取 EditorImpl.CODE_STYLE_SETTINGS 同一实例，避免编译期依赖 platform-impl；241 上无人写该键恒 null），modifier（DetectableIndentSettingsModifier）恒返 null 时**必须回退 FileIndentOptionsProvider 路径**（2026.1 缩进检测场景，缺失回退=无标题「缩进检测」+无「禁用缩进检测」动作）；标题回退 `ApplicationBundle code.style.language.settings.indent.provider`（try/catch MissingResourceException：241 无键时退化无标题，与 241 原生一致）；内存悬停=原生 HTML 模板同构表格（堆/字节缓冲区/JVM 段；内存映射文件行与 OS-Provided 段 2026-09-22 第二轮已按例外许可反射补齐，见下条）。菜单数据上下文统一 `EditorContext.popupContext`（编辑器存在 → `EditorUtil.getEditorDataContext`，原生 EditorBasedStatusBarPopup.context 同款；否则组件树上下文）
- **提交流程教训（2026-09-22，b92c766）**：为省 CI 等待「流水线式」先写好下一改动代码未提交，随后的 `git add -A` 把三个改动并进一个提交（说明与内容不符，只能靠 docs 补记）。流程铁律：**一个改动完成 → 立即 add+commit+push → CI 绿灯 → 再动下一个文件**；写而不提交的代码只能存在于已 `git stash` 或独立目录中
- **读数据反射的例外许可（2026-09-22 第二轮菜单对齐，d236a4f）**：内存详情的「内存映射文件」行与「操作系统提供」段数据源是 2026.x 新增类（`com.intellij.platform.util.io.storages.mmapped.MMappedFileStorage.totalBytesMapped()`、`com.intellij.diagnostic.PlatformMemoryUtil.getCurrentProcessMemoryStats()`），241 编译基线不存在、无法编译期引用。允许**仅对这两个只读数据源**按键名反射读取（任何 Throwable → 整段隐藏，旧平台原生同样无此段）。此为「不反射内部实现」规则的范围化例外：**只允许只读数据、禁止 UI 内部结构、必须优雅降级**；新增场景须先在此登记
- **第二轮菜单对齐（2026-09-22，92b01b0/de447fe/ec01ea2/d236a4f，用户截图驱动）**：① 文件编码 `createPopup` 在 2026.1 EDT 断言读访问（用户日志 RuntimeExceptionWithAttachments）→ 调用必须包 `ReadAction.compute`；② 行列号点击 = `CommandProcessor.executeCommand` 包裹 `EditorGotoLineNumberDialog` + `IdeDocumentHistory.includeCurrentCommandAsNavigation`（勿改用平台 GotoLine 动作——2026.x 上其行为与原生微件不一致），悬停「转到行」+`KeymapUtil.getFirstKeyboardShortcutText("GotoLine")`，文本选区 `(N chars)` 用 UIBundle position.panel.* 键；③ 省电模式 2026.x 原生为弹菜单（切换条目 + 分隔线 + 「配置...」），切换条目执行平台 `TogglePowerSave` 动作（保留通知）、配置条目执行平台 `ShowSettings`；④ 内存指示器：单元格显示样式 2026-09-22 第三轮用户反馈定案回退——自有 KB/MB/GB 纯文本（"700 MB / 1700 MB"）+ 超 90% 警告色，不画原生仪表条（顶栏上满单元格色块观感差，用户两次截图实证，d236a4f/3bce32b 的对齐画法已否决）、不用 UIBundle 文本与 Registry 总预估口径；悬停详情保留原生同构 HTML 表格与反射例外段，总预估公式 heapCommitted+线程栈+直接缓冲+JVM 内部（roundUpTo 100M）
- 本地不执行任何 gradle 构建（含 runIde）：编译验证一律以 CI 结果为准，沙盒验证留给人工
- 不修改 IDEA 内部 UI：不创建第二行 Toolbar、不反射内部实现、不碰 MainFrame；只用官方 Action System 公共 API
- 绝不提交凭据/令牌（GitHub PAT 仅用于推送鉴权）；每完成一个改动立即 commit 并 push，提交身份固定为 `xaxka`
- 目标平台 2024.1（sinceBuild 233）：升级平台前先复查上述已知坑清单中的 API 是否迁移

## 协作约定（仓库所有者已确认；发布模型 2026-09-22 更新）

- 发布模型（旧「正式版人工上传」约定已废）：master 每次 CI 通过后自动更新 dev 预构建（tag `dev`，版本号自动递增）；**正式版走 CI**——推送 tag `v*`（如 `v1.0.0`，版本号见硬约束）触发 `release.yml`：显式版本构建 → Plugin Verifier（2023.3/2024.1/recommended）→ 发布 GitHub Releases 正式版；配置 `MARKETPLACE_PUBLISH_TOKEN` repo secret 时同一构建自动 `publishPlugin` 上传 JetBrains Marketplace（未配置则打日志跳过，不影响发布）
- Marketplace 上架（2026-09-22 用户定案，取代早前「不上架」）：上架材料已随 v1.0.0 就位——英文主 README + `README.zh-CN.md`、plugin.xml 完整英文描述（HTML 子集、无 h1/h2）与 `<change-notes>`、`META-INF/pluginIcon(_dark).svg`（40×40）、Plugin Verifier CI 关卡。Marketplace 页面侧（License 选择、截图、标签、Vendor 资料）由所有者人工提交；把 publish token 加入 repo secrets 后，之后每个正式版全自动上架
- 分支模型：单人直推 master，CI 绿灯是唯一合入门槛，不建 PR
- 出问题一律 fix-forward：追加修复提交推进，不 revert、不改写已推送历史
