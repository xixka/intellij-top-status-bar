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
import java.util.List;
import java.util.Map;

/**
 * Settings page: Settings | Appearance &amp; Behavior | Top Status Bar.
 * <p>
 * Hybrid visibility model (2026-09-20, cross-version robust):
 * <ul>
 * <li>The 11 items mirroring built-in IDE widgets (行列号/文件编码/Git 分支…)
 * follow the native "Status Bar Widgets" menu (View | Appearance | Status
 * Bar Widgets) — that menu's checkboxes are exactly what the top bar shows.
 * They are deliberately NOT configurable here: a second, competing set of
 * toggles only misleads.</li>
 * <li>The 6 plugin-specific items (当前项目/文件同步/CodeBuddy/聚合器/
 * 网络位置/默认部署服务器) are configured here. They never register
 * native widget factories — 2026.x synthesizes a second, independently-
 * stored set of menu entries for every classic factory, which duplicated
 * the menu and made half the toggles ineffective (2026-09-20 incident).
 * This page works identically on every supported platform version.
 * fileSystemSync 曾短暂镜像平台 VfsRefresh 工厂（2026-09-21 上午），
 * 当日实测 2026.1 把该微件勾选写入前端独立存储（插件不可读）而回退。</li>
 * </ul>
 * The page also keeps the global switch and the item content settings
 * (deploy server label, CodeBuddy label).
 */
public final class TopStatusBarConfigurable implements Configurable {

    /**
     * The plugin-specific items configured on this page (ids never change).
     * fileSystemSync 在本页（2026-09-21 回退）：其镜像对象 VfsRefresh 的
     * 原生菜单勾选在 2026.1 落入前端独立存储，经典存储读不到任何变化，
     * 镜像无法实现「原生菜单控制顶栏」，只有本页开关全版本可靠。
     */
    private static final List<String> OWN_ITEM_IDS =
            List.of("statusText", "fileSystemSync", "codeBuddy", "aggregator",
                    "networkLocation", "deployServer");

    private final Project project;

    private JPanel panel;
    private JBCheckBox enabledCheckBox;
    private final Map<String, JBCheckBox> ownItemCheckBoxes = new LinkedHashMap<>();
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
        builder.addVerticalGap(8);
        builder.addComponent(new JBLabel("插件自有状态项（勾选即显示，取消即隐藏，全版本一致）："));
        ownItemCheckBoxes.clear();
        for (String itemId : OWN_ITEM_IDS) {
            JBCheckBox checkBox = new JBCheckBox(
                    StatusItems.displayNames().getOrDefault(itemId, itemId),
                    settings.isItemEnabled(itemId));
            ownItemCheckBoxes.put(itemId, checkBox);
            builder.addComponent(checkBox);
        }
        builder.addVerticalGap(8);
        builder.addComponent(new JBLabel("镜像平台原生微件的 11 项（行列号、语言服务、行分隔符、文件编码、省电模式、"
                + "编辑器选择模式、缩进、JSON 架构、Git 分支、只读特性、内存指示器）由菜单"
                + "「视图 → 外观 → 状态栏微件」勾选控制，与底部状态栏原生行为一致。"));
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
        for (Map.Entry<String, JBCheckBox> entry : ownItemCheckBoxes.entrySet()) {
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
        for (Map.Entry<String, JBCheckBox> entry : ownItemCheckBoxes.entrySet()) {
            settings.setItemEnabled(entry.getKey(), entry.getValue().isSelected());
        }
        settings.setDeployServer(text(deployServerField));
        settings.setCodeBuddyLabel(text(codeBuddyField));
        DebugLog.log("设置页 apply: enabled=" + enabledCheckBox.isSelected()
                + ", 自有项=" + ownItemCheckBoxes
                + ", deployServer=\"" + text(deployServerField) + "\""
                + ", codeBuddy=\"" + text(codeBuddyField) + "\" → 触发 reload"
                + "（镜像原生微件的 11 项仍由原生「状态栏微件」菜单决定）");
        TopStatusBarManager.getInstance(project).reload();
    }

    @Override
    public void reset() {
        TopStatusBarSettings settings = TopStatusBarSettings.getInstance(project);
        if (enabledCheckBox == null) {
            return;
        }
        enabledCheckBox.setSelected(settings.isEnabled());
        for (Map.Entry<String, JBCheckBox> entry : ownItemCheckBoxes.entrySet()) {
            entry.getValue().setSelected(settings.isItemEnabled(entry.getKey()));
        }
        deployServerField.setText(nullToEmpty(settings.getDeployServer()));
        codeBuddyField.setText(nullToEmpty(settings.getCodeBuddyLabel()));
    }

    @Override
    public void disposeUIResources() {
        panel = null;
        enabledCheckBox = null;
        ownItemCheckBoxes.clear();
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
