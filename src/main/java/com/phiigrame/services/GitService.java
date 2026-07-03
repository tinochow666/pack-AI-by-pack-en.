package com.phiigrame.services;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Git integration service. Uses the system 'git' command to fetch
 * log entries, current status, and branch info. No external dependencies.
 */
public class GitService {
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "git-service");
        t.setDaemon(true);
        return t;
    });
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    
    private File currentRepo;
    private List<GitCommit> commits = new ArrayList<>();
    private String currentBranch = "";
    private boolean isRepo = false;
    private String lastError = "";
    
    public interface Callback<T> {
        void onResult(T result);
    }
    
    public boolean isRepo() {
        return isRepo;
    }
    
    public String getCurrentBranch() {
        return currentBranch;
    }
    
    public List<GitCommit> getCommits() {
        return commits;
    }
    
    public String getLastError() {
        return lastError;
    }
    
    /**
     * Set the project root and refresh git info asynchronously.
     */
    public void setProject(File projectDir, Callback<Boolean> onComplete) {
        executor.submit(() -> {
            try {
                File gitDir = new File(projectDir, ".git");
                if (!gitDir.exists()) {
                    isRepo = false;
                    currentRepo = null;
                    currentBranch = "";
                    commits = new ArrayList<>();
                    if (onComplete != null) onComplete.onResult(false);
                    return;
                }
                currentRepo = projectDir;
                isRepo = true;
                refreshSync();
                if (onComplete != null) onComplete.onResult(true);
            } catch (Exception e) {
                lastError = e.getMessage();
                if (onComplete != null) onComplete.onResult(false);
            }
        });
    }
    
    /**
     * Refresh git log for the current repository.
     */
    public void refresh(Callback<Boolean> onComplete) {
        executor.submit(() -> {
            try {
                if (currentRepo == null) {
                    if (onComplete != null) onComplete.onResult(false);
                    return;
                }
                refreshSync();
                if (onComplete != null) onComplete.onResult(true);
            } catch (Exception e) {
                lastError = e.getMessage();
                if (onComplete != null) onComplete.onResult(false);
            }
        });
    }
    
    private void refreshSync() throws Exception {
        // Get current branch
        currentBranch = runGitCommand("rev-parse", "--abbrev-ref", "HEAD").trim();
        if (currentBranch.isEmpty()) currentBranch = "main";
        
        // Get log (--follow to handle renames, -n to limit)
        String logOutput = runGitCommand("log", "--pretty=format:%H|%h|%an|%ae|%at|%s", "-n", "200");
        commits = new ArrayList<>();
        if (!logOutput.isEmpty()) {
            String[] lines = logOutput.split("\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split("\\|", 6);
                if (parts.length >= 6) {
                    GitCommit c = new GitCommit();
                    c.hash = parts[0];
                    c.shortHash = parts[1];
                    c.author = parts[2];
                    c.email = parts[3];
                    c.timestamp = Long.parseLong(parts[4]) * 1000L;
                    c.message = parts[5];
                    commits.add(c);
                }
            }
        }
    }
    
    public void getCommitDiff(String hash, Callback<String> onResult) {
        executor.submit(() -> {
            try {
                String diff = runGitCommand("show", "--stat", "--pretty=format:", hash);
                onResult.onResult(diff);
            } catch (Exception e) {
                onResult.onResult("Error: " + e.getMessage());
            }
        });
    }
    
    public void getFileDiff(String hash, String filePath, Callback<String> onResult) {
        executor.submit(() -> {
            try {
                String diff = runGitCommand("show", hash, "--", filePath);
                onResult.onResult(diff);
            } catch (Exception e) {
                onResult.onResult("Error: " + e.getMessage());
            }
        });
    }
    
    public void checkout(String ref, Callback<Boolean> onResult) {
        executor.submit(() -> {
            try {
                runGitCommand("checkout", ref);
                refreshSync();
                onResult.onResult(true);
            } catch (Exception e) {
                lastError = e.getMessage();
                onResult.onResult(false);
            }
        });
    }
    
    private String runGitCommand(String... args) throws Exception {
        if (currentRepo == null) throw new IllegalStateException("No project loaded");
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        Collections.addAll(cmd, args);
        
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(currentRepo);
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        int exit = process.waitFor();
        if (exit != 0 && output.length() == 0) {
            throw new RuntimeException("git " + String.join(" ", args) + " exited with " + exit);
        }
        return output.toString();
    }
    
    public String formatTimestamp(long ts) {
        return dateFormat.format(new Date(ts));
    }
    
    public static class GitCommit {
        public String hash;
        public String shortHash;
        public String author;
        public String email;
        public long timestamp;
        public String message;
    }
}
