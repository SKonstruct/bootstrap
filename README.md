# skapsule-bootstrap

Platform-independent Java bootstrap code for launching the Spiral Knights client via getdown.
Shared as a submodule across [SKapsule](https://github.com/SKonstruct/SKapsule) (Android) and [SKapsule-iOS](https://github.com/SKonstruct/SKapsule-iOS).

## Contents

- **`SkBootstrap.java`**: Configures SK's classpath, env, properties, and cacio bridge.
- **`HeadlessGetdown.java`**: Headless driver for getdown to download SK assets.
- **`NativeBridgePrompt.java`**: Implements frenchpress `CredentialPrompt` via the JNI/Block native bridge.
- **`GLFW.java`**: Custom GLFW shadow implementation bridging LWJGL to native touch/input systems.
- **`GLFW*Callback.java`, `SKGLFWCallbackBase.java`**: shadows of LWJGL's GLFW callback classes that skip `org.lwjgl.system.Callback` (its FFM upcall stubs crash the iOS JIT code cache); the game's GLFW is fully shadowed, so nothing native ever calls them.
- **`GLFWNativeCocoa.java`**: shadow that reports no Cocoa window or monitor, since the game queries them as the macOS build.

## Integration

### Android (SKapsule)
Included via `settings.gradle.kts` and built by Gradle (`build.gradle.kts`).

### iOS (SKapsule-iOS)
Built directly into `sk-bootstrap.jar` via `scripts/build-bootstrap.sh`.
