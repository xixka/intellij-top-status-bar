package com.xixka.topstatusbar.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import com.xixka.topstatusbar.DebugLog;
import com.xixka.topstatusbar.TopStatusBarManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Settings page: Settings | Appearance &amp; Behavior | Top Status Bar.
 * <p>
 * Per-item visibility is deliberately NOT configured here anymore
 * (2026-09-20): the native "Status Bar Widgets" menu (View | Appearance |
 * Status Bar Widgets, or right-click the bottom status bar) is the single
 * source of truth for which items the top bar shows — its checkboxes are
 * exactly what the top bar mirrors. Keeping a second, competing set of
 * per-item toggles here only misleads (the earlier "everything shows"
 * confusion came from this page being treated as the visibility source).
 * <p>
 * This page keeps the global switch and the item content settings (deploy
 * server label, CodeBuddy label).
 */
public final class TopStatusBarConfigurable implements Configurable {

    private final Project project;

    private JPanel panel;
    private JBCheckBox enabledCheckBox;
    private JTextField deployServerField;
    private JTextField codeBuddyField;

    public TopStatusBarConfigurable(@NotNull Project project) {
        this.project = project;
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Top Status Bar";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        TopStatusBarSettings settings = TopStatusBarSettings.getInstance(project);
        FormBuilder builder = FormBuilder.createFormBuilder();
        enabledCheckBox = new JBCheckBox("启用 Top Status Bar 状态项", settings.isEnabled());
        builder.addComponent(enabledCheckBox);
        builder.addVerticalGap(4);
        builder.addComponent(new JBLabel("显示哪些状态项由菜单「视图 → 外观 → 状态栏微件」逐项勾选决定：勾选即显示，取消即隐藏。"));
        builder.addVerticalGap(8);
        deployServerField = new JTextField(nullToEmpty(settings.getDeployServer()));
        builder.addLabeledComponent("默认部署服务器：", deployServerField);
        codeBuddyField = new JTextField(nullToEmpty(settings.getCodeBuddyLabel()));
        builder.addLabeledComponent("CodeBuddy 标签：", codeBuddyField);
        panel = builder.getPanel();
        return panel;
    }

    @Override
    public boolean isModified() {
        TopStatusBarSettings settings = TopStatusBarSettings.getInstance(project);
        if (enabledCheckBox == null) {
            return false;
        }
        if (enabledCheckBox.isSelected() != settings.isEnabled()) {
            return true;
        }
        if (!text(deployServerField).equals(nullToEmpty(settings.getDeployServer()))) {
            return true;
        }
        return !text(codeBuddyField).equals(nullToEmpty(settings.getCodeBuddyLabel()));
    }

    @Override
    public void apply() {
        TopStatusBarSettings settings = TopStatusBarSettings.getInstance(project);
        settings.setEnabled(enabledCheckBox.isSelected());
        settings.setDeployServer(text(deployServerField));
        settings.setCodeBuddyLabel(text(codeBuddyField));
        DebugLog.log("设置页 apply: enabled=" + enabledCheckBox.isSelected()
                + ", deployServer=\"" + text(deployServerField) + "\""
                + ", codeBuddy=\"" + text(codeBuddyField) + "\" → 触发 reload"
                + "（逐项显示由原生「状态栏微件」菜单决定，设置页不再参与）");
        TopStatusBarManager.getInstance(project).reload();
    }

    @Override
    public void reset() {
        TopStatusBarSettings settings = TopStatusBarSettings.getInstance(project);
        if (enabledCheckBox == null) {
            return;
        }
        enabledCheckBox.setSelected(settings.isEnabled());
        deployServerField.setText(nullToEmpty(settings.getDeployServer()));
        codeBuddyField.setText(nullToEmpty(settings.getCodeBuddyLabel()));
    }

    @Override
    public void disposeUIResources() {
        panel = null;
        enabledCheckBox = null;
        deployServerField = null;
        codeBuddyField = null;
    }

    private static String text(@Nullable JTextField field) {
        return field == null || field.getText() == null ? "" : field.getText().trim();
    }

    private static String nullToEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }
}
