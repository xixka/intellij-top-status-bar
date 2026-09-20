package com.xixka.topstatusbar.widget;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.xixka.topstatusbar.model.StatusItems;
import org.jetbrains.annotations.NotNull;

/**
 * Base for the per-item widget factories that expose the Top Status Bar's
 * plugin-specific items — those without a native counterpart — in the native
 * "Status Bar Widgets" toggle menu (right-click the bottom status bar, or
 * View | Appearance | Status Bar Widgets).
 * <p>
 * 2026-09-20 semantics: the native menu checkbox is the single source of
 * truth for the whole top bar. The checkbox state is read from the persisted
 * platform {@code StatusBarWidgetSettings} resolved through
 * {@code StatusBarWidgetFactory.EP_NAME} — the same data the menu itself
 * renders from. The created {@link TopBarSyncWidget} renders nothing on the
 * bottom status bar; it only provides an instant add/remove notification
 * while the platform still hosts legacy custom status bar widgets (on 2026.x
 * it may never be hosted, in which case the manager's periodic poll picks
 * the toggle up instead).
 * <p>
 * The display name comes from {@link StatusItems} so the menu entry always
 * matches the top bar's own labels.
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
