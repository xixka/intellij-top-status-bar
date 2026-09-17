package com.xixka.topstatusbar.items;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件编码: charset of the currently edited file. Clicking opens the same
 * encoding picker as the native widget — the recently used encodings first,
 * then every available charset — and applies the choice to the file.
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
        setTooltip("文件编码：" + charsetName + "（点击切换）");
        setVisible(true);
    }

    @Override
    public void onClick(@Nullable Project project, @NotNull JComponent source) {
        Project effective = project != null ? project : project();
        Editor editor = EditorContext.selectedEditor(effective);
        VirtualFile file = EditorContext.virtualFile(editor);
        if (effective == null || file == null) {
            return;
        }
        List<Charset> encodings = new ArrayList<>(EncodingManager.getInstance().getFavorites());
        for (Charset charset : CharsetToolkit.getAvailableCharsets()) {
            if (!encodings.contains(charset)) {
                encodings.add(charset);
            }
        }
        Charset current = file.getCharset();
        BaseListPopupStep<Charset> step = new BaseListPopupStep<>("文件编码", encodings) {
            @Override
            public Icon getIconFor(Charset value) {
                return current.equals(value) ? AllIcons.Actions.Checked : null;
            }

            @Override
            public PopupStep onChosen(Charset selected, boolean finalChoice) {
                Charset choice = selected;
                return doFinalStep(() -> ApplicationManager.getApplication().runWriteAction(
                        () -> EncodingManager.getInstance().setEncoding(file, choice)));
            }
        };
        JBPopupFactory.getInstance().createListPopup(step)
                .show(new RelativePoint(source, new Point(source.getWidth() / 2, source.getHeight())));
    }
}
