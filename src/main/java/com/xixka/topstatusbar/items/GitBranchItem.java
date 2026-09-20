package com.xixka.topstatusbar.items;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.xixka.topstatusbar.model.AbstractStatusItem;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import git4idea.GitBranch;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import git4idea.repo.GitRepositoryManager;
import git4idea.ui.branch.BranchIconUtil;
import git4idea.ui.branch.popup.GitBranchesTreePopup;
import com.intellij.ui.awt.RelativePoint;
import com.xixka.topstatusbar.DebugLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import java.awt.*;
import java.util.List;

/**
 * Git 分支: current branch of the repository containing the active file,
 * kept up to date via the official {@link GitRepository#GIT_REPO_CHANGE}
 * message bus topic.
 * <p>
 * Repository lookup runs off the EDT: {@code GitRepositoryManager
 * .getRepositoryForFile} may trigger a synchronous repository-mapping
 * update, which the platform forbids on the EDT ("Do not call synchronous
 * repository update in EDT"). The active file is captured on the EDT, the
 * repository and branch are resolved via a {@link ReadAction#nonBlocking}
 * on a pooled thread, and the presentation is applied back on the EDT. A
 * generation counter drops stale results when updates pile up.
 * <p>
 * Threading note (thread dump 2026-09-18): the resolution MUST use a
 * non-blocking read action. A plain read action keeps its read permit while
 * waiting for the VCS repository-collection lock, and the collection update
 * in turn waits for a read permit while a write is pending — together with
 * a blocking write action on the EDT that forms a hard deadlock cycle and
 * freezes the IDE. A non-blocking read action yields its permit when a
 * write action is requested, which breaks the cycle. The lookup also uses
 * the quick variant ({@code getRepositoryForFileQuick}) that never triggers
 * the synchronous collection update itself.
 */
public final class GitBranchItem extends AbstractStatusItem {

    @Nullable
    private MessageBusConnection repositoryConnection;

    /** Monotonic generation of scheduled updates; only touched on the EDT. */
    private int updateGeneration = 0;

    public GitBranchItem() {
        super("gitBranch", 90);
    }

    // No getPlatformWidgetId: item visibility is governed solely by the
    // plugin settings page (2026-09-18 decision); the platform-widget
    // mirroring was removed entirely from the render pipeline.

    @Override
    protected void install() {
        Project project = project();
        if (project != null) {
            repositoryConnection = project.getMessageBus().connect();
            repositoryConnection.subscribe(GitRepository.GIT_REPO_CHANGE, new GitRepositoryChangeListener() {
                @Override
                public void repositoryChanged(@NotNull GitRepository repository) {
                    update();
                }
            });
        }
        update();
    }

    @Override
    public void uninstall() {
        if (repositoryConnection != null) {
            repositoryConnection.dispose();
            repositoryConnection = null;
        }
        super.uninstall();
    }

    @Override
    public void refresh() {
        update();
    }

    private void update() {
        Project project = project();
        if (project == null) {
            DebugLog.log("gitBranch update: project=null → 隐藏");
            setVisible(false);
            return;
        }
        if (!ApplicationManager.getApplication().isDispatchThread()) {
            ApplicationManager.getApplication().invokeLater(this::update);
            return;
        }
        // EDT: FileEditorManager access must stay here; the generation
        // snapshot lets a late background result be dropped silently.
        VirtualFile file = EditorContext.virtualFile(EditorContext.selectedEditor(project));
        int generation = ++updateGeneration;
        DebugLog.log("gitBranch update: gen=" + generation + ", file="
                + (file == null ? "null（无选中编辑器）" : file.getName()));
        // Block lambda with an explicit return: resolves the Runnable-vs-
        // Callable overload ambiguity of ReadAction.nonBlocking.
        ReadAction.nonBlocking(() -> {
            return computeSnapshot(project, file);
        })
                .expireWith(project)
                .finishOnUiThread(ModalityState.defaultModalityState(), snapshot -> {
                    if (generation != updateGeneration) {
                        DebugLog.log("gitBranch 丢弃过期结果: gen=" + generation
                                + ", 当前=" + updateGeneration);
                        return;
                    }
                    applySnapshot(snapshot);
                })
                .submit(AppExecutorUtil.getAppExecutorService());
    }

    @Nullable
    private static Snapshot computeSnapshot(@NotNull Project project, @Nullable VirtualFile file) {
        GitRepositoryManager repositoryManager = GitRepositoryManager.getInstance(project);
        GitRepository repository = currentRepository(repositoryManager, file);
        if (repository == null) {
            DebugLog.log("gitBranch 后台解析: 未找到仓库（file="
                    + (file == null ? "null" : file.getName())
                    + ", 仓库数=" + repositoryManager.getRepositories().size() + "）→ snapshot=null");
            return null;
        }
        GitBranch branch = repository.getCurrentBranch();
        DebugLog.log("gitBranch 后台解析: 仓库=" + repository.getRoot().getName()
                + ", 分支=" + (branch == null ? "null(分离HEAD)" : branch.getName()));
        String label;
        String tooltip;
        if (branch != null) {
            label = branch.getName();
            tooltip = "Git 分支：" + label;
        } else {
            String revision = repository.getCurrentRevision();
            label = revision == null ? "HEAD" : revision.substring(0, Math.min(7, revision.length()));
            tooltip = "Git：分离 HEAD 状态（" + label + "）";
        }
        int repositoryCount = repositoryManager.getRepositories().size();
        if (repositoryCount > 1) {
            tooltip += "（当前仓库：" + repository.getRoot().getName() + "，共 " + repositoryCount + " 个仓库）";
        }
        return new Snapshot(label, tooltip, BranchIconUtil.Companion.getBranchIcon(repository));
    }

    private void applySnapshot(@Nullable Snapshot snapshot) {
        if (snapshot == null) {
            setVisible(false);
            return;
        }
        setIcon(snapshot.icon);
        setText(snapshot.label);
        setTooltip(snapshot.tooltip);
        setVisible(true);
    }

    @Override
    public void onClick(@Nullable Project project, @NotNull JComponent source) {
        Project effective = project != null ? project : project();
        if (effective == null) {
            return;
        }
        // Same async pattern as update(): resolve the repository off the EDT
        // with a non-blocking read action, then show the same popup as the
        // native git branch widget on the EDT.
        VirtualFile file = EditorContext.virtualFile(EditorContext.selectedEditor(effective));
        ReadAction.nonBlocking(() -> {
            return currentRepository(GitRepositoryManager.getInstance(effective), file);
        })
                .expireWith(effective)
                .finishOnUiThread(ModalityState.defaultModalityState(), repository -> {
                    if (repository == null) {
                        DebugLog.warn("gitBranch onClick: 未解析到仓库，不弹分支面板");
                        return;
                    }
                    // 2024.x–2025.x（编译目标）：直连 git4idea 分支树弹窗 API
                    try {
                        GitBranchesTreePopup.create(effective, repository)
                                .show(new RelativePoint(source, new Point(source.getWidth() / 2, source.getHeight())));
                        return;
                    } catch (LinkageError | RuntimeException e) {
                        // 2026.x：git4idea 拆分为 content 模块后该类已迁移
                        // （idea.log 2026-09-20 实测 ClassNotFoundException:
                        // git4idea.ui.branch.popup.GitBranchesTreePopup），
                        // 编译期无法暴露，只能在运行期降级——改走平台注册动作
                        DebugLog.warn("gitBranch onClick: 直连分支面板 API 失败（" + e
                                + "），降级执行平台 Git.Branches 动作", e);
                    }
                    openBranchesViaPlatformAction(effective, source);
                })
                .submit(AppExecutorUtil.getAppExecutorService());
    }

    /**
     * 跨版本兜底：执行平台注册的 {@code Git.Branches} 动作。该 id 在
     * 2024.1（git4idea.xml）与 2026.x（intellij.vcs.git.backend.xml）中均由
     * {@code git4idea.ui.branch.GitBranchesAction} 注册，实现内部自行解析
     * 仓库并弹出分支面板——动作系统是官方保证的稳定层，不依赖任何具体类。
     */
    private static void openBranchesViaPlatformAction(@NotNull Project project, @NotNull JComponent source) {
        AnAction branchesAction = ActionManager.getInstance().getAction("Git.Branches");
        if (branchesAction == null) {
            DebugLog.warn("gitBranch onClick: 平台未注册 Git.Branches 动作，无法弹出分支面板");
            NotificationGroupManager.getInstance().getNotificationGroup("TopStatusBar")
                    .createNotification("Top Status Bar",
                            "当前 IDE 的 Git 插件未提供分支面板 API，请通过 Git 菜单切换分支。",
                            NotificationType.WARNING)
                    .notify(project);
            return;
        }
        ActionUtil.invokeAction(branchesAction, source, ActionPlaces.MAIN_TOOLBAR, null, null);
        DebugLog.log("gitBranch onClick: 已通过平台动作 Git.Branches 弹出分支面板");
    }

    @Nullable
    private static GitRepository currentRepository(@NotNull GitRepositoryManager manager, @Nullable VirtualFile file) {
        if (file != null) {
            // Quick variant: never triggers the synchronous repository-mapping
            // update, so it cannot park for a long collection update.
            GitRepository repository = manager.getRepositoryForFileQuick(file);
            if (repository != null) {
                return repository;
            }
        }
        List<GitRepository> repositories = manager.getRepositories();
        if (repositories.size() == 1) {
            return repositories.get(0);
        }
        return null;
    }

    private static final class Snapshot {

        private final String label;
        private final String tooltip;
        private final Icon icon;

        private Snapshot(@NotNull String label, @NotNull String tooltip, @Nullable Icon icon) {
            this.label = label;
            this.tooltip = tooltip;
            this.icon = icon;
        }
    }
}
