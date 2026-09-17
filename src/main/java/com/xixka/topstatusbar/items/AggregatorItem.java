package com.xixka.topstatusbar.items;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.xixka.topstatusbar.model.AbstractStatusItem;
import com.xixka.topstatusbar.model.StatusItem;
import com.xixka.topstatusbar.model.StatusItems;
import com.xixka.topstatusbar.model.StatusSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import java.util.List;

/**
 * 聚合器: aggregates all status items — shows the number of abnormal items,
 * summarizes every item in the tooltip and in the click notification.
 */
public final class AggregatorItem extends AbstractStatusItem {

    private volatile List<StatusItem> aggregated = List.of();

    public AggregatorItem() {
        super("aggregator", 35);
    }

    @Override
    public @Nullable String getPlatformWidgetId() {
        // Mirrors this plugin's own widget factory (see widget/), so the item
        // follows its toggle in the native "Status Bar Widgets" menu.
        return "aggregator";
    }

    @Override
    protected void install() {
        updateSummary(aggregated);
    }

    @Override
    public void uninstall() {
        aggregated = List.of();
        super.uninstall();
    }

    /**
     * Called by the manager (on the EDT) whenever any item changes.
     */
    public void updateSummary(@NotNull List<StatusItem> items) {
        aggregated = items;
        int warnings = 0;
        int errors = 0;
        int visible = 0;
        StringBuilder tooltip = new StringBuilder();
        for (StatusItem item : items) {
            if (item == this || !item.isVisible()) {
                continue;
            }
            visible++;
            if (item.getSeverity() == StatusSeverity.WARNING) {
                warnings++;
            } else if (item.getSeverity() == StatusSeverity.ERROR) {
                errors++;
            }
            if (tooltip.length() > 0) {
                tooltip.append('\n');
            }
            tooltip.append(displayText(item));
        }
        int abnormal = warnings + errors;
        if (abnormal > 0) {
            setText(abnormal + " 项异常");
            setSeverity(errors > 0 ? StatusSeverity.ERROR : StatusSeverity.WARNING);
        } else {
            setText("状态正常");
            setSeverity(StatusSeverity.NORMAL);
        }
        String summary = tooltip.length() == 0 ? "暂无可见状态项" : tooltip.toString();
        setTooltip("聚合器：共 " + visible + " 项状态\n" + summary);
        setVisible(true);
    }

    @Override
    public void onClick(@Nullable Project project, @NotNull JComponent source) {
        if (project == null) {
            return;
        }
        StringBuilder report = new StringBuilder();
        for (StatusItem item : aggregated) {
            if (item == this || !item.isVisible()) {
                continue;
            }
            if (report.length() > 0) {
                report.append('\n');
            }
            report.append(displayText(item));
        }
        if (report.length() == 0) {
            report.append("暂无可见状态项");
        }
        NotificationGroupManager.getInstance().getNotificationGroup("TopStatusBar")
                .createNotification("聚合器状态总览", report.toString(), NotificationType.INFORMATION)
                .notify(project);
    }

    private static String displayText(StatusItem item) {
        String name = StatusItems.displayNames().getOrDefault(item.getId(), item.getId());
        String text = item.getText();
        return text == null || text.isEmpty() ? name : name + ": " + text;
    }
}
