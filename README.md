# Phiigrame IDE

A powerful IntelliJ IDEA-style desktop IDE editor for Kotlin, Java, and Groovy, built with JavaFX and Spring.

## Features

### 🎨 IntelliJ IDEA-Style UI
- Dark theme matching IntelliJ IDEA's aesthetic
- Sidebar with project file tree
- Tab-based editor with multiple file support
- Status bar with file info and git branch
- Top menu bar with IDE navigation

### 📁 File Explorer
- Hierarchical project tree view
- Language-specific file icons (Kotlin, Java, Groovy)
- Expandable/collapsible directories
- Quick file navigation

### ✍️ Code Editor
- Full-featured code editor with JavaFX
- Syntax highlighting for Kotlin, Java, and Groovy
- Code completion and IntelliSense (planned)
- Line numbers and bracket matching
- Auto-indentation and formatting

### 🔍 Search & Replace
- Global search across all files (planned)
- Case-sensitive and regex options (planned)
- Replace and replace all functionality (planned)

### 💻 Integrated Terminal
- Built-in terminal panel (planned)
- Command execution (planned)
- Build output display (planned)

### 🛠️ Build Tools Support
- Gradle configuration
- Maven support (planned)
- Run and Debug buttons

### 📊 Code Analysis
- Error and warning indicators (planned)
- File modification tracking (planned)
- Language detection
- UTF-8 encoding support

### 🔧 Version Control UI
- Git branch display
- Version control panel (UI ready)
- Commit status indicators (planned)

## Tech Stack

- **JavaFX 21** - Desktop application framework
- **Java 17** - Programming language
- **Gradle** - Build tool
- **Spring Boot** - Backend framework (optional)
- **RichTextFX** - Code editor component
- **ControlsFX** - Additional UI components

## Prerequisites

Before building this project, you need to install:

1. **Java Development Kit (JDK) 17 or higher**
   - Download from: https://adoptium.net/ (Eclipse Temurin)
   - Or from: https://www.oracle.com/java/technologies/downloads/
   - Verify installation: `java -version`
   - Verify JAVA_HOME is set

2. **Gradle** (optional, can use Gradle wrapper)
   - Download from: https://gradle.org/install/
   - Or use the included Gradle wrapper

3. **Git** (optional, for version control)
   - Download from: https://git-scm.com/

## Building the Desktop Application (EXE)

### Step 1: Build the Application

```bash
cd e:/ide/Phiigrame
gradlew build
```

Or if you have Gradle installed:

```bash
gradle build
```

### Step 2: Create EXE Installer

```bash
gradlew jpackage
```

Or:

```bash
gradle jpackage
```

This will:
1. Build the Java application
2. Use jpackage to create Windows installer
3. Generate EXE file in the `dist/` directory

### Step 3: Find the EXE File

After building, you will find the executable in the `dist/` directory:

- **PhiigrameIDE-1.0.0.exe** - Windows installer

### Running the Application

To run the application without building EXE:

```bash
gradlew run
```

Or:

```bash
gradle run
```

## Project Structure

```
Phiigrame/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/phiigrame/
│   │   │       ├── PhiigrameApp.java  # Main application entry
│   │   │       ├── controllers/       # UI controllers (planned)
│   │   │       ├── models/           # Data models (planned)
│   │   │       └── services/         # Business logic (planned)
│   │   └── resources/
│   │       ├── styles/
│   │       │   └── dark-theme.css    # Dark theme styling
│   │       └── fxml/                 # FXML layouts (planned)
├── build.gradle           # Gradle build configuration
├── settings.gradle       # Gradle settings
├── gradlew               # Gradle wrapper (Unix)
├── gradlew.bat           # Gradle wrapper (Windows)
└── dist/                 # Built EXE files
```

## Sample Project

The IDE comes pre-loaded with a sample project structure in the file tree:

- **Kotlin files**: Main.kt, Calculator.kt
- **Java files**: App.java
- **Groovy files**: Script.groovy
- **Build files**: build.gradle

## Keyboard Shortcuts

- `Ctrl+S` - Save (planned)
- `Ctrl+F` - Find (planned)
- `Ctrl+H` - Replace (planned)
- `Ctrl+G` - Go to line (planned)
- `Ctrl+/` - Toggle comment (planned)

## Build Configuration

The jpackage tool is configured to create:
- **Windows EXE Installer** - Standard Windows installer with:
  - Custom installation directory
  - Desktop shortcut
  - Start menu shortcut

## Troubleshooting

### Build Fails
- Ensure Java version is 17 or higher
- Check that JAVA_HOME environment variable is set
- Verify Gradle is properly installed or use the wrapper

### Application Won't Start
- Check Java version compatibility
- Verify all dependencies are downloaded
- Check the console for error messages

### Missing JavaFX
- JavaFX modules are included via Gradle plugin
- Ensure internet connection for first build to download dependencies

## Future Enhancements

- [ ] RichTextFX integration for advanced code editing
- [ ] Syntax highlighting for Kotlin/Java/Groovy
- [ ] Real backend compilation for Kotlin/Java/Groovy
- [ ] Actual terminal with shell access
- [ ] Git integration with real operations
- [ ] Plugin system
- [ ] Theme customization
- [ ] Multiple project support
- [ ] Debugging integration
- [ ] Code refactoring tools
- [ ] Live templates and snippets
- [ ] Auto-update functionality

## License

MIT License - feel free to use this project for learning and development.

## Contributing

Contributions are welcome! Feel free to submit issues and pull requests.
