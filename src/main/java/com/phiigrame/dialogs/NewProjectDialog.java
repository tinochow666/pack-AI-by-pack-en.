package com.phiigrame.dialogs;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class NewProjectDialog {
    
    private TextField nameField;
    private TextField locationField;
    private ComboBox<String> languageCombo;
    private ComboBox<String> buildSystemCombo;
    private ComboBox<String> jdkCombo;
    private CheckBox springBootCheck;
    private CheckBox sampleCodeCheck;
    private Stage dialog;
    private Map<String, Object> result = null;
    
    public Map<String, Object> showAndWait() {
        dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.DECORATED);
        dialog.setTitle("New Project");
        dialog.setWidth(680);
        dialog.setHeight(720);
        dialog.setMinWidth(580);
        dialog.setMinHeight(640);
        dialog.setResizable(true);
        
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #2d2d2d;");
        
        // Wrap content in ScrollPane to prevent overflow
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #2d2d2d; -fx-border-color: transparent;");
        scrollPane.getStyleClass().add("edge-to-edge");
        scrollPane.setContent(createContent());
        root.setCenter(scrollPane);
        root.setBottom(createButtonBar());
        
        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        dialog.setScene(scene);
        dialog.showAndWait();
        
        return result;
    }
    
    private VBox createContent() {
        VBox content = new VBox();
        content.setStyle("-fx-background-color: #2d2d2d;");
        content.setPadding(new Insets(32));
        content.setSpacing(18);
        
        Label header = new Label("Create New Project");
        header.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 22px; -fx-font-weight: bold;");
        content.getChildren().add(header);
        
        Label description = new Label("Set up a new project with Phiigrame");
        description.setStyle("-fx-text-fill: #8b8b8b; -fx-font-size: 12px;");
        content.getChildren().add(description);
        
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #454545;");
        content.getChildren().add(sep);
        
        content.getChildren().add(createForm());
        content.getChildren().add(createAdvancedOptions());
        
        return content;
    }
    
    private VBox createForm() {
        VBox form = new VBox(14);
        form.setFillWidth(true);
        
        // Name
        nameField = new TextField();
        nameField.setPromptText("MyProject");
        nameField.setMaxWidth(Double.MAX_VALUE);
        form.getChildren().add(createField("Name", nameField));
        
        // Location
        HBox locationRow = new HBox(8);
        locationRow.setAlignment(Pos.CENTER_LEFT);
        locationField = new TextField();
        locationField.setPromptText("Project location");
        HBox.setHgrow(locationField, Priority.ALWAYS);
        Button browseBtn = new Button("Browse...");
        browseBtn.setPrefWidth(90);
        browseBtn.setOnAction(e -> browseLocation());
        locationRow.getChildren().addAll(locationField, browseBtn);
        form.getChildren().add(createField("Location", locationRow));
        
        // Language
        languageCombo = new ComboBox<>();
        languageCombo.getItems().addAll("Java", "Kotlin", "Groovy");
        languageCombo.setValue("Java");
        languageCombo.setMaxWidth(Double.MAX_VALUE);
        form.getChildren().add(createField("Language", languageCombo));
        
        // Build System
        buildSystemCombo = new ComboBox<>();
        buildSystemCombo.getItems().addAll("Gradle - Groovy DSL", "Gradle - Kotlin DSL", "Maven");
        buildSystemCombo.setValue("Gradle - Groovy DSL");
        buildSystemCombo.setMaxWidth(Double.MAX_VALUE);
        form.getChildren().add(createField("Build System", buildSystemCombo));
        
        return form;
    }
    
    private VBox createAdvancedOptions() {
        VBox advanced = new VBox(12);
        advanced.setStyle("-fx-background-color: #252526; -fx-padding: 16; -fx-background-radius: 6;");
        advanced.setFillWidth(true);
        
        Label advancedTitle = new Label("Advanced Settings");
        advancedTitle.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px; -fx-font-weight: bold;");
        advanced.getChildren().add(advancedTitle);
        
        springBootCheck = new CheckBox("Add Spring Boot support");
        springBootCheck.setStyle("-fx-text-fill: #d4d4d4; -fx-font-size: 12px;");
        advanced.getChildren().add(springBootCheck);
        
        HBox jdkRow = new HBox(8);
        jdkRow.setAlignment(Pos.CENTER_LEFT);
        Label jdkLabel = new Label("JDK:");
        jdkLabel.setStyle("-fx-text-fill: #d4d4d4; -fx-font-size: 12px; -fx-min-width: 100;");
        jdkCombo = new ComboBox<>();
        jdkCombo.getItems().addAll("11", "17", "21", "25");
        jdkCombo.setValue("17");
        jdkCombo.setPrefWidth(120);
        jdkRow.getChildren().addAll(jdkLabel, jdkCombo);
        advanced.getChildren().add(jdkRow);
        
        sampleCodeCheck = new CheckBox("Generate sample code");
        sampleCodeCheck.setSelected(true);
        sampleCodeCheck.setStyle("-fx-text-fill: #d4d4d4; -fx-font-size: 12px;");
        advanced.getChildren().add(sampleCodeCheck);
        
        return advanced;
    }
    
    private VBox createField(String labelText, javafx.scene.Node field) {
        VBox container = new VBox(6);
        container.setFillWidth(true);
        Label label = new Label(labelText);
        label.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px; -fx-font-weight: bold;");
        container.getChildren().add(label);
        container.getChildren().add(field);
        return container;
    }
    
    private HBox createButtonBar() {
        HBox buttonBar = new HBox();
        buttonBar.setStyle("-fx-background-color: #252526; -fx-padding: 16 32; -fx-border-color: #1e1e1e transparent transparent transparent; -fx-border-width: 1 0 0 0;");
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setSpacing(10);
        
        Button createButton = new Button("Create");
        createButton.setPrefWidth(100);
        createButton.setDefaultButton(true);
        createButton.setStyle("-fx-background-color: #3574f0; -fx-text-fill: white; -fx-background-radius: 4;");
        createButton.setOnAction(e -> createProject());
        createButton.setOnMouseEntered(e -> createButton.setStyle("-fx-background-color: #4a85f2; -fx-text-fill: white; -fx-background-radius: 4;"));
        createButton.setOnMouseExited(e -> createButton.setStyle("-fx-background-color: #3574f0; -fx-text-fill: white; -fx-background-radius: 4;"));
        
        Button cancelButton = new Button("Cancel");
        cancelButton.setPrefWidth(100);
        cancelButton.setOnAction(e -> dialog.close());
        
        buttonBar.getChildren().addAll(cancelButton, createButton);
        
        return buttonBar;
    }
    
    private void browseLocation() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Project Location");
        File selectedDir = chooser.showDialog(dialog);
        if (selectedDir != null) {
            locationField.setText(selectedDir.getAbsolutePath());
        }
    }
    
    private void createProject() {
        String name = nameField.getText().trim();
        String location = locationField.getText().trim();
        String language = languageCombo.getValue();
        String buildSystemDisplay = buildSystemCombo.getValue();
        String jdk = jdkCombo != null ? jdkCombo.getValue() : "17";
        boolean sampleCode = sampleCodeCheck != null && sampleCodeCheck.isSelected();
        
        if (name.isEmpty()) {
            showAlert("Error", "Please enter a project name");
            return;
        }
        
        if (location.isEmpty()) {
            showAlert("Error", "Please select a project location");
            return;
        }
        
        // Normalize build system
        String buildSystem = "Gradle";
        if (buildSystemDisplay.startsWith("Maven")) {
            buildSystem = "Maven";
        }
        
        result = new HashMap<>();
        result.put("name", name);
        result.put("location", location);
        result.put("language", language);
        result.put("buildSystem", buildSystem);
        result.put("springBoot", springBootCheck.isSelected());
        result.put("jdk", jdk);
        result.put("sampleCode", sampleCode);
        
        dialog.close();
    }
    
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}