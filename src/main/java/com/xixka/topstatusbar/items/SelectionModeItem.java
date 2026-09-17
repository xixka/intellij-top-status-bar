package com.xixka.topstatusbar.items;

import com.intellij.openapi.editor.Editor;
import com.xixka.topstatusbar.model.StatusSeverity;

/**
 * 编辑器选择模式: visible only while column (block) selection mode is
 * active, highlighted with info color.
 */
public final class SelectionModeItem extends CurrentFileItem {

    public SelectionModeItem() {
        super("selectionMode", 45);
    }

    @Override
    protected void update() {
        Editor editor = EditorContext.selectedEditor(project());
        if (editor == null) {
            setVisible(false);
            return;
        }
        boolean columnMode = editor.getSettings().isColumnMode();
        if (columnMode) {
            setText("列选择");
            setSeverity(StatusSeverity.INFO);
            setTooltip("编辑器选择模式：列（块）选择，可通过 Edit → Column Selection Mode 切换");
            setVisible(true);
        } else {
            setSeverity(StatusSeverity.NORMAL);
            setVisible(false);
        }
    }
}
