package com.xixka.topstatusbar.widget;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.xixka.topstatusbar.model.StatusItems;
import org.jetbrains.annotations.NotNull;

/**
 * Base for the per-item widget factories that expose the Top Status Bar's
 * plugin-specific items — those without a native counterpart — in the native
 * "Status Bar Widgets" toggle menu.
 * <p>
 * The display name is taken from {@link StatusItems} so the menu entry always
 * matches the settings page label. The created {@link TopBarSyncWidget}
 * renders nothing on the bottom status bar; it exists so its presence
 * reflects the toggle state through the public {@code StatusBar.getWidget}
 * API, which the top bar consults (see
 * {@code TopStatusBarManager#isSuppressedByPlatformWidget}).
 */
public abstract class AbstractTopBarItemWidgetFactory implements StatusBarWidgetFactory {

    private final String id;
    private final String displayName;

    protected AbstractTopBarItemWidgetFactory(@NotNull String id) {
        this.id = id;
        this.displayName = StatusItems.displayNames().getOrDefault(id, id);
    }

    @Override
    public final @NotNull String getId() {
        return id;
    }

    @Override
    public final @NotNull String getDisplayName() {
        return displayName;
    }

    @Override
    public final boolean isAvailable(@NotNull Project project) {
        return true;
    }

    @Override
    public final @NotNull StatusBarWidget createWidget(@NotNull Project project) {
        return new TopBarSyncWidget(id, project);
    }

    @Override
    public final void disposeWidget(@NotNull StatusBarWidget widget) {
        // The sync widget holds no resources; nothing to release.
    }
}
