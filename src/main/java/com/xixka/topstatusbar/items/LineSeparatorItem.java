package com.xixka.topstatusbar.items;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.UIBundle;
import com.intellij.ui.awt.RelativePoint;
import com.xixka.topstatusbar.DebugLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * 行分隔符: LF / CRLF / CR, detected from the raw bytes of the current file.
 * Detection runs on a background thread to avoid blocking the EDT.
 * <p>
 * 点击弹出与原生 LineSeparatorPanel 完全一致的动作组菜单：直接复用平台
 * 注册的 {@code ChangeLineSeparators} Action 组（含每个分隔符的完整描述、
 * 勾选态与写入逻辑），标题取 {@code UIBundle
 * status.bar.line.separator.widget.name}（233→2026.x 源码核实，各版一致），
 * 不再自拼条目列表。上下文与原生 EditorBasedStatusBarPopup 相同：
 * 编辑器数据上下文优先，无编辑器时回退组件树上下文。
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
        AnAction group = ActionManager.getInstance().getAction("ChangeLineSeparators");
        if (!(group instanceof ActionGroup)) {
            DebugLog.warn("lineSeparator onClick: 平台未注册 ChangeLineSeparators 动作组，不弹菜单");
            return;
        }
        // 原生 LineSeparatorPanel.createPopup 同款：动作组 + 标题 + SPEEDSEARCH
        ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
                UIBundle.message("status.bar.line.separator.widget.name"),
                (ActionGroup) group,
                editorContext(editor, source),
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                false);
        // 原生底栏在组件上方弹出（Point(0,-h)）；顶栏镜像为下方，锚点同为左对齐
        popup.show(new RelativePoint(source, new Point(0, source.getHeight())));
    }

    /**
     * 与原生 {@code EditorBasedStatusBarPopup.context} 相同的解析顺序：
     * 编辑器存在 → {@link EditorUtil#getEditorDataContext}（动作据此取
     * VIRTUAL_FILE/PSI_FILE 等作用于当前文件）；否则回退组件树数据上下文。
     */
    static DataContext editorContext(@Nullable Editor editor, @NotNull JComponent source) {
        return editor != null
                ? EditorUtil.getEditorDataContext(editor)
                : DataManager.getInstance().getDataContext(source);
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
