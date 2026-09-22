package com.xixka.topstatusbar.items;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.messages.MessageBusConnection;
import com.xixka.topstatusbar.model.AbstractStatusItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.MissingResourceException;

/**
 * 省电模式: mirrors the native power-save widget — an icon-only cell that
 * always shows while the widget is enabled: an eye while power save is off,
 * the power-save icon while it is on, updated live via the app-level
 * {@link PowerSaveMode#TOPIC} topic.
 * <p>
 * 点击弹出与 2026.x 原生一致的菜单（2026-09-22 用户截图核实）：
 * 「省电模式: 已启用/已禁用」切换项 + 分隔线 + 「配置...」。切换实际执行
 * 平台注册的 {@code TogglePowerSave} 动作（保持 PowerSaveModeNotifier 行为），
 * 配置项执行平台 {@code ShowSettings} 动作；菜单文案取
 * {@code InspectionsBundle power.save.mode.widget.tooltip.*}（与原生同一键，
 * 241 无键时退化为内置文案）。经典（2024.x）原生微件点击为直接切换，本实现
 * 的弹窗在两种版本上均可用。
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
        boolean enabled = PowerSaveMode.isEnabled();
        AnAction toggle = new ToggleEntry(enabled);
        AnAction configure = new ConfigureEntry();
        ActionGroup group = new DefaultActionGroup(toggle, Separator.create(), configure);
        ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
                null, group,
                EditorContext.popupContext(null, source),
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false);
        // 顶栏在下方弹出（原生底栏上方弹的镜像）
        popup.show(new RelativePoint(source, new Point(0, source.getHeight())));
    }

    private void update() {
        boolean enabled = PowerSaveMode.isEnabled();
        setIcon(enabled ? AllIcons.General.InspectionsPowerSaveMode : AllIcons.General.InspectionsEye);
        setText("");
        setTooltip(enabled ? enabledText() : disabledText());
        setVisible(true);
    }

    static String enabledText() {
        try {
            return InspectionsBundle.message("power.save.mode.widget.tooltip.enabled");
        } catch (MissingResourceException e) {
            return "省电模式: 已启用";
        }
    }

    static String disabledText() {
        try {
            return InspectionsBundle.message("power.save.mode.widget.tooltip.disabled");
        } catch (MissingResourceException e) {
            return "省电模式: 已禁用";
        }
    }

    /** 菜单条目：执行平台注册动作，保持其全部附带行为（通知等）。 */
    private static void performPlatformAction(@NotNull String actionId, @NotNull AnActionEvent source) {
        AnAction action = ActionManager.getInstance().getAction(actionId);
        if (action == null) {
            return;
        }
        action.actionPerformed(AnActionEvent.createFromAnAction(
                action, source.getInputEvent(), ActionPlaces.MAIN_TOOLBAR, source.getDataContext()));
    }

    private static final class ToggleEntry extends AnAction implements DumbAware {
        private final boolean enabled;

        ToggleEntry(boolean enabled) {
            super(enabled ? enabledText() : disabledText());
            this.enabled = enabled;
            getTemplatePresentation().setIcon(enabled
                    ? AllIcons.General.InspectionsPowerSaveMode
                    : AllIcons.General.InspectionsEye);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            performPlatformAction("TogglePowerSave", e);
        }
    }

    private static final class ConfigureEntry extends AnAction implements DumbAware {
        ConfigureEntry() {
            super("配置...");
            getTemplatePresentation().setIcon(AllIcons.General.GearPlain);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            performPlatformAction("ShowSettings", e);
        }
    }
}
