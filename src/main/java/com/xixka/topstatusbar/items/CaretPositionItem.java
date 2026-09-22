package com.xixka.topstatusbar.items;

import com.intellij.ide.util.EditorGotoLineNumberDialog;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.ui.UIBundle;
import com.intellij.util.messages.MessageBusConnection;
import com.xixka.topstatusbar.model.AbstractStatusItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * 行列号: caret line/column of the current editor, updated live via the
 * {@link EditorEventMulticaster}.
 */
public final class CaretPositionItem extends AbstractStatusItem {

    /** 原生 PositionPanel 常量：超过该字符数的选区同步计数降级为 "..."。 */
    private static final int CHAR_COUNT_SYNC_LIMIT = 500_000;
    private static final String CHAR_COUNT_UNKNOWN = "...";

    private final CaretListener caretListener = new CaretListener() {
        @Override
        public void caretPositionChanged(@NotNull CaretEvent event) {
            handleEvent(event.getEditor());
        }

        @Override
        public void caretAdded(@NotNull CaretEvent event) {
            handleEvent(event.getEditor());
        }

        @Override
        public void caretRemoved(@NotNull CaretEvent event) {
            handleEvent(event.getEditor());
        }
    };

    @Nullable
    private MessageBusConnection connection;

    public CaretPositionItem() {
        super("caretPosition", 100);
    }

    @Override
    public @Nullable String getPlatformWidgetId() {
        return "Position";
    }

    @Override
    protected void install() {
        EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();
        multicaster.addCaretListener(caretListener);
        Project project = project();
        if (project != null) {
            connection = project.getMessageBus().connect();
            connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
                @Override
                public void selectionChanged(@NotNull FileEditorManagerEvent event) {
                    update();
                }
            });
        }
        update();
    }

    @Override
    public void uninstall() {
        EditorFactory.getInstance().getEventMulticaster().removeCaretListener(caretListener);
        if (connection != null) {
            connection.dispose();
            connection = null;
        }
        super.uninstall();
    }

    @Override
    public void refresh() {
        update();
    }

    private void handleEvent(@Nullable Editor editor) {
        Project project = project();
        if (editor == null || project == null || editor.getProject() != project) {
            return;
        }
        updateFrom(editor);
    }

    private void update() {
        updateFrom(EditorContext.selectedEditor(project()));
    }

    private void updateFrom(@Nullable Editor editor) {
        if (editor == null) {
            setVisible(false);
            return;
        }
        // 与原生 PositionPanel.getPositionText 相同的格式：单光标 "line:column"，
        // 多光标 "N carets"，有选区时追加 "(N chars)"（UIBundle 键、半角括号、
        // codePointCount；超过 500k 同步上限时先显示 "..."，下次刷新替换）。
        LogicalPosition position = editor.getCaretModel().getLogicalPosition();
        int caretCount = editor.getCaretModel().getCaretCount();
        String text;
        if (caretCount > 1) {
            text = UIBundle.message("position.panel.caret.count", caretCount);
        } else {
            StringBuilder message = new StringBuilder();
            message.append(position.line + 1).append(':').append(position.column + 1);
            int selectionStart = editor.getCaretModel().getCurrentCaret().getSelectionStart();
            int selectionEnd = editor.getCaretModel().getCurrentCaret().getSelectionEnd();
            if (selectionEnd > selectionStart) {
                message.append(" (");
                if (selectionEnd - selectionStart < CHAR_COUNT_SYNC_LIMIT) {
                    int charCount = Character.codePointCount(
                            editor.getDocument().getImmutableCharSequence(), selectionStart, selectionEnd);
                    message.append(charCount).append(' ')
                            .append(UIBundle.message("position.panel.selected.chars.count", charCount));
                } else {
                    message.append(CHAR_COUNT_UNKNOWN).append(' ')
                            .append(UIBundle.message("position.panel.selected.chars.count", 2));
                }
                message.append(')');
            }
            text = message.toString();
        }
        setText(text);
        // 与原生一致的悬停提示：「转到行」标题 + GotoLine 快捷键（2026-09-22
        // 用户截图：两行式，标题加粗）。无快捷键时仅显示标题。
        String shortcut = KeymapUtil.getFirstKeyboardShortcutText("GotoLine");
        setTooltip(shortcut.isEmpty()
                ? UIBundle.message("go.to.line.command.name")
                : "<html><b>" + UIBundle.message("go.to.line.command.name") + "</b><br>" + shortcut + "</html>");
        setVisible(true);
    }

    @Override
    public void onClick(@Nullable Project project, @NotNull JComponent source) {
        Project effective = project != null ? project : project();
        Editor editor = EditorContext.selectedEditor(effective);
        if (effective == null || editor == null) {
            return;
        }
        // 原生 PositionPanel.getClickConsumer 同款（master 源码核实）：
        // 命令包装中打开 EditorGotoLineNumberDialog 并将本次命令计入导航历史。
        CommandProcessor.getInstance().executeCommand(
                effective,
                () -> {
                    EditorGotoLineNumberDialog dialog = new EditorGotoLineNumberDialog(effective, editor);
                    dialog.show();
                    IdeDocumentHistory.getInstance(effective).includeCurrentCommandAsNavigation();
                },
                UIBundle.message("go.to.line.command.name"),
                null);
    }
}
