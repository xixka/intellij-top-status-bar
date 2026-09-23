package com.xixka.topstatusbar.items;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
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
 * <p>
 * 点击弹出与原生 CodeStyleStatusBarWidget 完全相同的缩进菜单
 * （233/241/master 三版源码核实）：动作清单 = contributor 动作 +
 * 「为 &lt;语言&gt; 配置缩进…」+ contributor 的禁用/显示全部动作；
 * contributor 解析对齐 master（2026-09-22 修复）：编辑器瞬态设置
 * {@link #EDITOR_CODE_STYLE_SETTINGS} 优先，modifier 无 contributor 时
 * <em>回退</em> FileIndentOptionsProvider 路径——此前缺失该回退导致
 * 2026.1 上菜单无标题、无「禁用缩进检测」（DetectableIndentSettingsModifier
 * .getStatusBarUiContributor 恒返 null，原生同版靠回退拿到「缩进检测」
 * contributor）。
 * <p>
 * 文本与工具提示复刻原生 createWidgetState：有 UI contributor 时取
 * contributor.getStatusText / getTooltip（缩进检测生效时即官方
 * 「4 个空格」样式），否则回退 IndentStatusBarUIContributor.
 * getIndentInfo / createTooltip。
 */
public final class IndentItem extends CurrentFileItem {

    /**
     * 与 {@code EditorImpl.CODE_STYLE_SETTINGS}（2026.x 起 2026.1 实测存在，
     * 241 编译基线不存在该常量）同一个键——按键名取同一实例，避免编译期
     * 依赖 platform-impl 的 EditorImpl；旧版本上无人写入该键，恒为 null。
     */
    private static final Key<CodeStyleSettings> EDITOR_CODE_STYLE_SETTINGS =
            Key.create("editor.code.style.settings");

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
        CommonCodeStyleSettings.IndentOptions options = effectiveIndentOptions(editor, psiFile);
        if (options == null) {
            setVisible(false);
            return;
        }
        CodeStyleStatusBarUIContributor contributor = findUiContributor(editor, psiFile, options);
        String text;
        String tooltip;
        if (contributor != null) {
            text = contributor.getStatusText(psiFile);
            tooltip = contributor.getTooltip();
        } else {
            text = IndentStatusBarUIContributor.getIndentInfo(options);
            tooltip = IndentStatusBarUIContributor.createTooltip(text, null);
        }
        setText(text);
        setTooltip(tooltip != null ? tooltip : text);
        setVisible(true);
    }

    /**
     * Mirrors the platform CodeStyleStatusBarWidget.createPopup (233/241/
     * master sources): contributor actions first, then "为 &lt;语言&gt; 配置缩进…",
     * then the contributor's disable / show-all actions; the popup title is
     * the contributor's action group title (e.g. “缩进检测”), and no title
     * when no contributor answers — matching the released 241~2026.x native
     * builds (the master-only language-name fallback key ships from 2025.3,
     * see the note below).
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
        CommonCodeStyleSettings.IndentOptions indentOptions = effectiveIndentOptions(editor, psiFile);
        if (indentOptions == null) {
            DebugLog.log("indent onClick: 无缩进选项，不弹缩进菜单");
            return;
        }
        CodeStyleStatusBarUIContributor contributor = findUiContributor(editor, psiFile, indentOptions);

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
                        EditorContext.popupContext(editor, source),
                        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false)
                .show(new RelativePoint(source, new Point(0, source.getHeight())));
    }

    /**
     * 语言名回退标题已移除（v1.0.2 Plugin Verifier 实测）：master 的
     * {@code ApplicationBundle.message("code.style.language.settings.indent.provider", …)}
     * 键只在 2025.3+ 已发布版本出货（241~252 全部缺失，Verifier 对
     * IC-233~252 逐一报 missing property 兼容性错误；且 ApplicationBundle
     * 与 LangBundle 同属 DynamicBundle 系，缺键不抛 MissingResourceException
     * 而返回 "!键名!" 占位，原 catch 降级是死代码）。无 contributor 时
     * 弹窗无标题——与 241~252 原生行为一致；待最低支持版本 ≥ 2025.3 时
     * 可恢复该标题（届时键必有）。
     */

    /**
     * Master getWidgetState 同款 settings 解析：编辑器瞬态设置优先（键同
     * EditorImpl.CODE_STYLE_SETTINGS），否则 CodeStyle.getSettings(psiFile)；
     * 缩进选项从该 settings 上取（瞬态生效时即检测出的缩进）。
     */
    @Nullable
    private static CommonCodeStyleSettings.IndentOptions effectiveIndentOptions(
            @Nullable Editor editor, @NotNull PsiFile psiFile) {
        CodeStyleSettings settings = effectiveSettings(editor, psiFile);
        return settings == null ? null : settings.getIndentOptionsByFile(psiFile);
    }

    @Nullable
    private static CodeStyleSettings effectiveSettings(
            @Nullable Editor editor, @NotNull PsiFile psiFile) {
        CodeStyleSettings settings = editor == null ? null : editor.getUserData(EDITOR_CODE_STYLE_SETTINGS);
        return settings != null ? settings : CodeStyle.getSettings(psiFile);
    }

    /**
     * Same resolution as the platform widget (master sources, 2026-09-22
     * fix): a transient-settings modifier's contributor wins when present;
     * <em>otherwise</em> fall through to the provider path — the recorded
     * provider on the indent options, else the first EP provider whose
     * contributor has actions for the file. The missing fall-through was why
     * 2026.1 showed an untitled menu without「禁用缩进检测」.
     */
    @Nullable
    private static CodeStyleStatusBarUIContributor findUiContributor(
            @Nullable Editor editor, @NotNull PsiFile psiFile,
            @NotNull CommonCodeStyleSettings.IndentOptions indentOptions) {
        CodeStyleSettings settings = effectiveSettings(editor, psiFile);
        if (settings instanceof TransientCodeStyleSettings) {
            TransientCodeStyleSettings transientSettings = (TransientCodeStyleSettings) settings;
            CodeStyleSettingsModifier modifier = transientSettings.getModifier();
            if (modifier != null) {
                CodeStyleStatusBarUIContributor fromModifier =
                        modifier.getStatusBarUiContributor(transientSettings);
                if (fromModifier != null) {
                    return fromModifier;
                }
            }
            // fall through：modifier 无 contributor（2026.1 缩进检测即如此）
            // → 走 provider 路径，与原生 master getWidgetState 一致
        }
        return findProviderContributor(psiFile.getVirtualFile(), indentOptions);
    }

    @Nullable
    private static CodeStyleStatusBarUIContributor findProviderContributor(
            @Nullable VirtualFile file, @NotNull CommonCodeStyleSettings.IndentOptions indentOptions) {
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
