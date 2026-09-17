package com.xixka.topstatusbar.widget;

/**
 * The per-item widget factories registered in {@code plugin.xml}. One nested
 * class per plugin-specific item is required because each declared
 * {@code statusBarWidgetFactory} entry needs its own implementation class.
 * <p>
 * Items that mirror a built-in IDE widget (editor position, encoding, Git
 * branch, …) are intentionally NOT listed here: they already follow their
 * native counterpart's toggle in the same menu.
 */
public final class TopBarItemWidgetFactories {

    private TopBarItemWidgetFactories() {
    }

    public static final class StatusText extends AbstractTopBarItemWidgetFactory {
        public StatusText() {
            super("statusText");
        }
    }

    public static final class FileSystemSync extends AbstractTopBarItemWidgetFactory {
        public FileSystemSync() {
            super("fileSystemSync");
        }
    }

    public static final class CodeBuddy extends AbstractTopBarItemWidgetFactory {
        public CodeBuddy() {
            super("codeBuddy");
        }
    }

    public static final class Aggregator extends AbstractTopBarItemWidgetFactory {
        public Aggregator() {
            super("aggregator");
        }
    }

    public static final class NetworkLocation extends AbstractTopBarItemWidgetFactory {
        public NetworkLocation() {
            super("networkLocation");
        }
    }

    public static final class DeployServer extends AbstractTopBarItemWidgetFactory {
        public DeployServer() {
            super("deployServer");
        }
    }
}
