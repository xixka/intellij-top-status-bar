package com.xixka.topstatusbar.items;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.FileIndentOptionsProvider;
import com.intellij.psi.codeStyle.IndentStatusBarUIContributor;
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier;
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor;
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings;
import com.intellij.psi.codeStyle.statusbar.CodeStyleStatusBarWidgetFactory;
import com.intellij.ui.awt.RelativePoint;
import com.xixka.topstatusbar.DebugLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 缩进: tab / space indent effective for the current file.
 * Clicking opens the same indent menu as the native CodeStyleStatusBarWidget
 * (动作清单逐一对应平台 CodeStyleStatusBarWidget.getActions，241 GA 源码核实)。
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

    /**
     * Mirrors the platform CodeStyleStatusBarWidget.createPopup (241 GA
     * sources): contributor actions first, then "为 &lt;语言&gt; 配置缩进…",
     * then the contributor's disable / show-all actions; the popup title is
     * the contributor's action group title (e.g. “缩进检测” for the indent
     * detection contributor shown in the user's screenshot).
     */
    @Override
    public void onClick(@Nullable Project project, @NotNull JComponent source) {
        Project effective = project != null ? project : project();
        Editor editor = EditorContext.selectedEditor(effective);
        PsiFile psiFile = EditorContext.psiFile(effective, editor);
        if (effective == null || psiFile == null) {
            DebugLog.log("indent onClick: 无当前文件，不弹缩进菜单");
            return;
        }
        CommonCodeStyleSettings.IndentOptions indentOptions = CodeStyle.getIndentOptions(psiFile);
        CodeStyleStatusBarUIContributor contributor = findUiContributor(psiFile, indentOptions);

        List<AnAction> actions = new ArrayList<>();
        if (contributor != null) {
            AnAction[] contributorActions = contributor.getActions(psiFile);
            if (contributorActions != null) {
                Collections.addAll(actions, contributorActions);
            }
        }
        if (contributor == null
                || contributor instanceof IndentStatusBarUIContributor
                        && ((IndentStatusBarUIContributor) contributor).isShowFileIndentOptionsEnabled()) {
            actions.add(CodeStyleStatusBarWidgetFactory.createDefaultIndentConfigureAction(psiFile));
        }
        if (contributor != null) {
            AnAction disableAction = contributor.createDisableAction(effective);
            if (disableAction != null) {
                actions.add(disableAction);
            }
            AnAction showAllAction = contributor.createShowAllAction(effective);
            if (showAllAction != null) {
                actions.add(showAllAction);
            }
        }
        if (actions.isEmpty()) {
            DebugLog.log("indent onClick: 无可用动作，不弹缩进菜单");
            return;
        }
        String title = contributor == null ? null : contributor.getActionGroupTitle();
        ActionGroup group = new ActionGroup() {
            @Override
            public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
                return actions.toArray(AnAction.EMPTY_ARRAY);
            }
        };
        DebugLog.log("indent onClick: 弹出缩进菜单，动作数=" + actions.size()
                + ", contributor=" + (contributor == null ? "无" : contributor.getClass().getSimpleName()));
        JBPopupFactory.getInstance()
                .createActionGroupPopup(title, group,
                        DataManager.getInstance().getDataContext(source),
                        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false)
                .show(new RelativePoint(source, new Point(source.getWidth() / 2, source.getHeight())));
    }

    /**
     * Same resolution as CodeStyleStatusBarWidget: a transient-settings
     * modifier wins; otherwise the provider recorded on the indent options;
     * otherwise the first EP provider whose contributor has actions for the
     * file.
     */
    @Nullable
    private static CodeStyleStatusBarUIContributor findUiContributor(
            @NotNull PsiFile psiFile, @NotNull CommonCodeStyleSettings.IndentOptions indentOptions) {
        CodeStyleSettings settings = CodeStyle.getSettings(psiFile);
        if (settings instanceof TransientCodeStyleSettings) {
            TransientCodeStyleSettings transientSettings = (TransientCodeStyleSettings) settings;
            CodeStyleSettingsModifier modifier = transientSettings.getModifier();
            return modifier == null ? null : modifier.getStatusBarUiContributor(transientSettings);
        }
        VirtualFile file = psiFile.getVirtualFile();
        FileIndentOptionsProvider provider = indentOptions.getFileIndentOptionsProvider();
        if (provider == null && file != null) {
            for (FileIndentOptionsProvider candidate : FileIndentOptionsProvider.EP_NAME.getExtensionList()) {
                CodeStyleStatusBarUIContributor candidateContributor =
                        candidate.getIndentStatusBarUiContributor(indentOptions);
                if (candidateContributor != null && candidateContributor.areActionsAvailable(file)) {
                    provider = candidate;
                    break;
                }
            }
        }
        return provider == null ? null : provider.getIndentStatusBarUiContributor(indentOptions);
    }
}
