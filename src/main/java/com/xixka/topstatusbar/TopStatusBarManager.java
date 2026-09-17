package com.xixka.topstatusbar;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Project-level service owning the status items: installs their listeners,
 * schedules the periodic refresh and forwards change notifications to the
 * toolbar component.
 */
public final class TopStatusBarManager implements Disposable {

    private static final long REFRESH_INTERVAL_SECONDS = 5;

    private final Project project;
    private final List<StatusItem> items = new CopyOnWriteArrayList<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final Set<String> suppressedWidgetIds = new HashSet<>();
    private Future<?> periodicRefresh;

    public static TopStatusBarManager getInstance(@NotNull Project project) {
        return project.getService(TopStatusBarManager.class);
    }

    public TopStatusBarManager(@NotNull Project project) {
        this.project = project;
        reload();
        periodicRefresh = AppExecutorUtil.getAppScheduledExecutorService()
                .scheduleWithFixedDelay(this::scheduledRefresh,
                        REFRESH_INTERVAL_SECONDS, REFRESH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public @NotNull List<StatusItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    /**
     * Mirrors the native status bar widget toggles: an item mapped to a
     * built-in widget (see {@link StatusItem#getPlatformWidgetId()}) is
     * suppressed while that widget is turned off in the IDE status bar.
     * Must be called on the EDT.
     */
    public boolean isSuppressedByPlatformWidget(@NotNull StatusItem item) {
        String widgetId = item.getPlatformWidgetId();
        if (widgetId == null || project.isDisposed()) {
            return false;
        }
        StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
        return statusBar != null && statusBar.getWidget(widgetId) == null;
    }

    /**
     * Detects changes of the native widget toggles on the periodic refresh
     * and fires a change event so the top bar re-syncs its cells.
     */
    private void syncPlatformWidgets() {
        if (project.isDisposed() || items.isEmpty()) {
            return;
        }
        StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
        Set<String> suppressed = new HashSet<>();
        if (statusBar != null) {
            for (StatusItem item : items) {
                String widgetId = item.getPlatformWidgetId();
                if (widgetId != null && statusBar.getWidget(widgetId) == null) {
                    suppressed.add(widgetId);
                }
            }
        }
        if (!suppressed.equals(suppressedWidgetIds)) {
            suppressedWidgetIds.clear();
            suppressedWidgetIds.addAll(suppressed);
            fireChanged();
        }
    }

    public void addChangeListener(@NotNull Runnable listener) {
        listeners.add(listener);
    }

    public void removeChangeListener(@NotNull Runnable listener) {
        listeners.remove(listener);
    }

    /**
     * Re-checks the native widget toggles immediately. Called by the sync
     * widgets ({@code com.xixka.topstatusbar.widget.TopBarSyncWidget}) when
     * they are added to or removed from the status bar, so toggling an entry
     * in the native "Status Bar Widgets" menu takes effect right away instead
     * of waiting for the periodic refresh.
     */
    public void syncNow() {
        ApplicationManager.getApplication().invokeLater(this::syncPlatformWidgets);
    }

    public void reload() {
        for (StatusItem item : items) {
            item.uninstall();
        }
        items.clear();

        TopStatusBarSettings settings = TopStatusBarSettings.getInstance(project);
        if (settings.isEnabled()) {
            for (StatusItem candidate : createItems()) {
                if (settings.isItemEnabled(candidate.getId())) {
                    items.add(candidate);
                }
            }
        }

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

    private void scheduledRefresh() {
        if (project.isDisposed() || items.isEmpty()) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }
            for (StatusItem item : items) {
                try {
                    item.refresh();
                } catch (Exception ignored) {
                }
            }
            syncPlatformWidgets();
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
            List<StatusItem> unsuppressed = new ArrayList<>();
            for (StatusItem item : items) {
                if (!isSuppressedByPlatformWidget(item)) {
                    unsuppressed.add(item);
                }
            }
            aggregator.updateSummary(unsuppressed);
        }
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    @Override
    public void dispose() {
        if (periodicRefresh != null) {
            periodicRefresh.cancel(false);
            periodicRefresh = null;
        }
        for (StatusItem item : items) {
            item.uninstall();
        }
        items.clear();
        listeners.clear();
    }
}
