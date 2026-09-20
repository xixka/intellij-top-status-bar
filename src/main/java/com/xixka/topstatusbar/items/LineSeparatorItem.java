package com.xixka.topstatusbar.items;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.LineSeparator;
import com.intellij.ui.awt.RelativePoint;
import com.xixka.topstatusbar.DebugLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * 行分隔符: LF / CRLF / CR, detected from the raw bytes of the current file.
 * Detection runs on a background thread to avoid blocking the EDT. Clicking
 * opens the same separator picker as the native widget.
 */
public final class LineSeparatorItem extends CurrentFileItem {

    public LineSeparatorItem() {
        super("lineSeparator", 70);
    }

    @Override
    public @Nullable String getPlatformWidgetId() {
        return "LineSeparator";
    }

    @Override
    protected void update() {
        Editor editor = EditorContext.selectedEditor(project());
        VirtualFile file = EditorContext.virtualFile(editor);
        if (file == null) {
            setVisible(false);
            return;
        }
        String fileUrl = file.getUrl();
        VirtualFile snapshot = file;
        com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService().execute(() -> {
            String detected = detectLineSeparator(snapshot);
            String label = labelOf(detected);
            String tooltip = "行分隔符：" + label + "（检测自文件原始字节）";
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                Editor current = EditorContext.selectedEditor(project());
                VirtualFile currentFile = EditorContext.virtualFile(current);
                if (currentFile == null || !fileUrl.equals(currentFile.getUrl())) {
                    return;
                }
                setText(label);
                setTooltip(tooltip);
                setVisible(true);
            });
        });
    }

    @Override
    public void onClick(@Nullable Project project, @NotNull JComponent source) {
        Project effective = project != null ? project : project();
        Editor editor = EditorContext.selectedEditor(effective);
        VirtualFile file = EditorContext.virtualFile(editor);
        if (file == null) {
            return;
        }
        String current = file.getDetectedLineSeparator();
        BaseListPopupStep<LineSeparator> step = new BaseListPopupStep<>("行分隔符", List.of(LineSeparator.values())) {
            @Override
            public Icon getIconFor(LineSeparator value) {
                return value.getSeparatorString().equals(current) ? AllIcons.Actions.Checked : null;
            }

            @Override
            public PopupStep onChosen(LineSeparator selected, boolean finalChoice) {
                LineSeparator choice = selected;
                return doFinalStep(() -> ApplicationManager.getApplication().runWriteAction(
                        () -> file.setDetectedLineSeparator(choice.getSeparatorString())));
            }
        };
        JBPopupFactory.getInstance().createListPopup(step)
                .show(new RelativePoint(source, new Point(source.getWidth() / 2, source.getHeight())));
    }

    private static String labelOf(@Nullable String separator) {
        if ("\r\n".equals(separator)) {
            return "CRLF";
        }
        if ("\r".equals(separator)) {
            return "CR";
        }
        return "LF";
    }

    @Nullable
    private static String detectLineSeparator(VirtualFile file) {
        try (java.io.InputStream stream = file.getInputStream()) {
            if (stream == null) {
                return null;
            }
            int budget = 8192;
            int b;
            while (budget-- > 0 && (b = stream.read()) != -1) {
                if (b == '\r') {
                    return stream.read() == '\n' ? "\r\n" : "\r";
                }
                if (b == '\n') {
                    return "\n";
                }
            }
            return null;
        } catch (java.io.IOException e) {
            DebugLog.warn("lineSeparator 读取文件字节失败: file="
                    + (file == null ? "null" : file.getName()), e);
            return null;
        }
    }
}
