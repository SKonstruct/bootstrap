package org.lwjgl.glfw;

// Shadow of GLFWScrollCallback — see SKGLFWCallbackBase. Skips the FFM upcall
// stub the real class builds; SK invokes the stored callback directly.
public abstract class GLFWScrollCallback extends SKGLFWCallbackBase implements GLFWScrollCallbackI {
    protected GLFWScrollCallback() {}

    public static GLFWScrollCallback create(GLFWScrollCallbackI instance) {
        return instance instanceof GLFWScrollCallback
            ? (GLFWScrollCallback) instance
            : new GLFWScrollCallback() {
                @Override public void invoke(long window, double xoffset, double yoffset) { instance.invoke(window, xoffset, yoffset); }
            };
    }

    public GLFWScrollCallback set(long window) { GLFW.glfwSetScrollCallback(window, this); return this; }
}
