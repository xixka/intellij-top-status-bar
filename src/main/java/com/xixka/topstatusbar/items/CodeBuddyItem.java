package com.xixka.topstatusbar.items;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.xixka.topstatusbar.model.AbstractStatusItem;
import com.xixka.topstatusbar.model.StatusSeverity;
import com.xixka.topstatusbar.settings.TopStatusBarSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * CodeBuddy: user-configurable status cell for the CodeBuddy AI assistant
 * integration. The label is configurable in the plugin settings.
 */
public final class CodeBuddyItem extends AbstractStatusItem {

    public CodeBuddyItem() {
        super("codeBuddy", 15);
    }

    @Override
    protected void install() {
        update();
    }

    @Override
    public void refresh() {
        update();
    }

    @Override
    public void onClick(@Nullable Project project, @NotNull JComponent source) {
        if (project == null) {
            return;
        }
        NotificationGroupManager.getInstance().getNotificationGroup("TopStatusBar")
                .createNotification("CodeBuddy",
                        "CodeBuddy 状态项显示 AI 助手集成占位状态，标签可在 Settings → Appearance & Behavior → Top Status Bar 中自定义。",
                        NotificationType.INFORMATION)
                .notify(project);
    }

    private void update() {
        Project project = project();
        if (project == null) {
            setVisible(false);
            return;
        }
        String label = TopStatusBarSettings.getInstance(project).getCodeBuddyLabel();
        if (label == null || label.isBlank()) {
            setVisible(false);
            return;
        }
        setText(label.trim());
        setSeverity(StatusSeverity.INFO);
        setTooltip("CodeBuddy：AI 助手集成状态（点击查看说明，标签可在设置中修改）");
        setVisible(true);
    }
}
