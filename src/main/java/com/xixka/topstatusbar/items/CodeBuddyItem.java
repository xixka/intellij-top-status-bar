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
 * <p>
 * 2026-09-22（用户定案）：CodeBuddy 是第三方插件（腾讯云 CodeBuddy）加进
 * 状态栏的微件——本插件不处理任何第三方插件添加的微件，codeBuddy 默认关闭
 * （{@code StatusItems.DEFAULT_DISABLED_IDS}，遗留显式值由
 * {@code applyDefaultOffPolicy} 一次性清除），顶栏模板默认不显示。
 * 项与 id 保留仅为已发布 id 不可删（AGENTS.md 硬约束）；设置页仍可勾选恢复。
 * 第三方微件在原生底栏的显隐不受本插件影响（镜像开关只读原生存储）。
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
