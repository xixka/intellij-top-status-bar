package com.xixka.topstatusbar.items;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

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
}
