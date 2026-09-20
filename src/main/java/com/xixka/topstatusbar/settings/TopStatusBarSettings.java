package com.xixka.topstatusbar.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persisted, project-level Top Status Bar settings.
 * <p>
 * Hybrid visibility model (2026-09-20, cross-version robust): the 11 items
 * mirroring built-in IDE widgets follow the native "Status Bar Widgets"
 * menu (View | Appearance | Status Bar Widgets) — that menu's checkboxes
 * are exactly what the top bar shows. The 6 plugin-specific items
 * (statusText/fileSystemSync/codeBuddy/aggregator/networkLocation/
 * deployServer) are governed by the {@code itemEnabled} map configured on
 * this plugin's settings page ({@code TopStatusBarConfigurable}) on every
 * platform version — they no longer register native widget factories,
 * because 2026.x synthesizes a second, independently-stored set of menu
 * entries for every classic factory (the duplicated-menu incident).
 * <p>
 * {@code itemEnabled} also remains the fallback for mirrored items whose
 * platform widget factory cannot be resolved (older platform version /
 * corresponding plugin not installed); default is enabled.
 */
@State(name = "TopStatusBarSettings", storages = @Storage("topStatusBar.xml"))
@Service(Service.Level.PROJECT)
public final class TopStatusBarSettings implements PersistentStateComponent<TopStatusBarSettings.State> {

    public static TopStatusBarSettings getInstance(@NotNull Project project) {
        return project.getService(TopStatusBarSettings.class);
    }

    public static class State {
        public boolean enabled = true;
        public Map<String, Boolean> itemEnabled = new LinkedHashMap<>();
        public String deployServer = "";
        public String codeBuddyLabel = "CodeBuddy";
    }

    private State state = new State();

    @Nullable
    @Override
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State loaded) {
        state = loaded;
    }

    public boolean isEnabled() {
        return state.enabled;
    }

    public void setEnabled(boolean value) {
        state.enabled = value;
    }

    public boolean isItemEnabled(@NotNull String itemId) {
        return state.itemEnabled.getOrDefault(itemId, Boolean.TRUE);
    }

    /**
     * Whether the user has an explicit persisted choice for this item (as
     * opposed to the implicit default "enabled"). Used by the one-time
     * import of legacy native-menu toggles
     * ({@code TopStatusBarManager.migrateLegacyOwnItemToggles}): only items
     * without an explicit settings-page choice participate in the import.
     */
    public boolean hasItemEnabledExplicitly(@NotNull String itemId) {
        return state.itemEnabled.containsKey(itemId);
    }

    public void setItemEnabled(@NotNull String itemId, boolean enabled) {
        state.itemEnabled.put(itemId, enabled);
    }

    @Nullable
    public String getDeployServer() {
        return state.deployServer;
    }

    public void setDeployServer(@Nullable String deployServer) {
        state.deployServer = deployServer;
    }

    @Nullable
    public String getCodeBuddyLabel() {
        return state.codeBuddyLabel;
    }

    public void setCodeBuddyLabel(@Nullable String label) {
        state.codeBuddyLabel = label;
    }
}
