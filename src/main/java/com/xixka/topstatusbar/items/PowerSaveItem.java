package com.xixka.topstatusbar.items;

import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.messages.MessageBusConnection;
import com.xixka.topstatusbar.model.AbstractStatusItem;
import com.xixka.topstatusbar.model.StatusSeverity;
import org.jetbrains.annotations.Nullable;

/**
 * 省电模式: visible (highlighted) only while Power Save Mode is enabled,
 * updated live via the app-level {@link PowerSaveMode#TOPIC} topic.
 */
public final class PowerSaveItem extends AbstractStatusItem {

    @Nullable
    private MessageBusConnection connection;

    public PowerSaveItem() {
        super("powerSave", 55);
    }

    @Override
    protected void install() {
        connection = ApplicationManager.getApplication().getMessageBus().connect();
        connection.subscribe(PowerSaveMode.TOPIC, new PowerSaveMode.Listener() {
            @Override
            public void powerSaveStateChanged() {
                update();
            }
        });
        update();
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
        update();
    }

    private void update() {
        boolean enabled = PowerSaveMode.isEnabled();
        if (enabled) {
            setText("省电模式");
            setSeverity(StatusSeverity.WARNING);
            setTooltip("Power Save Mode 已开启：代码洞察与自动补全暂停，File → Power Save Mode 可关闭");
            setVisible(true);
        } else {
            setSeverity(StatusSeverity.NORMAL);
            setVisible(false);
        }
    }
}
