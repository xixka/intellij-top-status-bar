package com.xixka.topstatusbar.items;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.xixka.topstatusbar.model.AbstractStatusItem;
import com.xixka.topstatusbar.model.StatusSeverity;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 网络位置: online/offline state based on active network interfaces,
 * refreshed on a background schedule. Offline state is highlighted.
 */
public final class NetworkLocationItem extends AbstractStatusItem {

    private static final long CHECK_INTERVAL_SECONDS = 10;

    @Nullable
    private Future<?> checkTask;

    public NetworkLocationItem() {
        super("networkLocation", 25);
    }

    @Override
    protected void install() {
        checkTask = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
                this::scheduledCheck, 1, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void uninstall() {
        if (checkTask != null) {
            checkTask.cancel(false);
            checkTask = null;
        }
        super.uninstall();
    }

    @Override
    public void onClick(@Nullable com.intellij.openapi.project.Project project) {
        scheduledCheck();
    }

    private void scheduledCheck() {
        if (project() == null) {
            return;
        }
        List<String> activeInterfaces = new ArrayList<>();
        boolean online = false;
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isUp() && !networkInterface.isLoopback()) {
                    online = true;
                    String name = networkInterface.getDisplayName();
                    if (name != null && !name.isBlank()) {
                        activeInterfaces.add(name);
                    }
                }
            }
        } catch (IOException ignored) {
        }
        String tooltip = buildTooltip(online, activeInterfaces);
        boolean onlineState = online;
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project() == null) {
                return;
            }
            if (onlineState) {
                setText("在线");
                setSeverity(StatusSeverity.NORMAL);
                setTooltip(tooltip);
            } else {
                setText("离线");
                setSeverity(StatusSeverity.WARNING);
                setTooltip("网络位置：未检测到活动网络接口");
            }
            setVisible(true);
        });
    }

    private static String buildTooltip(boolean online, List<String> interfaces) {
        if (!online) {
            return "网络位置：离线";
        }
        if (interfaces.isEmpty()) {
            return "网络位置：在线";
        }
        return "网络位置：在线（" + String.join("、", interfaces) + "）";
    }
}
