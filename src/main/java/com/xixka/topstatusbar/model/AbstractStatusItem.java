package com.xixka.topstatusbar.model;

import com.intellij.openapi.project.Project;
import com.xixka.topstatusbar.DebugLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

/**
 * Base implementation holding the presentation state and change notification.
 * State setters fire {@code onChange} only when the value actually changes,
 * which keeps rebuild storms away.
 */
public abstract class AbstractStatusItem implements StatusItem {

    private final String id;
    private final int priority;

    private Project project;
    private Runnable onChange;

    private Icon icon;
    private String text = "";
    private String tooltip;
    private boolean visible = true;
    private StatusSeverity severity = StatusSeverity.NORMAL;

    protected AbstractStatusItem(@NotNull String id, int priority) {
        this.id = id;
        this.priority = priority;
    }

    @Override
    public final void install(@NotNull Project project, @NotNull Runnable onChange) {
        this.project = project;
        this.onChange = onChange;
        DebugLog.log("item=" + id + " install → project=" + project.getName());
        install();
    }

    protected abstract void install();

    @Override
    public void uninstall() {
        DebugLog.log("item=" + id + " uninstall（text=\"" + text + "\", visible=" + visible + "）");
        this.project = null;
        this.onChange = null;
    }

    @Override
    public final @NotNull String getId() {
        return id;
    }

    @Override
    public final int getPriority() {
        return priority;
    }

    protected final Project project() {
        return project;
    }

    protected final void changed() {
        Runnable listener = onChange;
        if (listener != null) {
            listener.run();
        }
    }

    protected final void setText(@Nullable String value) {
        String newValue = value == null ? "" : value;
        if (!newValue.equals(text)) {
            DebugLog.log("item=" + id + " text: \"" + text + "\" → \"" + newValue + "\"");
            text = newValue;
            changed();
        }
    }

    protected final void setIcon(@Nullable Icon value) {
        if (value != icon) {
            DebugLog.log("item=" + id + " icon: " + iconName(icon) + " → " + iconName(value));
            icon = value;
            changed();
        }
    }

    protected final void setTooltip(@Nullable String value) {
        if (!Objects.equals(value, tooltip)) {
            DebugLog.log("item=" + id + " tooltip: \"" + tooltip + "\" → \"" + value + "\"");
            tooltip = value;
            changed();
        }
    }

    protected final void setVisible(boolean value) {
        if (value != visible) {
            DebugLog.log("item=" + id + " visible: " + visible + " → " + value);
            visible = value;
            changed();
        }
    }

    protected final void setSeverity(@NotNull StatusSeverity value) {
        if (value != severity) {
            DebugLog.log("item=" + id + " severity: " + severity + " → " + value);
            severity = value;
            changed();
        }
    }

    private static String iconName(@Nullable Icon icon) {
        return icon == null ? "null" : icon.getClass().getSimpleName();
    }

    @Override
    public final boolean isVisible() {
        return visible;
    }

    @Override
    public final @NotNull StatusSeverity getSeverity() {
        return severity;
    }

    @Override
    public final @Nullable Icon getIcon() {
        return icon;
    }

    @Override
    public final @NotNull String getText() {
        return text;
    }

    @Override
    public final @Nullable String getTooltip() {
        return tooltip;
    }
}
