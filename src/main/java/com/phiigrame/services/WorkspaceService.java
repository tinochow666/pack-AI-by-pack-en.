package com.phiigrame.services;

import javafx.application.Platform;
import javafx.scene.control.TreeView;

import java.io.File;
import java.util.function.Consumer;

/**
 * Lightweight bridge between the AI tool layer and the IDE workspace.  The
 * IDE constructs a single instance, registers its file tree and
 * project-directory accessor with it, and the AI tools can refresh the tree
 * after mutating disk.
 */
public class WorkspaceService {

    private File projectDir;
    private TreeView<?> fileTree;
    private Consumer<Void> refresher;

    /** The current project's root directory, or {@code null} if none is open. */
    public File getProjectDir() { return projectDir; }

    public void setProjectDir(File dir) { this.projectDir = dir; }

    /** Register the UI tree to refresh on changes. */
    public void setFileTree(TreeView<?> tree) { this.fileTree = tree; }

    /**
     * Register a callback that the AI tools can fire to re-scan the
     * workspace (e.g. after create / edit / delete).  This is the
     * single integration point the IDE needs to wire.
     */
    public void setRefresher(Consumer<Void> refresher) { this.refresher = refresher; }

    /** Trigger the registered refresh, or fall back to touching the tree. */
    public void refreshFileTree() {
        if (refresher != null) {
            Platform.runLater(() -> refresher.accept(null));
            return;
        }
        if (fileTree != null && fileTree.getRoot() != null) {
            // Force a visual refresh by toggling expanded state on the root.
            Platform.runLater(() -> {
                boolean wasExpanded = fileTree.getRoot().isExpanded();
                fileTree.getRoot().setExpanded(!wasExpanded);
                fileTree.getRoot().setExpanded(wasExpanded);
            });
        }
    }
}
