package com.xixka.topstatusbar.items;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.awt.RelativePoint;
import com.xixka.topstatusbar.DebugLog;
import com.xixka.topstatusbar.model.StatusSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JSON 架构: shows the "$schema" reference of the current JSON document.
 * Self-contained detection — no dependency on JSON plugin internals.
 */
public final class JsonSchemaItem extends CurrentFileItem {

    private static final Pattern SCHEMA_PATTERN = Pattern.compile("\"\\$schema\"\\s*:\\s*\"([^\"]+)\"");

    public JsonSchemaItem() {
        super("jsonSchema", 50);
    }

    /**
     * 原生「状态栏微件」菜单「JSON 架构」条目的工厂 id（JSON 插件注册，
     * 241/262 核实 id 一致）。顶栏读取该菜单勾选状态决定显隐。
     */
    @Override
    public @Nullable String getPlatformWidgetId() {
        return "JSONSchemaSelector";
    }

    @Override
    protected void update() {
        Editor editor = EditorContext.selectedEditor(project());
        VirtualFile file = EditorContext.virtualFile(editor);
        Document document = EditorContext.document(editor);
        if (file == null || document == null || !isJsonFile(file)) {
            setVisible(false);
            return;
        }
        String schema = findSchemaReference(document);
        if (schema == null) {
            setVisible(false);
            return;
        }
        setText("Schema: " + shortLabel(schema));
        setSeverity(StatusSeverity.INFO);
        setTooltip("JSON 架构：" + schema);
        setVisible(true);
    }

    private static boolean isJsonFile(VirtualFile file) {
        String extension = file.getExtension();
        if (extension != null && extension.toLowerCase().contains("json")) {
            return true;
        }
        String typeName = file.getFileType().getName();
        return typeName.toUpperCase().contains("JSON");
    }

    @Nullable
    private static String findSchemaReference(Document document) {
        CharSequence text = document.getCharsSequence();
        int limit = Math.min(text.length(), 4096);
        Matcher matcher = SCHEMA_PATTERN.matcher(text.subSequence(0, limit));
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String shortLabel(String schema) {
        int slash = Math.max(schema.lastIndexOf('/'), schema.lastIndexOf('\\'));
        String label = slash >= 0 && slash < schema.length() - 1 ? schema.substring(slash + 1) : schema;
        return label.length() > 32 ? label.substring(0, 32) + "…" : label;
    }

    /**
     * 原生点击行为（全项审计 2026-09-22 补齐）：原生 JsonSchemaStatusWidget
     * .createPopup → JsonSchemaStatusPopup.createPopup(service, project, file,
     * warning)——版本切换、远程 schema 下载、「显示 Schema」等全部平台实现。
     * 该静态工厂为 package-private（241 与 master 均如此，包名未变），跨包
     * 无法编译期引用，按键名反射调用（只读数据例外许可同族：构建平台自有
     * 弹窗、不触碰 UI 内部结构；任何失败即静默不弹并留日志，行为回到无点击）。
     */
    @Override
    public void onClick(@Nullable Project project, @NotNull JComponent source) {
        Project effective = project != null ? project : project();
        Editor editor = EditorContext.selectedEditor(effective);
        VirtualFile file = EditorContext.virtualFile(editor);
        if (effective == null || file == null) {
            return;
        }
        try {
            Class<?> serviceClass = Class.forName("com.jetbrains.jsonSchema.ide.JsonSchemaService");
            Class<?> implClass = Class.forName("com.jetbrains.jsonSchema.ide.JsonSchemaService$Impl");
            Object service = implClass.getMethod("get", Project.class).invoke(null, effective);
            Class<?> popupClass = Class.forName("com.jetbrains.jsonSchema.widget.JsonSchemaStatusPopup");
            java.lang.reflect.Method create = popupClass.getDeclaredMethod(
                    "createPopup", serviceClass, Project.class, VirtualFile.class, boolean.class);
            create.setAccessible(true);
            ListPopup popup = (ListPopup) create.invoke(null, service, effective, file, false);
            if (popup != null) {
                popup.show(new RelativePoint(source, new Point(0, source.getHeight())));
            }
        } catch (Throwable t) {
            DebugLog.warn("jsonSchema onClick: 原生弹窗不可用（" + t.getClass().getSimpleName()
                    + "），保持无点击行为", t);
        }
    }
}
