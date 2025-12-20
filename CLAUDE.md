# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

### Build Commands
- `cargo run` - Build and run the desktop application (requires libsciter library)
- `python3 build.py --flutter` - Build Flutter version (desktop)
- `python3 build.py --flutter --release` - Build Flutter version in release mode
- `python3 build.py --hwcodec` - Build with hardware codec support
- `python3 build.py --vram` - Build with VRAM feature (Windows only)
- `cargo build --release` - Build Rust binary in release mode
- `cargo build --features hwcodec` - Build with specific features

### Flutter Mobile Commands
- `cd flutter && flutter build apk --release` - Build Android release APK
- `cd flutter && flutter build apk --release --target-platform android-arm64 --split-per-abi` - Build ARM64-only APK
- `cd flutter && flutter build ios` - Build iOS app
- `cd flutter && flutter run` - Run Flutter app in development mode
- `cd flutter && flutter test` - Run Flutter tests
- `cd flutter && flutter clean` - Clean build artifacts (use before rebuild if encountering resource merge errors)

### Testing
- `cargo test` - Run Rust tests
- `cd flutter && flutter test` - Run Flutter tests

### Platform-Specific Build Scripts
- `flutter/build_android.sh` - Android build script
- `flutter/build_ios.sh` - iOS build script
- `flutter/build_fdroid.sh` - F-Droid build script
- `flutter/ndk_arm64.sh` - Build Rust library for Android ARM64

## Project Architecture

### Directory Structure
- **`src/`** - Main Rust application code
  - `src/ui/` - Legacy Sciter UI (deprecated, use Flutter instead)
  - `src/server/` - Audio/clipboard/input/video services and network connections
  - `src/client.rs` - Peer connection handling
  - `src/platform/` - Platform-specific code
- **`flutter/`** - Flutter UI code for desktop and mobile
- **`libs/`** - Core libraries
  - `libs/hbb_common/` - Video codec, config, network wrapper, protobuf, file transfer utilities
  - `libs/scrap/` - Screen capture functionality
  - `libs/enigo/` - Platform-specific keyboard/mouse control
  - `libs/clipboard/` - Cross-platform clipboard implementation

### Key Components
- **Remote Desktop Protocol**: Custom protocol implemented in `src/rendezvous_mediator.rs` for communicating with rustdesk-server
- **Screen Capture**: Platform-specific screen capture in `libs/scrap/`
- **Input Handling**: Cross-platform input simulation in `libs/enigo/`
- **Audio/Video Services**: Real-time audio/video streaming in `src/server/`
- **File Transfer**: Secure file transfer implementation in `libs/hbb_common/`

### UI Architecture
- **Legacy UI**: Sciter-based (deprecated) - files in `src/ui/`
- **Modern UI**: Flutter-based - files in `flutter/`
  - Desktop: `flutter/lib/desktop/`
  - Mobile: `flutter/lib/mobile/`
  - Shared: `flutter/lib/common/` and `flutter/lib/models/`

## Important Build Notes

### Dependencies
- Requires vcpkg for C++ dependencies: `libvpx`, `libyuv`, `opus`, `aom`
- Set `VCPKG_ROOT` environment variable
- Download appropriate Sciter library for legacy UI support

### Ignore Patterns
When working with files, ignore these directories:
- `target/` - Rust build artifacts
- `flutter/build/` - Flutter build output
- `flutter/.dart_tool/` - Flutter tooling files

### Cross-Platform Considerations
- Windows builds require additional DLLs and virtual display drivers
- macOS builds need proper signing and notarization for distribution
- Linux builds support multiple package formats (deb, rpm, AppImage)
- Mobile builds require platform-specific toolchains (Android SDK, Xcode)

### Feature Flags
- `hwcodec` - Hardware video encoding/decoding
- `vram` - VRAM optimization (Windows only)
- `flutter` - Enable Flutter UI
- `unix-file-copy-paste` - Unix file clipboard support
- `screencapturekit` - macOS ScreenCaptureKit (macOS only)

### Config
All configurations or options are under `libs/hbb_common/src/config.rs` file, 4 types:
- Settings (`OVERWRITE_SETTINGS`) - Server-side settings like rendezvous server, relay server, API server
- Local (`OVERWRITE_LOCAL_SETTINGS`) - Client-side settings like language
- Display (`OVERWRITE_DISPLAY_SETTINGS`) - Display preferences
- Built-in (`BUILTIN_SETTINGS`) - UI visibility options like `hide-server-settings`, `hide-network-settings`

### Android-Specific Architecture
- **Kotlin files**: `flutter/android/app/src/main/kotlin/href/cleverty/remote/`
  - `MainActivity.kt` - Flutter activity, method channels, clipboard management
  - `MainService.kt` - Foreground service for screen capture and media projection
  - `InputService.kt` - Accessibility service for input injection
  - `BootReceiver.kt` - Handles device boot for auto-start
  - `EnterpriseSetupReceiver.kt` - Broadcast receiver for unattended enterprise deployment
  - `EnterpriseConfig.kt` - Hardcoded server configuration for enterprise builds
- **SharedPreferences**: Used for local settings, accessed via `KEY_SHARED_PREFERENCES`
- **Direct Boot**: Services marked `android:directBootAware="true"` can run before device unlock

### FFI Bridge (Rust ↔ Flutter)
- Bridge generated by `flutter_rust_bridge_codegen`
- Rust side: `src/flutter_ffi.rs`, `src/bridge_generated.rs`
- Dart side: `flutter/lib/generated_bridge.dart`
- JNI bindings: `flutter/android/app/src/main/kotlin/ffi.kt`

### Android Icons
- Launcher icons: `mipmap-*/ic_launcher.png` (legacy) and `mipmap-*/ic_launcher_foreground.png` (adaptive)
- Round icons: `mipmap-*/ic_launcher_round.png`
- Notification icons: `mipmap-*/ic_stat_logo.png`
- Adaptive icon config: `mipmap-anydpi-v26/ic_launcher.xml`
