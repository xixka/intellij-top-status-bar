package com.xixka.topstatusbar.widget;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.util.ui.JBUI;
import com.xixka.topstatusbar.TopStatusBarManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * An invisible, zero-size status bar widget that mirrors the enabled state of
 * a Top Status Bar item through the native "Status Bar Widgets" toggles
 * (right-click the bottom status bar, or View | Appearance | Status Bar
 * Widgets).
 * <p>
 * The platform creates this widget on the bottom status bar while the toggle
 * is on and removes it when the toggle is off; the top bar checks
 * {@code StatusBar.getWidget(id)} — a public API — to decide whether the
 * corresponding item may be shown. The component is created lazily (widgets
 * may be instantiated on a background thread) and carries an explicitly empty
 * border so the platform does not apply the themed widget border, which would
 * add visible spacing to the bottom status bar.
 */
public final class TopBarSyncWidget implements CustomStatusBarWidget {

    private final String id;
    private final Project project;
    private volatile JComponent component;

    public TopBarSyncWidget(@NotNull String id, @NotNull Project project) {
        this.id = id;
        this.project = project;
    }

    @Override
    public @NotNull String ID() {
        return id;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
    }

    @Override
    public @NotNull JComponent getComponent() {
        JComponent result = component;
        if (result == null) {
            synchronized (this) {
                result = component;
                if (result == null) {
                    result = new SyncComponent();
                    component = result;
                }
            }
        }
        return result;
    }

    @Override
    public void dispose() {
    }

    /**
     * Notifies the owning project's top bar immediately when the widget is
     * added to or removed from the status bar, so toggling an entry in the
     * "Status Bar Widgets" menu takes effect without waiting for the periodic
     * refresh.
     */
    private void scheduleSync() {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!project.isDisposed()) {
                TopStatusBarManager.getInstance(project).syncNow();
            }
        });
    }

    private final class SyncComponent extends JComponent {

        private SyncComponent() {
            setOpaque(false);
            setBorder(JBUI.Borders.empty());
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(0, 0);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(0, 0);
        }

        @Override
        public void addNotify() {
            super.addNotify();
            scheduleSync();
        }

        @Override
        public void removeNotify() {
            super.removeNotify();
            scheduleSync();
        }
    }
}
