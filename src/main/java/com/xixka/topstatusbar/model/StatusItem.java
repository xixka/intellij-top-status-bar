package com.xixka.topstatusbar.model;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * A single logical status displayed inside the Top Status Bar.
 * <p>
 * Implementations read their state from IntelliJ Platform APIs, update their
 * presentation and notify {@code onChange} (installed by the manager)
 * whenever something changes.
 */
public interface StatusItem {

    @NotNull
    String getId();

    /**
     * Higher value = more important: the item is kept visible longer when
     * the toolbar runs out of horizontal space.
     */
    int getPriority();

    /**
     * Id of the corresponding entry in the native "Status Bar Widgets"
     * menu (View | Appearance | Status Bar Widgets). Its checkbox state is
     * the single source of truth for whether this item shows in the top
     * bar (2026-09-20 user decision: "show exactly what the system
     * settings selected").
     * <p>
     * The state is read from the persisted platform widget settings
     * (resolved via {@code StatusBarWidgetFactory.EP_NAME} + {@code
     * StatusBarWidgetSettings}) — the same data the menu checkbox renders
     * from — never from bottom-bar widget instances, which 2026.x no
     * longer hosts for many native widgets (that instance-based lookup
     * hid items the user had checked; see AGENTS.md). Plugin-specific
     * items without a native counterpart return their own factory id
     * (registered by this plugin). {@code null} means "no factory at all —
     * the manager falls back to the plugin settings-page toggle".
     */
    default @Nullable String getPlatformWidgetId() {
        return null;
    }

    boolean isVisible();

    @NotNull
    StatusSeverity getSeverity();

    @Nullable
    Icon getIcon();

    @NotNull
    String getText();

    @Nullable
    String getTooltip();

    /**
     * @param onChange called whenever the presentation changes; may be invoked
     *                 from any thread (the manager marshals it to the EDT)
     */
    void install(@NotNull Project project, @NotNull Runnable onChange);

    void uninstall();

    /**
     * Periodic refresh, executed on the EDT by the manager.
     */
    default void refresh() {
    }

    /**
     * Invoked when the user clicks the status cell on the EDT.
     *
     * @param source the clicked cell, usable to anchor popups (typically
     *               shown below the cell since the bar sits at the top)
     */
    default void onClick(@Nullable Project project, @NotNull JComponent source) {
    }
}
