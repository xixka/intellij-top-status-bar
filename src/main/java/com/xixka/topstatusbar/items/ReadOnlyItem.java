package com.xixka.topstatusbar.items;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFile;
import com.xixka.topstatusbar.model.StatusSeverity;

/**
 * 只读特性: visible (highlighted) only while the current file is read-only.
 */
public final class ReadOnlyItem extends CurrentFileItem {

    public ReadOnlyItem() {
        super("readOnly", 75);
    }

    @Override
    protected void update() {
        Editor editor = EditorContext.selectedEditor(project());
        VirtualFile file = EditorContext.virtualFile(editor);
        Document document = EditorContext.document(editor);
        if (file == null || document == null) {
            setVisible(false);
            return;
        }
        boolean writable = file.isWritable() && document.isWritable();
        if (writable) {
            setSeverity(StatusSeverity.NORMAL);
            setVisible(false);
        } else {
            setText("只读");
            setSeverity(StatusSeverity.WARNING);
            setTooltip("当前文件为只读，修改不会被保存");
            setVisible(true);
        }
    }
}
