package com.xixka.topstatusbar.items;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
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
 * repository and branch are resolved under a read action on a pooled thread,
 * and the presentation is applied back on the EDT. A generation counter
 * drops stale results when updates pile up.
 */
public final class GitBranchItem extends AbstractStatusItem {

    @Nullable
    private MessageBusConnection repositoryConnection;

    /** Monotonic generation of scheduled updates; only touched on the EDT. */
    private int updateGeneration = 0;

    public GitBranchItem() {
        super("gitBranch", 90);
    }

    @Override
    public @Nullable String getPlatformWidgetId() {
        return "git";
    }

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
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (project.isDisposed()) {
                return;
            }
            Snapshot snapshot = ReadAction.compute(() -> computeSnapshot(project, file));
            ApplicationManager.getApplication().invokeLater(() -> {
                if (project.isDisposed() || generation != updateGeneration) {
                    return;
                }
                applySnapshot(snapshot);
            });
        });
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
        // Same async pattern as update(): resolve the repository off the EDT,
        // then show the same popup as the native git branch widget on the EDT.
        VirtualFile file = EditorContext.virtualFile(EditorContext.selectedEditor(effective));
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (effective.isDisposed()) {
                return;
            }
            GitRepository repository = ReadAction.compute(() ->
                    currentRepository(GitRepositoryManager.getInstance(effective), file));
            if (repository == null) {
                return;
            }
            ApplicationManager.getApplication().invokeLater(() -> {
                if (effective.isDisposed()) {
                    return;
                }
                GitBranchesTreePopup.create(effective, repository)
                        .show(new RelativePoint(source, new Point(source.getWidth() / 2, source.getHeight())));
            });
        });
    }

    @Nullable
    private static GitRepository currentRepository(@NotNull GitRepositoryManager manager, @Nullable VirtualFile file) {
        if (file != null) {
            GitRepository repository = manager.getRepositoryForFile(file);
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
