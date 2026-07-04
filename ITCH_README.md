# Phiigrame IDE — itch.io build

Thank you for downloading **Phiigrame IDE 1.0.0**, an AI-augmented
desktop IDE for Kotlin, Java, and Groovy with a built-in local LLM
(Qwen2.5-Coder 1.5B).  This build is a portable app-image - no
installer, no admin rights required.

## How to run (Windows)

1. Unzip `PhiigrameIDE-itch-1.0.0.zip` anywhere you have write
   access (Desktop, `D:\\Games`, etc.).
2. Open the extracted `PhiigrameIDE` folder.
3. Double-click **`PhiigrameIDE.exe`**.

The first launch may take 10-20 seconds while the JVM starts.  When
the login dialog appears, choose **Create Account** or close the
dialog to continue as a guest (the guest account still has full AI
access, but chat history is local-only).

> If SmartScreen blocks the binary, click "More info" → "Run anyway".
> Phiigrame is unsigned while we are in early access.

## What is included

| File / folder           | Purpose                                  |
| ----------------------- | ---------------------------------------- |
| `PhiigrameIDE.exe`      | The launcher (bundles a private JDK 17). |
| `app/PhiigrameIDE.cfg`  | Runtime config (memory, jvm args).       |
| `runtime/`              | Bundled JRE 17 - nothing to install.     |
| `lib/`                  | Phiigrame jars + dependencies.           |

## Quick tour

* **File -> New Project** - scaffold a new project.
* **File -> Open** or **File -> Recent Projects** - reopen a folder.
  The **Welcome** tab shows your most recently used projects; right
  click a row to open it in Explorer or remove it from the list.
* **AI** sidebar tab - chat with the local Qwen2.5-Coder model.  All
  inference happens on your machine; no data leaves your computer.
* **Plugins** -> **Phiigrame AI Assistant** - swap the local model
  for a remote OpenAI-compatible endpoint (DeepSeek, Qwen, OpenAI
  itself, anything with `/chat/completions`).

## System requirements

* Windows 10 64-bit or newer (build 17763+).
* ~1.5 GB free disk space (the bundled JRE alone is ~250 MB).
* 4 GB RAM minimum; 8 GB recommended for the AI.
* No Python, no Ollama, no Docker.

## Source and updates

* Source code: https://github.com/tinochow666/pack-AI-by-pack-en
* Bug reports: open an issue on the GitHub repository.
* License: MIT.

Made with care.  Happy hacking!
