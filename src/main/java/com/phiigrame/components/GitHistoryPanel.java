package com.phiigrame.components;

import com.phiigrame.services.GitService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;

import java.util.List;

/**
 * Git history panel - displays commits for the current project.
 * Appears as a tab in the left sidebar alongside the project file tree.
 */
public class GitHistoryPanel extends VBox {
    
    private final GitService gitService;
    
    private ListView<GitService.GitCommit> commitList;
    private Label branchLabel;
    private Label statusLabel;
    private TextArea detailArea;
    private Button refreshButton;
    
    public GitHistoryPanel(GitService gitService) {
        this.gitService = gitService;
        
        getStyleClass().add("git-panel");
        setPadding(new Insets(0));
        setSpacing(0);
        setFillWidth(true);
        
        buildUi();
    }
    
    private void buildUi() {
        // Header
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: #2d2d2d; -fx-padding: 8 12; " +
                "-fx-border-color: transparent transparent #1e1e1e transparent; " +
                "-fx-border-width: 0 0 1 0;");
        
        Label headerLabel = new Label("Git");
        headerLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-weight: bold; -fx-font-size: 12px;");
        HBox.setHgrow(headerLabel, Priority.ALWAYS);
        
        refreshButton = new Button("Refresh");
        refreshButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #cccccc; " +
                "-fx-cursor: hand; -fx-min-height: 24; -fx-padding: 0 8; -fx-background-radius: 4;");
        refreshButton.setOnAction(e -> refresh());
        
        header.getChildren().addAll(headerLabel, refreshButton);
        
        // Branch info
        HBox branchRow = new HBox(8);
        branchRow.setAlignment(Pos.CENTER_LEFT);
        branchRow.setStyle("-fx-background-color: #2d2d2d; -fx-padding: 4 12 8 12;");
        Label branchTag = new Label("Branch:");
        branchTag.setStyle("-fx-text-fill: #8b8b8b; -fx-font-size: 11px;");
        branchLabel = new Label("-");
        branchLabel.setStyle("-fx-text-fill: #d4d4d4; -fx-font-size: 11px; -fx-font-weight: bold;");
        branchRow.getChildren().addAll(branchTag, branchLabel);
        
        // Commit list
        commitList = new ListView<>();
        commitList.setStyle("-fx-background-color: #1e1e1e; -fx-control-inner-background: #1e1e1e; " +
                "-fx-border-color: transparent;");
        commitList.setCellFactory(lv -> new ListCell<GitService.GitCommit>() {
            @Override
            protected void updateItem(GitService.GitCommit c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    VBox v = new VBox(2);
                    v.setPadding(new Insets(4, 8, 4, 8));
                    
                    Label msg = new Label(c.message);
                    msg.setStyle("-fx-text-fill: #d4d4d4; -fx-font-size: 12px;");
                    msg.setWrapText(false);
                    msg.setMaxWidth(280);
                    
                    HBox meta = new HBox(8);
                    meta.setAlignment(Pos.CENTER_LEFT);
                    Label hash = new Label(c.shortHash);
                    hash.setStyle("-fx-text-fill: #6e7681; -fx-font-size: 10px; -fx-font-family: monospace;");
                    Label author = new Label(c.author);
                    author.setStyle("-fx-text-fill: #6e7681; -fx-font-size: 10px;");
                    Label time = new Label(gitService.formatTimestamp(c.timestamp));
                    time.setStyle("-fx-text-fill: #6e7681; -fx-font-size: 10px;");
                    meta.getChildren().addAll(hash, author, time);
                    
                    v.getChildren().addAll(msg, meta);
                    setGraphic(v);
                    setStyle("-fx-background-color: transparent; -fx-border-color: transparent transparent #2d2d2d transparent; -fx-border-width: 0 0 1 0;");
                }
            }
        });
        commitList.getSelectionModel().selectedItemProperty().addListener((obs, old, newC) -> {
            if (newC != null) showCommitDetail(newC);
        });
        VBox.setVgrow(commitList, Priority.ALWAYS);
        
        // Detail area
        detailArea = new TextArea();
        detailArea.setEditable(false);
        detailArea.setWrapText(false);
        detailArea.setStyle("-fx-control-inner-background: #1e1e1e; " +
                "-fx-text-fill: #d4d4d4; -fx-font-size: 11px; -fx-font-family: monospace; " +
                "-fx-background-color: #1e1e1e; -fx-border-color: #2d2d2d transparent transparent transparent; " +
                "-fx-border-width: 1 0 0 0;");
        detailArea.setPromptText("Select a commit to see details");
        detailArea.setPrefHeight(180);
        
        // Status
        statusLabel = new Label("Not a git repository");
        statusLabel.setStyle("-fx-text-fill: #8b8b8b; -fx-font-size: 10px; " +
                "-fx-padding: 4 12; -fx-background-color: #1e1e1e;");
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        
        getChildren().addAll(header, branchRow, commitList, detailArea, statusLabel);
    }
    
    public void refresh() {
        if (!gitService.isRepo()) {
            statusLabel.setText("Not a git repository");
            commitList.getItems().clear();
            detailArea.clear();
            branchLabel.setText("-");
            return;
        }
        statusLabel.setText("Refreshing...");
        gitService.refresh(success -> {
            Platform.runLater(() -> {
                if (success) {
                    branchLabel.setText(gitService.getCurrentBranch());
                    commitList.getItems().clear();
                    commitList.getItems().addAll(gitService.getCommits());
                    statusLabel.setText(commitList.getItems().size() + " commits");
                } else {
                    statusLabel.setText("Error: " + gitService.getLastError());
                }
            });
        });
    }
    
    private void showCommitDetail(GitService.GitCommit commit) {
        detailArea.setText("Loading...");
        gitService.getCommitDiff(commit.hash, diff -> Platform.runLater(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("Commit:  ").append(commit.hash).append("\n");
            sb.append("Author:  ").append(commit.author).append(" <").append(commit.email).append(">\n");
            sb.append("Date:    ").append(gitService.formatTimestamp(commit.timestamp)).append("\n");
            sb.append("Message: ").append(commit.message).append("\n");
            sb.append("\n");
            sb.append(diff);
            detailArea.setText(sb.toString());
        }));
    }
}
