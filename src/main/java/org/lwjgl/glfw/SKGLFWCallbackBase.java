package org.lwjgl.glfw;

import org.lwjgl.system.Callback;
import org.lwjgl.system.NativeResource;

// Base for SKapsule's shadowed GLFW callback classes.
//
// The real LWJGL GLFW*Callback classes extend org.lwjgl.system.Callback, whose
// constructor builds a Java FFM (Panama) upcall stub. On the AngelAuraMC
// mirror-mapped code cache that stub emission crashes (SIGBUS / BUS_ADRALN in
// UpcallLinker::make_upcall_stub). SK's GLFW is fully shadowed (see GLFW.java) —
// native GLFW never calls these stubs — so we skip Callback entirely and store
// the Java callback object, invoking it directly. No upcall, no crash.
//
// sk-bootstrap.jar is pinned first on the classpath, so these override the real
// LWJGL classes from the downloaded lwjgl-glfw.jar.
abstract class SKGLFWCallbackBase implements NativeResource {
    public long address() { return 0L; }

    public Callback.Descriptor getDescriptor() { return null; }

    @Override
    public void free() {}
}
