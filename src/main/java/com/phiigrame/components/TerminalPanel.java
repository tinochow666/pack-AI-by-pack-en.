package com.phiigrame.components;

import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class TerminalPanel extends VBox {
    private TextArea terminalOutput;
    private Process currentProcess;
    
    public TerminalPanel() {
        terminalOutput = new TextArea();
        terminalOutput.setStyle("""
            -fx-background-color: #1e1e1e;
            -fx-text-fill: #a9b7c6;
            -fx-font-family: 'Consolas', monospace;
            -fx-font-size: 12px;
            """);
        terminalOutput.setEditable(false);
        terminalOutput.setWrapText(true);
        
        VBox.setVgrow(terminalOutput, Priority.ALWAYS);
        getChildren().add(terminalOutput);
        
        setStyle("-fx-background-color: #1e1e1e; -fx-border-color: #4e5254;");
        setPrefHeight(200);
    }
    
    public void executeCommand(String command, File workingDir) {
        appendOutput("$ " + command + "\n");
        
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            
            // For Windows
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                processBuilder.command("cmd", "/c", command);
            } else {
                // For Unix-like systems
                processBuilder.command("sh", "-c", command);
            }
            
            if (workingDir != null) {
                processBuilder.directory(workingDir);
            }
            
            processBuilder.redirectErrorStream(true);
            currentProcess = processBuilder.start();
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(currentProcess.getInputStream())
            );
            
            String line;
            while ((line = reader.readLine()) != null) {
                appendOutput(line + "\n");
            }
            
            int exitCode = currentProcess.waitFor();
            appendOutput("\nProcess exited with code: " + exitCode + "\n");
            
        } catch (IOException | InterruptedException e) {
            appendOutput("Error: " + e.getMessage() + "\n");
        }
    }
    
    public void executeCommandAsync(String command, File workingDir) {
        new Thread(() -> executeCommand(command, workingDir)).start();
    }
    
    public void clear() {
        terminalOutput.clear();
    }
    
    private void appendOutput(String text) {
        javafx.application.Platform.runLater(() -> {
            terminalOutput.appendText(text);
        });
    }
    
    public void stopCurrentProcess() {
        if (currentProcess != null && currentProcess.isAlive()) {
            currentProcess.destroy();
            appendOutput("\nProcess terminated\n");
        }
    }
}
