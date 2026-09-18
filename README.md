# intellij-top-status-bar

[![Build](https://github.com/xixka/intellij-top-status-bar/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/xixka/intellij-top-status-bar/actions/workflows/ci.yml)

一个 IntelliJ IDEA 插件：把 New UI 风格的紧凑状态栏作为官方 Action System 的自定义组件（`CustomComponentAction`），通过 IDEA 原生「自定义工具栏」机制添加到 Main Toolbar 搜索按钮左侧。

## 设计原则

- 不创建第二行 Toolbar，不修改 IDEA 内部 UI，不使用反射
- 整个状态栏是一个可添加到 Main Toolbar 的自定义动作（`Top Status Bar`，CustomComponentAction），内部横向渲染状态项，而不是一堆普通 Toolbar 按钮
- 只使用 IntelliJ 官方 API（Action System、Message Bus topics、`CustomComponentAction`）
- UI 遵循 JetBrains New UI 惯例：紧凑、Icon + Text、圆角 Hover、Light/Dark 自适应、状态异常时才着色、窗口宽度不足自动隐藏低优先级项、不增加工具栏高度

## 状态项（按显示顺序）

状态文本 · 文件系统同步 · CodeBuddy · 聚合器 · 网络位置 · 默认部署服务器 · 行列号 · 语言服务 · 行分隔符 · 文件编码 · 省电模式 · 编辑器选择模式 · 缩进 · JSON 架构 · Git 分支 · 只读特性 · 内存指示器

实时更新机制：光标移动（行列号）、文件切换（编码/行分隔符/缩进/JSON Schema/语言）、`GIT_REPO_CHANGE`（分支）、`PowerSaveMode.TOPIC`（省电模式）、后台定时（内存 2s、网络 10s、通用刷新 5s）、`VFS_CHANGES`（文件系统同步）。

## 安装并添加到 Main Toolbar

1. 获取插件 zip：从 GitHub Actions 构建产物下载（`build/distributions/*.zip`），或本地执行 `./gradlew buildPlugin`
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

- 总开关与逐项开关（勾选即显示、取消即隐藏，即时生效）
- 默认部署服务器（显示为 `部署: xxx`，点击单元格可跳回设置）
- CodeBuddy 标签（留空则隐藏该项）

> 顶栏显示只由本设置页控制，与 IDEA 底部状态栏的原生微件开关互相独立。

## 工程与构建

- Gradle 8.8 + IntelliJ Platform Gradle Plugin 2.0.1
- 目标平台：IntelliJ IDEA Community 2024.1（`sinceBuild=233`，兼容 2023.3+；依赖捆绑的 Git 插件 `Git4Idea`）
- Java 17，源码编码 UTF-8
- CI（GitHub Actions）执行 `./gradlew buildPlugin -PbuildVersion=0.1.<run_number>` 完成编译与打包验证，每次构建版本号自动递增
- master 每次 CI 通过后自动发布 dev 预构建到 [GitHub Releases](https://github.com/xixka/intellij-top-status-bar/releases)（tag `dev`，文件名带版本号），可直接下载安装
- 本地调试沙盒：`./gradlew runIde`；正式发布构建：`./gradlew buildPlugin -PbuildVersion=x.y.z`

## 升级与排障

- **安装 dev 构建后请确认版本号**：`Settings → Plugins → Top Status Bar` 显示的版本应与 Release 页面 asset 文件名一致；若仍是旧版本号，说明 IDE 未把它当作升级——先完全卸载旧版并重启，再 Install Plugin from Disk。
- **Add Action 树中出现两个条目 / 工具栏出现幽灵条目**：说明 IDE 内仍残留旧构建（历史版本曾同时注册 `Top Status Bar` 组与 `Top Status Bar Widget` 动作，新版只保留单一动作 `Top Status Bar`，显示文本由平台按动作 id 实时解析）。处理：卸载插件并重启 → 安装最新 dev 构建 → 在 `Settings → Appearance & Behavior → Menus and Toolbars` 中删除 Main Toolbar 里的 `TopStatusBar` / `Top Status Bar Widget` 残留条目 → 重新 Add Action 添加 `Top Status Bar`。

## 状态项定制开发

- 新增状态项：实现 `model/StatusItem`（或继承 `items/CurrentFileItem` / `model/AbstractStatusItem`），在 `TopStatusBarManager.createItems()` 与 `model/StatusItems.java` 登记即可
- 单元格视觉在 `ui/StatusCell.java`，宽度自适应隐藏策略在 `TopStatusBarPanel.java`
