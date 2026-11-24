package com.skarm.launcher.bootstrap;

import co.frenchpress.CredentialPrompt;

import java.lang.reflect.Method;

/**
 * {@link CredentialPrompt} that shows the Steam sign-in QR code through a native bridge to the
 * host UI ({@code GameActivity} on Android, {@code GameViewController} on iOS).
 *
 * <p>Selected via {@code -Dfrenchpress.credentialPrompt=...} from the host launcher. Nothing
 * here blocks: the host draws the code, and reports a cancel that frenchpress polls for.
 *
 * <p><b>Classloader note.</b> This class and frenchpress are loaded by SK's
 * URLClassLoader (parent = the platform loader), but the native methods on
 * {@link SkBootstrap} were registered by the host launcher against the copy of
 * SkBootstrap on the JVM <i>system</i> classpath. Those are two distinct Class
 * objects, so we can't call {@code SkBootstrap.native*} directly — the
 * symbolic reference would resolve to our (unregistered) copy and throw
 * {@link UnsatisfiedLinkError}. Instead we reflect onto the system-loader copy,
 * whose natives are live. The exchanged values are {@link String}s and primitives,
 * defined by the boot loader, so they cross the loader boundary cleanly.
 */
public final class NativeBridgePrompt implements CredentialPrompt {

  private final Method nativeShowSignIn;      // (String, String) -> void, null if unavailable
  private final Method nativeHideSignIn;      // () -> void
  private final Method nativeSignInCancelled; // () -> boolean

  public NativeBridgePrompt () {
    this(lookup("nativeShowSignIn", String.class, String.class),
         lookup("nativeHideSignIn"),
         lookup("nativeSignInCancelled"));
  }

  NativeBridgePrompt (Method show, Method hide, Method cancelled) {
    this.nativeShowSignIn = show;
    this.nativeHideSignIn = hide;
    this.nativeSignInCancelled = cancelled;
  }

  @Override public void showSignIn (String challengeUrl, String qrModules) {
    invoke(nativeShowSignIn, challengeUrl, qrModules);
  }

  @Override public void hideSignIn () {
    invoke(nativeHideSignIn);
  }

  /**
   * With no bridge, or one that fails, nobody can see the code or cancel it: report a cancel so
   * the launch continues with a web account instead of waiting out the sign-in.
   */
  @Override public boolean isSignInCancelled () {
    if (nativeSignInCancelled == null) return true;
    try {
      return (Boolean) nativeSignInCancelled.invoke(null);
    } catch (Throwable t) {
      System.err.println("[frenchpress] NativeBridgePrompt cancel poll failed: " + t);
      return true;
    }
  }

  private static Method lookup (String name, Class<?>... params) {
    try {
      // The registered SkBootstrap lives on the system classpath, not in SK's loader.
      Class<?> sysBoot = Class.forName("com.skarm.launcher.bootstrap.SkBootstrap",
          false, ClassLoader.getSystemClassLoader());
      Method m = sysBoot.getDeclaredMethod(name, params);
      m.setAccessible(true);
      return m;
    } catch (Throwable t) {
      System.err.println("[frenchpress] NativeBridgePrompt: native bridge unavailable ("
          + t + "); the Steam sign-in QR code can't be shown");
      return null;
    }
  }

  private static void invoke (Method m, Object... args) {
    if (m == null) return;
    try {
      m.invoke(null, args);
    } catch (Throwable t) {
      System.err.println("[frenchpress] NativeBridgePrompt native call failed: " + t);
    }
  }
}
