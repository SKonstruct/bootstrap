package com.skarm.launcher.bootstrap;

import co.frenchpress.CredentialPrompt;

import java.lang.reflect.Method;

/**
 * {@link CredentialPrompt} that shows the Steam sign-in QR code, and the optional username and
 * password forms, through a native bridge to the host UI ({@code GameActivity} on Android,
 * {@code GameViewController} on iOS).
 *
 * <p>Selected via {@code -Dfrenchpress.credentialPrompt=...} from the host launcher. Nothing
 * here blocks: the host draws what it is handed, and reports a cancel, a switch to the form and
 * whatever the user typed, which frenchpress polls for.
 *
 * <p>The password natives are looked up one by one, and a host that never registered them (or a
 * jar older than its host) just does not offer the form: the QR code does not depend on them.
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

  private final Method nativeShowForm;            // (String, String, boolean) -> void
  private final Method nativePasswordRequested;   // () -> boolean
  private final Method nativeTakeCredentials;     // () -> String "username\npassword" or null
  private final Method nativeTakeGuardCode;       // () -> String or null

  /** Set once a password native fails, so the poll goes quiet and the QR code carries on. */
  private volatile boolean passwordBroken;

  public NativeBridgePrompt () {
    this(lookup("nativeShowSignIn", String.class, String.class),
         lookup("nativeHideSignIn"),
         lookup("nativeSignInCancelled"),
         lookup("nativeShowSignInForm", String.class, String.class, boolean.class),
         lookup("nativeSignInPasswordRequested"),
         lookup("nativeTakeSignInCredentials"),
         lookup("nativeTakeSignInGuardCode"));
  }

  NativeBridgePrompt (Method show, Method hide, Method cancelled) {
    this(show, hide, cancelled, null, null, null, null);
  }

  NativeBridgePrompt (Method show, Method hide, Method cancelled, Method showForm,
      Method passwordRequested, Method takeCredentials, Method takeGuardCode) {
    this.nativeShowSignIn = show;
    this.nativeHideSignIn = hide;
    this.nativeSignInCancelled = cancelled;
    this.nativeShowForm = showForm;
    this.nativePasswordRequested = passwordRequested;
    this.nativeTakeCredentials = takeCredentials;
    this.nativeTakeGuardCode = takeGuardCode;
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

  /** Without the natives nobody can ask for the form, so the QR code is all there is. */
  @Override public boolean isPasswordSignInRequested () {
    Object requested = pollPassword(nativePasswordRequested);
    return requested instanceof Boolean && (Boolean) requested;
  }

  @Override public void showCredentialsForm (String failure) {
    invokePassword(nativeShowForm, "credentials", failure, false);
  }

  @Override public String[] takeCredentials () {
    Object packed = pollPassword(nativeTakeCredentials);
    if (!(packed instanceof String)) return null;
    // A single-line field cannot hold a newline, so the first one is the separator.
    String s = (String) packed;
    int split = s.indexOf('\n');
    return split < 0 ? null : new String[] { s.substring(0, split), s.substring(split + 1) };
  }

  @Override public void showGuardCodeForm (boolean emailCode, String emailHint, boolean wrong) {
    invokePassword(nativeShowForm, emailCode ? "guard-email" : "guard-device", emailHint, wrong);
  }

  @Override public String takeGuardCode () {
    Object code = pollPassword(nativeTakeGuardCode);
    return code instanceof String ? (String) code : null;
  }

  /** Calls a password native; null when it is missing or has failed (reported once, quietly). */
  private Object pollPassword (Method m) {
    if (m == null || passwordBroken) return null;
    try {
      return m.invoke(null);
    } catch (Throwable t) {
      passwordBroken = true;
      System.err.println("[frenchpress] NativeBridgePrompt: the username/password sign-in is not "
          + "available (" + t + "); the QR code still works");
      return null;
    }
  }

  private void invokePassword (Method m, Object... args) {
    if (m == null || passwordBroken) return;
    try {
      m.invoke(null, args);
    } catch (Throwable t) {
      passwordBroken = true;
      System.err.println("[frenchpress] NativeBridgePrompt: the username/password form could not "
          + "be shown (" + t + ")");
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
      System.err.println("[frenchpress] NativeBridgePrompt: native " + name + " unavailable ("
          + t + ")");
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
