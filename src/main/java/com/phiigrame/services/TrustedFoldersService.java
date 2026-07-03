package com.phiigrame.services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

/**
 * Persistent list of folder paths the user has explicitly trusted.
 *
 * <p>The IDE will show a "Do you trust this folder?" dialog the first
 * time a workspace is opened.  The decision is remembered in
 * {@code ~/.phiigrame/trusted-folders.json} so the dialog is not
 * shown again for the same folder.
 *
 * <p>Trust is a path-level concept.  Trusting {@code /home/me/proj}
 * does <b>not</b> automatically trust its siblings; the user can
 * still be prompted for each project they open.
 */
public class TrustedFoldersService {

    private static final Path CONFIG_PATH = Paths.get(
            System.getProperty("user.home"), ".phiigrame", "trusted-folders.json");

    private final Set<String> trusted = new HashSet<>();
    private final Gson gson = new Gson();
    private boolean loaded = false;

    private synchronized void ensureLoaded() {
        if (loaded) return;
        if (Files.exists(CONFIG_PATH)) {
            try (Reader r = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                JsonObject o = gson.fromJson(r, JsonObject.class);
                if (o != null && o.has("trusted") && o.get("trusted").isJsonArray()) {
                    o.getAsJsonArray("trusted").forEach(e -> trusted.add(e.getAsString()));
                }
            } catch (IOException e) {
                System.err.println("Failed to load trusted-folders: " + e.getMessage());
            }
        }
        loaded = true;
    }

    public boolean isTrusted(Path folder) {
        ensureLoaded();
        if (folder == null) return false;
        return trusted.contains(folder.toAbsolutePath().normalize().toString());
    }

    public synchronized void trust(Path folder) {
        ensureLoaded();
        if (folder == null) return;
        trusted.add(folder.toAbsolutePath().normalize().toString());
        persist();
    }

    public synchronized void revoke(Path folder) {
        ensureLoaded();
        if (folder == null) return;
        trusted.remove(folder.toAbsolutePath().normalize().toString());
        persist();
    }

    private void persist() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            JsonObject o = new JsonObject();
            o.add("trusted", gson.toJsonTree(trusted));
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                gson.toJson(o, w);
            }
        } catch (IOException e) {
            System.err.println("Failed to save trusted-folders: " + e.getMessage());
        }
    }
}
