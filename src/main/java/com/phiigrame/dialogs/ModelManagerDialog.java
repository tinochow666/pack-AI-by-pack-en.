package com.phiigrame.dialogs;

import com.phiigrame.services.AiService;
import com.phiigrame.services.ModelConfigService;
import com.phiigrame.services.ModelConfigService.Kind;
import com.phiigrame.services.ModelConfigService.Model;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Modal dialog that lets the user pick the active model, edit existing
 * presets, and add new ones (including custom OpenAI-compatible APIs).
 */
public class ModelManagerDialog {

    private final ModelConfigService config;
    private final AiService aiService;

    public ModelManagerDialog(ModelConfigService config, AiService aiService) {
        this.config = config;
        this.aiService = aiService;
    }

    public void show() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("AI Models");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setStyle(
                "-fx-background-color: #2d2d2d; " +
                "-fx-min-width: 720; -fx-min-height: 540;");

        VBox root = new VBox(12);
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: #2d2d2d;");

        Label title = new Label("Model Manager");
        title.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 14px; -fx-font-weight: bold;");
        root.getChildren().add(title);

        ListView<Model> list = new ListView<>(FXCollections.observableArrayList(config.all()));
        list.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Model m, boolean empty) {
                super.updateItem(m, empty);
                if (empty || m == null) { setText(null); return; }
                String kindTag = m.kind == Kind.LOCAL ? "[local] " : "[remote] ";
                String active = m.id.equals(config.activeId()) ? "  \u2022 active" : "";
                setText(kindTag + m.name + active + "\n    " + m.description);
            }
        });
        list.setStyle("-fx-background-color: #1e1e1e; -fx-control-inner-background: #1e1e1e; " +
                "-fx-text-fill: #d4d4d4;");
        VBox.setVgrow(list, Priority.ALWAYS);
        root.getChildren().add(list);

        // Buttons: set active / new local / new custom / delete
        HBox row = new HBox(8);
        Button setActive = new Button("Use selected");
        setActive.setStyle(btn("#3574f0"));
        Button newLocal = new Button("New local preset");
        newLocal.setStyle(btn("#3a3d41"));
        Button newRemote = new Button("New remote (OpenAI-compatible)");
        newRemote.setStyle(btn("#3a3d41"));
        Button deleteBtn = new Button("Delete");
        deleteBtn.setStyle(btn("#7a2020"));
        row.getChildren().addAll(setActive, newLocal, newRemote, deleteBtn);
        root.getChildren().add(row);

        setActive.setOnAction(e -> {
            Model m = list.getSelectionModel().getSelectedItem();
            if (m == null) return;
            config.setActive(m.id);
            applyActiveModel(m);
            list.refresh();
        });
        newLocal.setOnAction(e -> {
            Model m = editDialog(null, true);
            if (m != null) { config.upsert(m); list.getItems().add(m); list.refresh(); }
        });
        newRemote.setOnAction(e -> {
            Model m = editDialog(null, false);
            if (m != null) { config.upsert(m); list.getItems().add(m); list.refresh(); }
        });
        deleteBtn.setOnAction(e -> {
            Model m = list.getSelectionModel().getSelectedItem();
            if (m == null) return;
            if (m.id.equals(config.activeId())) {
                warn("Cannot delete the active model."); return;
            }
            config.delete(m.id);
            list.getItems().remove(m);
        });

        // Footer
        Label hint = new Label(
            "Local presets run on this machine via the bundled llama.cpp. " +
            "Remote presets send your prompt to the configured endpoint - " +
            "do not enter real API keys unless you trust the URL.");
        hint.setStyle("-fx-text-fill: #8b8b8b; -fx-font-size: 11px;");
        hint.setWrapText(true);
        root.getChildren().add(hint);

        dialog.getDialogPane().setContent(root);
        dialog.setResultConverter(b -> b);
        dialog.showAndWait();
    }

    /** Edit dialog for a single model.  Returns null if the user cancels. */
    private Model editDialog(Model preset, boolean local) {
        Dialog<Model> d = new Dialog<>();
        d.setTitle(local ? "Local Model Preset" : "Remote API Preset");
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox form = new VBox(8);
        form.setPadding(new Insets(16));
        form.setStyle("-fx-background-color: #2d2d2d;");
        form.setPrefWidth(560);

        TextField name = new TextField(preset == null ? "" : preset.name);
        TextField desc = new TextField(preset == null ? "" : preset.description);
        TextField repo = new TextField(preset == null ? "owner/repo" : preset.repo);
        TextField file = new TextField(preset == null ? "model-q4_k_m.gguf" : preset.file);
        TextField mirror = new TextField(preset == null ? "" : preset.mirror);
        TextField baseUrl = new TextField(preset == null ? "https://api.openai.com/v1" : preset.baseUrl);
        TextField apiKey = new TextField(preset == null ? "" : preset.apiKey);
        TextField remoteModel = new TextField(preset == null ? "gpt-4o-mini" : preset.remoteModel);

        form.getChildren().add(row("Name", "Display name shown in the dropdown", name));
        form.getChildren().add(row("Description", "", desc));
        if (local) {
            form.getChildren().add(row("Hugging Face repo",
                    "e.g. Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF", repo));
            form.getChildren().add(row("GGUF file",
                    "Single .gguf file inside the repo", file));
            form.getChildren().add(row("Mirror (optional)",
                    "https://hf-mirror.com or similar", mirror));
        } else {
            form.getChildren().add(row("Base URL",
                    "OpenAI-compatible root, e.g. https://api.openai.com/v1", baseUrl));
            form.getChildren().add(row("API key",
                    "Leave blank for unauthenticated endpoints", apiKey));
            form.getChildren().add(row("Model name",
                    "Model id the endpoint should run", remoteModel));
        }
        d.getDialogPane().setContent(form);
        d.setResultConverter(b -> {
            if (b != ButtonType.OK) return null;
            Model m = new Model();
            m.id = preset == null ? "model-" + System.currentTimeMillis() : preset.id;
            m.kind = local ? Kind.LOCAL : Kind.REMOTE;
            m.name = name.getText().trim();
            m.description = desc.getText().trim();
            m.repo = repo.getText().trim();
            m.file = file.getText().trim();
            m.mirror = mirror.getText().trim();
            m.baseUrl = baseUrl.getText().trim();
            m.apiKey = apiKey.getText().trim();
            m.remoteModel = remoteModel.getText().trim();
            if (m.name.isEmpty()) m.name = local ? "New local model" : "New remote API";
            return m;
        });
        return d.showAndWait().orElse(null);
    }

    /** A vertical "label + hint + field" row used by the edit dialog. */
    private static VBox row(String label, String hint, Control field) {
        VBox box = new VBox(2);
        Label l = new Label(label);
        l.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 11px;");
        box.getChildren().add(l);
        if (hint != null && !hint.isEmpty()) {
            Label h = new Label(hint);
            h.setStyle("-fx-text-fill: #6e7681; -fx-font-size: 10px; -fx-font-style: italic;");
            h.setWrapText(true);
            box.getChildren().add(h);
        }
        field.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: #d4d4d4; " +
                "-fx-prompt-text-fill: #6e7681; -fx-border-color: #454545; " +
                "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 6 10;");
        field.setMaxWidth(Double.MAX_VALUE);
        box.getChildren().add(field);
        return box;
    }

    private void applyActiveModel(Model m) {
        if (m.kind == Kind.LOCAL) {
            aiService.setModelConfig(m.repo, m.file, m.mirror);
        } else {
            aiService.setRemoteModel(m.baseUrl, m.apiKey, m.remoteModel);
        }
    }

    private static void warn(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }

    private static String btn(String color) {
        return "-fx-background-color: " + color + "; -fx-text-fill: white; " +
                "-fx-padding: 6 12; -fx-background-radius: 4; -fx-cursor: hand;";
    }
}
