package com.phiigrame;

import com.phiigrame.components.AiChatPanel;
import com.phiigrame.components.CodeEditorTab;
import com.phiigrame.components.FileIconProvider;
import com.phiigrame.components.FileTreeItem;
import com.phiigrame.components.GitHistoryPanel;
import com.phiigrame.components.SyntaxHighlighter;
import com.phiigrame.components.TerminalPanel;
import com.phiigrame.components.WelcomeView;
import com.phiigrame.dialogs.LoginDialog;
import com.phiigrame.dialogs.NewProjectDialog;
import com.phiigrame.dialogs.SearchReplaceDialog;
import com.phiigrame.services.AiHistoryService;
import com.phiigrame.services.AiService;
import com.phiigrame.services.AuthService;
import com.phiigrame.services.GitService;
import com.phiigrame.services.ProjectManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PhiigrameApp extends Application {
    
    private ProjectManager projectManager;
    private TreeView<File> fileTree;
    private TabPane tabPane;
    private Label lineColumnLabel;
    private Label projectLabel;
    private Label statusLabel;
    private Label userLabel;
    private File currentProjectDir;
    private Stage primaryStage;
    private TerminalPanel terminalPanel;
    private SplitPane editorSplitPane;
    
    // Toolbar buttons
    private Button runButton;
    private Button stopButton;
    private Button debugButton;
    
    private Label emptyProjectLabel;
    private StackPane emptyProjectContainer;
    
    // New services
    private AuthService authService;
    private com.phiigrame.services.UserDatabase userDb;
    private AiService aiService;
    private com.phiigrame.services.ModelConfigService modelConfigService;
    private AiHistoryService aiHistoryService;
    private GitService gitService;
    private com.phiigrame.services.WorkspaceService workspaceService;
    private com.phiigrame.ai.ToolRegistry toolRegistry;
    private com.phiigrame.services.TrustedFoldersService trustedFoldersService;
    
    private AiChatPanel aiChatPanel;
    private GitHistoryPanel gitHistoryPanel;
    private TabPane sideTabs;
    
    private Alert currentCompletionAlert;
    
    public static void main(String[] args) {
        launch(args);
    }
    
    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("Phiigrame IDE");
        primaryStage.setWidth(1400);
        primaryStage.setHeight(900);
        primaryStage.setMinWidth(1024);
        primaryStage.setMinHeight(768);
        
        // Set application icon (window title bar)
        try {
            javafx.scene.image.Image appIcon = new javafx.scene.image.Image(
                getClass().getResourceAsStream("/logo.png"));
            if (appIcon != null) {
                primaryStage.getIcons().add(appIcon);
            }
        } catch (Exception ignored) {
        }
        
        // Initialize services
        projectManager = new ProjectManager();
        authService = new AuthService();
        userDb = new com.phiigrame.services.UserDatabase();
        trustedFoldersService = new com.phiigrame.services.TrustedFoldersService();
        aiService = new AiService();
        modelConfigService = new com.phiigrame.services.ModelConfigService();
        applyModelConfigToAiService();
        aiHistoryService = new AiHistoryService();
        gitService = new GitService();
        
        // Show login first (Phiigrame Account)
        if (!authService.isLoggedIn()) {
            LoginDialog loginDialog = new LoginDialog(authService);
            if (!loginDialog.showAndWait()) {
                // User closed the login dialog - continue as guest
            }
        }
        
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1e1e1e;");
        
        // Initialize status label first so it is available to children
        statusLabel = new Label("Ready");
        userLabel = new Label();
        updateUserLabel();
        
        VBox topContainer = new VBox();
        MenuBar menuBar = createMenuBar();
        HBox toolbar = createToolbar();
        topContainer.getChildren().addAll(menuBar, toolbar);
        root.setTop(topContainer);
        
        SplitPane mainSplit = new SplitPane();
        mainSplit.setDividerPositions(0.20, 0.78);
        mainSplit.setStyle("-fx-background-color: #1e1e1e;");
        
        VBox sidebar = createSidebar();
        mainSplit.getItems().add(sidebar);
        
        VBox editorArea = createEditorArea();
        mainSplit.getItems().add(editorArea);

        // Workspace + AI tools (read_file, list_dir, create_file, edit_file, delete_file)
        workspaceService = new com.phiigrame.services.WorkspaceService();
        workspaceService.setFileTree(fileTree);
        toolRegistry = new com.phiigrame.ai.ToolRegistry()
                .register(new com.phiigrame.ai.ReadFileTool(workspaceService))
                .register(new com.phiigrame.ai.ListDirTool(workspaceService))
                .register(new com.phiigrame.ai.CreateFileTool(workspaceService))
                .register(new com.phiigrame.ai.EditFileTool(workspaceService))
                .register(new com.phiigrame.ai.WriteFileTool(workspaceService))
                .register(new com.phiigrame.ai.DeleteFileTool(workspaceService))
                .register(new com.phiigrame.ai.GrepTool(workspaceService))
                .register(new com.phiigrame.ai.GlobTool(workspaceService))
                .register(new com.phiigrame.ai.BashTool(workspaceService))
                .register(new com.phiigrame.ai.GitTool(workspaceService, "status"))
                .register(new com.phiigrame.ai.GitTool(workspaceService, "log"))
                .register(new com.phiigrame.ai.GitTool(workspaceService, "diff"))
                .register(new com.phiigrame.ai.GitTool(workspaceService, "add"))
                .register(new com.phiigrame.ai.GitTool(workspaceService, "commit"))
                .register(new com.phiigrame.ai.GitTool(workspaceService, "push"));

        // Right sidebar - AI chat panel (requires login to start a chat)
        aiChatPanel = new AiChatPanel(aiService, aiHistoryService, authService, toolRegistry);
        mainSplit.getItems().add(aiChatPanel);
        
        root.setCenter(mainSplit);
        
        HBox statusBar = createStatusBar();
        root.setBottom(statusBar);
        
        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        
        setupKeyboardShortcuts(scene);
        
        primaryStage.setScene(scene);
        primaryStage.show();
        
        showWelcome();
    }
    
    private HBox createToolbar() {
        HBox toolbar = new HBox();
        toolbar.getStyleClass().add("toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        
        // Use SVG-based icons that always render regardless of installed fonts
        runButton = createIconButton("run", "Run (Shift+F10)");
        runButton.setOnAction(e -> runProject());
        runButton.setDisable(true);
        toolbar.getChildren().add(runButton);
        
        stopButton = createIconButton("stop", "Stop (Ctrl+F2)");
        stopButton.setOnAction(e -> stopProject());
        stopButton.setDisable(true);
        toolbar.getChildren().add(stopButton);
        
        debugButton = createIconButton("debug", "Debug (Shift+F9)");
        debugButton.setOnAction(e -> debugProject());
        debugButton.setDisable(true);
        toolbar.getChildren().add(debugButton);
        
        toolbar.getChildren().add(createToolbarSeparator());
        
        // Build
        Button buildButton = createIconButton("build", "Build Project (Ctrl+F9)");
        buildButton.setOnAction(e -> buildProject());
        toolbar.getChildren().add(buildButton);
        
        toolbar.getChildren().add(createToolbarSeparator());
        
        // Terminal toggle
        Button terminalButton = createIconButton("terminal", "Terminal (Alt+F12)");
        terminalButton.setOnAction(e -> toggleTerminal());
        toolbar.getChildren().add(terminalButton);
        
        toolbar.getChildren().add(createToolbarSeparator());
        
        // New file
        Button newFileButton = createIconButton("newfile", "New File");
        newFileButton.setOnAction(e -> createNewFile());
        toolbar.getChildren().add(newFileButton);
        
        // Save
        Button saveButton = createIconButton("save", "Save (Ctrl+S)");
        saveButton.setOnAction(e -> saveCurrentFile());
        toolbar.getChildren().add(saveButton);
        
        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        toolbar.getChildren().add(spacer);
        
        // Project name on right
        projectLabel = new Label("No project");
        projectLabel.setStyle("-fx-text-fill: #8b8b8b; -fx-font-size: 12px; -fx-padding: 0 12;");
        toolbar.getChildren().add(projectLabel);
        
        return toolbar;
    }
    
    /**
     * Create a toolbar button with a vector (Path-based) icon that renders reliably
     * regardless of installed system fonts.
     */
    private Button createIconButton(String iconType, String tooltip) {
        Button btn = new Button();
        btn.getStyleClass().add("toolbar-button");
        btn.setTooltip(new Tooltip(tooltip));
        btn.setMinSize(34, 34);
        btn.setPrefSize(34, 34);
        btn.setMaxSize(34, 34);
        btn.setPadding(javafx.geometry.Insets.EMPTY);
        btn.setGraphic(buildIcon(iconType));
        return btn;
    }
    
    /**
     * Build an SVG-like vector icon for the given type, returns a StackPane containing the path.
     */
    private javafx.scene.Node buildIcon(String iconType) {
        javafx.scene.layout.StackPane container = new javafx.scene.layout.StackPane();
        container.setMinSize(18, 18);
        container.setPrefSize(18, 18);
        container.setMaxSize(18, 18);
        container.setStyle("-fx-background-color: transparent;");
        
        javafx.scene.shape.SVGPath svg = new javafx.scene.shape.SVGPath();
        svg.setFill(javafx.scene.paint.Color.valueOf("#cccccc"));
        svg.setStroke(javafx.scene.paint.Color.valueOf("#cccccc"));
        svg.setStrokeWidth(0.8);
        svg.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        svg.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        
        switch (iconType) {
            case "run":
                // Play triangle
                svg.setContent("M 3 2 L 16 9 L 3 16 Z");
                svg.setStrokeWidth(0);
                break;
            case "stop":
                // Square
                svg.setContent("M 3 3 L 15 3 L 15 15 L 3 15 Z");
                svg.setStrokeWidth(0);
                break;
            case "debug":
                // Bug: circle with antennas and legs
                svg.setContent(
                    "M 9 4 L 9 6 " +
                    "M 6 6 L 9 6 L 12 6 " +
                    "M 5 8 L 4 7 M 13 8 L 14 7 " +
                    "M 5 10 L 3 10 M 13 10 L 15 10 " +
                    "M 5 12 L 3 13 M 13 12 L 15 13 " +
                    "M 5 14 L 4 15 M 13 14 L 14 15"
                );
                svg.setFill(javafx.scene.paint.Color.TRANSPARENT);
                break;
            case "build":
                // Hammer
                svg.setContent(
                    "M 3 13 L 11 5 L 13 7 L 5 15 Z " +
                    "M 11 5 L 13 3 L 15 5 L 13 7"
                );
                svg.setFill(javafx.scene.paint.Color.TRANSPARENT);
                break;
            case "terminal":
                // Chevron and underscore
                svg.setContent("M 3 5 L 8 9 L 3 13 M 9 13 L 15 13");
                svg.setFill(javafx.scene.paint.Color.TRANSPARENT);
                break;
            case "newfile":
                // Document with +
                svg.setContent(
                    "M 4 2 L 12 2 L 15 5 L 15 16 L 4 16 Z " +
                    "M 12 2 L 12 5 L 15 5 " +
                    "M 9 9 L 9 13 M 7 11 L 11 11"
                );
                svg.setFill(javafx.scene.paint.Color.TRANSPARENT);
                break;
            case "save":
                // Floppy disk
                svg.setContent(
                    "M 3 3 L 13 3 L 15 5 L 15 15 L 3 15 Z " +
                    "M 5 3 L 5 7 L 11 7 L 11 3 " +
                    "M 6 11 L 12 11 L 12 15 L 6 15 Z"
                );
                svg.setFill(javafx.scene.paint.Color.TRANSPARENT);
                break;
            default:
                // Default: small square
                svg.setContent("M 4 4 L 14 4 L 14 14 L 4 14 Z");
                svg.setStrokeWidth(0);
                break;
        }
        
        container.getChildren().add(svg);
        return container;
    }
    
    private Button createToolbarButton(String icon, String tooltip) {
        Button btn = new Button(icon);
        btn.getStyleClass().add("toolbar-button");
        btn.setTooltip(new Tooltip(tooltip));
        return btn;
    }
    
    private Region createToolbarSeparator() {
        Region sep = new Region();
        sep.getStyleClass().add("toolbar-separator");
        return sep;
    }
    
    private void showWelcome() {
        // Clear existing tabs except welcome
        tabPane.getTabs().clear();

        Runnable selectPlugins = () -> {
            if (sideTabs != null) {
                sideTabs.getTabs().stream()
                        .filter(t -> "Plugins".equals(t.getText()))
                        .findFirst()
                        .ifPresent(t -> sideTabs.getSelectionModel().select(t));
            }
        };
        Runnable selectLearn = () -> {
            // For now Learn has no dedicated sidebar tab - the Welcome
            // screen already rendered a Learn section. We just refresh
            // the Welcome tab to make sure the user sees the new view.
            showWelcome();
        };
        Runnable selectCustomize = () -> showWelcome();
        Runnable selectRemote = () -> showWelcome();

        WelcomeView welcome = new WelcomeView(
            this::loadProject,
            this::showNewProjectDialog,
            this::showVcsDialog,
            this::loadProject,
            selectPlugins,
            selectLearn,
            selectCustomize,
            selectRemote,
            userDb
        );

        Tab welcomeTab = new Tab("Welcome");
        welcomeTab.setClosable(false);
        welcomeTab.setContent(welcome);
        tabPane.getTabs().add(welcomeTab);

        fileTree.setRoot(null);
        if (emptyProjectContainer != null) {
            emptyProjectContainer.setVisible(true);
            emptyProjectContainer.setManaged(true);
        }
        projectLabel.setText("No project");
        statusLabel.setText("Ready");

        runButton.setDisable(true);
        stopButton.setDisable(true);
        debugButton.setDisable(true);
    }
    
    private void showNewProjectDialog() {
        NewProjectDialog dialog = new NewProjectDialog();
        Map<String, Object> result = dialog.showAndWait();
        
        if (result != null) {
            statusLabel.setText("Creating project...");
            
            boolean success = projectManager.createProject(result);
            if (success) {
                String name = (String) result.get("name");
                String location = (String) result.get("location");
                File projectDir = new File(location, name);
                statusLabel.setText("Project created: " + name);
                loadProject(projectDir);
            } else {
                statusLabel.setText("Failed to create project");
                showAlert("Error", "Failed to create project. Please check the location and try again.");
            }
        }
    }
    
    private void showVcsDialog() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Clone from Version Control (select local directory)");
        File dir = chooser.showDialog(primaryStage);
        if (dir != null) {
            loadProject(dir);
        }
    }
    
    private void loadProject(File projectDir) {
        if (projectDir == null || !projectDir.exists()) {
            showAlert("Error", "Project directory does not exist");
            return;
        }
        // The IDE will execute scripts and AI tools against this folder.
        // Ask the user once per folder whether they trust its authors.
        if (trustedFoldersService != null
                && !trustedFoldersService.isTrusted(projectDir.toPath())) {
            boolean ok = com.phiigrame.dialogs.TrustDialog.showAndWait(projectDir.toPath());
            if (!ok) {
                statusLabel.setText("Project open cancelled (folder not trusted)");
                return;
            }
            trustedFoldersService.trust(projectDir.toPath());
        }

        currentProjectDir = projectDir;
        projectLabel.setText(projectDir.getName());
        statusLabel.setText("Project loaded: " + projectDir.getName());

        // Keep the AI tool layer in sync with the current project root.
        if (workspaceService != null) {
            workspaceService.setProjectDir(projectDir);
            workspaceService.setRefresher(v -> {
                if (fileTree != null) {
                    FileTreeItem refreshed = new FileTreeItem(projectDir);
                    refreshed.setExpanded(true);
                    loadDirectory(refreshed);
                    fileTree.setRoot(refreshed);
                    fileTree.refresh();
                }
                // The AI tools (write_file / edit_file / create_file /
                // delete_file) call refreshFileTree() after touching a
                // file.  If any of those files is already open in a tab
                // and the user hasn't edited it locally, reload it from
                // disk so the editor matches reality.
                if (tabPane != null) {
                    for (Tab t : tabPane.getTabs()) {
                        if (t instanceof CodeEditorTab) {
                            ((CodeEditorTab) t).reloadFromDiskIfUnmodified();
                        }
                    }
                }
            });
        }

        // Build file tree
        FileTreeItem rootItem = new FileTreeItem(projectDir);
        rootItem.setExpanded(true);
        loadDirectory(rootItem);
        
        fileTree.setRoot(rootItem);
        fileTree.refresh();
        if (emptyProjectContainer != null) {
            emptyProjectContainer.setVisible(false);
            emptyProjectContainer.setManaged(false);
        }
        
        // Refresh git info for the project (async). When the result lands,
        // we also stamp the recent-projects row with the current branch so
        // the welcome screen can show "untitled  master" like IntelliJ does.
        gitService.setProject(projectDir, isRepo -> {
            final String branch = isRepo ? gitService.getCurrentBranch() : null;
            if (userDb != null) {
                userDb.recordRecent(projectDir.getAbsolutePath(), branch);
            }
            Platform.runLater(() -> {
                if (isRepo && gitHistoryPanel != null) {
                    gitHistoryPanel.refresh();
                }
            });
        });
        // Record the project immediately, even before git finishes -
        // the welcome screen will see the row right away, with a blank
        // branch field that fills in once git resolves.
        if (userDb != null) {
            userDb.recordRecent(projectDir.getAbsolutePath(), null);
        }
        
        // Force UI refresh on JavaFX thread
        Platform.runLater(() -> {
            fileTree.requestLayout();
            fileTree.refresh();
        });
        
        // Remove welcome tab if present
        tabPane.getTabs().removeIf(tab -> tab.getText().equals("Welcome"));
        
        // Enable buttons
        runButton.setDisable(false);
        stopButton.setDisable(false);
        debugButton.setDisable(false);
        
        primaryStage.setTitle("Phiigrame IDE - " + projectDir.getName());
    }
    
    private void loadDirectory(FileTreeItem directoryItem) {
        File directory = directoryItem.getFile();
        File[] files = directory.listFiles();
        
        if (files != null) {
            // Sort: directories first, then files
            java.util.Arrays.sort(files, (a, b) -> {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
            
            for (File file : files) {
                if (!file.getName().startsWith(".") && 
                    !file.getName().equals("build") && 
                    !file.getName().equals("node_modules")) {
                    FileTreeItem item = new FileTreeItem(file);
                    if (file.isDirectory()) {
                        loadDirectory(item);
                    }
                    directoryItem.getChildren().add(item);
                }
            }
        }
    }    
    private void openFile(File file) {
        try {
            if (file == null || !file.exists()) {
                showAlert("Error", "File does not exist: " + (file == null ? "null" : file.getAbsolutePath()));
                return;
            }
            if (file.isDirectory()) {
                return; // ignore directories
            }
            
            for (Tab tab : tabPane.getTabs()) {
                if (tab instanceof CodeEditorTab) {
                    CodeEditorTab codeTab = (CodeEditorTab) tab;
                    if (codeTab.getFile() != null && codeTab.getFile().equals(file)) {
                        tabPane.getSelectionModel().select(tab);
                        return;
                    }
                }
            }
            
            CodeEditorTab newTab = new CodeEditorTab(file);
            
            newTab.getCodeArea().caretPositionProperty().addListener((obs, oldVal, newVal) -> {
                updateCursorPosition(newTab.getCodeArea());
            });
            
            // Wire up AI code completion on Ctrl+Space (handled in code area)
            // The completion handler is per-tab so the popup is wired to
            // the right editor.  We also feed the AI result back into the
            // floating popup so the user can accept / reject it from
            // there instead of through an Alert dialog.
            final CodeEditorTab theTab = newTab;
            newTab.setCompletionHandler(req -> requestAiCompletion(theTab, req));
            newTab.getCodeArea().setOnKeyPressed(event -> {
                if (event.isControlDown() && event.getCode() == KeyCode.SPACE) {
                    theTab.triggerCompletion();
                    event.consume();
                }
            });

            // When the editor loses focus / the user clicks elsewhere,
            // hide the popup so it doesn't dangle.
            newTab.getCodeArea().focusedProperty().addListener((o, ov, nv) -> {
                if (!nv) {
                    newTab.hideCompletion();
                }
            });
            
            tabPane.getTabs().add(newTab);
            tabPane.getSelectionModel().select(newTab);
            statusLabel.setText("Opened: " + file.getName());
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Failed to open file: " + e.getMessage());
        }
    }
    
    /**
     * Request an AI completion for the given request and feed the result
     * back into the floating completion popup.  The user can then accept
     * the suggestion (Enter / Tab) or dismiss it (Esc).  Falls back to a
     * confirmation dialog if the popup is somehow not visible.
     */
    private void requestAiCompletion(CodeEditorTab tab, CodeEditorTab.CompletionRequest req) {
        if (tab == null) return;
        statusLabel.setText("Asking local AI for completion...");

        // Make sure the local server is up (no-op if it already is)
        aiService.checkAvailability(ok -> Platform.runLater(() -> {
            if (!ok) {
                statusLabel.setText("AI server not available: " + aiService.getLastError());
                return;
            }
            aiService.completeCode(req.prefix, req.suffix, req.language, req.fileName,
                    completion -> Platform.runLater(() -> {
                        if (completion == null || completion.isEmpty()) {
                            statusLabel.setText("AI returned no completion");
                            return;
                        }
                        if (completion.startsWith("Error:")) {
                            statusLabel.setText("AI error: " + completion);
                            return;
                        }
                        // Feed the suggestion back into the popup so the
                        // user can pick it from the existing list.
                        tab.feedAiCompletion(completion);
                        if (!tab.isCompletionVisible()) {
                            // popup was dismissed while we waited - show
                            // a confirmation dialog as a fallback.
                            showAiCompletionDialog(tab, completion);
                        } else {
                            statusLabel.setText("AI completion ready (Enter to accept)");
                        }
                    }));
        }));
    }

    private void showAiCompletionDialog(CodeEditorTab tab, String completion) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("AI Code Completion");
        alert.setHeaderText("Suggested by " + aiService.getModelName() + " (local)");

        TextArea textArea = new TextArea(completion);
        textArea.setEditable(true);
        textArea.setWrapText(false);
        textArea.setPrefSize(560, 320);
        textArea.setStyle("-fx-font-family: 'JetBrains Mono', monospace; -fx-font-size: 12px;");
        alert.getDialogPane().setContent(textArea);

        alert.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                tab.applyCompletion(textArea.getText());
                statusLabel.setText("AI completion applied");
            }
        });
    }
    
    private void updateCursorPosition(org.fxmisc.richtext.CodeArea codeArea) {
        int caretPos = codeArea.getCaretPosition();
        int currentLine = 0;
        int currentCol = 0;
        
        String text = codeArea.getText();
        for (int i = 0; i < caretPos && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                currentLine++;
                currentCol = 0;
            } else {
                currentCol++;
            }
        }
        
        lineColumnLabel.setText("Ln " + (currentLine + 1) + ", Col " + (currentCol + 1));
    }
    
    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();
        
        // File menu
        Menu fileMenu = new Menu("File");
        
        MenuItem newProjectItem = new MenuItem("New Project...");
        newProjectItem.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        newProjectItem.setOnAction(e -> showNewProjectDialog());
        
        MenuItem openProjectItem = new MenuItem("Open Project...");
        openProjectItem.setOnAction(e -> openProject());
        MenuItem openRecentItem = new MenuItem("Open Recent");
        MenuItem closeProjectItem = new MenuItem("Close Project");
        closeProjectItem.setOnAction(e -> showWelcome());
        
        fileMenu.getItems().addAll(newProjectItem, openProjectItem, openRecentItem, 
                                    new SeparatorMenuItem(), closeProjectItem,
                                    new SeparatorMenuItem());
        
        MenuItem newFileItem = new MenuItem("New File");
        newFileItem.setOnAction(e -> createNewFile());
        MenuItem newDirectoryItem = new MenuItem("New Directory");
        newDirectoryItem.setOnAction(e -> createNewDirectory());
        fileMenu.getItems().addAll(newFileItem, newDirectoryItem, new SeparatorMenuItem());
        
        MenuItem saveItem = new MenuItem("Save");
        saveItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
        saveItem.setOnAction(e -> saveCurrentFile());
        MenuItem saveAllItem = new MenuItem("Save All");
        saveAllItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        saveAllItem.setOnAction(e -> saveAllFiles());
        fileMenu.getItems().addAll(saveItem, saveAllItem, new SeparatorMenuItem());
        
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> Platform.exit());
        fileMenu.getItems().add(exitItem);
        
        // Edit menu
        Menu editMenu = new Menu("Edit");
        MenuItem undoItem = new MenuItem("Undo");
        undoItem.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN));
        undoItem.setOnAction(e -> undo());
        MenuItem redoItem = new MenuItem("Redo");
        redoItem.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        redoItem.setOnAction(e -> redo());
        
        editMenu.getItems().addAll(undoItem, redoItem, new SeparatorMenuItem());
        
        MenuItem cutItem = new MenuItem("Cut");
        cutItem.setAccelerator(new KeyCodeCombination(KeyCode.X, KeyCombination.CONTROL_DOWN));
        cutItem.setOnAction(e -> cut());
        MenuItem copyItem = new MenuItem("Copy");
        copyItem.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN));
        copyItem.setOnAction(e -> copy());
        MenuItem pasteItem = new MenuItem("Paste");
        pasteItem.setAccelerator(new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN));
        pasteItem.setOnAction(e -> paste());
        editMenu.getItems().addAll(cutItem, copyItem, pasteItem, new SeparatorMenuItem());
        
        MenuItem findItem = new MenuItem("Find...");
        findItem.setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN));
        findItem.setOnAction(e -> showSearchReplace());
        MenuItem findInPathItem = new MenuItem("Find in Files...");
        findInPathItem.setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        findInPathItem.setOnAction(e -> showFindInPath());
        editMenu.getItems().addAll(findItem, findInPathItem, new SeparatorMenuItem());
        
        MenuItem gotoLineItem = new MenuItem("Go to Line...");
        gotoLineItem.setAccelerator(new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN));
        gotoLineItem.setOnAction(e -> gotoLine());
        editMenu.getItems().add(gotoLineItem);
        
        // View menu
        Menu viewMenu = new Menu("View");
        MenuItem toggleTerminalItem = new MenuItem("Tool Windows");
        Menu terminalMenu = new Menu("Tool Windows");
        MenuItem terminalItem = new MenuItem("Terminal");
        terminalItem.setAccelerator(new KeyCodeCombination(KeyCode.F12));
        terminalItem.setOnAction(e -> toggleTerminal());
        terminalMenu.getItems().add(terminalItem);
        viewMenu.getItems().addAll(terminalMenu);
        
        // AI menu
        Menu aiMenu = new Menu("AI");
        MenuItem aiChatItem = new MenuItem("AI Chat...");
        aiChatItem.setAccelerator(new KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        aiChatItem.setOnAction(e -> {
            if (aiChatPanel != null) {
                aiChatPanel.requestFocus();
            }
        });
        MenuItem aiCompleteItem = new MenuItem("AI Code Completion");
        aiCompleteItem.setAccelerator(new KeyCodeCombination(KeyCode.SPACE, KeyCombination.CONTROL_DOWN));
        aiCompleteItem.setOnAction(e -> {
            Tab sel = tabPane.getSelectionModel().getSelectedItem();
            if (sel instanceof CodeEditorTab) {
                ((CodeEditorTab) sel).triggerCompletion();
            }
        });
        MenuItem aiSettingsItem = new MenuItem("AI Settings...");
        aiSettingsItem.setOnAction(e -> showAiSettings());
        MenuItem aiModelItem = new MenuItem("Model Manager...");
        aiModelItem.setOnAction(e -> showModelManager());
        aiMenu.getItems().addAll(aiChatItem, aiCompleteItem, new SeparatorMenuItem(),
                aiSettingsItem, aiModelItem);
        
        // Account menu
        Menu accountMenu = new Menu("Account");
        MenuItem signInItem = new MenuItem("Sign In / Create Account...");
        signInItem.setOnAction(e -> {
            new LoginDialog(authService).showAndWait();
            updateUserLabel();
            if (aiChatPanel != null) aiChatPanel.applyLoginState();
        });
        MenuItem signOutItem = new MenuItem("Sign Out");
        signOutItem.setOnAction(e -> {
            authService.logout();
            updateUserLabel();
            if (aiChatPanel != null) aiChatPanel.applyLoginState();
            new LoginDialog(authService).showAndWait();
            updateUserLabel();
            if (aiChatPanel != null) aiChatPanel.applyLoginState();
        });
        accountMenu.getItems().addAll(signInItem, signOutItem);
        
        // Help menu
        Menu helpMenu = new Menu("Help");
        MenuItem aboutItem = new MenuItem("About Phiigrame");
        aboutItem.setOnAction(e -> showAbout());
        helpMenu.getItems().add(aboutItem);
        
        menuBar.getMenus().addAll(fileMenu, editMenu, viewMenu, aiMenu, accountMenu, helpMenu);
        
        return menuBar;
    }
    
    private void showAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About Phiigrame");
        alert.setHeaderText("Phiigrame IDE 2024.1");
        alert.setContentText("Smart IDE for Kotlin, Java, and Groovy with Spring Boot support.\n\n" +
                              "Built with JavaFX and RichTextFX.");
        alert.showAndWait();
    }
    
    private void setupKeyboardShortcuts(Scene scene) {
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.S && event.isControlDown()) {
                event.consume();
                saveCurrentFile();
            } else if (event.getCode() == KeyCode.F && event.isControlDown()) {
                event.consume();
                showSearchReplace();
            } else if (event.getCode() == KeyCode.F12) {
                event.consume();
                toggleTerminal();
            } else if (event.getCode() == KeyCode.F5) {
                event.consume();
                if (event.isShiftDown()) {
                    debugProject();
                } else {
                    runProject();
                }
            } else if (event.getCode() == KeyCode.F2 && event.isControlDown()) {
                event.consume();
                stopProject();
            } else if (event.getCode() == KeyCode.F9 && event.isControlDown()) {
                event.consume();
                buildProject();
            }
        });
    }
    
    private void openProject() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Open Project");
        File selectedDir = chooser.showDialog(primaryStage);
        
        if (selectedDir != null) {
            loadProject(selectedDir);
        }
    }
    
    private void createNewFile() {
        if (currentProjectDir == null) {
            showAlert("Warning", "Please open or create a project first");
            return;
        }
        
        TextInputDialog dialog = new TextInputDialog("NewFile.java");
        dialog.setTitle("New File");
        dialog.setHeaderText(null);
        dialog.setContentText("File name:");
        
        dialog.showAndWait().ifPresent(fileName -> {
            File newFile = new File(currentProjectDir, fileName);
            try {
                if (newFile.createNewFile()) {
                    openFile(newFile);
                    loadProject(currentProjectDir); // refresh tree
                    statusLabel.setText("File created: " + fileName);
                } else {
                    showAlert("Error", "File already exists");
                }
            } catch (IOException e) {
                showAlert("Error", "Failed to create file: " + e.getMessage());
            }
        });
    }
    
    private void createNewDirectory() {
        if (currentProjectDir == null) {
            showAlert("Warning", "Please open or create a project first");
            return;
        }
        
        TextInputDialog dialog = new TextInputDialog("new_folder");
        dialog.setTitle("New Directory");
        dialog.setHeaderText(null);
        dialog.setContentText("Directory name:");
        
        dialog.showAndWait().ifPresent(dirName -> {
            File newDir = new File(currentProjectDir, dirName);
            if (newDir.mkdirs()) {
                loadProject(currentProjectDir);
                statusLabel.setText("Directory created: " + dirName);
            } else {
                showAlert("Error", "Failed to create directory");
            }
        });
    }
    
    private void saveCurrentFile() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab instanceof CodeEditorTab) {
            CodeEditorTab codeTab = (CodeEditorTab) selectedTab;
            codeTab.saveFile();
            statusLabel.setText("Saved: " + codeTab.getFile().getName());
        }
    }
    
    private void saveAllFiles() {
        int count = 0;
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof CodeEditorTab) {
                CodeEditorTab codeTab = (CodeEditorTab) tab;
                if (codeTab.isModified()) {
                    codeTab.saveFile();
                    count++;
                }
            }
        }
        statusLabel.setText("Saved " + count + " file(s)");
    }
    
    private void undo() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab instanceof CodeEditorTab) {
            CodeEditorTab codeTab = (CodeEditorTab) selectedTab;
            codeTab.getCodeArea().undo();
        }
    }
    
    private void redo() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab instanceof CodeEditorTab) {
            CodeEditorTab codeTab = (CodeEditorTab) selectedTab;
            codeTab.getCodeArea().redo();
        }
    }
    
    private void cut() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab instanceof CodeEditorTab) {
            CodeEditorTab codeTab = (CodeEditorTab) selectedTab;
            codeTab.getCodeArea().cut();
        }
    }
    
    private void copy() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab instanceof CodeEditorTab) {
            CodeEditorTab codeTab = (CodeEditorTab) tabPane.getSelectionModel().getSelectedItem();
            codeTab.getCodeArea().copy();
        }
    }
    
    private void paste() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab instanceof CodeEditorTab) {
            CodeEditorTab codeTab = (CodeEditorTab) tabPane.getSelectionModel().getSelectedItem();
            codeTab.getCodeArea().paste();
        }
    }
    
    private void showSearchReplace() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab instanceof CodeEditorTab) {
            CodeEditorTab codeTab = (CodeEditorTab) selectedTab;
            SearchReplaceDialog dialog = new SearchReplaceDialog(codeTab.getCodeArea());
            dialog.show();
        }
    }
    
    private void showFindInPath() {
        if (currentProjectDir == null) {
            showAlert("Warning", "Please open a project first");
            return;
        }
        
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Find in Files");
        dialog.setHeaderText(null);
        dialog.setContentText("Search text:");
        
        dialog.showAndWait().ifPresent(searchText -> {
            try {
                List<File> codeFiles = Files.walk(currentProjectDir.toPath())
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.toString().toLowerCase();
                        return name.endsWith(".java") || name.endsWith(".kt") || 
                               name.endsWith(".groovy") || name.endsWith(".xml") ||
                               name.endsWith(".gradle") || name.endsWith(".properties");
                    })
                    .map(path -> path.toFile())
                    .collect(Collectors.toList());
                
                StringBuilder results = new StringBuilder();
                int count = 0;
                
                for (File file : codeFiles) {
                    String content = Files.readString(file.toPath());
                    if (content.contains(searchText)) {
                        results.append(file.getAbsolutePath()).append("\n");
                        count++;
                    }
                }
                
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Search Results");
                alert.setHeaderText("Found " + count + " matching file(s)");
                alert.setContentText(count > 0 ? results.toString() : "No matches found");
                alert.getDialogPane().setPrefWidth(700);
                alert.getDialogPane().setPrefHeight(400);
                alert.showAndWait();
                
            } catch (IOException e) {
                showAlert("Error", "Search failed: " + e.getMessage());
            }
        });
    }
    
    private void gotoLine() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (!(selectedTab instanceof CodeEditorTab)) {
            showAlert("Warning", "Please open a file first");
            return;
        }
        
        CodeEditorTab codeTab = (CodeEditorTab) selectedTab;
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Go to Line");
        dialog.setHeaderText(null);
        dialog.setContentText("Line number:");
        
        dialog.showAndWait().ifPresent(lineStr -> {
            try {
                int line = Integer.parseInt(lineStr);
                int totalLines = codeTab.getCodeArea().getParagraphs().size();
                if (line >= 1 && line <= totalLines) {
                    codeTab.getCodeArea().moveTo(line - 1);
                } else {
                    showAlert("Error", "Line number out of range (1-" + totalLines + ")");
                }
            } catch (NumberFormatException e) {
                showAlert("Error", "Please enter a valid line number");
            }
        });
    }
    
    private void toggleTerminal() {
        if (terminalPanel.getParent() != null) {
            editorSplitPane.getItems().remove(terminalPanel);
        } else {
            editorSplitPane.getItems().add(terminalPanel);
            editorSplitPane.setDividerPositions(0.7);
        }
    }
    
    /**
     * Build the current project
     */
    private void buildProject() {
        if (currentProjectDir == null) {
            showAlert("Warning", "No project is open");
            return;
        }
        
        statusLabel.setText("Building project...");
        ensureTerminalVisible();
        
        String buildCommand = detectBuildCommand("build");
        if (buildCommand == null) {
            showAlert("Error", "No build file found (build.gradle or pom.xml)");
            return;
        }
        
        terminalPanel.clear();
        terminalPanel.executeCommandAsync(buildCommand, currentProjectDir);
        statusLabel.setText("Build started");
    }
    
    /**
     * Run the current project
     */
    private void runProject() {
        if (currentProjectDir == null) {
            showAlert("Warning", "No project is open");
            return;
        }
        
        statusLabel.setText("Running project...");
        ensureTerminalVisible();
        
        String runCommand = detectBuildCommand("run");
        if (runCommand == null) {
            showAlert("Error", "No build file found (build.gradle or pom.xml)");
            return;
        }
        
        terminalPanel.clear();
        terminalPanel.executeCommandAsync(runCommand, currentProjectDir);
        stopButton.setDisable(false);
        statusLabel.setText("Running...");
    }
    
    /**
     * Stop the current running process
     */
    private void stopProject() {
        terminalPanel.stopCurrentProcess();
        statusLabel.setText("Stopped");
    }
    
    /**
     * Debug the current project
     */
    private void debugProject() {
        if (currentProjectDir == null) {
            showAlert("Warning", "No project is open");
            return;
        }
        
        statusLabel.setText("Starting debug...");
        ensureTerminalVisible();
        
        String debugCommand = detectBuildCommand("run --debug-jvm");
        if (debugCommand == null) {
            showAlert("Error", "No build file found (build.gradle or pom.xml)");
            return;
        }
        
        terminalPanel.clear();
        terminalPanel.executeCommandAsync(debugCommand, currentProjectDir);
        statusLabel.setText("Debug started - listening on port 5005");
    }
    
    /**
     * Detect the build command based on the project structure
     */
    private String detectBuildCommand(String task) {
        File gradlew = new File(currentProjectDir, "gradlew.bat");
        if (gradlew.exists()) {
            return ".\\gradlew.bat " + task;
        }
        
        File gradlewUnix = new File(currentProjectDir, "gradlew");
        if (gradlewUnix.exists()) {
            return "./gradlew " + task;
        }
        
        File buildGradle = new File(currentProjectDir, "build.gradle");
        File buildGradleKts = new File(currentProjectDir, "build.gradle.kts");
        if (buildGradle.exists() || buildGradleKts.exists()) {
            return "gradle " + task;
        }
        
        File pomXml = new File(currentProjectDir, "pom.xml");
        if (pomXml.exists()) {
            if (task.equals("build")) return "mvn clean compile";
            if (task.startsWith("run")) return "mvn exec:java";
            return "mvn " + task;
        }
        
        return null;
    }
    
    private void ensureTerminalVisible() {
        if (terminalPanel.getParent() == null) {
            toggleTerminal();
        }
    }
    
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Show AI settings dialog - configure the bundled llama.cpp engine.
     */
    private void showAiSettings() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("AI Settings");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox content = new VBox(14);
        content.setPadding(new javafx.geometry.Insets(20));
        content.setStyle("-fx-background-color: #2d2d2d;");
        content.setPrefWidth(560);

        Label title = new Label("AI Backend (Local Qwen2.5-Coder, pure Java)");
        title.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 14px; -fx-font-weight: bold;");
        content.getChildren().add(title);

        // Model file status
        Label modelStatus = new Label("Model file: detecting...");
        modelStatus.setStyle("-fx-text-fill: #d4d4d4; -fx-font-size: 12px;");
        content.getChildren().add(modelStatus);
        Runnable refreshStatus = () -> {
            if (aiService.isModelFilePresent()) {
                long bytes;
                try { bytes = java.nio.file.Files.size(aiService.getModelPath()); }
                catch (Exception e) { bytes = 0; }
                String size = String.format("%.2f GB", bytes / 1e9);
                modelStatus.setText("Model file: " + size + " cached at "
                        + aiService.getModelPath().getParent());
                modelStatus.setStyle("-fx-text-fill: #6a9955; -fx-font-size: 12px; -fx-font-weight: bold;");
            } else {
                modelStatus.setText("Model file: not downloaded yet");
                modelStatus.setStyle("-fx-text-fill: #d4d4d4; -fx-font-size: 12px;");
            }
        };
        refreshStatus.run();

        Label infoLabel = new Label(
            "Phiigrame AI runs Qwen2.5-Coder-1.5B ENTIRELY on this machine.\n" +
            "No Python, no Ollama, no external service. The model is executed\n" +
            "by llama.cpp (bundled as a native JNI library) and loads from a\n" +
            "Q4_K_M GGUF file downloaded from Hugging Face.\n\n" +
            "First AI use will download ~1.1 GB of model weights, then run\n" +
            "locally. Subsequent runs use the cached file at\n"
            + aiService.getModelPath().getParent().toString() + " .");
        infoLabel.setStyle("-fx-text-fill: #8b8b8b; -fx-font-size: 11px;");
        infoLabel.setWrapText(true);
        content.getChildren().add(infoLabel);

        Label modelLabel = new Label("HF repo (owner/name):");
        modelLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 11px;");
        content.getChildren().add(modelLabel);
        TextField modelField = new TextField(aiService.getModelRepo());
        modelField.setPromptText("e.g. Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF");
        modelField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: #d4d4d4; " +
                "-fx-prompt-text-fill: #6e7681; -fx-border-color: #454545; -fx-border-radius: 4; " +
                "-fx-background-radius: 4; -fx-padding: 8 12;");
        content.getChildren().add(modelField);

        Label fileLabel = new Label("GGUF file:");
        fileLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 11px;");
        content.getChildren().add(fileLabel);
        TextField fileField = new TextField(aiService.getGgufFile());
        fileField.setPromptText("e.g. qwen2.5-coder-1.5b-instruct-q4_k_m.gguf");
        fileField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: #d4d4d4; " +
                "-fx-prompt-text-fill: #6e7681; -fx-border-color: #454545; -fx-border-radius: 4; " +
                "-fx-background-radius: 4; -fx-padding: 8 12;");
        content.getChildren().add(fileField);

        Label ctxLabel = new Label("Context size:");
        ctxLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 11px;");
        content.getChildren().add(ctxLabel);
        TextField ctxField = new TextField(String.valueOf(aiService.getContextSize()));
        ctxField.setPromptText("2048 - 8192");
        ctxField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: #d4d4d4; " +
                "-fx-prompt-text-fill: #6e7681; -fx-border-color: #454545; -fx-border-radius: 4; " +
                "-fx-background-radius: 4; -fx-padding: 8 12;");
        content.getChildren().add(ctxField);

        Label mirrorLabel = new Label("Download mirror (blank = official huggingface.co):");
        mirrorLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 11px;");
        content.getChildren().add(mirrorLabel);
        TextField mirrorField = new TextField(aiService.getMirrorBase());
        mirrorField.setPromptText("https://hf-mirror.com  (China) or leave blank for huggingface.co");
        mirrorField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: #d4d4d4; " +
                "-fx-prompt-text-fill: #6e7681; -fx-border-color: #454545; -fx-border-radius: 4; " +
                "-fx-background-radius: 4; -fx-padding: 8 12;");
        content.getChildren().add(mirrorField);

        // Live status area
        Label statusInfo = new Label("Current AI server: " + (aiService.isAvailable() ? "Connected" : "Not running"));
        statusInfo.setStyle("-fx-text-fill: " + (aiService.isAvailable() ? "#6a9955" : "#f48771") +
                "; -fx-font-size: 12px; -fx-font-weight: bold;");
        statusInfo.setWrapText(true);
        content.getChildren().add(statusInfo);

        // Progress bar (used during download)
        javafx.scene.control.ProgressBar progressBar = new javafx.scene.control.ProgressBar(0);
        progressBar.setVisible(false);
        progressBar.setPrefWidth(Double.MAX_VALUE);
        content.getChildren().add(progressBar);
        aiService.setDownloadProgressListener(pct -> {
            progressBar.setVisible(true);
            progressBar.setProgress(pct / 100.0);
            statusInfo.setText("Downloading model: " + pct + "%");
            statusInfo.setStyle("-fx-text-fill: #d4d4d4; -fx-font-size: 12px;");
        });

        HBox buttonsRow = new HBox(8);

        Button testBtn = new Button("Start / Test");
        testBtn.setStyle("-fx-background-color: #0e639c; -fx-text-fill: white; -fx-padding: 6 12; " +
                "-fx-background-radius: 4; -fx-cursor: hand;");
        testBtn.setOnAction(e -> {
            // Apply field changes
            if (!modelField.getText().isBlank()) aiService.setModelRepo(modelField.getText().trim());
            if (!fileField.getText().isBlank()) aiService.setGgufFile(fileField.getText().trim());
            try { aiService.setContextSize(Integer.parseInt(ctxField.getText().trim())); }
            catch (NumberFormatException nfe) { /* keep existing */ }
            aiService.setMirrorBase(mirrorField.getText());
            statusInfo.setText("Starting local AI engine (first run loads model, 1-2 min)...");
            statusInfo.setStyle("-fx-text-fill: #d4d4d4; -fx-font-size: 12px;");
            aiService.checkAvailability(ok -> Platform.runLater(() -> {
                progressBar.setVisible(false);
                if (ok) {
                    statusInfo.setText("Connected. Model loaded. AI is online.");
                    statusInfo.setStyle("-fx-text-fill: #6a9955; -fx-font-size: 12px; -fx-font-weight: bold;");
                    refreshStatus.run();
                } else {
                    statusInfo.setText("Failed: " + aiService.getLastError());
                    statusInfo.setStyle("-fx-text-fill: #f48771; -fx-font-size: 12px; -fx-font-weight: bold;");
                }
            }));
        });

        Button stopBtn = new Button("Stop");
        stopBtn.setStyle("-fx-background-color: #5a2828; -fx-text-fill: white; -fx-padding: 6 12; " +
                "-fx-background-radius: 4; -fx-cursor: hand;");
        stopBtn.setOnAction(e -> {
            aiService.shutdown();
            statusInfo.setText("Stopped. Click Start to launch again.");
            statusInfo.setStyle("-fx-text-fill: #f48771; -fx-font-size: 12px;");
        });

        Button openCacheBtn = new Button("Open cache folder");
        openCacheBtn.setStyle("-fx-background-color: #285a2d; -fx-text-fill: white; -fx-padding: 6 12; " +
                "-fx-background-radius: 4; -fx-cursor: hand;");
        openCacheBtn.setOnAction(e -> {
            try {
                java.nio.file.Files.createDirectories(aiService.getModelCacheDir());
                java.awt.Desktop.getDesktop().open(aiService.getModelCacheDir().toFile());
            } catch (Exception ex) {
                showAlert("Cache folder", aiService.getModelCacheDir().toString());
            }
        });

        buttonsRow.getChildren().addAll(testBtn, stopBtn, openCacheBtn);
        content.getChildren().add(buttonsRow);

        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    /** Open the model manager dialog (preset switching, remote API, etc.). */
    private void showModelManager() {
        com.phiigrame.dialogs.ModelManagerDialog dlg =
                new com.phiigrame.dialogs.ModelManagerDialog(modelConfigService, aiService);
        dlg.show();
    }

    /**
     * Apply the currently-active model preset to the AI service.  Called
     * at startup and after the user picks a new model in the manager.
     */
    private void applyModelConfigToAiService() {
        if (modelConfigService == null || aiService == null) return;
        com.phiigrame.services.ModelConfigService.Model m = modelConfigService.active();
        if (m == null) return;
        if (m.kind == com.phiigrame.services.ModelConfigService.Kind.REMOTE) {
            aiService.setRemoteModel(m.baseUrl, m.apiKey, m.remoteModel);
        } else {
            aiService.setModelConfig(m.repo, m.file, m.mirror);
        }
    }
    
    /**
     * Detect Python and return the version string (e.g. "Python 3.12.4") or
     * null if not found. Kept for backwards-compat; the new AI engine
     * does not need Python.
     */
    private String detectPythonVersion() {
        String[] cmds = {"python", "python3", "py"};
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder(c, "--version").redirectErrorStream(true).start();
                byte[] buf = new byte[256];
                StringBuilder sb = new StringBuilder();
                try (java.io.InputStream is = p.getInputStream()) {
                    int n;
                    while ((n = is.read(buf)) > 0) sb.append(new String(buf, 0, n));
                }
                if (p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0) {
                    return sb.toString().trim();
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
    
    private VBox createSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("tool-window");
        sidebar.setPrefWidth(280);
        sidebar.setMinWidth(200);
        sidebar.setMaxWidth(400);
        
        // Tab pane: Project | Git History | AI History
        sideTabs = new TabPane();
        sideTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        sideTabs.setSide(javafx.geometry.Side.TOP);
        sideTabs.getStyleClass().add("sidebar-tabs");
        
        // ---- Project tab ----
        VBox projectContent = new VBox();
        projectContent.setStyle("-fx-background-color: #1e1e1e;");
        VBox.setVgrow(projectContent, Priority.ALWAYS);
        
        HBox headerRow = new HBox();
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.getStyleClass().add("tool-window-header");
        Label header = new Label("PROJECT");
        header.getStyleClass().add("tool-window-header-label");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        headerRow.getChildren().addAll(header, headerSpacer);
        projectContent.getChildren().add(headerRow);
        
        // File tree - always present and always visible
        fileTree = new TreeView<>();
        fileTree.getStyleClass().add("file-tree");
        fileTree.setCellFactory(tv -> {
            TreeCell<File> cell = new TreeCell<File>() {
                @Override
                protected void updateItem(File file, boolean empty) {
                    super.updateItem(file, empty);
                    if (empty || file == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        setText(file.getName());
                        setGraphic(FileIconProvider.getIconNode(file.getName(), file.isDirectory()));
                    }
                }
            };
            return cell;
        });
        fileTree.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                TreeItem<File> selected = fileTree.getSelectionModel().getSelectedItem();
                if (selected != null && selected.getValue() != null && 
                    selected.getValue().isFile()) {
                    openFile(selected.getValue());
                }
            }
        });
        fileTree.setShowRoot(false);
        fileTree.setFixedCellSize(24);
        VBox.setVgrow(fileTree, Priority.ALWAYS);
        projectContent.getChildren().add(fileTree);
        
        // Empty placeholder shown when no project - overlaid on tree area
        emptyProjectLabel = new Label("No project loaded.\n\nCreate or open a project\nto see files here.");
        emptyProjectLabel.getStyleClass().add("empty-project-label");
        emptyProjectLabel.setWrapText(true);
        emptyProjectLabel.setAlignment(Pos.CENTER);
        emptyProjectLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        emptyProjectLabel.setMaxWidth(Double.MAX_VALUE);
        emptyProjectLabel.setMaxHeight(Double.MAX_VALUE);
        
        StackPane emptyContainer = new StackPane();
        emptyContainer.getStyleClass().add("empty-project-pane");
        emptyContainer.getChildren().add(emptyProjectLabel);
        emptyContainer.setAlignment(Pos.CENTER);
        emptyContainer.setVisible(false);
        emptyContainer.setManaged(false);
        emptyProjectContainer = emptyContainer;
        projectContent.getChildren().add(emptyContainer);
        
        Tab projectTab = new Tab("Project", projectContent);
        projectTab.setClosable(false);
        
        // ---- Git history tab ----
        gitHistoryPanel = new GitHistoryPanel(gitService);
        Tab gitTab = new Tab("Git", gitHistoryPanel);
        gitTab.setClosable(false);
        gitTab.setOnSelectionChanged(e -> {
            if (gitTab.isSelected() && gitService.isRepo()) {
                gitHistoryPanel.refresh();
            }
        });
        
        // ---- AI history tab ----
        VBox aiHistoryContent = new VBox(4);
        aiHistoryContent.setStyle("-fx-background-color: #1e1e1e; -fx-padding: 8;");
        HBox aiHistHeader = new HBox(8);
        aiHistHeader.setAlignment(Pos.CENTER_LEFT);
        Label aiHistTitle = new Label("AI Conversations");
        aiHistTitle.setStyle("-fx-text-fill: #cccccc; -fx-font-weight: bold; -fx-font-size: 12px;");
        HBox.setHgrow(aiHistTitle, Priority.ALWAYS);
        Button newChatBtn = new Button("+ New");
        newChatBtn.setStyle("-fx-background-color: #0e639c; -fx-text-fill: white; -fx-padding: 4 10; " +
                "-fx-background-radius: 4; -fx-cursor: hand; -fx-font-size: 11px;");
        aiHistHeader.getChildren().addAll(aiHistTitle, newChatBtn);
        aiHistoryContent.getChildren().add(aiHistHeader);
        
        ListView<String> aiHistoryList = new ListView<>();
        aiHistoryList.setStyle("-fx-background-color: #1e1e1e; -fx-control-inner-background: #1e1e1e;");
        refreshAiHistoryList(aiHistoryList, aiService, aiHistoryService);
        aiHistoryList.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setStyle("-fx-text-fill: #d4d4d4; -fx-padding: 6 8;");
                }
            }
        });
        aiHistoryList.setOnMouseClicked(ev -> {
            if (ev.getClickCount() == 2) {
                String sel = aiHistoryList.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    String id = sel.split("\\|")[0];
                    switchAiSession(id, aiHistoryList);
                }
            }
        });
        VBox.setVgrow(aiHistoryList, Priority.ALWAYS);
        aiHistoryContent.getChildren().add(aiHistoryList);
        
        newChatBtn.setOnAction(e -> {
            aiHistoryService.createSession("New Chat");
            refreshAiHistoryList(aiHistoryList, aiService, aiHistoryService);
        });
        
        Button deleteAiBtn = new Button("Delete");
        deleteAiBtn.setStyle("-fx-background-color: #5a2828; -fx-text-fill: white; -fx-padding: 4 10; " +
                "-fx-background-radius: 4; -fx-cursor: hand; -fx-font-size: 11px;");
        deleteAiBtn.setOnAction(e -> {
            String sel = aiHistoryList.getSelectionModel().getSelectedItem();
            if (sel != null) {
                String id = sel.split("\\|")[0];
                aiHistoryService.deleteSession(id);
                refreshAiHistoryList(aiHistoryList, aiService, aiHistoryService);
            }
        });
        aiHistoryContent.getChildren().add(deleteAiBtn);
        
        Tab aiHistTab = new Tab("AI History", aiHistoryContent);
        aiHistTab.setClosable(false);
        
        // ---- Plugins tab ----
        Tab pluginsTab = new Tab("Plugins", createPluginsPanel());
        pluginsTab.setClosable(false);
        
        sideTabs.getTabs().addAll(projectTab, gitTab, aiHistTab, pluginsTab);
        VBox.setVgrow(sideTabs, Priority.ALWAYS);
        sidebar.getChildren().add(sideTabs);
        
        return sidebar;
    }
    
    /**
     * Build the "Plugins" tab. Two bundled plugins are listed:
     *  - Phiigrame Service      (account, settings, sessions)
     *  - Phiigrame AI Assistant (local Qwen2.5-Coder integration)
     */
    private VBox createPluginsPanel() {
        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: #1e1e1e;");
        
        // Top toolbar: search + marketplace
        HBox toolbar = new HBox(6);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle("-fx-background-color: #3c3f41; -fx-padding: 6 8; -fx-border-color: " +
                "transparent transparent #2b2b2b transparent; -fx-border-width: 0 0 1 0;");
        
        TextField searchField = new TextField();
        searchField.setPromptText("Search plugins...");
        searchField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: #d4d4d4; " +
                "-fx-prompt-text-fill: #6e7681; -fx-border-color: #454545; -fx-border-radius: 4; " +
                "-fx-background-radius: 4; -fx-padding: 5 10;");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        toolbar.getChildren().add(searchField);
        
        Button marketplaceBtn = new Button("Marketplace");
        marketplaceBtn.setStyle("-fx-background-color: #0e639c; -fx-text-fill: white; " +
                "-fx-padding: 5 12; -fx-background-radius: 4; -fx-cursor: hand; -fx-font-size: 11px;");
        marketplaceBtn.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Marketplace");
            alert.setHeaderText("Plugin Marketplace");
            alert.setContentText("The marketplace is not yet available.\n\n" +
                    "Two plugins are bundled with Phiigrame by default:\n" +
                    "  - Phiigrame Service\n" +
                    "  - Phiigrame AI Assistant");
            alert.showAndWait();
        });
        toolbar.getChildren().add(marketplaceBtn);
        root.getChildren().add(toolbar);
        
        // List of installed plugins
        ListView<PluginEntry> pluginList = new ListView<>();
        pluginList.setStyle("-fx-background-color: #1e1e1e; -fx-control-inner-background: #1e1e1e; " +
                "-fx-border-color: transparent;");
        VBox.setVgrow(pluginList, Priority.ALWAYS);
        
        // Two bundled plugins
        pluginList.getItems().addAll(
                new PluginEntry("Phiigrame Service",
                        "Bundled",
                        "com.phiigrame.service",
                        "1.0.0",
                        "Core account, session and settings service. Provides the Phiigrame Account system, " +
                                "project session state, and IDE-wide preferences.",
                        true,
                        "Manages the local Phiigrame Account (sign in / sign up / sign out), " +
                                "stores user preferences, and exposes them to other plugins through " +
                                "the PhiigrameService API.",
                        "#3574f0"),
                new PluginEntry("Phiigrame AI Assistant",
                        "Bundled",
                        "com.phiigrame.ai",
                        "1.0.0",
                        "Local Qwen2.5-Coder-1.5B-Instruct integration. Provides code completion, " +
                                "chat, refactor and explain-code actions, all running entirely on the local machine.",
                        true,
                        "Ships ai_server.py, which auto-installs its Python dependencies on first use " +
                                "and loads the model from Hugging Face (Qwen/Qwen2.5-Coder-1.5B-Instruct). " +
                                "Exposes the AI menu (Chat, Code Completion, Explain, Refactor, Settings).",
                        "#6a9955")
        );
        
        pluginList.setCellFactory(lv -> new ListCell<PluginEntry>() {
            @Override
            protected void updateItem(PluginEntry p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(null);
                    setGraphic(buildPluginCard(p));
                }
            }
        });
        
        pluginList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                showPluginDetails(newVal);
            }
        });
        
        // Filter as the user types
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String q = newVal == null ? "" : newVal.toLowerCase().trim();
            if (q.isEmpty()) {
                pluginList.getItems().setAll(allPlugins());
            } else {
                List<PluginEntry> filtered = new ArrayList<>();
                for (PluginEntry p : allPlugins()) {
                    if (p.name.toLowerCase().contains(q) || p.description.toLowerCase().contains(q)) {
                        filtered.add(p);
                    }
                }
                pluginList.getItems().setAll(filtered);
            }
        });
        
        root.getChildren().add(pluginList);
        
        // Bottom status bar
        HBox status = new HBox(8);
        status.setStyle("-fx-background-color: #3c3f41; -fx-padding: 4 8; " +
                "-fx-border-color: #2b2b2b transparent transparent transparent; " +
                "-fx-border-width: 1 0 0 0;");
        Label statusLabel2 = new Label("2 plugins installed");
        statusLabel2.setStyle("-fx-text-fill: #8b8b8b; -fx-font-size: 11px;");
        status.getChildren().add(statusLabel2);
        root.getChildren().add(status);
        
        return root;
    }
    
    private List<PluginEntry> allPlugins() {
        return new ArrayList<>(java.util.Arrays.asList(
                new PluginEntry("Phiigrame Service", "Bundled",
                        "com.phiigrame.service", "1.0.0",
                        "Core account, session and settings service.", true,
                        "Manages the local Phiigrame Account (sign in / sign up / sign out), " +
                                "stores user preferences, and exposes them to other plugins through " +
                                "the PhiigrameService API.", "#3574f0"),
                new PluginEntry("Phiigrame AI Assistant", "Bundled",
                        "com.phiigrame.ai", "1.0.0",
                        "Local Qwen2.5-Coder-1.5B-Instruct integration.", true,
                        "Ships ai_server.py, which auto-installs Python dependencies and loads " +
                                "Qwen2.5-Coder-1.5B-Instruct from Hugging Face.", "#6a9955")
        ));
    }
    
    /**
     * Render one row in the plugin list as a small card (icon, name, version,
     * short description, status, enable/disable).
     */
    private VBox buildPluginCard(PluginEntry p) {
        VBox card = new VBox(4);
        card.setStyle("-fx-background-color: transparent; -fx-padding: 8 10;");
        
        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);
        
        // Plugin icon (colored square as placeholder)
        StackPane icon = new StackPane();
        icon.setMinSize(32, 32);
        icon.setPrefSize(32, 32);
        icon.setMaxSize(32, 32);
        icon.setStyle("-fx-background-color: " + p.iconColor + "; -fx-background-radius: 6;");
        Label iconText = new Label(p.name.substring(0, 1));
        iconText.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
        icon.getChildren().add(iconText);
        topRow.getChildren().add(icon);
        
        VBox nameRow = new VBox(2);
        HBox.setHgrow(nameRow, Priority.ALWAYS);
        
        HBox titleLine = new HBox(6);
        titleLine.setAlignment(Pos.CENTER_LEFT);
        Label name = new Label(p.name);
        name.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 13px; -fx-font-weight: bold;");
        Label version = new Label("v" + p.version);
        version.setStyle("-fx-text-fill: #6e7681; -fx-font-size: 11px;");
        titleLine.getChildren().addAll(name, version);
        
        Label id = new Label(p.pluginId);
        id.setStyle("-fx-text-fill: #6e7681; -fx-font-size: 10px;");
        
        nameRow.getChildren().addAll(titleLine, id);
        topRow.getChildren().add(nameRow);
        
        // Status badge
        Label status = new Label(p.enabled ? "Enabled" : "Disabled");
        status.setStyle("-fx-text-fill: " + (p.enabled ? "#6a9955" : "#f48771") +
                "; -fx-font-size: 10px; -fx-padding: 2 8; -fx-background-color: " +
                (p.enabled ? "#2d4a2d" : "#4a2d2d") + "; -fx-background-radius: 8;");
        topRow.getChildren().add(status);
        
        card.getChildren().add(topRow);
        
        // Short description
        Label desc = new Label(p.shortDescription);
        desc.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
        desc.setWrapText(true);
        card.getChildren().add(desc);
        
        // Action buttons
        HBox actions = new HBox(6);
        actions.setPadding(new Insets(4, 0, 0, 0));
        Button detailsBtn = new Button("Details");
        detailsBtn.setStyle("-fx-background-color: #0e639c; -fx-text-fill: white; -fx-padding: 3 10; " +
                "-fx-background-radius: 4; -fx-cursor: hand; -fx-font-size: 10px;");
        detailsBtn.setOnAction(e -> showPluginDetails(p));
        
        Button toggleBtn = new Button(p.enabled ? "Disable" : "Enable");
        toggleBtn.setStyle("-fx-background-color: " + (p.enabled ? "#5a2828" : "#285a2d") +
                "; -fx-text-fill: white; -fx-padding: 3 10; -fx-background-radius: 4; " +
                "-fx-cursor: hand; -fx-font-size: 10px;");
        toggleBtn.setOnAction(e -> {
            p.enabled = !p.enabled;
            // Recreate the visible row
            // (ListView will rebind via cell update automatically)
            statusLabel.setText((p.enabled ? "Enabled: " : "Disabled: ") + p.name);
        });
        
        actions.getChildren().addAll(detailsBtn, toggleBtn);
        card.getChildren().add(actions);
        
        return card;
    }
    
    /**
     * Open a modal with the full plugin description and metadata.
     */
    private void showPluginDetails(PluginEntry p) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(p.name + " - Plugin Details");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #2d2d2d;");
        content.setPrefWidth(480);
        
        HBox headerRow = new HBox(12);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        
        StackPane icon = new StackPane();
        icon.setMinSize(56, 56);
        icon.setPrefSize(56, 56);
        icon.setMaxSize(56, 56);
        icon.setStyle("-fx-background-color: " + p.iconColor + "; -fx-background-radius: 10;");
        Label iconText = new Label(p.name.substring(0, 1));
        iconText.setStyle("-fx-text-fill: white; -fx-font-size: 28px; -fx-font-weight: bold;");
        icon.getChildren().add(iconText);
        headerRow.getChildren().add(icon);
        
        VBox headerText = new VBox(4);
        Label name = new Label(p.name);
        name.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 18px; -fx-font-weight: bold;");
        Label meta = new Label(p.vendor + "  v" + p.version);
        meta.setStyle("-fx-text-fill: #8b8b8b; -fx-font-size: 11px;");
        headerText.getChildren().addAll(name, meta);
        headerRow.getChildren().add(headerText);
        content.getChildren().add(headerRow);
        
        content.getChildren().add(new Separator());
        
        Label descTitle = new Label("Description");
        descTitle.setStyle("-fx-text-fill: #cccccc; -fx-font-weight: bold; -fx-font-size: 12px;");
        content.getChildren().add(descTitle);
        Label desc = new Label(p.description);
        desc.setStyle("-fx-text-fill: #d4d4d4; -fx-font-size: 12px;");
        desc.setWrapText(true);
        content.getChildren().add(desc);
        
        Label detailsTitle = new Label("Details");
        detailsTitle.setStyle("-fx-text-fill: #cccccc; -fx-font-weight: bold; -fx-font-size: 12px;");
        content.getChildren().add(detailsTitle);
        Label details = new Label(
                "Plugin ID:  " + p.pluginId + "\n" +
                "Vendor:     " + p.vendor + "\n" +
                "Version:    " + p.version + "\n" +
                "Status:     " + (p.enabled ? "Enabled" : "Disabled"));
        details.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px; " +
                "-fx-font-family: 'JetBrains Mono', monospace;");
        content.getChildren().add(details);
        
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }
    
    /**
     * Lightweight POJO describing a single plugin in the catalog.
     */
    private static class PluginEntry {
        String name;
        String vendor;
        String pluginId;
        String version;
        String shortDescription;
        String description;
        boolean enabled;
        String iconColor;
        
        PluginEntry(String name, String vendor, String pluginId, String version,
                    String shortDescription, boolean enabled, String description, String iconColor) {
            this.name = name;
            this.vendor = vendor;
            this.pluginId = pluginId;
            this.version = version;
            this.shortDescription = shortDescription;
            this.enabled = enabled;
            this.description = description;
            this.iconColor = iconColor;
        }
    }
    
    private void refreshAiHistoryList(ListView<String> list, AiService ai, AiHistoryService hist) {
        list.getItems().clear();
        for (AiHistoryService.AiSession s : hist.getAllSessions()) {
            String entry = s.id + "|" + s.title + " (" + s.messages.size() + " msgs, " + 
                    hist.formatTimestamp(s.lastModified) + ")";
            list.getItems().add(entry);
        }
    }
    
    private void switchAiSession(String id, ListView<String> list) {
        // Just log for now - the AI chat panel uses its own session
        statusLabel.setText("AI history: " + id.substring(0, Math.min(8, id.length())));
    }
    
    private VBox createEditorArea() {
        VBox editorArea = new VBox();
        editorArea.setStyle("-fx-background-color: #1e1e1e;");
        
        tabPane = new TabPane();
        tabPane.setStyle("-fx-background-color: #1e1e1e;");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        
        terminalPanel = new TerminalPanel();
        
        editorSplitPane = new SplitPane();
        editorSplitPane.setOrientation(Orientation.VERTICAL);
        editorSplitPane.setStyle("-fx-background-color: #1e1e1e;");
        editorSplitPane.getItems().add(tabPane);
        
        VBox.setVgrow(editorSplitPane, Priority.ALWAYS);
        editorArea.getChildren().add(editorSplitPane);
        
        return editorArea;
    }
    
    private HBox createStatusBar() {
        HBox statusBar = new HBox();
        statusBar.getStyleClass().add("status-bar");
        statusBar.setAlignment(Pos.CENTER_LEFT);
        
        statusLabel.getStyleClass().add("status-bar-label");
        statusBar.getChildren().add(statusLabel);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        statusBar.getChildren().add(spacer);
        
        Label encodingLabel = new Label("UTF-8");
        encodingLabel.getStyleClass().add("status-bar-label");
        statusBar.getChildren().add(encodingLabel);
        
        lineColumnLabel = new Label("Ln 1, Col 1");
        lineColumnLabel.getStyleClass().add("status-bar-label");
        statusBar.getChildren().add(lineColumnLabel);
        
        Separator sep = new Separator();
        sep.setOrientation(Orientation.VERTICAL);
        statusBar.getChildren().add(sep);
        
        if (userLabel != null) {
            userLabel.getStyleClass().add("status-bar-label");
            userLabel.setStyle("-fx-text-fill: #6a9955; -fx-padding: 0 12;");
            statusBar.getChildren().add(userLabel);
        }
        
        return statusBar;
    }
    
    private void updateUserLabel() {
        if (userLabel == null) return;
        if (authService != null && authService.isLoggedIn()) {
            userLabel.setText("Phiigrame: " + authService.getCurrentUser().username);
        } else {
            userLabel.setText("Phiigrame: Guest");
        }
    }
}