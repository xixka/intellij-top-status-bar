package com.xixka.topstatusbar.items;

import com.intellij.openapi.project.Project;
import com.xixka.topstatusbar.model.AbstractStatusItem;
import org.jetbrains.annotations.Nullable;

/**
 * 状态文本: general status line showing the current project name.
 */
public final class StatusTextItem extends AbstractStatusItem {

    public StatusTextItem() {
        super("statusText", 30);
    }

    /**
     * Mirrors the plugin's own {@code statusText} statusBarWidgetFactory
     * registered in plugin.xml, so the native "Status Bar Widgets" menu
     * checkbox governs this item exactly like the built-in widgets.
     */
    @Override
    public @Nullable String getPlatformWidgetId() {
        return "statusText";
    }

    @Override
    protected void install() {
        update();
    }

    @Override
    public void refresh() {
        update();
    }

    private void update() {
        Project project = project();
        if (project == null) {
            setVisible(false);
            return;
        }
        setText(project.getName());
        setTooltip("当前项目：" + project.getName());
        setVisible(true);
    }
}
