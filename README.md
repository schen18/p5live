# p5.js Live Coding Suite

An interactive offline p5.js live coding environment for Android powered by an embedded CodeMirror 6 editor and a hardware-accelerated WebGL / 2D Canvas runtime.

## Features

- **Embedded Live Code Editor**: Custom-styled CodeMirror 6 editor with syntax highlighting, auto-indentation, and line error marking.
- **Hardware-Accelerated WebGL/2D Canvas**: Smooth 60 FPS rendering powered by Android WebView hardware acceleration.
- **Bi-Directional Native Bridge (`AndroidBridge`)**: Real-time console streaming, error reporting, and canvas frame export directly to Android gallery.
- **Generative Preset Library**: Built-in interactive generative art presets (Particle Swarms, Cyber Matrix Flow Grid, Neon Sine Waves, Constellation Field, Audio Synth Visualizer).
- **100% Offline Runtime**: All JavaScript libraries (`p5.min.js`, `cm6-bundle.js`, `runner.js`) are bundled locally within Android app assets—no network connection required.

## Getting Started

### Prerequisites
- [Android Studio](https://developer.android.com/studio) (Ladybug / Jellyfish or newer)
- Android SDK 35+
- JDK 11 or higher

### Building & Running
1. Open Android Studio.
2. Select **Open** and select the project directory.
3. Sync project with Gradle files.
4. Run on an Android emulator or connected device (Android 7.0 / API 24+).

## Project Structure
```
p5js_ide/
├── app/
│   ├── src/main/
│   │   ├── assets/              # Local JS runtime (p5.js, CodeMirror 6, runner)
│   │   ├── java/.../p5editor/   # Native Activity & JavaScript Bridge
│   │   └── res/                 # Layouts, Material 3 themes, resources
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml       # Centralized Version Catalog
└── build.gradle.kts
```

## License
Apache License 2.0
