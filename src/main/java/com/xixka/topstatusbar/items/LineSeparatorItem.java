package com.xixka.topstatusbar.items;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFile;
import com.xixka.topstatusbar.model.StatusSeverity;
import org.jetbrains.annotations.Nullable;

/**
 * 行分隔符: LF / CRLF / CR, detected from the raw bytes of the current file.
 * Detection runs on a background thread to avoid blocking the EDT.
 */
public final class LineSeparatorItem extends CurrentFileItem {

    public LineSeparatorItem() {
        super("lineSeparator", 70);
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
        } catch (java.io.IOException ignored) {
            return null;
        }
    }
}
