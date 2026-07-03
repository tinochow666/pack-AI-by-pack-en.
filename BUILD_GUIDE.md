# Phiigrame IDE 构建指南

## 需要下载的内容

### 1. 安装 Java Development Kit (JDK)

**下载地址**: 
- Eclipse Temurin (推荐): https://adoptium.net/
- Oracle JDK: https://www.oracle.com/java/technologies/downloads/

**推荐版本**: JDK 17 或更高版本

**安装步骤**:
1. 访问上述下载地址
2. 下载Windows x64版本的JDK
3. 运行安装程序，按照提示完成安装
4. 设置环境变量 JAVA_HOME（指向JDK安装目录）
5. 将 %JAVA_HOME%\bin 添加到 PATH 环境变量
6. 重启命令行窗口

**验证安装**:
```bash
java -version
javac -version
```

### 2. 安装 Gradle (可选)

项目包含Gradle wrapper，可以不单独安装Gradle。如果需要单独安装：

**下载地址**: https://gradle.org/install/

**推荐版本**: Gradle 8.x

## 构建EXE文件

### 步骤1: 构建项目

打开命令行，进入项目目录：

```bash
cd e:/ide/Phiigrame
gradlew build
```

或者如果安装了Gradle：

```bash
gradle build
```

这个过程会下载所有依赖，第一次可能需要几分钟。

### 步骤2: 创建EXE安装程序

```bash
gradlew jpackage
```

或者：

```bash
gradle jpackage
```

这个命令会：
1. 编译Java应用
2. 使用jpackage工具打包
3. 生成Windows安装程序EXE

### 步骤3: 找到EXE文件

构建完成后，在 `dist/` 目录下会找到：

- **PhiigrameIDE-1.0.0.exe** - Windows安装程序

## 运行应用

### 直接运行（不构建EXE）

```bash
gradlew run
```

或者：

```bash
gradle run
```

### 运行已安装的EXE

双击 `dist/PhiigrameIDE-1.0.0.exe` 安装后，从开始菜单或桌面快捷方式启动。

## 常见问题

### java命令找不到
- 确保已安装JDK
- 检查JAVA_HOME环境变量是否正确设置
- 检查PATH是否包含 %JAVA_HOME%\bin
- 重启命令行窗口

### gradlew命令执行失败
- 确保Java版本是17或更高
- 检查JAVA_HOME环境变量
- 尝试使用 `gradlew.bat` (Windows)

### jpackage命令失败
- 确保使用JDK 17或更高版本（jpackage在JDK 14+才可用）
- 检查JAVA_HOME是否指向JDK而不是JRE
- 验证jpackage是否存在：`%JAVA_HOME%\bin\jpackage --version`

### 依赖下载很慢
- 可以配置国内镜像源，在 `build.gradle` 中添加镜像配置
- 或者手动下载依赖到本地Maven仓库

### 应用启动失败
- 检查JavaFX模块是否正确下载
- 查看控制台错误信息
- 确保所有依赖都已成功下载

## 系统要求

- Windows 10 或更高版本
- JDK 17 或更高版本
- 至少 4GB RAM
- 至少 500MB 可用磁盘空间

## 环境变量配置示例

**JAVA_HOME**:
```
C:\Program Files\Eclipse Adoptium\jdk-17.0.8.101-hotspot
```

**PATH** (添加):
```
%JAVA_HOME%\bin
```

## 快速开始

1. 安装JDK 17+
2. 配置JAVA_HOME环境变量
3. 运行 `gradlew run` 测试应用
4. 运行 `gradlew jpackage` 构建EXE
