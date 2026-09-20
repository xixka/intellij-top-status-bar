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
 * Note (2026-09-20): per-item visibility is governed by the native
 * "Status Bar Widgets" menu (View | Appearance | Status Bar Widgets) —
 * that menu's checkboxes are exactly what the top bar shows. The persisted
 * {@code itemEnabled} map survives only as the fallback for items whose
 * platform widget factory cannot be resolved (older platform version /
 * corresponding plugin not installed), so users of those setups keep a
 * working per-item toggle; it is no longer part of the settings UI.
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
