package com.phiigrame.components;

import javafx.scene.control.TreeItem;

import java.io.File;

public class FileTreeItem extends TreeItem<File> {
    
    public FileTreeItem(File file) {
        super(file);
    }
    
    public File getFile() {
        return getValue();
    }
    
    public boolean isDirectory() {
        File f = getValue();
        return f != null && f.isDirectory();
    }
    
    public String getName() {
        File f = getValue();
        return f != null ? f.getName() : "";
    }
    
    @Override
    public boolean isLeaf() {
        File f = getValue();
        return f != null && f.isFile();
    }
}