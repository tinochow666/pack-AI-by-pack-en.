package com.phiigrame.components;

import com.phiigrame.services.UserDatabase;
import com.phiigrame.services.UserDatabase.Recent;
import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Modern welcome view embedded in the main IDE window.
 *
 * Layout: the "Projects" tab is the default and mirrors IntelliJ IDEA's
 * welcome screen - logo + tagline + three action buttons on the left,
 * a tall list of recent projects on the right with right-click actions.
 * Other sidebar items (Plugins / Learn / Customize / Remote) still
 * render in-place; the previous bug where they jumped to the host
 * shell is preserved as a no-op so any cached callbacks stay safe.
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
    private final UserDatabase userDb;

    private final List<Button> navButtons = new ArrayList<>();
    private String selectedSection = "projects";
    private BorderPane contentHolder;

    // The recents list VBox - kept around so we can refresh it in place
    // after a "Remove from recents" right-click without rebuilding the
    // whole Projects section.
    private VBox recentsContainer;

    public WelcomeView(Consumer<File> onOpenProject,
                       Runnable onCreateProject,
                       Runnable onOpenFromVcs,
                       Consumer<File> onOpenRecent) {
        this(onOpenProject, onCreateProject, onOpenFromVcs, onOpenRecent,
                () -> {}, () -> {}, () -> {}, () -> {}, null);
    }

    public WelcomeView(Consumer<File> onOpenProject,
                       Runnable onCreateProject,
                       Runnable onOpenFromVcs,
                       Consumer<File> onOpenRecent,
                       Runnable onShowPlugins,
                       Runnable onShowLearn,
                       Runnable onShowCustomize,
                       Runnable onShowRemote,
                       UserDatabase userDb) {
        this.onOpenProject = onOpenProject;
        this.onCreateProject = onCreateProject;
        this.onOpenFromVcs = onOpenFromVcs;
        this.onOpenRecent = onOpenRecent;
        this.onShowPlugins = onShowPlugins;
        this.onShowLearn = onShowLearn;
        this.onShowCustomize = onShowCustomize;
        this.onShowRemote = onShowRemote;
        this.userDb = userDb;

        getStyleClass().add("welcome-root");
        setLeft(createSidebar());
        contentHolder = new BorderPane();
        contentHolder.setCenter(createProjectsContent());
        setCenter(contentHolder);
    }

    // ---------------------------------------------------------------- sidebar

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
        applySelectedStyle();

        Region spacer = new VBox();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sidebar.getChildren().add(spacer);

        Label version = new Label("2026.1");
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
            default -> {
                // Re-query recents on every visit to the Projects section
                // so the list reflects projects the user opened from
                // elsewhere (file menu, recent click, etc.).
                yield createProjectsContent();
            }
        };
        contentHolder.setCenter(section);
        section.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(140), section);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);
        fadeIn.play();
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

    // -------------------------------------------------------------- Projects

    private ScrollPane createProjectsContent() {
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: #1e1e1e; -fx-border-color: transparent;");
        scroll.getStyleClass().add("edge-to-edge");

        HBox root = new HBox();
        root.setStyle("-fx-background-color: #1e1e1e;");
        root.setPadding(new Insets(40, 50, 40, 50));
        root.setSpacing(40);
        root.getChildren().addAll(createLeftPanel(), createRightPanel());

        scroll.setContent(root);
        return scroll;
    }

    private VBox createLeftPanel() {
        VBox panel = new VBox();
        panel.setAlignment(Pos.TOP_LEFT);
        panel.setSpacing(18);
        HBox.setHgrow(panel, Priority.ALWAYS);

        // Big in-app logo
        try {
            javafx.scene.image.ImageView logoView = new javafx.scene.image.ImageView(
                new javafx.scene.image.Image(getClass().getResourceAsStream("/logo-in-app.png")));
            logoView.setFitHeight(180);
            logoView.setPreserveRatio(true);
            logoView.setSmooth(true);
            HBox logoContainer = new HBox(logoView);
            logoContainer.setAlignment(Pos.CENTER_LEFT);
            panel.getChildren().add(logoContainer);
        } catch (Exception ex) {
            Label title = new Label("Welcome to Phiigrame");
            title.getStyleClass().add("welcome-title");
            panel.getChildren().add(title);
        }

        Label subtitle = new Label("Smart IDE for Kotlin, Java, and Groovy with built-in local AI");
        subtitle.getStyleClass().add("welcome-subtitle");
        subtitle.setWrapText(true);
        panel.getChildren().add(subtitle);

        VBox actions = new VBox(10);
        actions.setMaxWidth(420);

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

        // Tip footer
        Label tip = new Label("Tip: right-click a recent project to open it in a new window, " +
                "show it in Explorer, or remove it from the list.");
        tip.setStyle("-fx-text-fill: #4d4d4d; -fx-font-size: 11px; -fx-padding: 16 0 0 0;");
        tip.setWrapText(true);
        tip.setMaxWidth(420);
        panel.getChildren().add(tip);

        return panel;
    }

    /**
     * Right column - the IntelliJ-style recent-projects list.  Each row
     * shows the project name, full path, current Git branch (if any), and
     * "Last opened N days ago".
     */
    private VBox createRightPanel() {
        VBox panel = new VBox(8);
        panel.setAlignment(Pos.TOP_LEFT);
        panel.setMinWidth(420);
        panel.setMaxWidth(460);
        HBox.setHgrow(panel, Priority.NEVER);

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label recentTitle = new Label("Recent Projects");
        recentTitle.getStyleClass().add("welcome-section-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(recentTitle, spacer);

        // "Clear all" mini-button
        if (userDb != null) {
            Button clearAll = new Button("Clear All");
            clearAll.setStyle("-fx-background-color: transparent; -fx-text-fill: #6a9955; " +
                    "-fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 4 8;");
            clearAll.setOnAction(e -> {
                Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                        "Remove all recent projects from the list?",
                        ButtonType.YES, ButtonType.NO);
                a.setHeaderText(null);
                a.setTitle("Clear recents");
                if (a.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                    userDb.clearRecents();
                    refreshRecents();
                }
            });
            header.getChildren().add(clearAll);
        }
        panel.getChildren().add(header);

        recentsContainer = new VBox(6);
        recentsContainer.setPadding(new Insets(8, 0, 0, 0));
        populateRecents();
        panel.getChildren().add(recentsContainer);

        return panel;
    }

    private void populateRecents() {
        recentsContainer.getChildren().clear();
        if (userDb == null) {
            Label empty = new Label("No recent projects");
            empty.setStyle("-fx-text-fill: #6e7681; -fx-font-size: 12px; -fx-padding: 8 0;");
            recentsContainer.getChildren().add(empty);
            return;
        }
        List<Recent> recents = userDb.listRecents();
        if (recents.isEmpty()) {
            Label empty = new Label("No recent projects — open or create one to get started.");
            empty.setStyle("-fx-text-fill: #6e7681; -fx-font-size: 12px; -fx-padding: 8 0;");
            empty.setWrapText(true);
            empty.setMaxWidth(440);
            recentsContainer.getChildren().add(empty);
            return;
        }
        for (Recent r : recents) {
            recentsContainer.getChildren().add(createRecentItem(r));
        }
    }

    private void refreshRecents() {
        if (recentsContainer != null) {
            populateRecents();
        }
    }

    private VBox createRecentItem(Recent r) {
        File file = new File(r.path);
        VBox item = new VBox(2);
        item.getStyleClass().add("welcome-recent-item");
        item.setPadding(new Insets(10, 12, 10, 12));
        item.setMaxWidth(440);

        // Top row: name + branch badge
        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label name = new Label(r.name);
        name.getStyleClass().add("welcome-recent-name");
        HBox.setHgrow(name, Priority.ALWAYS);
        topRow.getChildren().add(name);

        if (r.branch != null && !r.branch.isEmpty()) {
            Label branchBadge = new Label(r.branch);
            branchBadge.setStyle("-fx-background-color: #2d4a2d; -fx-text-fill: #6a9955; " +
                    "-fx-font-size: 10px; -fx-padding: 2 8; -fx-background-radius: 8; " +
                    "-fx-border-color: #3a5f3a; -fx-border-radius: 8;");
            topRow.getChildren().add(branchBadge);
        }
        item.getChildren().add(topRow);

        // Bottom row: path + "last opened" relative timestamp
        HBox bottomRow = new HBox(8);
        bottomRow.setAlignment(Pos.CENTER_LEFT);
        Label path = new Label(r.path);
        path.getStyleClass().add("welcome-recent-path");
        path.setEllipsisString("…");
        HBox.setHgrow(path, Priority.ALWAYS);
        bottomRow.getChildren().add(path);

        String rel = relativeTime(r.lastOpened);
        Label ts = new Label(rel);
        ts.setStyle("-fx-text-fill: #4d4d4d; -fx-font-size: 10px;");
        bottomRow.getChildren().add(ts);
        item.getChildren().add(bottomRow);

        // Dim the row if the folder has been deleted on disk.
        boolean exists = file.exists();
        item.setOpacity(exists ? 1.0 : 0.55);
        if (!exists) {
            Label missing = new Label("Folder no longer exists — right-click to remove.");
            missing.setStyle("-fx-text-fill: #f48771; -fx-font-size: 10px;");
            item.getChildren().add(missing);
        }

        // Single click - open.  Right click - context menu.
        item.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && exists) {
                onOpenRecent.accept(file);
            }
        });

        ContextMenu cm = new ContextMenu();
        MenuItem open = new MenuItem("Open");
        open.setOnAction(e -> { if (exists) onOpenRecent.accept(file); });
        MenuItem showInExplorer = new MenuItem("Show in Explorer");
        showInExplorer.setOnAction(e -> showInExplorer(file));
        MenuItem remove = new MenuItem("Remove from Recent Projects");
        remove.setOnAction(e -> {
            if (userDb != null) {
                userDb.removeRecent(r.path);
                refreshRecents();
            }
        });
        cm.getItems().addAll(open, showInExplorer, new javafx.scene.control.SeparatorMenuItem(), remove);
        item.setOnContextMenuRequested(e -> cm.show(item, e.getScreenX(), e.getScreenY()));

        return item;
    }

    private static String relativeTime(long ts) {
        if (ts <= 0) return "";
        long delta = System.currentTimeMillis() - ts;
        if (delta < 0) return "just now";
        long sec = delta / 1000;
        if (sec < 60) return "just now";
        long min = sec / 60;
        if (min < 60) return min + " min ago";
        long hr = min / 60;
        if (hr < 24) return hr + " hr ago";
        long day = hr / 24;
        if (day < 7) return day + " day" + (day == 1 ? "" : "s") + " ago";
        if (day < 30) return (day / 7) + " wk ago";
        if (day < 365) return (day / 30) + " mo ago";
        return (day / 365) + " yr ago";
    }

    private void showInExplorer(File f) {
        if (f == null || !f.exists()) return;
        try {
            // Use AWT Desktop for the platform default file manager.
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                // For a directory Desktop.open opens it directly; for a
                // file we ask for the parent folder.
                File target = f.isDirectory() ? f : f.getParentFile();
                if (target != null) java.awt.Desktop.getDesktop().open(target);
            }
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------ other sections

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

    /**
     * @return the recents container for external refresh hooks
     * (used by the file menu when the user opens a project).
     */
    public void notifyProjectOpened() {
        refreshRecents();
    }
}
