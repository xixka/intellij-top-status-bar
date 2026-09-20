package com.xixka.topstatusbar.items;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.messages.MessageBusConnection;
import com.xixka.topstatusbar.model.AbstractStatusItem;
import com.xixka.topstatusbar.model.StatusSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 文件系统同步: VFS change activity. Shows "同步中" while VFS events are
 * being processed and the timestamp of the last completed sync.
 */
public final class FileSystemSyncItem extends AbstractStatusItem {

    @Nullable
    private MessageBusConnection connection;

    private volatile boolean syncing;
    private volatile long lastSyncTime;
    private volatile int lastSyncCount;

    // SimpleDateFormat is not thread-safe: only touched on the EDT.
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    public FileSystemSyncItem() {
        super("fileSystemSync", 40);
    }

    /**
     * Mirrors the plugin's own {@code fileSystemSync} statusBarWidgetFactory
     * registered in plugin.xml, so the native "Status Bar Widgets" menu
     * checkbox governs this item exactly like the built-in widgets.
     */
    @Override
    public @Nullable String getPlatformWidgetId() {
        return "fileSystemSync";
    }

    @Override
    protected void install() {
        connection = ApplicationManager.getApplication().getMessageBus().connect();
        connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void before(@NotNull List<? extends VFileEvent> events) {
                if (!events.isEmpty()) {
                    syncing = true;
                    refreshPresentation();
                }
            }

            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                if (!events.isEmpty()) {
                    syncing = false;
                    lastSyncTime = System.currentTimeMillis();
                    lastSyncCount = events.size();
                    refreshPresentation();
                }
            }
        });
        setText("已同步");
        setTooltip("文件系统同步：等待变更");
        setVisible(true);
    }

    @Override
    public void uninstall() {
        if (connection != null) {
            connection.dispose();
            connection = null;
        }
        super.uninstall();
    }

    @Override
    public void refresh() {
        refreshPresentation();
    }

    private void refreshPresentation() {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project() == null) {
                return;
            }
            boolean syncingNow = syncing;
            long syncTime = lastSyncTime;
            int syncCount = lastSyncCount;
            if (syncingNow) {
                setText("同步中…");
                setSeverity(StatusSeverity.INFO);
                setTooltip("文件系统同步：正在将磁盘变更同步到 IDE");
            } else if (syncTime > 0) {
                String time = timeFormat.format(new Date(syncTime));
                setText("已同步 " + time);
                setSeverity(StatusSeverity.NORMAL);
                setTooltip("文件系统同步：最近一次同步 " + syncCount + " 个事件（" + time + "）");
            } else {
                setText("已同步");
                setSeverity(StatusSeverity.NORMAL);
                setTooltip("文件系统同步：等待变更");
            }
            setVisible(true);
        });
    }
}
