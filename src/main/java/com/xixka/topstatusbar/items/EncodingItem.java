package com.xixka.topstatusbar.items;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.ChangeFileEncodingAction;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * 文件编码: charset of the currently edited file.
 * <p>
 * 点击弹出与原生 EncodingPanel 完全相同的编码菜单（233→master 源码核实）：
 * 直接调用平台 {@link ChangeFileEncodingAction#createPopup}——「添加 BOM」、
 * 常用编码（含异常图标）、「更多」子菜单与勾选态、切换写入全部是平台
 * 原生实现，不再自拼 charset 列表。
 */
public final class EncodingItem extends CurrentFileItem {

    public EncodingItem() {
        super("encoding", 80);
    }

    @Override
    public @Nullable String getPlatformWidgetId() {
        return "Encoding";
    }

    @Override
    protected void update() {
        Editor editor = EditorContext.selectedEditor(project());
        VirtualFile file = EditorContext.virtualFile(editor);
        if (file == null) {
            setVisible(false);
            return;
        }
        String charsetName = file.getCharset().displayName();
        setText(charsetName);
        setTooltip("文件编码：" + charsetName + "（点击切换）");
        setVisible(true);
    }

    @Override
    public void onClick(@Nullable Project project, @NotNull JComponent source) {
        Project effective = project != null ? project : project();
        Editor editor = EditorContext.selectedEditor(effective);
        VirtualFile file = EditorContext.virtualFile(editor);
        if (file == null) {
            return;
        }
        // 原生 EncodingPanel.createPopup 同款：动作 + EncodingPanelActions 扩展组
        ChangeFileEncodingAction action = new ChangeFileEncodingAction();
        action.getTemplatePresentation().setText(
                IdeBundle.messagePointer("action.presentation.EncodingPanel.text"));
        ActionGroup extraActions = (ActionGroup) ActionManager.getInstance().getAction("EncodingPanelActions");
        // 顶栏在下方弹出（原生底栏上方弹的镜像）
        ListPopup popup = action.createPopup(
                EditorContext.popupContext(editor, source), extraActions);
        if (popup != null) {
            popup.show(new RelativePoint(source, new Point(0, source.getHeight())));
        }
    }
}
