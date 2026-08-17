package org.lwjgl.glfw;

// Shadow of GLFWMouseButtonCallback — see SKGLFWCallbackBase. Skips the FFM upcall
// stub the real class builds; SK invokes the stored callback directly.
public abstract class GLFWMouseButtonCallback extends SKGLFWCallbackBase implements GLFWMouseButtonCallbackI {
    protected GLFWMouseButtonCallback() {}

    public static GLFWMouseButtonCallback create(GLFWMouseButtonCallbackI instance) {
        return instance instanceof GLFWMouseButtonCallback
            ? (GLFWMouseButtonCallback) instance
            : new GLFWMouseButtonCallback() {
                @Override public void invoke(long window, int button, int action, int mods) { instance.invoke(window, button, action, mods); }
            };
    }

    public GLFWMouseButtonCallback set(long window) { GLFW.glfwSetMouseButtonCallback(window, this); return this; }
}
