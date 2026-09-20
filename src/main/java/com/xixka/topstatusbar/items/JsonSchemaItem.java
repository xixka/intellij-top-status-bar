package com.xixka.topstatusbar.items;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFile;
import com.xixka.topstatusbar.model.StatusSeverity;
import org.jetbrains.annotations.Nullable;

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
}
