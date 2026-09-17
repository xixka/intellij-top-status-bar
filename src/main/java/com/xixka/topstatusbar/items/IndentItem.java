package com.xixka.topstatusbar.items;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jetbrains.annotations.Nullable;

/**
 * 缩进: tab / space indent effective for the current file.
 */
public final class IndentItem extends CurrentFileItem {

    public IndentItem() {
        super("indent", 65);
    }

    @Override
    public @Nullable String getPlatformWidgetId() {
        return "CodeStyleStatusBarWidget";
    }

    @Override
    protected void update() {
        Project project = project();
        Editor editor = EditorContext.selectedEditor(project);
        PsiFile psiFile = EditorContext.psiFile(project, editor);
        if (project == null || psiFile == null) {
            setVisible(false);
            return;
        }
        CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project);
        var options = settings == null ? null : settings.getIndentOptionsByFile(psiFile);
        if (options == null) {
            setVisible(false);
            return;
        }
        String text = options.USE_TAB_CHARACTER
                ? "Tab 缩进"
                : options.INDENT_SIZE + " 空格缩进";
        setText(text);
        setTooltip("缩进：" + text);
        setVisible(true);
    }
}
