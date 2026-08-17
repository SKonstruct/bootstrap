package org.lwjgl.glfw;

// Shadow of GLFWCharCallback — see SKGLFWCallbackBase. Skips the FFM upcall
// stub the real class builds; SK invokes the stored callback directly.
public abstract class GLFWCharCallback extends SKGLFWCallbackBase implements GLFWCharCallbackI {
    protected GLFWCharCallback() {}

    public static GLFWCharCallback create(GLFWCharCallbackI instance) {
        return instance instanceof GLFWCharCallback
            ? (GLFWCharCallback) instance
            : new GLFWCharCallback() {
                @Override public void invoke(long window, int codepoint) { instance.invoke(window, codepoint); }
            };
    }

    public GLFWCharCallback set(long window) { GLFW.glfwSetCharCallback(window, this); return this; }
}
