package org.lwjgl.glfw;

// Shadow of LWJGL's GLFWNativeCocoa. SK runs as the macOS build (getdown serves
// macOS resources), so its display code queries the Cocoa window/monitor via this
// class. There is no Cocoa window here; returning 0 makes SK's isFullscreen()
// report windowed mode (matching GLFW.glfwGetWindowMonitor()==0) and avoids the
// real class, whose Functions.<clinit> calls GLFW.getLibrary() (which we don't
// provide) and would bind native Cocoa symbols that don't exist.
// sk-bootstrap.jar is pinned first on the classpath, so this overrides the real one.
public final class GLFWNativeCocoa {
    private GLFWNativeCocoa() {}

    public static long glfwGetCocoaMonitor(long monitor) { return 0L; }

    public static long glfwGetCocoaWindow(long window) { return 0L; }

    public static long glfwGetCocoaView(long window) { return 0L; }
}
