package org.lwjgl.glfw;

// Shadow of GLFWCursorPosCallback — see SKGLFWCallbackBase. Skips the FFM upcall
// stub the real class builds; SK invokes the stored callback directly.
public abstract class GLFWCursorPosCallback extends SKGLFWCallbackBase implements GLFWCursorPosCallbackI {
    protected GLFWCursorPosCallback() {}

    public static GLFWCursorPosCallback create(GLFWCursorPosCallbackI instance) {
        return instance instanceof GLFWCursorPosCallback
            ? (GLFWCursorPosCallback) instance
            : new GLFWCursorPosCallback() {
                @Override public void invoke(long window, double xpos, double ypos) { instance.invoke(window, xpos, ypos); }
            };
    }

    public GLFWCursorPosCallback set(long window) { GLFW.glfwSetCursorPosCallback(window, this); return this; }
}
