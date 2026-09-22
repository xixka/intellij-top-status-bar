package com.xixka.topstatusbar.items;

import com.intellij.lang.LangBundle;
import com.intellij.lang.Language;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.util.ShowSettingsUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.ide.DataManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.lang.lsWidget.LanguageServicePopupSection;
import com.intellij.platform.lang.lsWidget.LanguageServiceWidgetItem;
import com.intellij.platform.lang.lsWidget.LanguageServiceWidgetItemsProvider;
import com.intellij.psi.PsiFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.JBDimension;
import com.xixka.topstatusbar.DebugLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 语言服务: display name of the language of the currently edited file.
 * 点击弹出与原生「语言服务」微件相同的服务清单弹窗（数据来自平台公开 EP
 * {@code com.intellij.platform.lang.lsWidget.itemsProvider}，lang-api，
 * 社区版编译目标可用；241 GA 源码核实 LanguageServiceWidget 的弹窗构成）。
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

    /**
     * 与原生 LanguageServiceWidget 弹窗逐项一致（241 GA 源码核实
     * createPopup/createActionGroup）：标题与两个分段标签均取 LangBundle
     * 原生文案；当前文件段为空时放置灰的「无语言服务」占位项；
     * 逐项 createWidgetAction()（自带错误角标与内联动作）。
     * provider 查询可能触及 LSP 状态，放后台线程收集后回 EDT 弹窗。
     */
    @Override
    public void onClick(@Nullable Project project, @NotNull JComponent source) {
        Project effective = project != null ? project : project();
        if (effective == null) {
            DebugLog.log("languageService onClick: 项目为空，不弹菜单");
            return;
        }
        Editor editor = EditorContext.selectedEditor(effective);
        VirtualFile file = EditorContext.virtualFile(editor);
        DebugLog.log("languageService onClick: 开始收集语言服务项(file="
                + (file == null ? "null" : file.getName()) + ")");
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<LanguageServiceWidgetItem> currentFileItems = new ArrayList<>();
            List<LanguageServiceWidgetItem> otherItems = new ArrayList<>();
            try {
                for (LanguageServiceWidgetItemsProvider provider
                        : LanguageServiceWidgetItemsProvider.Companion.getEP_NAME().getExtensionList()) {
                    for (LanguageServiceWidgetItem item : provider.createWidgetItems(effective, file)) {
                        if (item.getWidgetActionLocation() == LanguageServicePopupSection.ForCurrentFile) {
                            currentFileItems.add(item);
                        } else {
                            otherItems.add(item);
                        }
                    }
                }
            } catch (Throwable t) {
                DebugLog.warn("languageService onClick: 收集语言服务项失败", t);
            }
            ApplicationManager.getApplication().invokeLater(() -> {
                if (effective.isDisposed()) {
                    return;
                }
                DefaultActionGroup group = new DefaultActionGroup();
                group.addSeparator(LangBundle.message(
                        "language.services.widget.section.running.on.current.file"));
                if (currentFileItems.isEmpty()) {
                    AnAction noServices = DumbAwareAction.create(
                            LangBundle.message("language.services.widget.no.services"),
                            e -> { /* 置灰占位项，无操作 */ });
                    noServices.getTemplatePresentation().setEnabled(false);
                    group.add(noServices);
                } else {
                    for (LanguageServiceWidgetItem item : currentFileItems) {
                        group.add(item.createWidgetAction());
                    }
                }
                group.addSeparator(LangBundle.message(
                        "language.services.widget.section.running.on.other.files"));
                for (LanguageServiceWidgetItem item : otherItems) {
                    group.add(item.createWidgetAction());
                }
                // 原生末尾：分隔线 + 「更多语言…」（跳插件市场 Language Server 标签，
                // MoreLanguagesAction 同款）
                group.addSeparator();
                group.add(DumbAwareAction.create(
                        LangBundle.message("language.services.widget.more.languages"),
                        e -> ShowSettingsUtil.getInstance().showSettingsDialog(
                                effective, PluginManagerConfigurable.class,
                                configurable -> configurable.navigateToMarketplace("/tag:\"Language Server\""))));
                DebugLog.log("languageService onClick: 弹出语言服务菜单，当前文件项="
                        + currentFileItems.size() + ", 其他项=" + otherItems.size());
                ListPopup popup = JBPopupFactory.getInstance()
                        .createActionGroupPopup(LangBundle.message("language.services.widget"), group,
                                DataManager.getInstance().getDataContext(source),
                                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true);
                popup.setMinimumSize(new JBDimension(300, 1));
                popup.show(new RelativePoint(source, new Point(source.getWidth() / 2, source.getHeight())));
            });
        });
    }
}
