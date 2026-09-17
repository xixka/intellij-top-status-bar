package com.xixka.topstatusbar.items;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * 语言服务: display name of the language of the currently edited file.
 */
public final class LanguageItem extends CurrentFileItem {

    public LanguageItem() {
        super("languageService", 60);
    }

    @Override
    protected void update() {
        Project project = project();
        Editor editor = EditorContext.selectedEditor(project);
        PsiFile psiFile = EditorContext.psiFile(project, editor);
        if (psiFile == null) {
            setVisible(false);
            return;
        }
        Language language = psiFile.getLanguage();
        String displayName = language.getDisplayName();
        if (displayName == null) {
            displayName = language.getID();
        }
        setText(displayName);
        setTooltip("语言服务：" + displayName);
        setVisible(true);
    }
}
