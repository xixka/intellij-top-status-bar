package com.xixka.topstatusbar.items;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import com.xixka.topstatusbar.model.AbstractStatusItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for items that depend on the currently selected file/editor.
 * Subscribes to editor selection changes via the official
 * {@link FileEditorManagerListener} message bus topic.
 */
abstract class CurrentFileItem extends AbstractStatusItem {

    @Nullable
    private MessageBusConnection connection;

    protected CurrentFileItem(@NotNull String id, int priority) {
        super(id, priority);
    }

    @Override
    protected void install() {
        Project project = project();
        if (project != null) {
            connection = project.getMessageBus().connect();
            connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
                @Override
                public void selectionChanged(@NotNull FileEditorManagerEvent event) {
                    update();
                }

                @Override
                public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                    update();
                }
            });
        }
        update();
    }

    @Override
    public void uninstall() {
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

    protected abstract void update();
}
