package com.xixka.topstatusbar.items;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
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
        // Same format as the native position widget: "line:column" for a
        // single caret, the caret count when several carets are active, and
        // the selected character count appended while a selection exists.
        LogicalPosition position = editor.getCaretModel().getLogicalPosition();
        int caretCount = editor.getCaretModel().getCaretCount();
        String text;
        if (caretCount > 1) {
            text = caretCount + " carets";
        } else {
            text = (position.line + 1) + ":" + (position.column + 1);
            int selectionStart = editor.getCaretModel().getCurrentCaret().getSelectionStart();
            int selectionEnd = editor.getCaretModel().getCurrentCaret().getSelectionEnd();
            if (selectionEnd > selectionStart) {
                text += "（" + (selectionEnd - selectionStart) + " 字符）";
            }
        }
        setText(text);
        String tooltip = "跳转到行/列（当前 " + text + "）";
        if (caretCount > 1) {
            tooltip += "（" + caretCount + " 个光标）";
        }
        setTooltip(tooltip);
        setVisible(true);
    }

    @Override
    public void onClick(@Nullable Project project, @NotNull JComponent source) {
        Project effective = project != null ? project : project();
        Editor editor = EditorContext.selectedEditor(effective);
        if (effective == null || editor == null) {
            return;
        }
        // Same behavior as the native position widget: invoke the platform
        // "Go to Line/Column" action on the current editor.
        AnAction gotoLine = ActionManager.getInstance().getAction("GotoLine");
        if (gotoLine == null) {
            return;
        }
        DataContext base = DataManager.getInstance().getDataContext(source);
        DataContext context = dataId -> {
            if (CommonDataKeys.PROJECT.is(dataId)) {
                return effective;
            }
            if (CommonDataKeys.EDITOR.is(dataId)) {
                return editor;
            }
            if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
                return EditorContext.virtualFile(editor);
            }
            return base.getData(dataId);
        };
        AnActionEvent event = AnActionEvent.createFromAnAction(
                gotoLine, null, ActionPlaces.EDITOR_TOOLBAR, context);
        gotoLine.update(event);
        if (event.getPresentation().isEnabled()) {
            gotoLine.actionPerformed(event);
        }
    }
}
