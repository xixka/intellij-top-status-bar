package com.xixka.topstatusbar.items;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFile;
import com.xixka.topstatusbar.model.StatusSeverity;
import org.jetbrains.annotations.Nullable;

/**
 * 只读特性: mirrors the native read-only attribute widget — an icon-only
 * cell that always shows while a regular file is open: a pencil when the
 * file is writable, a padlock when it is read-only. Clicking toggles the
 * file read-only attribute (see {@link #onClick}).
 */
public final class ReadOnlyItem extends CurrentFileItem {

    public ReadOnlyItem() {
        super("readOnly", 75);
    }

    @Override
    public @Nullable String getPlatformWidgetId() {
        return "ReadOnlyAttribute";
    }

    @Override
    protected void update() {
        Editor editor = EditorContext.selectedEditor(project());
        VirtualFile file = EditorContext.virtualFile(editor);
        if (file == null || file.getFileSystem().isReadOnly()) {
            setVisible(false);
            return;
        }
        boolean writable = file.isWritable();
        setIcon(writable ? AllIcons.Ide.Readwrite : AllIcons.Ide.Readonly);
        setText("");
        setSeverity(StatusSeverity.NORMAL);
        setTooltip(writable
                ? "当前文件可写，点击切换为只读"
                : "当前文件为只读，点击切换为可写");
        setVisible(true);
    }
}
