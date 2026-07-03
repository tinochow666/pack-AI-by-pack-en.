package com.phiigrame.services;

import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

public class ProjectManager {
    
    private String projectPath;
    private String projectName;
    private String language;
    private String buildSystem;
    private boolean springBoot;
    
    public boolean createProject(Map<String, Object> projectConfig) {
        this.projectName = (String) projectConfig.get("name");
        this.projectPath = (String) projectConfig.get("location");
        this.language = (String) projectConfig.get("language");
        this.buildSystem = (String) projectConfig.get("buildSystem");
        this.springBoot = (Boolean) projectConfig.get("springBoot");
        
        // Sanitize project name for package use
        String safeName = sanitizeName(projectName);
        if (safeName.isEmpty()) {
            return false;
        }
        
        try {
            // Create project directory
            Path projectDir = Paths.get(projectPath, projectName);
            if (Files.exists(projectDir)) {
                return false;
            }
            Files.createDirectories(projectDir);
            
            // Create src/main/java structure
            Path srcDir = projectDir.resolve("src");
            Path mainDir = srcDir.resolve("main");
            Path javaDir = mainDir.resolve("java");
            Files.createDirectories(javaDir);
            
            // Create resources directory
            Path resourcesDir = mainDir.resolve("resources");
            Files.createDirectories(resourcesDir);
            
            // Create test directory
            Path testDir = srcDir.resolve("test");
            Path testJavaDir = testDir.resolve("java");
            Files.createDirectories(testJavaDir);
            
            // Create build file based on build system
            if ("Gradle".equals(buildSystem)) {
                createGradleFiles(projectDir);
            } else if ("Maven".equals(buildSystem)) {
                createMavenFiles(projectDir);
            }
            
            // Create main class based on language
            createMainClass(javaDir, language);
            
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    private String sanitizeName(String name) {
        if (name == null) return "";
        // Replace invalid chars with underscore
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }
    
    private String packageName() {
        return "com." + sanitizeName(projectName).toLowerCase();
    }
    
    private void createGradleFiles(Path projectDir) throws IOException {
        // build.gradle
        String buildContent = generateGradleBuild();
        Files.writeString(projectDir.resolve("build.gradle"), buildContent);
        
        // settings.gradle
        String settingsContent = "rootProject.name = '" + projectName + "'";
        Files.writeString(projectDir.resolve("settings.gradle"), settingsContent);
        
        // gradlew wrapper files
        Files.createDirectories(projectDir.resolve("gradle/wrapper"));
        String wrapperProps = """
            distributionBase=GRADLE_USER_HOME
            distributionPath=wrapper/dists
            distributionUrl=https\\://services.gradle.org/distributions/gradle-8.5-bin.zip
            networkTimeout=10000
            validateDistributionUrl=true
            zipStoreBase=GRADLE_USER_HOME
            zipStorePath=wrapper/dists
            """;
        Files.writeString(projectDir.resolve("gradle/wrapper/gradle-wrapper.properties"), wrapperProps);
    }
    
    private void createMavenFiles(Path projectDir) throws IOException {
        // pom.xml
        String pomContent = generateMavenPom();
        Files.writeString(projectDir.resolve("pom.xml"), pomContent);
    }
    
    private void createMainClass(Path javaDir, String language) throws IOException {
        String packageName = packageName();
        Path packageDir = javaDir.resolve(packageName.replace('.', File.separatorChar));
        Files.createDirectories(packageDir);
        
        String className = "Main";
        String content;
        
        if ("Kotlin".equals(language)) {
            content = generateKotlinMain(packageName);
            Files.writeString(packageDir.resolve(className + ".kt"), content);
        } else if ("Groovy".equals(language)) {
            content = generateGroovyMain(packageName);
            Files.writeString(packageDir.resolve(className + ".groovy"), content);
        } else {
            content = generateJavaMain(packageName);
            Files.writeString(packageDir.resolve(className + ".java"), content);
        }
    }
    
    private String generateGradleBuild() {
        String safe = sanitizeName(projectName).toLowerCase();
        StringBuilder sb = new StringBuilder();
        sb.append("plugins {\n");
        sb.append("    id 'java'\n");
        
        if ("Kotlin".equals(language)) {
            sb.append("    id 'org.jetbrains.kotlin.jvm' version '1.9.20'\n");
        }
        
        if ("Groovy".equals(language)) {
            sb.append("    id 'groovy'\n");
        }
        
        if (springBoot) {
            sb.append("    id 'org.springframework.boot' version '3.2.0'\n");
        }
        
        sb.append("}\n\n");
        
        sb.append("group = 'com.").append(safe).append("'\n");
        sb.append("version = '1.0.0'\n\n");
        
        sb.append("repositories {\n");
        sb.append("    mavenCentral()\n");
        sb.append("}\n\n");
        
        sb.append("dependencies {\n");
        
        if ("Kotlin".equals(language)) {
            sb.append("    implementation 'org.jetbrains.kotlin:kotlin-stdlib:1.9.20'\n");
        }
        
        if (springBoot) {
            sb.append("    implementation 'org.springframework.boot:spring-boot-starter-web'\n");
        }
        
        sb.append("    testImplementation 'junit:junit:4.13.2'\n");
        sb.append("}\n\n");
        
        if (springBoot) {
            sb.append("tasks.named('bootJar') {\n");
            sb.append("    mainClass = 'com.").append(safe).append(".Main'\n");
            sb.append("}\n");
        }
        
        return sb.toString();
    }
    
    private String generateMavenPom() {
        String safe = sanitizeName(projectName).toLowerCase();
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n");
        sb.append("         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        sb.append("         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n");
        sb.append("    <modelVersion>4.0.0</modelVersion>\n\n");
        
        sb.append("    <groupId>com.").append(safe).append("</groupId>\n");
        sb.append("    <artifactId>").append(sanitizeName(projectName)).append("</artifactId>\n");
        sb.append("    <version>1.0.0</version>\n\n");
        
        sb.append("    <properties>\n");
        sb.append("        <maven.compiler.source>17</maven.compiler.source>\n");
        sb.append("        <maven.compiler.target>17</maven.compiler.target>\n");
        sb.append("        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n");
        sb.append("    </properties>\n\n");
        
        sb.append("    <dependencies>\n");
        
        if ("Kotlin".equals(language)) {
            sb.append("        <dependency>\n");
            sb.append("            <groupId>org.jetbrains.kotlin</groupId>\n");
            sb.append("            <artifactId>kotlin-stdlib</artifactId>\n");
            sb.append("            <version>1.9.20</version>\n");
            sb.append("        </dependency>\n");
        }
        
        if (springBoot) {
            sb.append("        <dependency>\n");
            sb.append("            <groupId>org.springframework.boot</groupId>\n");
            sb.append("            <artifactId>spring-boot-starter-web</artifactId>\n");
            sb.append("            <version>3.2.0</version>\n");
            sb.append("        </dependency>\n");
        }
        
        sb.append("        <dependency>\n");
        sb.append("            <groupId>junit</groupId>\n");
        sb.append("            <artifactId>junit</artifactId>\n");
        sb.append("            <version>4.13.2</version>\n");
        sb.append("            <scope>test</scope>\n");
        sb.append("        </dependency>\n");
        sb.append("    </dependencies>\n");
        sb.append("</project>\n");
        
        return sb.toString();
    }
    
    private String generateJavaMain(String packageName) {
        if (springBoot) {
            return "package " + packageName + ";\n\n" +
                   "import org.springframework.boot.SpringApplication;\n" +
                   "import org.springframework.boot.autoconfigure.SpringBootApplication;\n\n" +
                   "@SpringBootApplication\n" +
                   "public class Main {\n" +
                   "    public static void main(String[] args) {\n" +
                   "        SpringApplication.run(Main.class, args);\n" +
                   "    }\n" +
                   "}\n";
        }
        return "package " + packageName + ";\n\n" +
               "public class Main {\n" +
               "    public static void main(String[] args) {\n" +
               "        System.out.println(\"Hello, " + projectName + "!\");\n" +
               "    }\n" +
               "}\n";
    }
    
    private String generateKotlinMain(String packageName) {
        if (springBoot) {
            return "package " + packageName + "\n\n" +
                   "import org.springframework.boot.autoconfigure.SpringBootApplication\n" +
                   "import org.springframework.boot.runApplication\n\n" +
                   "@SpringBootApplication\n" +
                   "class Main\n\n" +
                   "fun main(args: Array<String>) {\n" +
                   "    runApplication<Main>(*args)\n" +
                   "}\n";
        }
        return "package " + packageName + "\n\n" +
               "fun main() {\n" +
               "    println(\"Hello, " + projectName + "!\")\n" +
               "}\n";
    }
    
    private String generateGroovyMain(String packageName) {
        if (springBoot) {
            return "package " + packageName + "\n\n" +
                   "import org.springframework.boot.autoconfigure.SpringBootApplication\n" +
                   "import org.springframework.boot.runApplication\n\n" +
                   "@SpringBootApplication\n" +
                   "class Main {\n" +
                   "    static void main(String[] args) {\n" +
                   "        runApplication(Main, args)\n" +
                   "    }\n" +
                   "}\n";
        }
        return "package " + packageName + "\n\n" +
               "class Main {\n" +
               "    static void main(String[] args) {\n" +
               "        println 'Hello, " + projectName + "!'\n" +
               "    }\n" +
               "}\n";
    }
    
    public String getProjectPath() {
        return projectPath;
    }
    
    public String getProjectName() {
        return projectName;
    }
}
