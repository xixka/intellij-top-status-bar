package com.xixka.topstatusbar.items;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.xixka.topstatusbar.model.AbstractStatusItem;
import com.xixka.topstatusbar.model.StatusSeverity;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 内存指示器: IDE heap usage, refreshed on a background schedule.
 * Click triggers a garbage collection.
 */
public final class MemoryItem extends AbstractStatusItem {

    private static final long UPDATE_INTERVAL_SECONDS = 2;

    @Nullable
    private Future<?> refreshTask;

    public MemoryItem() {
        super("memoryIndicator", 85);
    }

    @Override
    protected void install() {
        setIcon(AllIcons.Actions.GC);
        refreshTask = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
                this::scheduledUpdate, UPDATE_INTERVAL_SECONDS, UPDATE_INTERVAL_SECONDS, TimeUnit.SECONDS);
        ApplicationManager.getApplication().invokeLater(this::update);
    }

    @Override
    public void uninstall() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
            refreshTask = null;
        }
        super.uninstall();
    }

    @Override
    public void onClick(@Nullable Project project) {
        System.gc();
        ApplicationManager.getApplication().invokeLater(this::update);
    }

    private void scheduledUpdate() {
        if (project() == null) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(this::update);
    }

    private void update() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();
        setText(format(used) + " / " + format(max));
        boolean critical = max > 0 && used * 100 / max > 90;
        setSeverity(critical ? StatusSeverity.WARNING : StatusSeverity.NORMAL);
        setTooltip("内存指示器：已用 " + format(used) + "，上限 " + format(max) + "。点击触发垃圾回收。");
        setVisible(true);
    }

    private static String format(long bytes) {
        if (bytes < 1024 * 1024) {
            return bytes / 1024 + " KB";
        }
        long megaBytes = bytes / (1024 * 1024);
        if (megaBytes < 1024) {
            return megaBytes + " MB";
        }
        return String.format(Locale.ROOT, "%.1f GB", megaBytes / 1024.0);
    }
}
