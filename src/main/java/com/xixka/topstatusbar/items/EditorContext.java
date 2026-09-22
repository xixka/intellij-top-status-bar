package com.xixka.topstatusbar.items;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Shared helpers for resolving the current editor / file / PSI file.
 */
final class EditorContext {

    private EditorContext() {
    }

    @Nullable
    static Editor selectedEditor(@Nullable Project project) {
        if (project == null || project.isDisposed()) {
            return null;
        }
        return FileEditorManager.getInstance(project).getSelectedTextEditor();
    }

    @Nullable
    static VirtualFile virtualFile(@Nullable Editor editor) {
        if (editor == null) {
            return null;
        }
        return FileDocumentManager.getInstance().getFile(editor.getDocument());
    }

    @Nullable
    static Document document(@Nullable Editor editor) {
        return editor == null ? null : editor.getDocument();
    }

    @Nullable
    static PsiFile psiFile(@Nullable Project project, @Nullable Editor editor) {
        if (project == null || project.isDisposed() || editor == null) {
            return null;
        }
        return PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    }

    /**
     * 与原生 {@code EditorBasedStatusBarPopup.context} 相同的解析顺序：
     * 编辑器存在 → {@link EditorUtil#getEditorDataContext}（原生状态栏微件
     * 弹出菜单的动作据此取 VIRTUAL_FILE/PSI_FILE 等作用于当前文件的数据）；
     * 否则回退组件树数据上下文。
     */
    static DataContext popupContext(@Nullable Editor editor, @NotNull JComponent source) {
        return editor != null
                ? EditorUtil.getEditorDataContext(editor)
                : DataManager.getInstance().getDataContext(source);
    }
}

