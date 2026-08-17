package org.lwjgl.glfw;

// Shadow of GLFWKeyCallback — see SKGLFWCallbackBase. Skips the FFM upcall
// stub the real class builds; SK invokes the stored callback directly.
public abstract class GLFWKeyCallback extends SKGLFWCallbackBase implements GLFWKeyCallbackI {
    protected GLFWKeyCallback() {}

    public static GLFWKeyCallback create(GLFWKeyCallbackI instance) {
        return instance instanceof GLFWKeyCallback
            ? (GLFWKeyCallback) instance
            : new GLFWKeyCallback() {
                @Override public void invoke(long window, int key, int scancode, int action, int mods) { instance.invoke(window, key, scancode, action, mods); }
            };
    }

    public GLFWKeyCallback set(long window) { GLFW.glfwSetKeyCallback(window, this); return this; }
}
