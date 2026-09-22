# Top Status Bar

[![Build](https://github.com/xixka/intellij-top-status-bar/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/xixka/intellij-top-status-bar/actions/workflows/ci.yml)
[![Release](https://github.com/xixka/intellij-top-status-bar/actions/workflows/release.yml/badge.svg)](https://github.com/xixka/intellij-top-status-bar/actions/workflows/release.yml)

[English](README.md) | 简体中文

一个 IntelliJ IDEA 插件：把 New UI 风格的紧凑状态栏作为官方 Action System 的自定义组件（`CustomComponentAction`），通过 IDEA 原生「自定义工具栏」机制添加到 Main Toolbar 搜索按钮左侧。

## 设计原则

- 不创建第二行 Toolbar，不修改 IDEA 内部 UI，不使用反射
- 整个状态栏是一个可添加到 Main Toolbar 的自定义动作（`Top Status Bar`，CustomComponentAction），内部横向渲染状态项，而不是一堆普通 Toolbar 按钮
- 只使用 IntelliJ 官方 API（Action System、Message Bus topics、`CustomComponentAction`）
- UI 遵循 JetBrains New UI 惯例：紧凑、Icon + Text、圆角 Hover、Light/Dark 自适应、状态异常时才着色、窗口宽度不足自动隐藏低优先级项、不增加工具栏高度

## 状态项（按显示顺序）

当前项目（默认关闭）· 文件同步（默认关闭）· CodeBuddy（默认关闭）· 聚合器 · 网络位置 · 默认部署服务器 · 行列号 · 语言服务 · 行分隔符 · 文件编码 · 省电模式 · 编辑器选择模式 · 缩进 · JSON 架构 · Git 分支 · 只读特性 · 内存指示器

> 「当前项目」（顶栏显示项目名，悬停提示「当前项目：xxx」）、「文件同步」（顶栏显示「已同步 HH:mm:ss」，悬停提示「文件系统同步：xxx」）与「CodeBuddy」分别自 2026-09-21/09-22 起默认关闭：升级后不显示、无需任何操作；需要时在插件设置页勾选对应条目即可恢复。

> 第三方插件（如腾讯云 CodeBuddy）添加到状态栏的微件本插件不做处理（2026-09-22 定案）：自有的 CodeBuddy 项只是可配置的占位，默认不显示；第三方微件在原生底栏的显隐也不受本插件影响。

实时更新机制：光标移动（行列号）、文件切换（编码/行分隔符/缩进/JSON Schema/语言）、`GIT_REPO_CHANGE`（分支）、`PowerSaveMode.TOPIC`（省电模式）、后台定时（内存 2s、网络 10s、通用刷新 5s）、`VFS_CHANGES`（文件系统同步）。

## 安装并添加到 Main Toolbar

1. 获取插件 zip：从 [GitHub Releases](https://github.com/xixka/intellij-top-status-bar/releases) 下载正式版（或最新 `dev` 预构建），上架 JetBrains Marketplace 后也可直接从市场安装
2. 安装：`Settings → Plugins → ⚙ → Install Plugin from Disk...` 选择 zip
3. 打开 `Settings → Appearance & Behavior → Menus and Toolbars`
4. 选中 Main Toolbar 中的分组（如 *Main Toolbar Right*）→ `+` → `Add Action...`
5. 在弹出的可用动作树中搜索 **Top Status Bar** → 确定
6. 将其拖拽到搜索按钮左侧，得到目标布局：

```
[项目/Git] [Top Status Bar] [搜索] [其他工具]
```

> 插件默认不自动把自己塞进 Main Toolbar，完全走 IDEA 原生自定义机制，尊重用户自己的布局。

## 设置

`Settings → Appearance & Behavior → Top Status Bar`：

- 总开关（关闭 = 顶栏不装载任何状态项）
- 插件自有 6 项逐项开关：当前项目、文件同步、CodeBuddy、聚合器、网络位置、默认部署服务器——勾选即显示、取消即隐藏，**在任何 IDEA 版本上行为一致**。其中**当前项目、文件同步、CodeBuddy 默认关闭**（2026-09-21/09-22 起），早期构建整页保存留下的显式勾选会在首次启动新版时被一次性清除、按新默认隐藏，之后在设置页重新勾选的值不会再被触碰
- 默认部署服务器（显示为 `部署: xxx`，点击单元格可跳回设置）
- CodeBuddy 标签（留空则隐藏该项）

**镜像平台原生微件的 11 项**（行列号、行分隔符、文件编码、省电模式、缩进、JSON 架构、Git 分支、只读特性、内存指示器、语言服务、编辑器选择模式）由原生菜单
`View → 外观 → 状态栏微件`（或右键底部状态栏）勾选控制：菜单里勾选哪些，顶栏就显示哪些，勾选动作即时生效（动作级监听），另有约 5 秒轮询兜底——与底部状态栏的原生行为同源同义。

> 旧版本（曾注册原生微件工厂）里对自有 6 项做过的取消勾选，会在首次启动新版时自动迁移到插件设置页（仅迁移显式关闭记录，设置页已做过的选择优先），避免升级后已隐藏的项重新出现。

> 该菜单的勾选状态持久化在 `ide.general.xml`，插件读取的是这份开关状态（与菜单复选框同源），而非底部状态栏是否实际承载对应微件——2026.x 前端化后多数原生微件已不挂底栏，按实例判断会误隐藏已勾选项。

### 版本兼容（鲁棒性设计）

| 版本 | 自有 6 项 | 镜像 11 项 |
|---|---|---|
| 2023.3（233）— 2026.x+（299.*） | 插件设置页（无平台依赖，全版本一致） | 原生「状态栏微件」菜单（该菜单在 233/241/243/master 均存在，已对照源码核实） |

插件不注册任何 `statusBarWidgetFactory`，不依赖 2026.x 前端状态栏桥接、不依赖底栏微件实例、不依赖特定版本内部行为。唯一涉及的平台内部 API 是读取菜单开关的 `StatusBarWidgetSettings`（233.14475/241.14494/262 三版 FQN 与签名一致，已核实），且全部读取都有 fail-open 保护：即使未来版本移除该 API，镜像项保持可见、自有项仍可正常控制，插件不会崩溃。

## 工程与构建

- Gradle 8.8 + IntelliJ Platform Gradle Plugin 2.0.1
- 目标平台：IntelliJ IDEA Community 2024.1（`sinceBuild=233`，兼容 2023.3+；依赖捆绑的 Git 插件 `Git4Idea`）
- Java 17，源码编码 UTF-8
- CI（GitHub Actions）执行 `./gradlew buildPlugin -PbuildVersion=0.1.<run_number>` 完成编译与打包验证，每次构建版本号自动递增
- master 每次 CI 通过后自动发布 dev 预构建到 [GitHub Releases](https://github.com/xixka/intellij-top-status-bar/releases)（tag `dev`，文件名带版本号），可直接下载安装
- **正式版发布（2026-09-22 起走 CI）**：推送 tag `v*`（如 `v1.0.0`）触发 [release.yml](.github/workflows/release.yml)：按 tag 版本号构建 → 跑 Plugin Verifier（2023.3 / 2024.1 / 最新推荐版本）→ 发布 GitHub Releases 正式版；配置了 `MARKETPLACE_PUBLISH_TOKEN` secret 时还会自动上传 JetBrains Marketplace
- 本地调试沙盒：`./gradlew runIde`（见 AGENTS.md：本地不执行构建，编译验证以 CI 为准）

## 升级与排障

- **安装 dev 构建后请确认版本号**：`Settings → Plugins → Top Status Bar` 显示的版本应与 Release 页面 asset 文件名一致；若仍是旧版本号，说明 IDE 未把它当作升级——先完全卸载旧版并重启，再 Install Plugin from Disk。
- **原生菜单出现重复条目（同名两行，勾选状态还可能不一致）**：两种来源。① 安装了两个版本的插件副本（Settings → Plugins 搜索 "Top Status Bar" 出现多条）——新版本会在启动时检测到并弹气球通知，按提示禁用/卸载旧副本并重启即可；② 装的是 2026-09-20 之前的旧构建（那时插件还注册原生微件工厂，2026.x 会为每个工厂再生成一套独立存储的菜单条目）——升级到最新 dev 构建后，插件自有条目不再出现在原生菜单中，重复条目自然消失，自有 6 项改由插件设置页控制。
- **Add Action 树中出现两个条目 / 工具栏出现幽灵条目**：说明 IDE 内仍残留旧构建（历史版本曾同时注册 `Top Status Bar` 组与 `Top Status Bar Widget` 动作，新版只保留单一动作 `Top Status Bar`，显示文本由平台按动作 id 实时解析）。处理：卸载插件并重启 → 安装最新 dev 构建 → 在 `Settings → Appearance & Behavior → Menus and Toolbars` 中删除 Main Toolbar 里的 `TopStatusBar` / `Top Status Bar Widget` 残留条目 → 重新 Add Action 添加 `Top Status Bar`。
- **排查顶栏不显示 / 项丢失 / 不刷新**：插件在关键链路（管理器装载、陈旧工厂检测、面板绑定项目、单元格渲染与自适应隐藏、状态项状态迁移、Git 异步解析、设置页应用）持续输出调试日志，全部以 `TSB ` 前缀写入 IDE 日志。获取方式：菜单 `Help → Show Log in Explorer/Finder` 打开日志目录，找到 `idea.log`，检索 `TSB`。日志只在状态实际变化或出现异常时写入（窗口拖拽 resize 不会刷屏），把出现问题前后时间段的日志发给维护者即可定位。

## 状态项定制开发

- 新增插件自有状态项：实现 `model/StatusItem`（或继承 `items/CurrentFileItem` / `model/AbstractStatusItem`），在 `TopStatusBarManager.createItems()` 与 `model/StatusItems.java` 登记即可；`getPlatformWidgetId` 保持返回 null → 显隐由插件设置页控制，在 `TopStatusBarConfigurable.OWN_ITEM_IDS` 加上新 id 即出现在设置页
- 新增镜像原生微件的状态项：同上登记，但 `getPlatformWidgetId` 返回对应平台工厂 id（如 Position/Encoding/git），显隐跟随原生「状态栏微件」菜单
- **点击菜单必须复用平台原生实现，不要自拼列表**（2026-09-22 定案，自拼=视觉与行为双重漂移）：行分隔符=平台注册的 `ChangeLineSeparators` 动作组；文件编码=`ChangeFileEncodingAction.createPopup(context, "EncodingPanelActions")`；缩进=contributor 解析链（含编辑器瞬态设置回退，对齐 master 源码）；内存悬停详情=原生 HTML 模板同构表格。菜单数据上下文统一用 `EditorContext.popupContext`（编辑器存在时 `EditorUtil.getEditorDataContext`，原生 `EditorBasedStatusBarPopup.context` 同款）
- **不要**为插件自有项注册 `statusBarWidgetFactory`：2026.x 会为每个经典工厂生成第二套独立存储的菜单条目（重复菜单+开关失灵的根因，见上文排障）
- 单元格视觉在 `ui/StatusCell.java`，宽度自适应隐藏策略在 `TopStatusBarPanel.java`，逐项显隐决策在 `TopStatusBarManager.isDisplayEnabled`
