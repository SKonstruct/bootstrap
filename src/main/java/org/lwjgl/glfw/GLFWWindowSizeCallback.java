package org.lwjgl.glfw;

// Shadow of GLFWWindowSizeCallback — see SKGLFWCallbackBase. Skips the FFM upcall
// stub the real class builds; SK invokes the stored callback directly.
public abstract class GLFWWindowSizeCallback extends SKGLFWCallbackBase implements GLFWWindowSizeCallbackI {
    protected GLFWWindowSizeCallback() {}

    public static GLFWWindowSizeCallback create(GLFWWindowSizeCallbackI instance) {
        return instance instanceof GLFWWindowSizeCallback
            ? (GLFWWindowSizeCallback) instance
            : new GLFWWindowSizeCallback() {
                @Override public void invoke(long window, int width, int height) { instance.invoke(window, width, height); }
            };
    }

    public GLFWWindowSizeCallback set(long window) { GLFW.glfwSetWindowSizeCallback(window, this); return this; }
}
