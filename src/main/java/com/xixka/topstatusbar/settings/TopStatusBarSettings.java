package com.xixka.topstatusbar.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.xixka.topstatusbar.DebugLog;
import com.xixka.topstatusbar.model.StatusItems;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 * corresponding plugin not installed). Without an explicit choice the
 * per-item default applies ({@link StatusItems#isEnabledByDefault},
 * 2026-09-21: 当前项目/文件同步 默认关闭，其余默认开启).
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
        /** 已按「默认关闭策略」清账的 id（见 {@link TopStatusBarSettings#applyDefaultOffPolicy}）。 */
        public List<String> defaultOffPolicyApplied = new ArrayList<>();
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

    /**
     * 无显式选择时按项默认值处理（2026-09-21 起，见
     * {@link StatusItems#isEnabledByDefault}）：当前项目/文件同步默认关闭，
     * 其余默认开启。镜像项工厂缺失回退本值时同理。
     */
    public boolean isItemEnabled(@NotNull String itemId) {
        return state.itemEnabled.getOrDefault(itemId, StatusItems.isEnabledByDefault(itemId));
    }

    /**
     * 「默认关闭策略」一次性落地（2026-09-21，第五轮「还是会显示」定案）。
     * <p>
     * 早期构建（按项默认引入前）的设置页 Apply 会把全部自有项勾选状态
     * 整页写成显式值：即使默认值随后改为关闭，遗留的显式 true 仍优先于
     * 默认值，用户升级后项依旧显示。故对每个「新加入默认关闭集合」的 id
     * 一次性清除其显式值（无论 true/false，统一回退到新默认=关闭），并把
     * id 记入 {@code defaultOffPolicyApplied} 持久化——之后用户在设置页的
     * 重新勾选写回显式值，不再被触碰（幂等；后续把新 id 加入
     * {@link StatusItems#defaultDisabledIds()} 时自动获得同样处理）。
     * <p>
     * 由 {@code TopStatusBarManager} 构造时调用（先于遗留原生菜单勾选
     * 迁移：被清除的 false 即使被再次导入，结果仍为关闭，语义不变）。
     */
    public void applyDefaultOffPolicy() {
        List<String> cleared = new ArrayList<>();
        for (String id : StatusItems.defaultDisabledIds()) {
            if (state.defaultOffPolicyApplied.contains(id)) {
                continue;
            }
            if (state.itemEnabled.remove(id) != null) {
                cleared.add(id);
            }
            state.defaultOffPolicyApplied.add(id);
        }
        if (!cleared.isEmpty()) {
            DebugLog.log("默认关闭策略: 清除遗留显式勾选 " + cleared
                    + " → 按新默认隐藏（设置页可重新勾选恢复）");
        }
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
