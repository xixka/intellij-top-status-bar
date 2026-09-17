package com.xixka.topstatusbar;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;

/**
 * Root action group hosting the Top Status Bar custom component.
 * <p>
 * The group is registered at the action system root level, so it appears in the
 * "Add Action" tree of the native toolbar customization dialog
 * (Settings | Appearance &amp; Behavior | Menus and Toolbars) and can be placed
 * into the Main Toolbar without any custom UI patching.
 */
public final class TopStatusBarGroup extends DefaultActionGroup implements DumbAware {

    public TopStatusBarGroup() {
        super();
        setPopup(false);
    }
}
