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
     * menu (View | Appearance | Status Bar Widgets, present on every
     * supported platform 233+). Non-null for the 11 items mirroring
     * built-in IDE widgets: the menu checkbox — read from the persisted
     * platform widget settings ({@code StatusBarWidgetFactory.EP_NAME} +
     * {@code StatusBarWidgetSettings}) — is the single source of truth for
     * whether the item shows in the top bar, on every platform version.
     * <p>
     * Plugin-specific items return {@code null}: their visibility is
     * governed by this plugin's own settings page on every platform
     * version. They deliberately do not register a
     * {@code statusBarWidgetFactory}: 2026.x synthesizes a second,
     * independently-stored set of menu entries for every classic factory,
     * which duplicated the menu and made half the toggles ineffective
     * (2026-09-20 incident). {@code null} also covers "factory cannot be
     * resolved" (older platform / plugin not installed) — the manager then
     * falls back to the plugin settings-page toggle.
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
