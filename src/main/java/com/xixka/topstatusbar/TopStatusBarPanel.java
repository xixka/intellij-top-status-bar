package com.xixka.topstatusbar;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.messages.MessageBusConnection;
import com.xixka.topstatusbar.model.StatusItem;
import com.xixka.topstatusbar.ui.StatusCell;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The Top Status Bar component rendered inside the Main Toolbar.
 * <p>
 * Resolves the enclosing project from the IDE frame, mirrors the
 * {@link TopStatusBarManager} item list and lays the items out horizontally
 * with New UI styling. When the toolbar runs out of width, low priority
 * items are hidden automatically.
 */
final class TopStatusBarPanel extends JComponent {

    private final List<StatusCell> cells = new ArrayList<>();
    private final Runnable modelListener = this::syncCells;

    @Nullable
    private Project project;
    @Nullable
    private TopStatusBarManager manager;
    @Nullable
    private MessageBusConnection retryConnection;
    /** 上次自适应隐藏的项集合（调试日志用：只在集合变化时记录，避免 resize 刷屏）。 */
    @Nullable
    private String lastHiddenIds;

    TopStatusBarPanel() {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setOpaque(false);
        setAlignmentY(CENTER_ALIGNMENT);
        DebugLog.log("panel#" + panelId() + " 创建（等待 addNotify 绑定项目）");
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                applyVisibility();
            }
        });
    }

    private String panelId() {
        return Integer.toHexString(System.identityHashCode(this));
    }

    /**
     * 尺寸请求只由「项自身是否可见」决定（原生「状态栏微件」菜单勾选 + 项自身
     * 显示条件，由 Manager 装载时决定项的存在；无工厂映射的项回退设置页开关），
     * 绝不随宽度挤压隐藏收缩。
     * <p>
     * Main Toolbar 按组件 preferred 宽度分配空间：一旦挤压隐藏单元格，
     * 面板 preferred 随之变小，工具栏就同步缩小分配宽度，下一次变化又触发
     * 更多挤压——反馈循环会让面板一步步塌缩到只剩一两项（idea.log 2026-09-20
     * 实测：可用宽 283px 一路塌到 203px，17 项只剩 2 项）。宽度自适应只应
     * 在渲染层（{@link #applyVisibility()}）生效，不参与尺寸请求。
     */
    @Override
    public Dimension getPreferredSize() {
        if (cells.isEmpty()) {
            return super.getPreferredSize();
        }
        int width = 0;
        int height = 0;
        for (StatusCell cell : cells) {
            if (cell.item().isVisible()) {
                Dimension preferred = cell.getPreferredSize();
                width += preferred.width;
                height = Math.max(height, preferred.height);
            }
        }
        return new Dimension(width, height);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        bind();
    }

    @Override
    public void removeNotify() {
        unbind();
        super.removeNotify();
    }

    private void bind() {
        if (manager != null) {
            return;
        }
        Project resolved = resolveProject();
        if (resolved == null) {
            DebugLog.log("panel#" + panelId() + " bind: 暂无法解析项目（无可见 frame 项目且非单项目场景）"
                    + " → 订阅 projectOpened 等待重试");
            subscribeToProjectOpen();
            return;
        }
        DebugLog.log("panel#" + panelId() + " bind: 绑定项目 " + resolved.getName());
        project = resolved;
        manager = TopStatusBarManager.getInstance(resolved);
        manager.addChangeListener(modelListener);
        syncCells();
    }

    private void unbind() {
        DebugLog.log("panel#" + panelId() + " unbind: 移除监听并清空 " + cells.size() + " 个单元格");
        if (manager != null) {
            manager.removeChangeListener(modelListener);
            manager = null;
        }
        project = null;
        cells.clear();
        removeAll();
        disposeRetryConnection();
        revalidate();
        repaint();
    }

    private void subscribeToProjectOpen() {
        if (retryConnection != null) {
            return;
        }
        retryConnection = ApplicationManager.getApplication().getMessageBus().connect();
        retryConnection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
            @Override
            public void projectOpened(@NotNull Project opened) {
                DebugLog.log("panel#" + panelId() + " projectOpened: " + opened.getName()
                        + " → 延迟到 EDT 重试 bind");
                SwingUtilities.invokeLater(() -> {
                    if (manager == null && isShowing()) {
                        disposeRetryConnection();
                        bind();
                    } else {
                        DebugLog.log("panel#" + panelId() + " projectOpened 重试: 跳过"
                                + (manager != null ? "（已绑定）" : "（组件当前不可见）"));
                    }
                });
            }
        });
    }

    private void disposeRetryConnection() {
        if (retryConnection != null) {
            retryConnection.dispose();
            retryConnection = null;
        }
    }

    @Nullable
    private Project resolveProject() {
        Object frame = SwingUtilities.getAncestorOfClass(IdeFrame.class, this);
        if (frame instanceof IdeFrame) {
            Project frameProject = ((IdeFrame) frame).getProject();
            if (frameProject != null && !frameProject.isDisposed()) {
                DebugLog.log("panel#" + panelId() + " resolveProject: IdeFrame 携带项目 → " + frameProject.getName());
                return frameProject;
            }
            DebugLog.log("panel#" + panelId() + " resolveProject: IdeFrame 项目为 "
                    + (frameProject == null ? "null" : "已disposed"));
        }
        Project[] open = ProjectManager.getInstance().getOpenProjects();
        if (open.length == 1 && !open[0].isDisposed()) {
            DebugLog.log("panel#" + panelId() + " resolveProject: 回退单项目解析 → " + open[0].getName());
            return open[0];
        }
        DebugLog.log("panel#" + panelId() + " resolveProject: 解析失败（打开项目数=" + open.length + "）");
        return null;
    }

    private void syncCells() {
        TopStatusBarManager currentManager = manager;
        if (currentManager == null) {
            DebugLog.warn("panel#" + panelId() + " syncCells: manager 为空，跳过（监听器在未绑定时被触发？）");
            return;
        }
        List<StatusItem> currentItems = currentManager.getItems();
        if (!sameItems(currentItems)) {
            cells.clear();
            removeAll();
            for (StatusItem item : currentItems) {
                StatusCell cell = new StatusCell(item, project);
                cells.add(cell);
                add(cell);
            }
            // 单元格集合已变，重置隐藏快照，让下一次 applyVisibility 输出完整状态
            lastHiddenIds = null;
            DebugLog.log("panel#" + panelId() + " syncCells: 重建 " + cells.size()
                    + " 个单元格 ids=" + itemIds(currentItems));
        } else {
            for (StatusCell cell : cells) {
                cell.refresh();
            }
        }
        applyVisibility();
        Dimension preferred = getPreferredSize();
        // 最小宽度 0：工具栏空间不足时允许把面板压缩到实际可用宽度，
        // 渲染层按优先级隐藏单元格；preferred 始终请求全量宽度（见其覆写注释）
        setMinimumSize(new Dimension(0, preferred.height));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
        revalidate();
        repaint();
    }

    private static String itemIds(List<StatusItem> items) {
        List<String> ids = new ArrayList<>(items.size());
        for (StatusItem item : items) {
            ids.add(item.getId());
        }
        return ids.toString();
    }

    private boolean sameItems(List<StatusItem> currentItems) {
        if (cells.size() != currentItems.size()) {
            return false;
        }
        for (int i = 0; i < cells.size(); i++) {
            if (!Objects.equals(cells.get(i).item().getId(), currentItems.get(i).getId())) {
                return false;
            }
        }
        return true;
    }

    private void applyVisibility() {
        // Visibility is governed solely by the item itself (its own display
        // conditions). Which items exist at all is decided upstream by the
        // manager from the native "Status Bar Widgets" menu checkboxes
        // (persisted StatusBarWidgetSettings, not bottom-bar widget
        // instances — those are unreliable on 2026.x). No per-cell
        // suppression here on purpose.
        List<String> selfHidden = new ArrayList<>();
        for (StatusCell cell : cells) {
            boolean itemVisible = cell.item().isVisible();
            cell.setVisible(itemVisible);
            if (!itemVisible) {
                selfHidden.add(cell.item().getId());
            }
        }
        int available = getWidth();
        if (available <= 0) {
            return;
        }
        int needed = 0;
        for (StatusCell cell : cells) {
            if (cell.isVisible()) {
                needed += cell.getPreferredSize().width;
            }
        }
        int totalNeeded = needed;
        List<String> squeezed = new ArrayList<>();
        if (needed > available) {
            List<StatusCell> byPriority = new ArrayList<>(cells);
            byPriority.sort(Comparator.comparingInt(cell -> cell.item().getPriority()));
            for (StatusCell cell : byPriority) {
                if (needed <= available) {
                    break;
                }
                if (!cell.isVisible()) {
                    continue;
                }
                needed -= cell.getPreferredSize().width;
                cell.setVisible(false);
                squeezed.add(cell.item().getId());
            }
        }
        // 调试日志：完整快照（自身隐藏 | 挤压隐藏 | 显示）变化时才记录一行，
        // 窗口拖拽 resize 不会刷屏。自身隐藏与宽度挤压分列，避免误读
        String snapshot = selfHidden + "|" + squeezed + "|" + visibleIds();
        if (!snapshot.equals(lastHiddenIds)) {
            lastHiddenIds = snapshot;
            DebugLog.log("panel#" + panelId() + " applyVisibility: 可用宽=" + available
                    + ", 全量需宽=" + totalNeeded
                    + (selfHidden.isEmpty() ? "" : "; 项自身隐藏=" + selfHidden)
                    + (squeezed.isEmpty() ? "; 无宽度挤压" : "; 宽度挤压隐藏=" + squeezed)
                    + ", 显示=" + visibleIds());
        }
    }

    private String visibleIds() {
        List<String> ids = new ArrayList<>();
        for (StatusCell cell : cells) {
            if (cell.isVisible()) {
                ids.add(cell.item().getId());
            }
        }
        return ids.toString();
    }
}
