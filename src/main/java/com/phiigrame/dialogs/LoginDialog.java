package com.phiigrame.dialogs;

import com.phiigrame.services.AuthService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Login / Register dialog for the Phiigrame Account system.
 */
public class LoginDialog {
    
    private final AuthService authService;
    private Stage dialog;
    private boolean completed = false;
    
    public LoginDialog(AuthService authService) {
        this.authService = authService;
    }
    
    public boolean showAndWait() {
        if (authService.isLoggedIn()) {
            completed = true;
            return true;
        }
        
        dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.DECORATED);
        dialog.setTitle("Phiigrame Account");
        dialog.setWidth(460);
        dialog.setHeight(680);
        dialog.setMinWidth(440);
        dialog.setMinHeight(640);
        dialog.setResizable(false);
        
        VBox root = new VBox();
        root.setStyle("-fx-background-color: #2d2d2d;");
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(24, 36, 24, 36));
        root.setSpacing(12);
        
        // Logo (logo(in-app).png) - replaces the text "Phiigrame"
        ImageView logoView = new ImageView();
        try {
            Image logo = new Image(getClass().getResourceAsStream("/logo-in-app.png"));
            if (logo != null && !logo.isError()) {
                logoView.setImage(logo);
                logoView.setPreserveRatio(true);
                logoView.setFitWidth(140);
                logoView.setFitHeight(90);
                logoView.setSmooth(true);
                root.getChildren().add(logoView);
            } else {
                throw new RuntimeException("logo not loadable");
            }
        } catch (Exception ex) {
            // Fallback to the original text title if the image is missing
            Label title = new Label("Phiigrame");
            title.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 32px; -fx-font-weight: bold;");
            title.setTextAlignment(TextAlignment.CENTER);
            root.getChildren().add(title);
        }
        
        Label subtitle = new Label("Sign in to your Phiigrame Account");
        subtitle.setStyle("-fx-text-fill: #8b8b8b; -fx-font-size: 12px;");
        subtitle.setTextAlignment(TextAlignment.CENTER);
        
        root.getChildren().addAll(subtitle, new Separator());
        
        // Tabs for login / register
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setStyle("-fx-background-color: #2d2d2d;");
        
        Tab loginTab = new Tab("Sign In");
        loginTab.setContent(createLoginForm());
        
        Tab registerTab = new Tab("Create Account");
        registerTab.setContent(createRegisterForm());
        
        tabPane.getTabs().addAll(loginTab, registerTab);
        root.getChildren().add(tabPane);
        
        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        dialog.setScene(scene);
        dialog.showAndWait();
        return completed;
    }
    
    private VBox createLoginForm() {
        VBox form = new VBox(10);
        form.setStyle("-fx-background-color: #2d2d2d;");
        form.setPadding(new Insets(16, 0, 8, 0));
        
        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: #d4d4d4; " +
                "-fx-prompt-text-fill: #6e7681; -fx-border-color: #454545; -fx-border-radius: 4; " +
                "-fx-background-radius: 4; -fx-padding: 10 12;");
        
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: #d4d4d4; " +
                "-fx-prompt-text-fill: #6e7681; -fx-border-color: #454545; -fx-border-radius: 4; " +
                "-fx-background-radius: 4; -fx-padding: 10 12;");
        
        Button signInBtn = new Button("Sign In");
        signInBtn.setStyle("-fx-background-color: #0e639c; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-padding: 10 16; -fx-background-radius: 4; " +
                "-fx-cursor: hand;");
        signInBtn.setMaxWidth(Double.MAX_VALUE);
        
        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #f48771; -fx-font-size: 11px;");
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        
        Runnable attemptLogin = () -> {
            String username = usernameField.getText().trim();
            String password = passwordField.getText();
            if (username.isEmpty() || password.isEmpty()) {
                showError(errorLabel, "Please fill in all fields");
                return;
            }
            if (authService.login(username, password)) {
                completed = true;
                dialog.close();
            } else {
                showError(errorLabel, "Invalid username or password");
            }
        };
        
        signInBtn.setOnAction(e -> attemptLogin.run());
        passwordField.setOnAction(e -> attemptLogin.run());
        
        form.getChildren().addAll(usernameField, passwordField, signInBtn, errorLabel);
        return form;
    }
    
    private VBox createRegisterForm() {
        VBox form = new VBox(10);
        form.setStyle("-fx-background-color: #2d2d2d;");
        form.setPadding(new Insets(16, 0, 8, 0));
        
        TextField usernameField = new TextField();
        usernameField.setPromptText("Username (3+ characters)");
        usernameField.setStyle(fieldStyle());
        
        TextField emailField = new TextField();
        emailField.setPromptText("Email (optional)");
        emailField.setStyle(fieldStyle());
        
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password (4+ characters)");
        passwordField.setStyle(fieldStyle());
        
        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("Confirm password");
        confirmField.setStyle(fieldStyle());
        
        Button registerBtn = new Button("Create Account");
        registerBtn.setStyle("-fx-background-color: #0e639c; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-padding: 10 16; -fx-background-radius: 4; " +
                "-fx-cursor: hand;");
        registerBtn.setMaxWidth(Double.MAX_VALUE);
        
        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #f48771; -fx-font-size: 11px;");
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        
        registerBtn.setOnAction(e -> {
            String username = usernameField.getText().trim();
            String email = emailField.getText().trim();
            String password = passwordField.getText();
            String confirm = confirmField.getText();
            
            if (username.length() < 3) {
                showError(errorLabel, "Username must be at least 3 characters");
                return;
            }
            if (password.length() < 4) {
                showError(errorLabel, "Password must be at least 4 characters");
                return;
            }
            if (!password.equals(confirm)) {
                showError(errorLabel, "Passwords do not match");
                return;
            }
            if (authService.register(username, email, password)) {
                completed = true;
                dialog.close();
            } else {
                showError(errorLabel, "Username already exists");
            }
        });
        
        form.getChildren().addAll(usernameField, emailField, passwordField, confirmField, registerBtn, errorLabel);
        return form;
    }
    
    private String fieldStyle() {
        return "-fx-background-color: #1e1e1e; -fx-text-fill: #d4d4d4; " +
               "-fx-prompt-text-fill: #6e7681; -fx-border-color: #454545; -fx-border-radius: 4; " +
               "-fx-background-radius: 4; -fx-padding: 10 12;";
    }
    
    private void showError(Label label, String message) {
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
    }
}
