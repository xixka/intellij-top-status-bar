package com.xixka.topstatusbar.items;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * 文件编码: charset of the currently edited file.
 */
public final class EncodingItem extends CurrentFileItem {

    public EncodingItem() {
        super("encoding", 80);
    }

    @Override
    public @Nullable String getPlatformWidgetId() {
        return "Encoding";
    }

    @Override
    protected void update() {
        Editor editor = EditorContext.selectedEditor(project());
        VirtualFile file = EditorContext.virtualFile(editor);
        if (file == null) {
            setVisible(false);
            return;
        }
        String charsetName = file.getCharset().displayName();
        setText(charsetName);
        setTooltip("文件编码：" + charsetName);
        setVisible(true);
    }
}
