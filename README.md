# Top Status Bar

[![Build](https://github.com/xixka/intellij-top-status-bar/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/xixka/intellij-top-status-bar/actions/workflows/ci.yml)
[![Release](https://github.com/xixka/intellij-top-status-bar/actions/workflows/release.yml/badge.svg)](https://github.com/xixka/intellij-top-status-bar/actions/workflows/release.yml)

English | [简体中文](README.zh-CN.md)

An IntelliJ IDEA plugin that renders a compact, New UI-style status bar as an official Action System custom component (`CustomComponentAction`), added to the Main Toolbar to the left of the search icon via IDEA's native toolbar customization.

## Design principles

- No second toolbar row, no modification of IDE internals, no reflection into internal UI classes
- The whole bar is a single custom action (`Top Status Bar`, a `CustomComponentAction`) that renders status items horizontally, not a pile of plain toolbar buttons
- Only official IntelliJ APIs: Action System, Message Bus topics, `CustomComponentAction`
- UI follows JetBrains New UI conventions: compact, icon + text, rounded hover, light/dark adaptive, colored only on abnormal status, low-priority items auto-hide on narrow windows, never increases toolbar height

## Status items (display order)

Current project (off by default) · File sync (off by default) · CodeBuddy (off by default) · Aggregator · Network location · Deploy server · Line:column · Language service · Line separator · File encoding · Power save mode · Editor selection mode · Indent · JSON schema · Git branch · Read-only attribute · Memory indicator

> "Current project" (shows the project name, hover: "Current project: …"), "File sync" (shows "Synced HH:mm:ss", hover: file system sync details) and "CodeBuddy" have been off by default since 2026-09-21/09-22: they stay hidden after upgrading, no action needed. Re-enable them on the plugin settings page if wanted.

> Third-party widgets added by other plugins (e.g. Tencent CodeBuddy) are not handled by this plugin — its own CodeBuddy cell is a configurable placeholder, hidden by default.

Live updates: caret movement (line:column), file switching (encoding / line separator / indent / JSON schema / language), `GIT_REPO_CHANGE` (branch), `PowerSaveMode.TOPIC` (power save mode), background timers (memory 2s, network 10s, general refresh 5s), `VFS_CHANGES` (file system sync).

## Install and add to the Main Toolbar

1. Get the plugin zip: download an official release from [GitHub Releases](https://github.com/xixka/intellij-top-status-bar/releases) (or the latest `dev` pre-release), or install from JetBrains Marketplace once listed.
2. Install: `Settings → Plugins → ⚙ → Install Plugin from Disk…` and pick the zip (Marketplace installs skip this step).
3. Open `Settings → Appearance & Behavior → Menus and Toolbars`
4. Select a Main Toolbar group (e.g. *Main Toolbar Right*) → `+` → `Add Action…`
5. Search for **Top Status Bar** in the action tree → OK
6. Drag it left of the search icon for the target layout:

```
[Project/Git] [Top Status Bar] [Search] [Other tools]
```

> The plugin never inserts itself into the Main Toolbar automatically — it goes through IDEA's native customization only, respecting your own layout.

## Settings

`Settings → Appearance & Behavior → Top Status Bar`:

- Master switch (off = the bar loads no status items)
- Per-item toggles for the plugin's own items: Current project, File sync, CodeBuddy, Aggregator, Network location, Deploy server — check to show, uncheck to hide, **behaving identically on every IDEA version**. *Current project*, *File sync* and *CodeBuddy* are **off by default** (2026-09-21/09-22); explicit checkboxes persisted by early builds are cleared once on first launch of the new version, and any value you set afterwards is never touched again
- Deploy server (shown as `Deploy: …`; clicking the cell jumps back to settings)
- CodeBuddy label (empty = the item stays hidden)

**The 11 items mirroring built-in widgets** (line:column, line separator, file encoding, power save mode, indent, JSON schema, Git branch, read-only attribute, memory indicator, language service, editor selection mode) follow the native menu
`View → Appearance → Status Bar Widgets` (or right-click the bottom status bar): whatever is checked there shows on the top bar, with immediate effect (action-level listener) plus a ~5s polling fallback — the same source of truth as the native bottom status bar.

> Explicit hides made in older versions (which registered native widget factories) are migrated to the plugin settings page on first launch (explicit-off records only; settings-page choices take priority), so items hidden before an upgrade do not reappear.

> The menu's checkbox state persists in `ide.general.xml`; the plugin reads that same switch state (same source as the menu), not whether the bottom bar actually hosts a widget — after the 2026.x frontend migration most native widgets no longer mount on the bottom bar, so instance-based checks would wrongly hide checked items.

### Version compatibility (robust by design)

| Version | Plugin-specific items | Mirrored items |
|---|---|---|
| 2024.1 (241) — 2026.x+ (299.*) | Plugin settings page (no platform dependency, identical on all versions) | Native *Status Bar Widgets* menu (verified present in 241/243/master sources) |

The plugin registers no `statusBarWidgetFactory`, and depends neither on the 2026.x frontend status bar bridge, nor on bottom-bar widget instances, nor on version-specific internal behavior. The only platform-internal API used is read-only `StatusBarWidgetSettings` for menu switches (FQN and signature verified identical across 241.14494/262), and every read is fail-open protected: should a future version remove it, mirrored items stay visible, own items remain controllable, nothing crashes.

## Project & build

- Gradle 8.8 + IntelliJ Platform Gradle Plugin 2.0.1
- Target: IntelliJ IDEA Community 2024.1 (`sinceBuild=241`, compatible with 2024.1+; depends on the bundled `Git4Idea` plugin)
- Java 17, UTF-8 sources
- CI (GitHub Actions) runs `./gradlew buildPlugin` on every push; each build gets an auto-incremented version (`0.1.<run_number>`)
- Every green master build publishes a `dev` pre-release to [GitHub Releases](https://github.com/xixka/intellij-top-status-bar/releases) (tag `dev`, versioned zip)
- **Official releases are published from CI** (since 2026-09-22): pushing a tag `v*` (e.g. `v1.0.0`) builds that commit with the explicit version, runs the IntelliJ **Plugin Verifier** (2024.1 / latest recommended), and publishes a stable GitHub Release. If the `MARKETPLACE_PUBLISH_TOKEN` repository secret is set, the same build is uploaded to JetBrains Marketplace automatically.
- Local sandbox: `./gradlew runIde`; local release build: `./gradlew buildPlugin -PbuildVersion=x.y.z`

## Upgrading & troubleshooting

- **After installing, verify the version number**: `Settings → Plugins → Top Status Bar` must show the version matching the release asset filename; if it still shows an old number, the IDE did not treat it as an upgrade — fully uninstall the old version, restart, then *Install Plugin from Disk* again.
- **Duplicate entries in the native Status Bar Widgets menu (same name twice, possibly with different checkbox states)**: two possible causes. (1) Two plugin copies installed (search "Top Status Bar" in `Settings → Plugins` shows multiple entries) — recent builds detect this at startup and show a balloon notification; disable/uninstall the stale copy and restart. (2) A pre-2026-09-20 build (which still registered native widget factories — 2026.x generates a second, separately-stored menu entry per factory) — after upgrading to a recent build the plugin-specific entries disappear from the native menu and the duplicates vanish; the plugin's own items move to its settings page.
- **Two entries in the Add Action tree / ghost entries on the toolbar**: an old build is still resident (historic versions registered both a `Top Status Bar` group and a `Top Status Bar Widget` action; the current build registers the single `Top Status Bar` action only). Fix: uninstall the plugin and restart → install the latest build → delete leftover `TopStatusBar` / `Top Status Bar Widget` entries from the Main Toolbar in `Settings → Appearance & Behavior → Menus and Toolbars` → re-add `Top Status Bar` via *Add Action*.
- **Diagnosing a missing / stale / non-refreshing top bar**: the plugin logs its critical paths (manager loading, stale-factory detection, panel project binding, cell rendering & adaptive hiding, item state transitions, async Git resolution, settings apply) to the IDE log with the `TSB ` prefix — written only on actual state changes or errors (no spam during window resizing). Open the log via `Help → Show Log in Explorer/Finder`, search `TSB` in `idea.log`, and send the lines around the incident to the maintainer.

## Extending with new status items

- Adding a plugin-specific item: implement `model/StatusItem` (or extend `items/CurrentFileItem` / `model/AbstractStatusItem`), register it in `TopStatusBarManager.createItems()` and `model/StatusItems.java`; keep `getPlatformWidgetId` returning null → visibility controlled by the plugin settings page; add the id to `TopStatusBarConfigurable.OWN_ITEM_IDS` to make it appear there.
- Adding a native-widget-mirroring item: same registration, but `getPlatformWidgetId` returns the platform factory id (e.g. Position/Encoding/git); visibility follows the native *Status Bar Widgets* menu.
- **Click menus must reuse native platform implementations, never hand-rolled lists** (decided 2026-09-22 after visual and behavioral drift): line separator = the platform-registered `ChangeLineSeparators` action group; file encoding = `ChangeFileEncodingAction.createPopup(context, "EncodingPanelActions")`; indent = contributor resolution chain (with editor-transient fallback, aligned with master sources); memory hover details = the native HTML template. Menu data contexts use `EditorContext.popupContext` (`EditorUtil.getEditorDataContext` when an editor exists, same as native `EditorBasedStatusBarPopup.context`).
- **Do not** register a `statusBarWidgetFactory` for plugin-specific items: 2026.x generates a second, separately-stored menu entry per classic factory (root cause of duplicate menu entries and dead toggles; see troubleshooting above).
- Cell visuals live in `ui/StatusCell.java`, width-adaptive hiding in `TopStatusBarPanel.java`, per-item visibility decisions in `TopStatusBarManager.isDisplayEnabled`.
