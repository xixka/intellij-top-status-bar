package com.xixka.topstatusbar;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * The single action backing the Top Status Bar toolbar component.
 * <p>
 * When this action is placed in a toolbar, the platform asks it for a custom
 * Swing component instead of rendering a plain button — the officially
 * supported way to embed custom UI in the (New UI) Main Toolbar without
 * creating a second toolbar row or touching IDEA internal UI.
 */
public final class TopStatusBarAction extends AnAction implements CustomComponentAction, DumbAware {

    public TopStatusBarAction() {
        super("Top Status Bar", "Compact status bar for the Main Toolbar", null);
    }

    @Override
    public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation,
                                                      @NotNull String place) {
        return new TopStatusBarPanel();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        // Reached only when the action is placed somewhere that renders it as a
        // plain menu item instead of the custom component.
        Project project = e.getProject();
        NotificationGroupManager.getInstance().getNotificationGroup("TopStatusBar")
                .createNotification("Top Status Bar",
                        "请通过 Settings → Appearance & Behavior → Menus and Toolbars 将 “Top Status Bar” 添加到 Main Toolbar，建议放在搜索按钮左侧。",
                        NotificationType.INFORMATION)
                .notify(project);
    }
}
