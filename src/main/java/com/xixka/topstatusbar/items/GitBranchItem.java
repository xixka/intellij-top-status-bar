package com.xixka.topstatusbar.items;

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
 */
public final class GitBranchItem extends AbstractStatusItem {

    @Nullable
    private MessageBusConnection repositoryConnection;

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
        GitRepositoryManager repositoryManager = GitRepositoryManager.getInstance(project);
        GitRepository repository = currentRepository(project, repositoryManager);
        if (repository == null) {
            setVisible(false);
            return;
        }
        GitBranch branch = repository.getCurrentBranch();
        String label;
        String tooltip;
        int repositoryCount = repositoryManager.getRepositories().size();
        if (branch != null) {
            label = branch.getName();
            tooltip = "Git 分支：" + label;
        } else {
            String revision = repository.getCurrentRevision();
            label = revision == null ? "HEAD" : revision.substring(0, Math.min(7, revision.length()));
            tooltip = "Git：分离 HEAD 状态（" + label + "）";
        }
        if (repositoryCount > 1) {
            tooltip += "（当前仓库：" + repository.getRoot().getName() + "，共 " + repositoryCount + " 个仓库）";
        }
        setIcon(BranchIconUtil.Companion.getBranchIcon(repository));
        setText(label);
        setTooltip(tooltip);
        setVisible(true);
    }

    @Override
    public void onClick(@Nullable Project project, @NotNull JComponent source) {
        Project effective = project != null ? project : project();
        if (effective == null) {
            return;
        }
        GitRepositoryManager manager = GitRepositoryManager.getInstance(effective);
        GitRepository repository = currentRepository(effective, manager);
        if (repository == null) {
            return;
        }
        // Same popup as the native git branch widget.
        GitBranchesTreePopup.create(effective, repository)
                .show(new RelativePoint(source, new Point(source.getWidth() / 2, source.getHeight())));
    }

    @Nullable
    private static GitRepository currentRepository(Project project, GitRepositoryManager manager) {
        Editor editor = EditorContext.selectedEditor(project);
        VirtualFile file = EditorContext.virtualFile(editor);
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
}
