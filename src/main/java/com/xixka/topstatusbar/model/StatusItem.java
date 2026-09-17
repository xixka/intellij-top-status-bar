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
     * Optional ID of the built-in IDE status bar widget this item mirrors.
     * When set, the item is hidden while the corresponding widget is turned
     * off in the IDE status bar (context menu or Settings), so the top bar
     * stays in sync with the native widget toggles.
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
