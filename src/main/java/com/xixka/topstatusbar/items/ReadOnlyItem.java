package com.xixka.topstatusbar.items;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.io.ReadOnlyAttributeUtil;
import com.xixka.topstatusbar.DebugLog;
import com.xixka.topstatusbar.model.StatusSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;

/**
 * 只读特性: mirrors the native read-only attribute widget — an icon-only
 * cell that always shows while a regular file is open: a pencil when the
 * file is writable, a padlock when it is read-only. Clicking toggles the
 * file read-only attribute, exactly like the native widget.
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

    @Override
    public void onClick(@Nullable Project project, @NotNull JComponent source) {
        Editor editor = EditorContext.selectedEditor(project != null ? project : project());
        VirtualFile file = EditorContext.virtualFile(editor);
        if (file == null || file.getFileSystem().isReadOnly()) {
            DebugLog.log("readOnly onClick: 无可切换文件"
                    + (file == null ? "（file=null）" : "（只读文件系统）") + "，忽略");
            return;
        }
        DebugLog.log("readOnly onClick: file=" + file.getName()
                + ", 当前可写=" + file.isWritable() + "，保存后切换磁盘属性");
        // 与原生 ToggleReadOnlyAttributePanel.getClickConsumer 一致（241 GA
        // 源码核实）：保存全部文档后直接在 EDT 上 WriteAction 翻转磁盘属性。
        // 2026.1 起 WriteAction.run 仅允许 EDT（ThreadingAssertions），此前
        // 「pooled 线程 WriteAction.run」的写法在 2026.1 运行时直接抛
        // RuntimeExceptionWithAttachments（2026-09-21 用户堆栈）。
        // mouseClicked 是裸 Swing 回调、不持 write-intent 锁（与 IDE action
        // update 不同），保存文档必须包 WriteIntentReadAction；(Runnable) 强转
        // 用于区分 run(Runnable) 与 run(ThrowableRunnable) 两个重载。
        WriteIntentReadAction.run((Runnable) () -> FileDocumentManager.getInstance().saveAllDocuments());
        boolean writable = file.isWritable();
        try {
            WriteAction.run((ThrowableRunnable<IOException>) () ->
                    ReadOnlyAttributeUtil.setReadOnlyAttribute(file, writable));
            DebugLog.log("readOnly 磁盘属性切换完成: file=" + file.getName()
                    + ", 切换为只读=" + writable);
        } catch (IOException e) {
            DebugLog.warn("readOnly 磁盘属性切换失败: file=" + file.getName(), e);
            Messages.showErrorDialog(project, e.getMessage(), "无法切换只读属性");
        }
        update();
    }
}

