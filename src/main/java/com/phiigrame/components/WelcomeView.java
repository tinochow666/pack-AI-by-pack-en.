package com.phiigrame.components;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Modern welcome view embedded in the main IDE window.
 * Designed to match the modern IntelliJ IDEA 2024+ style.
 */
public class WelcomeView extends BorderPane {

    private final Consumer<File> onOpenProject;
    private final Runnable onCreateProject;
    private final Runnable onOpenFromVcs;
    private final Consumer<File> onOpenRecent;
    private final Runnable onShowPlugins;
    private final Runnable onShowLearn;
    private final Runnable onShowCustomize;
    private final Runnable onShowRemote;
    /** Callback used by the "Projects" item to clear the selection state when the user navigates back. */
    private final Runnable onShowProjects;

    /** Sidebar buttons; only one is highlighted at a time. */
    private final List<Button> navButtons = new ArrayList<>();
    /** Currently selected section id, e.g. "projects". */
    private String selectedSection = "projects";
    /** Holds the main content area so we can swap sections in place. */
    private BorderPane contentHolder;

    public WelcomeView(Consumer<File> onOpenProject,
                       Runnable onCreateProject,
                       Runnable onOpenFromVcs,
                       Consumer<File> onOpenRecent) {
        this(onOpenProject, onCreateProject, onOpenFromVcs, onOpenRecent,
                () -> {}, () -> {}, () -> {}, () -> {});
    }

    public WelcomeView(Consumer<File> onOpenProject,
                       Runnable onCreateProject,
                       Runnable onOpenFromVcs,
                       Consumer<File> onOpenRecent,
                       Runnable onShowPlugins,
                       Runnable onShowLearn,
                       Runnable onShowCustomize,
                       Runnable onShowRemote) {
        this.onOpenProject = onOpenProject;
        this.onCreateProject = onCreateProject;
        this.onOpenFromVcs = onOpenFromVcs;
        this.onOpenRecent = onOpenRecent;
        this.onShowPlugins = onShowPlugins;
        this.onShowLearn = onShowLearn;
        this.onShowCustomize = onShowCustomize;
        this.onShowRemote = onShowRemote;
        this.onShowProjects = () -> selectSection("projects");

        getStyleClass().add("welcome-root");
        setLeft(createSidebar());
        contentHolder = new BorderPane();
        contentHolder.setCenter(createProjectsContent());
        setCenter(contentHolder);
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(2);
        sidebar.getStyleClass().add("welcome-sidebar");
        sidebar.setPadding(new Insets(16, 0, 16, 0));

        String[][] navItems = {
                {"Projects", "projects"},
                {"Remote Development", "remote"},
                {"Customization", "customization"},
                {"Plugins", "plugins"},
                {"Learn", "learn"}
        };
        for (String[] item : navItems) {
            Button navBtn = createNavButton(item[0], item[1]);
            navButtons.add(navBtn);
            sidebar.getChildren().add(navBtn);
        }
        // Highlight Projects by default
        applySelectedStyle();

        Region spacer = new VBox();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sidebar.getChildren().add(spacer);

        Label version = new Label("2024.1");
        version.getStyleClass().add("welcome-version");
        version.setPadding(new Insets(0, 0, 0, 18));
        sidebar.getChildren().add(version);

        return sidebar;
    }

    private Button createNavButton(String title, String id) {
        Button b = new Button(title);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setUserData(id);
        b.getStyleClass().add("welcome-nav-item");
        b.setOnAction(e -> selectSection(id));
        return b;
    }

    private void selectSection(String id) {
        if (id == null) return;
        selectedSection = id;
        applySelectedStyle();
        Node section = switch (id) {
            case "plugins" -> createPluginsContent();
            case "learn" -> createLearnContent();
            case "customization" -> createCustomizationContent();
            case "remote" -> createRemoteContent();
            default -> createProjectsContent();
        };
        // Swap content directly - the user reported the previous fade-out
        // transition was making clicks feel unresponsive, and we want
        // instant feedback when they tap a sidebar item.
        contentHolder.setCenter(section);
        section.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(140), section);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);
        fadeIn.play();
        // Navigation inside the welcome view never leaves the welcome view.
        // The "Open Plugins tab" / similar buttons inside each section are
        // the only way to hand off to the host shell.
    }

    private void applySelectedStyle() {
        for (Button b : navButtons) {
            b.getStyleClass().remove("selected");
            if (selectedSection.equals(b.getUserData())) {
                if (!b.getStyleClass().contains("selected")) {
                    b.getStyleClass().add("selected");
                }
            }
        }
    }

    // ---- sections ------------------------------------------------------------

    private ScrollPane createProjectsContent() {
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: #1e1e1e; -fx-border-color: transparent;");
        scroll.getStyleClass().add("edge-to-edge");

        HBox root = new HBox();
        root.setStyle("-fx-background-color: #1e1e1e;");
        root.setPadding(new Insets(60, 60, 60, 60));
        root.setSpacing(60);
        root.getChildren().addAll(createLeftPanel(), createRightPanel());

        scroll.setContent(root);
        return scroll;
    }

    private ScrollPane createPluginsContent() {
        VBox body = new VBox(20);
        body.setStyle("-fx-background-color: #1e1e1e;");
        body.setPadding(new Insets(60, 60, 60, 60));

        Label title = new Label("Plugins");
        title.getStyleClass().add("welcome-title");
        Label desc = new Label("Phiigrame ships with two bundled plugins. Manage them in the Plugins tab " +
                "of the left sidebar for a fuller experience, or open the marketplace to discover more.");
        desc.setWrapText(true);
        desc.getStyleClass().add("welcome-subtitle");

        HBox cards = new HBox(20);
        cards.getChildren().addAll(buildPluginCard("Phiigrame Service", "#3574f0",
                "Core account, session, and settings service."),
                buildPluginCard("Phiigrame AI Assistant", "#6a9955",
                "Local Qwen2.5-Coder-1.5B integration (no Python, no Ollama)."));

        Button openPluginsTab = new Button("Open Plugins tab");
        openPluginsTab.getStyleClass().add("welcome-action");
        openPluginsTab.setOnAction(e -> onShowPlugins.run());

        body.getChildren().addAll(title, desc, cards, openPluginsTab);
        ScrollPane sp = new ScrollPane(body);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: #1e1e1e; -fx-border-color: transparent;");
        sp.getStyleClass().add("edge-to-edge");
        return sp;
    }

    private Node buildPluginCard(String name, String color, String desc) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(18));
        card.setPrefSize(280, 140);
        card.setStyle("-fx-background-color: #2d2d2d; -fx-background-radius: 10; " +
                "-fx-border-color: #3c3f41; -fx-border-radius: 10;");
        Label badge = new Label(name.substring(0, 1));
        badge.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; " +
                "-fx-font-size: 22px; -fx-font-weight: bold; -fx-padding: 8 14; " +
                "-fx-background-radius: 8; -fx-min-width: 36; -fx-alignment: center;");
        Label nameLbl = new Label(name);
        nameLbl.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
        Label descLbl = new Label(desc);
        descLbl.setWrapText(true);
        descLbl.setStyle("-fx-text-fill: #8b8b8b; -fx-font-size: 11px;");
        card.getChildren().addAll(badge, nameLbl, descLbl);
        return card;
    }

    private ScrollPane createLearnContent() {
        VBox body = new VBox(20);
        body.setStyle("-fx-background-color: #1e1e1e;");
        body.setPadding(new Insets(60, 60, 60, 60));

        Label title = new Label("Learn Phiigrame");
        title.getStyleClass().add("welcome-title");
        Label desc = new Label("New here? These short guides will get you productive in minutes.");
        desc.setWrapText(true);
        desc.getStyleClass().add("welcome-subtitle");

        VBox lessons = new VBox(10);
        String[][] items = {
                {"Create your first project", "File -> New Project, pick a language, choose JDK."},
                {"Use the local AI", "Open AI -> AI Chat (sign in first). All inference runs on your machine."},
                {"Version control", "The Git sidebar tab shows history; commits and diffs work out of the box."},
                {"Keyboard shortcuts", "Ctrl+Space (AI complete), Ctrl+Shift+A (AI chat), Ctrl+N (new file)."},
                {"Build & run", "Use the green play / red stop / bug buttons in the toolbar."}
        };
        for (String[] item : items) {
            VBox card = new VBox(2);
            card.setPadding(new Insets(14));
            card.setStyle("-fx-background-color: #2d2d2d; -fx-background-radius: 8;");
            Label l1 = new Label(item[0]);
            l1.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");
            Label l2 = new Label(item[1]);
            l2.setStyle("-fx-text-fill: #8b8b8b; -fx-font-size: 11px;");
            l2.setWrapText(true);
            card.getChildren().addAll(l1, l2);
            lessons.getChildren().add(card);
        }

        body.getChildren().addAll(title, desc, lessons);
        ScrollPane sp = new ScrollPane(body);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: #1e1e1e; -fx-border-color: transparent;");
        sp.getStyleClass().add("edge-to-edge");
        return sp;
    }

    private ScrollPane createCustomizationContent() {
        VBox body = new VBox(20);
        body.setStyle("-fx-background-color: #1e1e1e;");
        body.setPadding(new Insets(60, 60, 60, 60));

        Label title = new Label("Customization");
        title.getStyleClass().add("welcome-title");
        Label desc = new Label("Tweak the look and feel of Phiigrame.");
        desc.getStyleClass().add("welcome-subtitle");

        VBox rows = new VBox(10);
        rows.getChildren().addAll(
                settingRow("Theme", "Dark (Darcula) is the only built-in theme. More coming."),
                settingRow("Editor font", "JetBrains Mono, 13 px"),
                settingRow("Tab size", "4 spaces"),
                settingRow("AI backend", "Local Qwen2.5-Coder via bundled llama.cpp (no Python).")
        );

        body.getChildren().addAll(title, desc, rows);
        ScrollPane sp = new ScrollPane(body);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: #1e1e1e; -fx-border-color: transparent;");
        sp.getStyleClass().add("edge-to-edge");
        return sp;
    }

    private Node settingRow(String k, String v) {
        HBox row = new HBox(20);
        row.setPadding(new Insets(12, 14, 12, 14));
        row.setStyle("-fx-background-color: #2d2d2d; -fx-background-radius: 6;");
        Label key = new Label(k);
        key.setStyle("-fx-text-fill: #d4d4d4; -fx-font-size: 12px; -fx-font-weight: bold;");
        key.setPrefWidth(140);
        Label val = new Label(v);
        val.setStyle("-fx-text-fill: #8b8b8b; -fx-font-size: 12px;");
        val.setWrapText(true);
        HBox.setHgrow(val, Priority.ALWAYS);
        row.getChildren().addAll(key, val);
        return row;
    }

    private ScrollPane createRemoteContent() {
        VBox body = new VBox(20);
        body.setStyle("-fx-background-color: #1e1e1e;");
        body.setPadding(new Insets(60, 60, 60, 60));

        Label title = new Label("Remote Development");
        title.getStyleClass().add("welcome-title");
        Label desc = new Label("Connect to a remote machine over SSH and use its toolchain from here.\n\n" +
                "This feature is on the roadmap and will arrive in a later milestone.");
        desc.setWrapText(true);
        desc.getStyleClass().add("welcome-subtitle");

        body.getChildren().addAll(title, desc);
        ScrollPane sp = new ScrollPane(body);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: #1e1e1e; -fx-border-color: transparent;");
        sp.getStyleClass().add("edge-to-edge");
        return sp;
    }
    
    private VBox createLeftPanel() {
        VBox panel = new VBox();
        panel.setAlignment(Pos.TOP_LEFT);
        panel.setSpacing(20);
        HBox.setHgrow(panel, Priority.ALWAYS);
        
        // Big in-app logo as title (replaces logo.png with logo-in-app.png)
        try {
            javafx.scene.image.ImageView logoView = new javafx.scene.image.ImageView(
                new javafx.scene.image.Image(getClass().getResourceAsStream("/logo-in-app.png")));
            logoView.setFitHeight(220);
            logoView.setPreserveRatio(true);
            logoView.setSmooth(true);
            // Center the logo horizontally
            HBox logoContainer = new HBox(logoView);
            logoContainer.setAlignment(Pos.CENTER_LEFT);
            panel.getChildren().add(logoContainer);
        } catch (Exception ex) {
            Label title = new Label("Welcome to Phiigrame");
            title.getStyleClass().add("welcome-title");
            panel.getChildren().add(title);
        }
        
        Label subtitle = new Label("Smart IDE for Kotlin, Java, and Groovy with Spring Boot support");
        subtitle.getStyleClass().add("welcome-subtitle");
        subtitle.setWrapText(true);
        panel.getChildren().add(subtitle);
        
        VBox actions = new VBox(12);
        actions.setMaxWidth(400);
        
        Button newProjectBtn = createActionButton(
            "New Project",
            "Create a new project from scratch",
            true
        );
        newProjectBtn.setOnAction(e -> onCreateProject.run());
        actions.getChildren().add(newProjectBtn);
        
        Button openProjectBtn = createActionButton(
            "Open",
            "Open an existing project from disk",
            false
        );
        openProjectBtn.setOnAction(e -> {
            javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
            chooser.setTitle("Open Project");
            File selectedDir = chooser.showDialog(getScene().getWindow());
            if (selectedDir != null) {
                onOpenProject.accept(selectedDir);
            }
        });
        actions.getChildren().add(openProjectBtn);
        
        Button vcsBtn = createActionButton(
            "Get from Version Control",
            "Check out a project from Git, Mercurial, or Subversion",
            false
        );
        vcsBtn.setOnAction(e -> onOpenFromVcs.run());
        actions.getChildren().add(vcsBtn);
        
        panel.getChildren().add(actions);
        
        return panel;
    }
    
    private VBox createRightPanel() {
        VBox panel = new VBox();
        panel.setAlignment(Pos.TOP_LEFT);
        panel.setMinWidth(380);
        panel.setMaxWidth(380);
        
        Label recentTitle = new Label("Recent Projects");
        recentTitle.getStyleClass().add("welcome-section-title");
        panel.getChildren().add(recentTitle);
        
        VBox recentList = new VBox(8);
        
        List<File> recent = getRecentProjects();
        if (recent.isEmpty()) {
            Label empty = new Label("No recent projects");
            empty.setStyle("-fx-text-fill: #6e7681; -fx-font-size: 12px; -fx-padding: 8 0;");
            recentList.getChildren().add(empty);
        } else {
            for (File project : recent) {
                recentList.getChildren().add(createRecentItem(project));
            }
        }
        
        panel.getChildren().add(recentList);
        
        return panel;
    }
    
    private VBox createRecentItem(File project) {
        VBox item = new VBox(2);
        item.getStyleClass().add("welcome-recent-item");
        
        Label name = new Label(project.getName());
        name.getStyleClass().add("welcome-recent-name");
        
        Label path = new Label(project.getAbsolutePath());
        path.getStyleClass().add("welcome-recent-path");
        
        item.getChildren().addAll(name, path);
        item.setOnMouseClicked(e -> {
            if (project.exists()) {
                onOpenRecent.accept(project);
            }
        });
        
        return item;
    }
    
    private List<File> getRecentProjects() {
        java.util.List<File> recents = new java.util.ArrayList<>();
        File projectsDir = new File("E:/projects");
        if (projectsDir.exists() && projectsDir.isDirectory()) {
            File[] dirs = projectsDir.listFiles(File::isDirectory);
            if (dirs != null) {
                java.util.Arrays.sort(dirs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                for (File dir : dirs) {
                    if (new File(dir, "build.gradle").exists() ||
                        new File(dir, "pom.xml").exists() ||
                        hasJavaFiles(dir)) {
                        recents.add(dir);
                    }
                }
            }
        }
        return recents;
    }
    
    private boolean hasJavaFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File f : files) {
            if (f.isFile() && (f.getName().endsWith(".java") || 
                                f.getName().endsWith(".kt") || 
                                f.getName().endsWith(".groovy"))) {
                return true;
            }
        }
        return false;
    }
    
    private Button createActionButton(String title, String subtitle, boolean primary) {
        VBox content = new VBox(2);
        
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("welcome-action-title");
        
        Label subLabel = new Label(subtitle);
        subLabel.getStyleClass().add("welcome-action-sub");
        
        content.getChildren().addAll(titleLabel, subLabel);
        content.setAlignment(Pos.CENTER_LEFT);
        
        Button button = new Button();
        button.setGraphic(content);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add(primary ? "welcome-action-primary" : "welcome-action");
        button.setAlignment(Pos.CENTER_LEFT);
        
        return button;
    }
}