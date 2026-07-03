package com.phiigrame.dialogs;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.fxmisc.richtext.CodeArea;

public class SearchReplaceDialog {
    
    private TextField searchField;
    private TextField replaceField;
    private CheckBox matchCaseCheck;
    private CheckBox regexCheck;
    private Label resultLabel;
    private Stage dialog;
    private CodeArea codeArea;
    
    public SearchReplaceDialog(CodeArea codeArea) {
        this.codeArea = codeArea;
    }
    
    public void show() {
        dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UTILITY);
        dialog.setTitle("Find and Replace");
        dialog.setWidth(450);
        dialog.setHeight(300);
        
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #313335;");
        
        // Search field
        Label searchLabel = new Label("Find:");
        searchLabel.setStyle("-fx-text-fill: #a9b7c6;");
        searchField = new TextField();
        searchField.setStyle("-fx-background-color: #3c3f41; -fx-text-fill: #a9b7c6; -fx-border-color: #4e5254;");
        
        // Replace field
        Label replaceLabel = new Label("Replace with:");
        replaceLabel.setStyle("-fx-text-fill: #a9b7c6;");
        replaceField = new TextField();
        replaceField.setStyle("-fx-background-color: #3c3f41; -fx-text-fill: #a9b7c6; -fx-border-color: #4e5254;");
        
        // Options
        HBox optionsBox = new HBox(20);
        matchCaseCheck = new CheckBox("Match case");
        matchCaseCheck.setStyle("-fx-text-fill: #a9b7c6;");
        regexCheck = new CheckBox("Regular expression");
        regexCheck.setStyle("-fx-text-fill: #a9b7c6;");
        optionsBox.getChildren().addAll(matchCaseCheck, regexCheck);
        
        // Buttons
        HBox buttonBox = new HBox(10);
        Button findButton = new Button("Find");
        findButton.setStyle("-fx-background-color: #4b6eaf; -fx-text-fill: white;");
        findButton.setOnAction(e -> findNext());
        
        Button replaceButton = new Button("Replace");
        replaceButton.setStyle("-fx-background-color: #4b6eaf; -fx-text-fill: white;");
        replaceButton.setOnAction(e -> replaceCurrent());
        
        Button replaceAllButton = new Button("Replace All");
        replaceAllButton.setStyle("-fx-background-color: #4b6eaf; -fx-text-fill: white;");
        replaceAllButton.setOnAction(e -> replaceAll());
        
        Button closeButton = new Button("Close");
        closeButton.setStyle("-fx-background-color: #3c3f41; -fx-text-fill: #a9b7c6;");
        closeButton.setOnAction(e -> dialog.close());
        
        buttonBox.getChildren().addAll(findButton, replaceButton, replaceAllButton, closeButton);
        
        // Result label
        resultLabel = new Label("");
        resultLabel.setStyle("-fx-text-fill: #a9b7c6;");
        
        root.getChildren().addAll(
            searchLabel, searchField,
            replaceLabel, replaceField,
            optionsBox,
            buttonBox,
            resultLabel
        );
        
        Scene scene = new Scene(root);
        dialog.setScene(scene);
        dialog.show();
    }
    
    private void findNext() {
        String searchText = searchField.getText();
        if (searchText.isEmpty()) return;
        
        String text = codeArea.getText();
        int caretPos = codeArea.getCaretPosition();
        
        String searchPattern = searchText;
        if (!regexCheck.isSelected()) {
            searchPattern = java.util.regex.Pattern.quote(searchText);
        }
        
        if (!matchCaseCheck.isSelected()) {
            searchPattern = "(?i)" + searchPattern;
        }
        
        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(searchPattern);
            java.util.regex.Matcher matcher = pattern.matcher(text);
            
            if (matcher.find(caretPos)) {
                codeArea.selectRange(matcher.start(), matcher.end());
                resultLabel.setText("Found at position " + matcher.start());
            } else if (matcher.find(0)) {
                codeArea.selectRange(matcher.start(), matcher.end());
                resultLabel.setText("Found at position " + matcher.start() + " (wrapped)");
            } else {
                resultLabel.setText("Not found");
            }
        } catch (Exception e) {
            resultLabel.setText("Invalid pattern: " + e.getMessage());
        }
    }
    
    private void replaceCurrent() {
        String searchText = searchField.getText();
        String replaceText = replaceField.getText();
        
        if (searchText.isEmpty()) return;
        
        if (codeArea.getSelectedText().isEmpty()) {
            findNext();
            return;
        }
        
        String selectedText = codeArea.getSelectedText();
        boolean matches = false;
        
        if (regexCheck.isSelected()) {
            try {
                String searchPattern = searchText;
                if (!matchCaseCheck.isSelected()) {
                    searchPattern = "(?i)" + searchPattern;
                }
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(searchPattern);
                matches = pattern.matcher(selectedText).matches();
            } catch (Exception e) {
                resultLabel.setText("Invalid pattern: " + e.getMessage());
                return;
            }
        } else {
            if (matchCaseCheck.isSelected()) {
                matches = selectedText.equals(searchText);
            } else {
                matches = selectedText.equalsIgnoreCase(searchText);
            }
        }
        
        if (matches) {
            codeArea.replaceSelection(replaceText);
            resultLabel.setText("Replaced");
            findNext();
        } else {
            findNext();
        }
    }
    
    private void replaceAll() {
        String searchText = searchField.getText();
        String replaceText = replaceField.getText();
        
        if (searchText.isEmpty()) return;
        
        String text = codeArea.getText();
        String searchPattern = searchText;
        
        if (!regexCheck.isSelected()) {
            searchPattern = java.util.regex.Pattern.quote(searchText);
        }
        
        if (!matchCaseCheck.isSelected()) {
            searchPattern = "(?i)" + searchPattern;
        }
        
        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(searchPattern);
            java.util.regex.Matcher matcher = pattern.matcher(text);
            
            int count = 0;
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                matcher.appendReplacement(sb, replaceText);
                count++;
            }
            matcher.appendTail(sb);
            
            codeArea.replaceText(0, codeArea.getLength(), sb.toString());
            resultLabel.setText("Replaced " + count + " occurrences");
        } catch (Exception e) {
            resultLabel.setText("Invalid pattern: " + e.getMessage());
        }
    }
}
