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
import git4idea.GitBranch;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import git4idea.repo.GitRepositoryManager;
import git4idea.ui.branch.BranchIconUtil;
import git4idea.ui.branch.popup.GitBranchesTreePopup;
import com.intellij.ui.awt.RelativePoint;
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

    // No getPlatformWidgetId override on purpose: the old bottom-bar git
    // widget ("git" StatusBarWidgetFactory) is gone in 2026.x — the branch
    // widget lives in the Main Toolbar as the frontend action
    // "main.toolbar.git.Branches" (GitToolbarWidgetAction), so
    // StatusBar.getWidget("git") always returns null there and mirroring it
    // would permanently hide this item. Even on 241 New UI the old widget is
    // disabled by default when the main toolbar is shown. The git branch
    // item therefore follows only the plugin's own settings.

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
        // Block lambda with an explicit return: resolves the Runnable-vs-
        // Callable overload ambiguity of ReadAction.nonBlocking.
        ReadAction.nonBlocking(() -> {
            return computeSnapshot(project, file);
        })
                .expireWith(project)
                .finishOnUiThread(ModalityState.anyModalityState(), snapshot -> {
                    if (generation != updateGeneration) {
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
            return null;
        }
        GitBranch branch = repository.getCurrentBranch();
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
                .finishOnUiThread(ModalityState.anyModalityState(), repository -> {
                    if (repository == null) {
                        return;
                    }
                    GitBranchesTreePopup.create(effective, repository)
                            .show(new RelativePoint(source, new Point(source.getWidth() / 2, source.getHeight())));
                })
                .submit(AppExecutorUtil.getAppExecutorService());
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
