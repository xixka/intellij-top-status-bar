package com.xixka.topstatusbar.items;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

/**
 * 语言服务: display name of the language of the currently edited file.
 */
public final class LanguageItem extends CurrentFileItem {

    public LanguageItem() {
        super("languageService", 60);
    }

    /**
     * 原生「状态栏微件」菜单「语言服务」条目的工厂 id（平台自 241 起注册，
     * 262 核实 id 未变）。顶栏读取该菜单勾选状态决定显隐。
     */
    @Override
    public @Nullable String getPlatformWidgetId() {
        return "LanguageServiceStatusBarWidget";
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
