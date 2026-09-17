package com.xixka.topstatusbar.items;

import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.xixka.topstatusbar.model.AbstractStatusItem;
import com.xixka.topstatusbar.model.StatusSeverity;
import com.xixka.topstatusbar.settings.TopStatusBarConfigurable;
import com.xixka.topstatusbar.settings.TopStatusBarSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * 默认部署服务器: user-configured default deployment target; clicking the
 * cell opens the plugin settings page.
 */
public final class DeployServerItem extends AbstractStatusItem {

    public DeployServerItem() {
        super("deployServer", 20);
    }

    @Override
    public @Nullable String getPlatformWidgetId() {
        // Mirrors this plugin's own widget factory (see widget/), so the item
        // follows its toggle in the native "Status Bar Widgets" menu.
        return "deployServer";
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
        ShowSettingsUtil.getInstance().showSettingsDialog(project, TopStatusBarConfigurable.class);
    }

    private void update() {
        Project project = project();
        if (project == null) {
            setVisible(false);
            return;
        }
        String server = TopStatusBarSettings.getInstance(project).getDeployServer();
        if (server == null || server.isBlank()) {
            setVisible(false);
            return;
        }
        String trimmed = server.trim();
        setText("部署: " + trimmed);
        setSeverity(StatusSeverity.INFO);
        setTooltip("默认部署服务器：" + trimmed + "（点击打开设置）");
        setVisible(true);
    }
}
