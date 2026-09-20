package com.xixka.topstatusbar;

import com.intellij.openapi.Disposable;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionResult;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetSettings;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.xixka.topstatusbar.items.CaretPositionItem;
import com.xixka.topstatusbar.items.CodeBuddyItem;
import com.xixka.topstatusbar.items.AggregatorItem;
import com.xixka.topstatusbar.items.DeployServerItem;
import com.xixka.topstatusbar.items.EncodingItem;
import com.xixka.topstatusbar.items.FileSystemSyncItem;
import com.xixka.topstatusbar.items.GitBranchItem;
import com.xixka.topstatusbar.items.IndentItem;
import com.xixka.topstatusbar.items.JsonSchemaItem;
import com.xixka.topstatusbar.items.LanguageItem;
import com.xixka.topstatusbar.items.LineSeparatorItem;
import com.xixka.topstatusbar.items.MemoryItem;
import com.xixka.topstatusbar.items.NetworkLocationItem;
import com.xixka.topstatusbar.items.PowerSaveItem;
import com.xixka.topstatusbar.items.ReadOnlyItem;
import com.xixka.topstatusbar.items.SelectionModeItem;
import com.xixka.topstatusbar.items.StatusTextItem;
import com.xixka.topstatusbar.model.StatusItem;
import com.xixka.topstatusbar.settings.TopStatusBarSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Project-level service owning the status items: installs their listeners,
 * schedules the periodic refresh and forwards change notifications to the
 * toolbar component.
 * <p>
 * Hybrid source of truth (2026-09-20, cross-version robust design):
 * <ul>
 * <li>The 11 items mirroring built-in IDE widgets follow the native
 * "Status Bar Widgets" menu (View | Appearance | Status Bar Widgets,
 * available on every supported platform 233+): a candidate loads while its
 * checkbox is on there <em>and</em> the resolved factory reports
 * {@code isAvailable(project)} — the same "own show/hide logic" the native
 * menu applies before listing an entry (ToggleWidgetAction.update, 241 and
 * 2026.x platform sources). The checkbox state is read from the persisted
 * platform {@link StatusBarWidgetSettings} — the same source the menu
 * renders — never from bottom-bar widget instances (2026.x no longer hosts
 * them).</li>
 * <li>The 6 plugin-specific items are governed by this plugin's own settings
 * page ({@code TopStatusBarConfigurable}, persisted in
 * {@code TopStatusBarSettings.itemEnabled}). They intentionally do NOT
 * register {@code statusBarWidgetFactory} extensions: 2026.x synthesizes a
 * second, independently-stored set of menu entries for every classic
 * factory, which duplicated the menu and made half the toggles ineffective
 * (user screenshot 2026-09-20 — the "unchecked but still shown" incident).
 * </li>
 * </ul>
 * The manager also detects stale widget factories from an old plugin copy
 * left on disk (the other historical source of doubled menu entries) and
 * notifies the user to remove it.
 */
public final class TopStatusBarManager implements Disposable {

    private static final long REFRESH_INTERVAL_SECONDS = 5;

    /**
     * Widget ids this plugin used to register as {@code statusBarWidgetFactory}
     * extensions (until 2026-09-20). Finding any of them in the extension
     * point now means an OLD copy of this plugin is still loaded — the menu
     * will show duplicated entries from it. Used by the startup detection
     * only; ids double as status item ids and never change (hard constraint).
     */
    private static final Set<String> LEGACY_OWN_WIDGET_IDS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("statusText", "fileSystemSync", "codeBuddy",
                    "aggregator", "networkLocation", "deployServer")));

    /** One stale-copy notification per application session. */
    private static final AtomicBoolean STALE_FACTORY_NOTIFIED = new AtomicBoolean();

    private final Project project;
    private final List<StatusItem> items = new CopyOnWriteArrayList<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    /**
     * All candidates of {@link #createItems()} — including the ones currently
     * switched off in the native menu — cached for the periodic decision
     * comparison so the poll does not rebuild 17 item instances every 5s.
     */
    private List<StatusItem> candidates = Collections.emptyList();
    /** Last display-decision snapshot ({@code master=…;id=0/1;…}); reload only on change. */
    private String lastDecisionSnapshot = "";
    private Future<?> periodicRefresh;

    public static TopStatusBarManager getInstance(@NotNull Project project) {
        return project.getService(TopStatusBarManager.class);
    }

    public TopStatusBarManager(@NotNull Project project) {
        this.project = project;
        DebugLog.log("manager 创建: project=" + project.getName()
                + ", 周期刷新间隔=" + REFRESH_INTERVAL_SECONDS + "s");
        detectStaleWidgetFactories();
        migrateLegacyOwnItemToggles();
        installNativeToggleListener();
        reload();
        periodicRefresh = AppExecutorUtil.getAppScheduledExecutorService()
                .scheduleWithFixedDelay(this::scheduledRefresh,
                        REFRESH_INTERVAL_SECONDS, REFRESH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public @NotNull List<StatusItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    private String itemIds() {
        List<String> ids = new ArrayList<>(items.size());
        for (StatusItem item : items) {
            ids.add(item.getId());
        }
        return ids.toString();
    }

    public void addChangeListener(@NotNull Runnable listener) {
        listeners.add(listener);
    }

    public void removeChangeListener(@NotNull Runnable listener) {
        listeners.remove(listener);
    }


    public void reload() {
        for (StatusItem item : items) {
            item.uninstall();
        }
        items.clear();

        TopStatusBarSettings settings = TopStatusBarSettings.getInstance(project);
        candidates = createItems();
        if (settings.isEnabled()) {
            List<String> skippedByMenu = new ArrayList<>();
            List<String> skippedBySettings = new ArrayList<>();
            List<String> skippedByAvailability = new ArrayList<>();
            List<String> missingFactories = new ArrayList<>();
            for (StatusItem candidate : candidates) {
                String widgetId = candidate.getPlatformWidgetId();
                StatusBarWidgetFactory factory = widgetId == null ? null : findWidgetFactory(widgetId);
                if (widgetId != null && factory == null) {
                    // 原生工厂缺失：老平台版本 / 对应插件未装，回退设置页开关（仅镜像项会遇到）
                    missingFactories.add(candidate.getId() + "->" + widgetId);
                }
                if (isDisplayEnabled(candidate, factory)) {
                    items.add(candidate);
                } else if (factory == null) {
                    skippedBySettings.add(candidate.getId());
                } else if (!isFactoryAvailable(factory)) {
                    skippedByAvailability.add(candidate.getId());
                } else {
                    skippedByMenu.add(candidate.getId());
                }
            }
            DebugLog.log("reload: 装载 " + items.size() + " 项: " + itemIds()
                    + (skippedByMenu.isEmpty() ? "" : "; 原生菜单关闭跳过: " + skippedByMenu)
                    + (skippedByAvailability.isEmpty() ? "" : "; 工厂自带显隐逻辑跳过(isAvailable=false): " + skippedByAvailability)
                    + (skippedBySettings.isEmpty() ? "" : "; 设置页关闭跳过(插件自有项/回退): " + skippedBySettings)
                    + (missingFactories.isEmpty() ? "" : "; 未找到平台微件工厂(回退设置页): " + missingFactories));
        } else {
            DebugLog.log("reload: 全局开关关闭 → 不装载任何状态项");
        }
        lastDecisionSnapshot = decisionSnapshot();

        Runnable notifyChanged = () -> ApplicationManager.getApplication().invokeLater(this::fireChanged);
        for (StatusItem item : items) {
            item.install(project, notifyChanged);
        }
        ApplicationManager.getApplication().invokeLater(this::fireChanged);
    }

    protected List<StatusItem> createItems() {
        List<StatusItem> result = new ArrayList<>();
        result.add(new StatusTextItem());
        result.add(new FileSystemSyncItem());
        result.add(new CodeBuddyItem());
        result.add(new AggregatorItem());
        result.add(new NetworkLocationItem());
        result.add(new DeployServerItem());
        result.add(new CaretPositionItem());
        result.add(new LanguageItem());
        result.add(new LineSeparatorItem());
        result.add(new EncodingItem());
        result.add(new PowerSaveItem());
        result.add(new SelectionModeItem());
        result.add(new IndentItem());
        result.add(new JsonSchemaItem());
        result.add(new GitBranchItem());
        result.add(new ReadOnlyItem());
        result.add(new MemoryItem());
        return result;
    }

    /**
     * Whether a candidate item may be shown — hybrid source of truth
     * (2026-09-20, cross-version robust):
     * <ul>
     * <li>{@code getPlatformWidgetId() != null} (item mirrors a built-in IDE
     * widget): the checkbox state of its entry in the native "Status Bar
     * Widgets" menu, read via the persisted {@link StatusBarWidgetSettings}
     * resolved against the factory from {@link StatusBarWidgetFactory#EP_NAME}
     * — the same data the menu itself computes its checkboxes from —
     * <em>and</em> the factory's own {@code isAvailable(project)} gate (the
     * same "own show/hide logic" the native menu applies). If the
     * factory cannot be resolved (older platform, corresponding plugin not
     * installed), the plugin settings-page toggle takes over.</li>
     * <li>{@code getPlatformWidgetId() == null} (plugin-specific item): the
     * plugin settings-page toggle, on every platform version.</li>
     * </ul>
     * Must be called on the EDT (the platform toggle action also updates
     * this setting on the EDT).
     */
    private boolean isDisplayEnabled(@NotNull StatusItem item, @Nullable StatusBarWidgetFactory resolvedFactory) {
        if (resolvedFactory != null) {
            return isFactoryEnabled(resolvedFactory) && isFactoryAvailable(resolvedFactory);
        }
        return TopStatusBarSettings.getInstance(project).isItemEnabled(item.getId());
    }

    /**
     * 平台微件自带的“要不要显示”逻辑（{@code StatusBarWidgetFactory.isAvailable}）。
     * 原生 View 菜单只在 {@code isAvailable(project)} 为真时才展示该条目
     * （241/2026.x 平台 ToggleWidgetAction.update 源码核实），镜像项必须同样
     * 遵守，否则原生场景下根本不出现的项（如非 Git 项目的 Git 分支、无 LSP
     * 服务的语言服务）会在顶栏常驻。读取异常 fail-open 按可用处理
     * （宁可多显示，不重演“什么都不显示”故障）。
     */
    private boolean isFactoryAvailable(@NotNull StatusBarWidgetFactory factory) {
        try {
            return factory.isAvailable(project);
        } catch (LinkageError | Exception e) {
            DebugLog.warn("isFactoryAvailable: 读取失败(factory=" + factory.getId() + ") → 按可用处理", e);
            return true;
        }
    }

    private static boolean isFactoryEnabled(@NotNull StatusBarWidgetFactory factory) {
        try {
            return StatusBarWidgetSettings.getInstance().isEnabled(factory);
        } catch (LinkageError | Exception e) {
            // Fail-open: a missing/unreadable settings service must never hide
            // items the user enabled (the historical "nothing shows" failure
            // mode was exactly a silent all-suppress). On a hypothetical
            // future platform that renames/moves this internal API the 11
            // mirrored items simply stay visible; the 6 own items keep their
            // settings-page control either way.
            DebugLog.warn("isFactoryEnabled: 读取 StatusBarWidgetSettings 失败(factory="
                    + factory.getId() + ") → 按启用处理", e);
            return true;
        }
    }

    @Nullable
    private static StatusBarWidgetFactory findWidgetFactory(@NotNull String widgetId) {
        for (StatusBarWidgetFactory factory : StatusBarWidgetFactory.EP_NAME.getExtensionList()) {
            if (widgetId.equals(factory.getId())) {
                return factory;
            }
        }
        return null;
    }

    /**
     * One-time import of the user's historical per-item choices (2026-09-20).
     * Builds before the hybrid source of truth registered the six
     * plugin-specific items as {@code statusBarWidgetFactory} extensions, so
     * their visibility was toggled via the native "Status Bar Widgets" menu
     * and persisted by the platform in the classic
     * {@link StatusBarWidgetSettings} store (ide.general.xml), keyed by the
     * same ids ({@link #LEGACY_OWN_WIDGET_IDS}). Current versions govern
     * those items exclusively via the plugin settings page — registering
     * factories is not an option because 2026.x synthesizes a second,
     * independently-stored set of menu entries for every classic factory.
     * Without this import, items the user had explicitly hidden in the
     * native menu reappear after the upgrade (the "everything shows again"
     * regression).
     * <p>
     * The old factories never overrode {@code isEnabledByDefault} (platform
     * default: enabled), so a persisted value can only be an explicit
     * <em>disabled</em>; nothing to import when the store has no entry.
     * An explicit settings-page choice always wins over the import, which
     * makes the migration idempotent across restarts and projects.
     * The legacy platform-store entries are intentionally left untouched
     * (harmless: nothing reads them once no factory answers to those ids).
     */
    private void migrateLegacyOwnItemToggles() {
        TopStatusBarSettings settings = TopStatusBarSettings.getInstance(project);
        List<String> migrated = new ArrayList<>();
        for (String id : LEGACY_OWN_WIDGET_IDS) {
            if (settings.hasItemEnabledExplicitly(id)) {
                continue;
            }
            if (isLegacyOwnItemExplicitlyDisabled(id)) {
                settings.setItemEnabled(id, false);
                migrated.add(id);
            }
        }
        if (!migrated.isEmpty()) {
            DebugLog.log("遗留原生菜单勾选迁移: " + migrated
                    + " → 插件设置页置为关闭（旧版曾注册同名原生微件工厂，用户已在原生菜单取消勾选）");
        }
    }

    private static boolean isLegacyOwnItemExplicitlyDisabled(@NotNull String id) {
        try {
            // 与 isEnabled 同属一个内部服务：241/261 源码核实签名一致；233 若缺失由守卫兜底（不迁移，保持默认启用）
            return StatusBarWidgetSettings.getInstance().isExplicitlyDisabled(id);
        } catch (LinkageError | Exception e) {
            DebugLog.warn("migrateLegacyOwnItemToggles: 读取遗留原生菜单勾选失败(id=" + id + ") → 跳过", e);
            return false;
        }
    }

    /**
     * Startup guard against an old copy of this plugin still being loaded:
     * current versions register no {@code statusBarWidgetFactory} at all, so
     * any factory in the extension point answering to one of the six legacy
     * ids comes from a stale copy — the native "Status Bar Widgets" menu
     * would show duplicated entries from it. Logs every instance (class +
     * classloader identity, for the idea.log evidence chain) and raises one
     * balloon per session telling the user how to clean it up. Pure EP scan,
     * no platform-version-specific API, best-effort guarded.
     */
    private void detectStaleWidgetFactories() {
        try {
            List<String> found = new ArrayList<>();
            for (StatusBarWidgetFactory factory : StatusBarWidgetFactory.EP_NAME.getExtensionList()) {
                if (factory != null && LEGACY_OWN_WIDGET_IDS.contains(factory.getId())) {
                    found.add(factory.getId() + " ← " + factory.getClass().getName()
                            + " @" + Integer.toHexString(System.identityHashCode(factory))
                            + " classloader=" + factory.getClass().getClassLoader());
                }
            }
            if (!found.isEmpty()) {
                DebugLog.warn("检测到本插件旧版本的微件工厂仍在注册（原生菜单重复条目来源，建议移除旧副本）: "
                        + String.join(" | ", found));
                notifyStaleWidgetFactories(found.size());
            }
        } catch (Throwable t) {
            DebugLog.warn("detectStaleWidgetFactories: 检测失败（不影响功能）", t);
        }
    }

    private void notifyStaleWidgetFactories(int factoryCount) {
        if (!STALE_FACTORY_NOTIFIED.compareAndSet(false, true)) {
            return;
        }
        // 项目服务构造可能发生在非 EDT：气球通知统一调度到 EDT，且校验项目未关闭
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }
            try {
                NotificationGroupManager.getInstance().getNotificationGroup("TopStatusBar")
                        .createNotification("Top Status Bar：检测到旧版本副本残留",
                                "原生「状态栏微件」菜单里本插件的条目来自旧版本副本（检测到 " + factoryCount
                                        + " 个残留微件工厂）。请到 Settings → Plugins 搜索 \"Top Status Bar\"，"
                                        + "若出现多条请禁用/卸载旧的一条并重启 IDE；菜单重复条目与开关失灵即会消失。"
                                        + "详见 idea.log 中 TSB 前缀日志。",
                                NotificationType.WARNING)
                        .notify(project);
            } catch (Throwable t) {
                DebugLog.warn("notifyStaleWidgetFactories: 通知发送失败（不影响功能）", t);
            }
        });
    }

    /**
     * Instant sync with the native "Status Bar Widgets" menu; the 5s
     * periodic poll ({@link #syncToNativeMenu}) remains the fallback.
     * <p>
     * The platform registers one toggle action per configurable widget
     * factory under {@code StatusBarWidgets.Toggle.<factoryId>}
     * (platform StatusBarActionManager) — those fire for the View menu and
     * the status-bar right-click menu alike (both are the same action group
     * on 233–261, verified against platform sources). The right-click
     * "hide this widget" action is an unregistered internal instance and is
     * matched by simple class name instead.
     * <p>
     * Diagnostics for the 2026.x frontend store: when such an action fires
     * but the classic-store decision snapshot does NOT change, the toggle
     * landed in the separate frontend store this plugin cannot read — the
     * log line is the evidence to confirm that scenario on a user machine.
     */
    private void installNativeToggleListener() {
        try {
            // 注册通道选用消息总线而非 ActionManager.addAnActionListener：
            // 后者在 241 已 @Deprecated(forRemoval) 且于 2026.x 被移除（运行期 NoSuchMethodError），
            // 而 ActionManagerImpl.fireAfterActionPerformed 在 241 与 2026.x 均会
            // 向 AnActionListener.TOPIC 广播（已对两版平台源码核实）。
            AnActionListener listener = new AnActionListener() {
                @Override
                public void afterActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event,
                                                 @NotNull AnActionResult result) {
                    if (project.isDisposed()) {
                        return;
                    }
                    String actionId = ActionManager.getInstance().getId(action);
                    boolean nativeToggle = actionId != null
                            && actionId.startsWith("StatusBarWidgets.Toggle.");
                    boolean hideCurrent = actionId == null
                            && "HideCurrentWidgetAction".equals(action.getClass().getSimpleName());
                    if (!nativeToggle && !hideCurrent) {
                        return;
                    }
                    // ToggleAction.actionPerformed 已同步写入经典存储，此刻读取即最新值
                    String before = lastDecisionSnapshot;
                    syncToNativeMenu();
                    if (lastDecisionSnapshot.equals(before)) {
                        DebugLog.log("原生微件开关动作未改变经典存储决策(actionId=" + actionId
                                + ", class=" + action.getClass().getSimpleName()
                                + ") → 该条目勾选落入 2026.x 前端独立存储（插件不可读），或开关值未变化");
                    } else {
                        DebugLog.log("原生微件开关动作即时同步(actionId=" + actionId + ")");
                    }
                }
            };
            ApplicationManager.getApplication().getMessageBus().connect(this)
                    .subscribe(AnActionListener.TOPIC, listener);
        } catch (Throwable t) {
            DebugLog.warn("installNativeToggleListener: 注册失败（退化为 5s 轮询）", t);
        }
    }

    /**
     * Snapshot of the current display decisions (master switch plus a
     * {@code id=0/1} pair per candidate). The periodic poll compares this
     * snapshot and reloads only on change, so an untouched menu costs one
     * cheap map read per widget id.
     */
    private String decisionSnapshot() {
        TopStatusBarSettings settings = TopStatusBarSettings.getInstance(project);
        StringBuilder snapshot = new StringBuilder("master=").append(settings.isEnabled() ? 1 : 0).append(';');
        for (StatusItem candidate : candidates) {
            String widgetId = candidate.getPlatformWidgetId();
            StatusBarWidgetFactory factory = widgetId == null ? null : findWidgetFactory(widgetId);
            // 与 reload 同一条判定（含工厂 isAvailable），保证可用性变化
            // （如非 Git 项目变为 Git 项目）也能被轮询/即时监听捕获
            boolean enabled = isDisplayEnabled(candidate, factory);
            snapshot.append(candidate.getId()).append(enabled ? "=1;" : "=0;");
        }
        return snapshot.toString();
    }

    /**
     * Detects native "Status Bar Widgets" menu toggles and re-syncs the
     * item list when a decision actually changed. The platform publishes no
     * change topic for these toggles (the menu action only persists the
     * state and updates the bottom-bar widgets), so the periodic refresh is
     * the reliable path with a worst-case delay of one refresh interval;
     * it also picks up plugin settings-page changes made in another IDE
     * window of the same project.
     */
    private void syncToNativeMenu() {
        if (project.isDisposed()) {
            return;
        }
        String snapshot = decisionSnapshot();
        if (!snapshot.equals(lastDecisionSnapshot)) {
            DebugLog.log("syncToNativeMenu: 显示决策变化 → reload; 旧=[" + lastDecisionSnapshot
                    + "], 新=[" + snapshot + "]");
            reload();
        }
    }

    private void scheduledRefresh() {
        if (project.isDisposed()) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }
            // 不因 items 为空而早退：全部条目都被取消勾选时，轮询仍要能发现重新勾选
            syncToNativeMenu();
            for (StatusItem item : items) {
                try {
                    item.refresh();
                } catch (Exception e) {
                    // 之前被静默吞掉——这正是"出问题查不到原因"的盲区，现在完整记录
                    DebugLog.warn("周期刷新异常: item=" + item.getId(), e);
                }
            }
        });
    }

    private void fireChanged() {
        AggregatorItem aggregator = null;
        for (StatusItem item : items) {
            if (item instanceof AggregatorItem) {
                aggregator = (AggregatorItem) item;
                break;
            }
        }
        if (aggregator != null) {
            aggregator.updateSummary(new ArrayList<>(items));
        }
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    @Override
    public void dispose() {
        DebugLog.log("manager dispose: project=" + project.getName());
        if (periodicRefresh != null) {
            periodicRefresh.cancel(false);
            periodicRefresh = null;
        }
        for (StatusItem item : items) {
            item.uninstall();
        }
        items.clear();
        candidates = Collections.emptyList();
        listeners.clear();
    }
}
