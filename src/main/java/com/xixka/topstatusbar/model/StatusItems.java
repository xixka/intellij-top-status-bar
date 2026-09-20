package com.xixka.topstatusbar.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of the well-known status item ids in display order.
 * Kept in sync with {@code TopStatusBarManager.createItems()}.
 */
public final class StatusItems {

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
}
