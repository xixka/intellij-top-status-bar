package com.xixka.topstatusbar;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.messages.MessageBusConnection;
import com.xixka.topstatusbar.model.StatusItem;
import com.xixka.topstatusbar.ui.StatusCell;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The Top Status Bar component rendered inside the Main Toolbar.
 * <p>
 * Resolves the enclosing project from the IDE frame, mirrors the
 * {@link TopStatusBarManager} item list and lays the items out horizontally
 * with New UI styling. When the toolbar runs out of width, low priority
 * items are hidden automatically.
 */
final class TopStatusBarPanel extends JComponent {

    private final List<StatusCell> cells = new ArrayList<>();
    private final Runnable modelListener = this::syncCells;

    @Nullable
    private Project project;
    @Nullable
    private TopStatusBarManager manager;
    @Nullable
    private MessageBusConnection retryConnection;

    TopStatusBarPanel() {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setOpaque(false);
        setAlignmentY(CENTER_ALIGNMENT);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                applyVisibility();
            }
        });
    }

    @Override
    public void addNotify() {
        super.addNotify();
        bind();
    }

    @Override
    public void removeNotify() {
        unbind();
        super.removeNotify();
    }

    private void bind() {
        if (manager != null) {
            return;
        }
        Project resolved = resolveProject();
        if (resolved == null) {
            subscribeToProjectOpen();
            return;
        }
        project = resolved;
        manager = TopStatusBarManager.getInstance(resolved);
        manager.addChangeListener(modelListener);
        syncCells();
    }

    private void unbind() {
        if (manager != null) {
            manager.removeChangeListener(modelListener);
            manager = null;
        }
        project = null;
        cells.clear();
        removeAll();
        disposeRetryConnection();
        revalidate();
        repaint();
    }

    private void subscribeToProjectOpen() {
        if (retryConnection != null) {
            return;
        }
        retryConnection = ApplicationManager.getApplication().getMessageBus().connect();
        retryConnection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
            @Override
            public void projectOpened(@NotNull Project opened) {
                SwingUtilities.invokeLater(() -> {
                    if (manager == null && isShowing()) {
                        disposeRetryConnection();
                        bind();
                    }
                });
            }
        });
    }

    private void disposeRetryConnection() {
        if (retryConnection != null) {
            retryConnection.dispose();
            retryConnection = null;
        }
    }

    @Nullable
    private Project resolveProject() {
        Object frame = SwingUtilities.getAncestorOfClass(IdeFrame.class, this);
        if (frame instanceof IdeFrame) {
            Project frameProject = ((IdeFrame) frame).getProject();
            if (frameProject != null && !frameProject.isDisposed()) {
                return frameProject;
            }
        }
        Project[] open = ProjectManager.getInstance().getOpenProjects();
        if (open.length == 1 && !open[0].isDisposed()) {
            return open[0];
        }
        return null;
    }

    private void syncCells() {
        TopStatusBarManager currentManager = manager;
        if (currentManager == null) {
            return;
        }
        List<StatusItem> currentItems = currentManager.getItems();
        if (!sameItems(currentItems)) {
            cells.clear();
            removeAll();
            for (StatusItem item : currentItems) {
                StatusCell cell = new StatusCell(item, project);
                cells.add(cell);
                add(cell);
            }
        } else {
            for (StatusCell cell : cells) {
                cell.refresh();
            }
        }
        applyVisibility();
        Dimension preferred = getPreferredSize();
        setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
        revalidate();
        repaint();
    }

    private boolean sameItems(List<StatusItem> currentItems) {
        if (cells.size() != currentItems.size()) {
            return false;
        }
        for (int i = 0; i < cells.size(); i++) {
            if (!Objects.equals(cells.get(i).item().getId(), currentItems.get(i).getId())) {
                return false;
            }
        }
        return true;
    }

    private void applyVisibility() {
        // Visibility is governed solely by the item itself (its own display
        // conditions) and the plugin settings page toggles (which decide
        // during reload whether the item exists at all). The old extra
        // suppression by native status-bar widget presence was removed:
        // on 2026.x the bottom bar no longer hosts many of those widgets,
        // which hid items the user had explicitly enabled.
        for (StatusCell cell : cells) {
            cell.setVisible(cell.item().isVisible());
        }
        int available = getWidth();
        if (available <= 0) {
            return;
        }
        int needed = 0;
        for (StatusCell cell : cells) {
            if (cell.isVisible()) {
                needed += cell.getPreferredSize().width;
            }
        }
        if (needed <= available) {
            return;
        }
        List<StatusCell> byPriority = new ArrayList<>(cells);
        byPriority.sort(Comparator.comparingInt(cell -> cell.item().getPriority()));
        for (StatusCell cell : byPriority) {
            if (needed <= available) {
                break;
            }
            if (!cell.isVisible()) {
                continue;
            }
            needed -= cell.getPreferredSize().width;
            cell.setVisible(false);
        }
    }
}
