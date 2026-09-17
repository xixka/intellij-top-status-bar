package com.xixka.topstatusbar.items;

import com.intellij.icons.AllIcons;
import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import com.xixka.topstatusbar.model.AbstractStatusItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * 省电模式: mirrors the native power-save widget — an icon-only cell that
 * always shows while the widget is enabled: an eye while power save is off,
 * the power-save icon while it is on. Clicking toggles the mode, updated
 * live via the app-level {@link PowerSaveMode#TOPIC} topic.
 */
public final class PowerSaveItem extends AbstractStatusItem {

    @Nullable
    private MessageBusConnection connection;

    public PowerSaveItem() {
        super("powerSave", 55);
    }

    @Override
    public @Nullable String getPlatformWidgetId() {
        return "PowerSaveMode";
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

    @Override
    public void onClick(@Nullable Project project, @NotNull JComponent source) {
        // Same as the native power-save widget: click toggles the mode; the
        // PowerSaveMode.TOPIC listener refreshes the icon right away.
        PowerSaveMode.setEnabled(!PowerSaveMode.isEnabled());
    }

    private void update() {
        boolean enabled = PowerSaveMode.isEnabled();
        setIcon(enabled ? AllIcons.General.InspectionsPowerSaveMode : AllIcons.General.InspectionsEye);
        setText("");
        setTooltip(enabled
                ? "Power Save Mode 已开启：代码洞察与自动补全暂停，点击关闭"
                : "Power Save Mode 已关闭，点击开启（将暂停代码洞察与自动补全）");
        setVisible(true);
    }
}
