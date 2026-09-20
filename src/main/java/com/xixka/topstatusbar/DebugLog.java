package com.xixka.topstatusbar;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Debug logging for troubleshooting the Top Status Bar (2026-09-20).
 * <p>
 * Everything goes to {@code idea.log} via a dedicated logger category
 * {@code TopStatusBar} at INFO/WARN level, so it shows up in the default
 * IDE log without enabling anything: Help | Show Log in Explorer/Finder,
 * then search for the {@code TSB} prefix (added automatically to every
 * message).
 * <p>
 * Convention: one short line per event, ASCII field labels
 * ({@code key=value}) for easy grep, Chinese wording for readability.
 * Log state <em>transitions</em> (old → new), never per-tick dumps, so the
 * log stays readable while still capturing every visibility/text/severity
 * change and every swallowed exception.
 */
public final class DebugLog {

    private static final Logger LOGGER = Logger.getInstance("TopStatusBar");

    private DebugLog() {
    }

    public static void log(@NotNull String message) {
        LOGGER.info("TSB " + message);
    }

    public static void warn(@NotNull String message) {
        LOGGER.warn("TSB " + message);
    }

    public static void warn(@NotNull String message, @Nullable Throwable t) {
        LOGGER.warn("TSB " + message, t);
    }
}
