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
 * An invisible, zero-size status bar widget backing the plugin's entries in
 * the native "Status Bar Widgets" menu (right-click the bottom status bar,
 * or View | Appearance | Status Bar Widgets).
 * <p>
 * The widget itself renders nothing on the bottom status bar. Its only
 * active role is instant change notification: the platform creates it while
 * the entry's toggle is on and disposes it when the toggle goes off, so
 * add/remove notifications give the top bar an immediate re-sync on those
 * platforms that still host legacy custom status bar widgets. On 2026.x,
 * where the frontend-ized status bar may never host the component, the
 * manager's periodic poll (decision-snapshot comparison) still picks the
 * change up with at most one refresh interval of delay.
 * <p>
 * The authoritative visibility source is NOT this widget's presence: it is
 * the persisted {@code StatusBarWidgetSettings} toggle the menu checkbox
 * renders from (see {@code TopStatusBarManager.isDisplayEnabled}). Judging
 * by widget presence was the failed mechanism of the earlier attempts —
 * 2026.x no longer hosts many native widgets on the bottom bar at all.
 * <p>
 * The component is created lazily (widgets may be instantiated on a
 * background thread) and carries an explicitly empty border so the platform
 * does not apply the themed widget border, which would add visible spacing
 * to the bottom status bar.
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
     * "Status Bar Widgets" menu takes effect without waiting for the
     * periodic refresh.
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
            scheduleSync();
            super.removeNotify();
        }
    }
}
