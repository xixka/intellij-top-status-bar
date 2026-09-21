package com.xixka.topstatusbar.model;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registry of the well-known status item ids in display order.
 * Kept in sync with {@code TopStatusBarManager.createItems()}.
 */
public final class StatusItems {

    /**
     * 2026-09-21（第五轮「还是会显示」反馈后定案）：默认关闭的插件自有项。
     * <p>
     * 当前项目与文件系统同步两项连续多轮被用户要求不再显示（原生菜单
     * 控制对自有项不可行——镜像已被 2026-09-20 前端化存储实证否决；
     * 设置页控制可用但依赖用户逐项目手动关闭，且早期设置页 Apply 会把
     * 整页勾选写成显式 true 压过任何默认值）。故把这两项的默认值改为
     * 关闭：升级后不显示、无需任何操作；设置页仍可勾选恢复（写入
     * itemEnabled 显式值，优先于默认值）。
     * <p>
     * 新增 id 到此集合时，{@code TopStatusBarSettings.applyDefaultOffPolicy}
     * 会在下次启动一次性清除该项的遗留显式值（按 id 记账，幂等）。
     */
    private static final Set<String> DEFAULT_DISABLED_IDS = Set.of(
            // commit 粒度：先落当前项目，文件同步见后续提交
            "statusText");

    private static final Map<String, String> DISPLAY_NAMES = createDisplayNames();

    private StatusItems() {
    }

    private static Map<String, String> createDisplayNames() {
        Map<String, String> names = new LinkedHashMap<>();
        // 显示名必须与顶栏实际观感一致，否则用户在设置页找不到对应条目
        // （2026-09-20：statusText 顶栏显示项目名被用户称为“当前项目”，
        // fileSystemSync 顶栏显示“已同步 HH:mm:ss”被称为“文件同步”）
        names.put("statusText", "当前项目");
        names.put("fileSystemSync", "文件同步");
        names.put("codeBuddy", "CodeBuddy");
        names.put("aggregator", "聚合器");
        names.put("networkLocation", "网络位置");
        names.put("deployServer", "默认部署服务器");
        names.put("caretPosition", "行列号");
        names.put("languageService", "语言服务");
        names.put("lineSeparator", "行分隔符");
        names.put("encoding", "文件编码");
        names.put("powerSave", "省电模式");
        names.put("selectionMode", "编辑器选择模式");
        names.put("indent", "缩进");
        names.put("jsonSchema", "JSON 架构");
        names.put("gitBranch", "Git 分支");
        names.put("readOnly", "只读特性");
        names.put("memoryIndicator", "内存指示器");
        return Collections.unmodifiableMap(names);
    }

    public static Map<String, String> displayNames() {
        return DISPLAY_NAMES;
    }

    /**
     * 无显式设置时该项的默认显隐（2026-09-21 起按项区分，
     * 见 {@link #defaultDisabledIds()}）。
     */
    public static boolean isEnabledByDefault(@NotNull String itemId) {
        return !DEFAULT_DISABLED_IDS.contains(itemId);
    }

    /** 默认关闭的 id 集合（不可变）。 */
    public static Set<String> defaultDisabledIds() {
        return DEFAULT_DISABLED_IDS;
    }
}
