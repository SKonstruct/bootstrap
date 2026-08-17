package org.lwjgl.glfw;

// Shadow of GLFWFramebufferSizeCallback — see SKGLFWCallbackBase. Skips the FFM upcall
// stub the real class builds; SK invokes the stored callback directly.
public abstract class GLFWFramebufferSizeCallback extends SKGLFWCallbackBase implements GLFWFramebufferSizeCallbackI {
    protected GLFWFramebufferSizeCallback() {}

    public static GLFWFramebufferSizeCallback create(GLFWFramebufferSizeCallbackI instance) {
        return instance instanceof GLFWFramebufferSizeCallback
            ? (GLFWFramebufferSizeCallback) instance
            : new GLFWFramebufferSizeCallback() {
                @Override public void invoke(long window, int width, int height) { instance.invoke(window, width, height); }
            };
    }

    public GLFWFramebufferSizeCallback set(long window) { GLFW.glfwSetFramebufferSizeCallback(window, this); return this; }
}
