package com.xixka.topstatusbar.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import com.xixka.topstatusbar.DebugLog;
import com.xixka.topstatusbar.TopStatusBarManager;
import com.xixka.topstatusbar.model.StatusItems;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Settings page: Settings | Appearance &amp; Behavior | Top Status Bar.
 * Toggles individual status items, the global switch and the custom labels.
 */
public final class TopStatusBarConfigurable implements Configurable {

    private final Project project;

    private JPanel panel;
    private JBCheckBox enabledCheckBox;
    private JTextField deployServerField;
    private JTextField codeBuddyField;
    private final Map<String, JBCheckBox> itemCheckBoxes = new LinkedHashMap<>();

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
        builder.addComponent(new JBLabel("状态项（取消勾选可隐藏）："));
        for (Map.Entry<String, String> entry : StatusItems.displayNames().entrySet()) {
            JBCheckBox checkBox = new JBCheckBox(entry.getValue(), settings.isItemEnabled(entry.getKey()));
            itemCheckBoxes.put(entry.getKey(), checkBox);
            builder.addComponent(checkBox);
        }
        builder.addVerticalGap(4);
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
        for (Map.Entry<String, JBCheckBox> entry : itemCheckBoxes.entrySet()) {
            if (entry.getValue().isSelected() != settings.isItemEnabled(entry.getKey())) {
                return true;
            }
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
        for (Map.Entry<String, JBCheckBox> entry : itemCheckBoxes.entrySet()) {
            settings.setItemEnabled(entry.getKey(), entry.getValue().isSelected());
        }
        settings.setDeployServer(text(deployServerField));
        settings.setCodeBuddyLabel(text(codeBuddyField));
        // 调试日志：设置页是顶栏显示的唯一真源，记录用户到底改了什么
        StringBuilder toggles = new StringBuilder();
        for (Map.Entry<String, JBCheckBox> entry : itemCheckBoxes.entrySet()) {
            if (toggles.length() > 0) {
                toggles.append(", ");
            }
            toggles.append(entry.getKey()).append('=').append(entry.getValue().isSelected());
        }
        DebugLog.log("设置页 apply: enabled=" + enabledCheckBox.isSelected()
                + ", deployServer=\"" + text(deployServerField) + "\""
                + ", codeBuddy=\"" + text(codeBuddyField) + "\""
                + ", 项开关=[" + toggles + "] → 触发 reload");
        TopStatusBarManager.getInstance(project).reload();
    }

    @Override
    public void reset() {
        TopStatusBarSettings settings = TopStatusBarSettings.getInstance(project);
        if (enabledCheckBox == null) {
            return;
        }
        enabledCheckBox.setSelected(settings.isEnabled());
        for (Map.Entry<String, JBCheckBox> entry : itemCheckBoxes.entrySet()) {
            entry.getValue().setSelected(settings.isItemEnabled(entry.getKey()));
        }
        deployServerField.setText(nullToEmpty(settings.getDeployServer()));
        codeBuddyField.setText(nullToEmpty(settings.getCodeBuddyLabel()));
    }

    @Override
    public void disposeUIResources() {
        panel = null;
        enabledCheckBox = null;
        deployServerField = null;
        codeBuddyField = null;
        itemCheckBoxes.clear();
    }

    private static String text(@Nullable JTextField field) {
        return field == null || field.getText() == null ? "" : field.getText().trim();
    }

    private static String nullToEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }
}
