package com.phiigrame.components;

import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class FileIconProvider {
    
    private static final Map<String, Image> iconCache = new HashMap<>();
    private static final int ICON_SIZE = 16;
    
    static {
        iconCache.put("folder", createEmoji("\uD83D\uDCC1"));
        iconCache.put("folder-open", createEmoji("\uD83D\uDCC2"));
        iconCache.put("java", createEmoji("\u2615"));
        iconCache.put("kotlin", createEmoji("\uD83D\uDC8E"));
        iconCache.put("groovy", createEmoji("\uD83C\uDF7C"));
        iconCache.put("gradle", createEmoji("\uD83D\uDE80"));
        iconCache.put("xml", createEmoji("\uD83D\uDCDD"));
        iconCache.put("properties", createEmoji("\u2699"));
        iconCache.put("txt", createEmoji("\uD83D\uDCC4"));
        iconCache.put("default", createEmoji("\uD83D\uDCC4"));
    }
    
    /**
     * Loads the application logo from resources.
     */
    public static Image loadLogo() {
        try (InputStream is = FileIconProvider.class.getResourceAsStream("/logo.png")) {
            if (is != null) {
                return new Image(is);
            }
        } catch (Exception ignored) {
        }
        return null;
    }
    
    private static Image createEmoji(String emoji) {
        // Return a 1x1 transparent placeholder; the actual rendering is done via getIconNode()
        return new Image("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=");
    }
    
    /**
     * Returns a Text node displaying an emoji icon. This is used as the graphic for tree cells.
     */
    public static Node getIconNode(String fileName, boolean isDirectory) {
        String emoji;
        if (isDirectory) {
            emoji = "\uD83D\uDCC1";  // 📁
        } else {
            String ext = getFileExtension(fileName);
            switch (ext) {
                case "java":       emoji = "\u2615"; break;       // ☕
                case "kotlin":     emoji = "\uD83D\uDC8E"; break;  // 💎
                case "groovy":     emoji = "\uD83C\uDF7C"; break;  // 🍼
                case "gradle":     emoji = "\uD83D\uDE80"; break;  // 🚀
                case "xml":        emoji = "\uD83D\uDCDD"; break;  // 📝
                case "properties": emoji = "\u2699"; break;        // ⚙
                case "txt":        emoji = "\uD83D\uDCC4"; break;  // 📄
                default:           emoji = "\uD83D\uDCC4"; break;  // 📄
            }
        }
        
        Text text = new Text(emoji);
        text.setFont(Font.font("Segoe UI Emoji", 14));
        text.setFill(Color.web("#cccccc"));
        return text;
    }
    
    public static ImageView getIcon(String fileName, boolean isDirectory) {
        Image image;
        
        if (isDirectory) {
            image = iconCache.get("folder");
        } else {
            String extension = getFileExtension(fileName);
            image = iconCache.getOrDefault(extension, iconCache.get("default"));
        }
        
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(ICON_SIZE);
        imageView.setFitHeight(ICON_SIZE);
        return imageView;
    }
    
    private static String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1) {
            return "default";
        }
        String ext = fileName.substring(lastDot + 1).toLowerCase();
        
        switch (ext) {
            case "java":
                return "java";
            case "kt":
                return "kotlin";
            case "groovy":
                return "groovy";
            case "gradle":
            case "gradle.kts":
                return "gradle";
            case "xml":
                return "xml";
            case "properties":
                return "properties";
            case "txt":
            case "md":
                return "txt";
            default:
                return "default";
        }
    }
}