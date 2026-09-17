package com.xixka.topstatusbar.model;

import com.intellij.openapi.project.Project;
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
        install();
    }

    protected abstract void install();

    @Override
    public void uninstall() {
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
            text = newValue;
            changed();
        }
    }

    protected final void setIcon(@Nullable Icon value) {
        if (value != icon) {
            icon = value;
            changed();
        }
    }

    protected final void setTooltip(@Nullable String value) {
        if (!Objects.equals(value, tooltip)) {
            tooltip = value;
            changed();
        }
    }

    protected final void setVisible(boolean value) {
        if (value != visible) {
            visible = value;
            changed();
        }
    }

    protected final void setSeverity(@NotNull StatusSeverity value) {
        if (value != severity) {
            severity = value;
            changed();
        }
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
