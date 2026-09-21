package com.xixka.topstatusbar.items;

import com.intellij.openapi.project.Project;
import com.xixka.topstatusbar.model.AbstractStatusItem;

/**
 * 状态文本: general status line showing the current project name.
 * <p>
 * 2026-09-21 起默认关闭（第五轮「还是会显示」定案，见
 * {@code StatusItems.DEFAULT_DISABLED_IDS}）：升级后不再显示，
 * 设置页勾选「当前项目」可恢复。
 */
public final class StatusTextItem extends AbstractStatusItem {

    public StatusTextItem() {
        super("statusText", 30);
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
