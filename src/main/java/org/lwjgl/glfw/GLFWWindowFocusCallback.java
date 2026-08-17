package org.lwjgl.glfw;

// Shadow of GLFWWindowFocusCallback — see SKGLFWCallbackBase. Skips the FFM upcall
// stub the real class builds; SK invokes the stored callback directly.
public abstract class GLFWWindowFocusCallback extends SKGLFWCallbackBase implements GLFWWindowFocusCallbackI {
    protected GLFWWindowFocusCallback() {}

    public static GLFWWindowFocusCallback create(GLFWWindowFocusCallbackI instance) {
        return instance instanceof GLFWWindowFocusCallback
            ? (GLFWWindowFocusCallback) instance
            : new GLFWWindowFocusCallback() {
                @Override public void invoke(long window, boolean focused) { instance.invoke(window, focused); }
            };
    }

    public GLFWWindowFocusCallback set(long window) { GLFW.glfwSetWindowFocusCallback(window, this); return this; }
}
