package com.xixka.topstatusbar;

import com.intellij.openapi.Disposable;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Project-level service owning the status items: installs their listeners,
 * schedules the periodic refresh and forwards change notifications to the
 * toolbar component.
 * <p>
 * Which items exist is decided by the native "Status Bar Widgets" menu
 * (View | Appearance | Status Bar Widgets, 2026-09-20 user decision): a
 * candidate is loaded while its checkbox is on in that menu. The checkbox
 * state is read from the persisted platform {@link StatusBarWidgetSettings}
 * (ide.general.xml) — the same source the menu itself renders from — and
 * never from bottom-bar widget instances, which 2026.x no longer hosts for
 * many native widgets (that instance-based lookup was the root cause of the
 * earlier "checked items never show" incident, see AGENTS.md).
 */
public final class TopStatusBarManager implements Disposable {

    private static final long REFRESH_INTERVAL_SECONDS = 5;

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
            List<String> skippedByFallback = new ArrayList<>();
            List<String> missingFactories = new ArrayList<>();
            for (StatusItem candidate : candidates) {
                String widgetId = candidate.getPlatformWidgetId();
                if (widgetId != null && findWidgetFactory(widgetId) == null) {
                    missingFactories.add(widgetId);
                }
                if (isDisplayEnabled(candidate)) {
                    items.add(candidate);
                } else if (widgetId != null) {
                    skippedByMenu.add(candidate.getId());
                } else {
                    skippedByFallback.add(candidate.getId());
                }
            }
            DebugLog.log("reload: 装载 " + items.size() + " 项: " + itemIds()
                    + (skippedByMenu.isEmpty() ? "" : "; 原生菜单关闭跳过: " + skippedByMenu)
                    + (skippedByFallback.isEmpty() ? "" : "; 设置页关闭跳过(无工厂回退): " + skippedByFallback)
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
     * Whether a candidate item may be shown: the checkbox state of its
     * entry in the native "Status Bar Widgets" menu (2026-09-20 user
     * decision — "show exactly what the system settings selected").
     * <p>
     * The state is read via the persisted {@link StatusBarWidgetSettings}
     * resolved against the factory from {@link StatusBarWidgetFactory#EP_NAME},
     * mirroring how the menu itself computes its checkboxes. Candidates
     * without a resolvable factory (older platform, corresponding plugin
     * not installed) fall back to the plugin settings-page toggle.
     * <p>
     * Must be called on the EDT (the platform toggle action also updates
     * this setting on the EDT).
     */
    private boolean isDisplayEnabled(@NotNull StatusItem item) {
        String widgetId = item.getPlatformWidgetId();
        if (widgetId != null) {
            StatusBarWidgetFactory factory = findWidgetFactory(widgetId);
            if (factory != null) {
                return isFactoryEnabled(factory);
            }
        }
        return TopStatusBarSettings.getInstance(project).isItemEnabled(item.getId());
    }

    private static boolean isFactoryEnabled(@NotNull StatusBarWidgetFactory factory) {
        try {
            return StatusBarWidgetSettings.getInstance().isEnabled(factory);
        } catch (LinkageError | Exception e) {
            // Fail-open: a missing/unreadable settings service must never hide
            // items the user enabled (the historical "nothing shows" failure
            // mode was exactly a silent all-suppress).
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
            boolean enabled;
            if (widgetId != null) {
                StatusBarWidgetFactory factory = findWidgetFactory(widgetId);
                enabled = factory != null ? isFactoryEnabled(factory)
                        : settings.isItemEnabled(candidate.getId());
            } else {
                enabled = settings.isItemEnabled(candidate.getId());
            }
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
     * where the platform still hosts the plugin's sync widgets they notify
     * immediately instead (see {@code widget/TopBarSyncWidget}).
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

    /**
     * Immediate re-sync entry point for the plugin's sync status bar
     * widgets: the platform adds/removes them (see
     * {@code widget/TopBarSyncWidget}) the moment their native menu toggle
     * flips, and the widget forwards that here. Marshals to the EDT and
     * reuses the same decision-snapshot comparison as the periodic poll, so
     * an unchanged state costs nothing.
     */
    public void syncNow() {
        ApplicationManager.getApplication().invokeLater(this::syncToNativeMenu);
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
