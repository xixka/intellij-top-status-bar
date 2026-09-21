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
 * <p>
 * 显示开关由插件设置页「文件同步」逐项开关控制（自有项，全版本一致）。
 * <p>
 * 2026-09-21 教训：曾改为镜像平台 VfsRefresh 工厂（跟随原生「状态栏微件」
 * 菜单勾选），但 2026.1（IU-261.25134.95）实测该微件的菜单勾选落入
 * 2026.x 前端独立存储——idea.log 中两次 ToggleWidgetAction(VfsRefresh)
 * 触发后经典 StatusBarWidgetSettings 决策快照纹丝不动，插件无从读取
 * （同日 PowerSaveMode 勾选却能即时同步，证明经典存储通道本身可用，
 * 仅前端化微件例外）。镜像此路不通，回退设置页控制。
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
