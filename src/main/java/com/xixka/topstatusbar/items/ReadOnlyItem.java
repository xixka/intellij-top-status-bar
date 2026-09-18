package com.xixka.topstatusbar.items;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.io.ReadOnlyAttributeUtil;
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
            return;
        }
        // Same behavior as the native read-only attribute widget: save
        // everything, then flip the file read-only attribute on disk.
        // mouseClicked is a plain Swing callback without the write-intent
        // lock (unlike IDE action updates), so saving documents must be
        // wrapped in WriteIntentReadAction — platform threading assert.
        // The (Runnable) cast disambiguates run(Runnable) vs
        // run(ThrowableRunnable), both accept a void lambda.
        WriteIntentReadAction.run((Runnable) () -> FileDocumentManager.getInstance().saveAllDocuments());
        // The attribute flip must NOT run as a blocking write action on the
        // EDT (thread dump 2026-09-18): while the write lock is contended
        // (e.g. a VCS repository scan in progress), a blocking EDT write
        // froze the whole IDE. WriteAction.run is legal from any thread and
        // the disk flip needs no UI access, so run it on a pooled thread.
        boolean writable = file.isWritable();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                WriteAction.run((ThrowableRunnable<IOException>) () ->
                        ReadOnlyAttributeUtil.setReadOnlyAttribute(file, writable));
            } catch (IOException e) {
                ApplicationManager.getApplication().invokeLater(() ->
                        Messages.showErrorDialog(project, e.getMessage(), "无法切换只读属性"));
            }
            ApplicationManager.getApplication().invokeLater(this::update);
        });
    }
}

