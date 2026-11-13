package com.skarm.launcher.bootstrap;

import com.threerings.getdown.data.EnvConfig;
import com.threerings.getdown.launcher.Getdown;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Entry point invoked from native code after the FCL JVM is up.
 *
 * Flow:
 *   1. Build an {@link EnvConfig} pointed at filesDir/sk/ (the appdir).
 *   2. Construct a {@link HeadlessGetdown} and run it. Getdown reads
 *      getdown.txt, validates digests, downloads/verifies every code/
 *      resource/, then calls our overridden launch() (which sets a flag
 *      instead of forking).
 *   3. Invoke Spiral Knights' main class in-process.
 */
public final class SkBootstrap {

    private static final String SK_MAIN_CLASS = "com.threerings.projectx.client.ProjectXApp";

    /**
     * @param args args[0] = appdir (absolute path to filesDir/sk).
     *             args[1..] = optional SK CLI arguments (forwarded as-is).
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("SkBootstrap requires appdir as first argument");
        }
        File appDir = new File(args[0]);
        String[] skArgs = (args.length > 1)
            ? java.util.Arrays.copyOfRange(args, 1, args.length)
            : new String[0];

        System.out.println("[SkBootstrap] appdir = " + appDir);
        System.out.println("[SkBootstrap] sk args = " + java.util.Arrays.toString(skArgs));

        EnvConfig envc = newEnvConfig(appDir);
        HeadlessGetdown gd = new HeadlessGetdown(envc);

        // Getdown.run() does its thing on its own
        try {
            System.out.println("[SkBootstrap] Getdown.run() spawned");
            Getdown.run(gd);
        } catch (Throwable t) {
            System.err.println("[SkBootstrap] Getdown.run() threw:");
            t.printStackTrace();
            fail("The game's updater could not start (" + t.getClass().getSimpleName() + ").");
            return;
        }

        try {
            System.out.println("[SkBootstrap] awaiting getdown completion (stall limit "
                + STALL_MINUTES + " min)…");
            boolean ok = gd.awaitCompletionUnlessStalled(TimeUnit.MINUTES.toMillis(STALL_MINUTES));
            if (!ok) {
                System.err.println("[SkBootstrap] getdown stalled");
                fail("The game's updater stopped responding. Check your connection and try again.");
                return;
            }
        } catch (InterruptedException e) {
            System.err.println("[SkBootstrap] interrupted waiting for getdown");
            return;
        }

        if (gd.exitCode() != null) {
            System.out.println("[SkBootstrap] getdown exited with code " + gd.exitCode());
            fail("The game's updater stopped (code " + gd.exitCode() + "). Share the log if it persists.");
            return;
        }
        if (!gd.wasLaunchRequested()) {
            System.out.println("[SkBootstrap] getdown completed without requesting launch");
            fail("The game's updater finished without starting the game. Share the log if it persists.");
            return;
        }

        // Build SK's runtime classloader. Subtle but important:
        //   parent  = platform classloader (JDK modules only)
        //   urls    = SK's code jars MINUS desktop LWJGL + bootstrap getdown,
        //             PLUS our Android-built LWJGL jars
        //
        // Getdown jars will get loaded too many times and error out unless
        // we exclude them, and we NEED Android LWJGL natives or else we
        // hit a bunch of library errors. Basically be careful if you're
        // going to modify the ordering or what's included...
        ClassLoader skParent = ClassLoader.getPlatformClassLoader();
        URLClassLoader skCl = buildSkClassLoader(appDir, skParent);
        if (skCl == null) return;

        Thread.currentThread().setContextClassLoader(skCl);

        try {
            Class<?> glfw = skCl.loadClass("org.lwjgl.glfw.GLFW");
            registerGlfwNatives(glfw);
        } catch (Throwable t) {
            System.err.println("[SkBootstrap] registerGlfwNatives failed:");
            t.printStackTrace();
            // SK sometimes works here so... might as well try.
        }

        applyExtraProperties(appDir);
        // The saved web account, handed over by the launcher's environment (never argv, which
        // a crash report would print). SK's main takes username then password.
        // Only if the game is really pointed at the server the launcher chose: a game update can
        // restore the stock config after the launcher edited it, and the login must not then go to
        // a different server than the one it was saved for.
        skArgs = withSavedLogin(skArgs, System.getenv("SK_WEB_USER"), System.getenv("SK_WEB_PASS"),
            System.getenv("SK_LOGIN_HOST"), configuredHost(skCl));

        try {
            System.out.println("[SkBootstrap] invoking " + SK_MAIN_CLASS + ".main(" + skArgs.length + " args)");
            Class<?> skMain = Class.forName(SK_MAIN_CLASS, true, skCl);
            Method main = skMain.getMethod("main", String[].class);
            main.invoke(null, (Object) skArgs);
            System.out.println("[SkBootstrap] " + SK_MAIN_CLASS + ".main returned");
        } catch (Throwable t) {
            System.err.println("[SkBootstrap] SK launch threw:");
            t.printStackTrace();
            fail("The game stopped while starting. Share the log if it keeps happening.", t);
        }
    }

    /** Explicit command-line arguments win; otherwise the saved login, if both halves are present. */
    static String[] withSavedLogin(String[] skArgs, String user, String pass) {
        if (skArgs.length > 0 || user == null || user.isEmpty() || pass == null || pass.isEmpty()) {
            return skArgs;
        }
        return new String[] {user, pass};
    }

    /**
     * As {@link #withSavedLogin(String[], String, String)}, but when the launcher named the server
     * the login belongs to ({@code expectedHost}), it is dropped unless the game's own
     * {@code server_host} is that server. An unreadable config counts as a mismatch.
     */
    static String[] withSavedLogin(String[] skArgs, String user, String pass, String expectedHost, String actualHost) {
        if (expectedHost != null && !expectedHost.isEmpty() && !expectedHost.equals(actualHost)) {
            if (user != null && !user.isEmpty()) {
                System.err.println("[SkBootstrap] saved login withheld: the game is set up for "
                    + actualHost + ", the login is for " + expectedHost);
            }
            return skArgs;
        }
        return withSavedLogin(skArgs, user, pass);
    }

    /** The {@code server_host} in the {@code deployment.properties} the game will load, or null. */
    private static String configuredHost(ClassLoader loader) {
        try (java.io.InputStream in = loader.getResourceAsStream("deployment.properties")) {
            if (in == null) return null;
            java.util.Properties props = new java.util.Properties();
            props.load(in);
            return props.getProperty("server_host");
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /**
     * The {@code -Dkey=value} lines of extra.txt, which the desktop Getdown passes to the game's
     * JVM. The JVM is already running here, so only system properties can still take effect (this
     * is how a player's own {@code -Dusername=} and {@code -Dpassword=} auto login works); any
     * other line is ignored.
     */
    static Map<String, String> extraProperties(List<String> lines) {
        Map<String, String> props = new LinkedHashMap<>();
        for (String raw : lines) {
            String line = raw.trim();
            int eq = line.indexOf('=');
            if (line.startsWith("-D") && eq > 2) {
                props.put(line.substring(2, eq).trim(), line.substring(eq + 1));
            }
        }
        return props;
    }

    private static void applyExtraProperties(File appDir) {
        File extra = new File(appDir, "extra.txt");
        if (!extra.isFile()) return;
        try {
            Map<String, String> props = extraProperties(Files.readAllLines(extra.toPath(), StandardCharsets.UTF_8));
            for (Map.Entry<String, String> e : props.entrySet()) {
                System.setProperty(e.getKey(), e.getValue());
            }
            // Keys only: the values can be a password.
            System.out.println("[SkBootstrap] extra.txt properties: " + props.keySet());
        } catch (Exception e) {
            System.err.println("[SkBootstrap] could not read extra.txt: " + e);
        }
    }

    /** Getdown may be silent this long before it counts as stalled. */
    private static final int STALL_MINUTES = 2;

    /**
     * The one place a boot failure becomes visible: the host shows the message with a Share Logs
     * button. Hosts that have not registered the native (older builds, iOS) fall back to the
     * overlay text, which at least stops saying "Validating".
     */
    private static void fail(String message) {
        fail(message, null);
    }

    /**
     * As {@link #fail(String)}, with the error behind it. The host shows only the first line to the
     * user and sends the rest with the report, which is otherwise a bare "the game stopped".
     */
    private static void fail(String message, Throwable cause) {
        if (cause != null) message = message + '\n' + describe(cause);
        try {
            nativeBootFailed(message);
            return;
        } catch (Throwable notRegistered) {
            // fall through
        }
        try {
            nativeLaunchStatus(message);
        } catch (Throwable ignored) {
            // best effort
        }
    }

    /**
     * One line naming what threw and where: the exception behind a reflective call, its message, and
     * its top frame. The game's classes are obfuscated, so the frame is what locates the fault.
     */
    static String describe(Throwable t) {
        Throwable root = t;
        while (root instanceof InvocationTargetException && root.getCause() != null) root = root.getCause();
        StringBuilder sb = new StringBuilder(root.getClass().getName());
        String message = root.getMessage();
        if (message != null && !message.isEmpty()) sb.append(": ").append(message);
        StackTraceElement[] frames = root.getStackTrace();
        if (frames.length > 0) sb.append(" at ").append(frames[0]);
        String line = sb.toString().replaceAll("\\s+", " ");
        return line.length() <= 400 ? line : line.substring(0, 400);
    }

    /**
     * Getdown's config for the appdir. getdown.txt is deliberately left exactly as the server
     * wrote it. It is itself a digested file: any edit (an http:// appbase made https://, say) makes
     * Getdown's own check fail on every launch, refetch its metadata and then clear every
     * validation marker, which re-downloads and re-unpacks everything a mod changed. 2.4.2 did
     * that, and no mod survived a launch.
     */
    private static EnvConfig newEnvConfig(File appDir) {
        return new EnvConfig(appDir);
    }

    private static final Set<String> SK_LWJGL_OVERRIDES = new HashSet<>();
    static {
        SK_LWJGL_OVERRIDES.add("lwjgl.jar");
        SK_LWJGL_OVERRIDES.add("lwjgl-opengl.jar");
        SK_LWJGL_OVERRIDES.add("lwjgl-openal.jar");
        SK_LWJGL_OVERRIDES.add("lwjgl-glfw.jar");
    }

    /** Bootstrap jars that must NOT leak into SK's classloader. */
    private static final Set<String> EXCLUDE_FROM_SK = new HashSet<>();
    static {
        EXCLUDE_FROM_SK.add("getdown-pro.jar");
        EXCLUDE_FROM_SK.add("getdown-pro-new.jar");
        EXCLUDE_FROM_SK.add("sk-bootstrap.jar");
    }

    private static URLClassLoader buildSkClassLoader(File appDir, ClassLoader parent) {
        File codeDir = new File(appDir, "code");
        File[] skJars = codeDir.listFiles((FilenameFilter) (dir, name) -> name.endsWith(".jar"));
        if (skJars == null || skJars.length == 0) {
            System.err.println("[SkBootstrap] no jars in " + codeDir + " — getdown didn't populate code/?");
            return null;
        }

        List<URL> urls = new ArrayList<>();
        int included = 0, skippedLwjgl = 0, skippedExcluded = 0, skippedNatives = 0;
        try {
            // Put sk-bootstrap.jar FIRST so our shadow classes (org.lwjgl.glfw.GLFW)
            // win over the upstream.
            File ownJar = new File(codeDir, "sk-bootstrap.jar");
            if (ownJar.isFile()) {
                urls.add(ownJar.toURI().toURL());
                System.out.println("[SkBootstrap] sk-bootstrap.jar pinned first on SK classpath");
            }

            // frenchpress next, ahead of the SK jars, so its com.threerings.froth.*
            // (SteamAPI etc.) shadow the froth-foamy classes bundled in projectx-pcode.jar.
            // Path comes from sklauncher.c (-Dfrenchpress.jar), set only in Steam mode;
            // Web launches omit it so a stored Steam token can't override the web choice.
            String fpJar = System.getProperty("frenchpress.jar");
            if (fpJar != null && !fpJar.isEmpty()) {
                File fp = new File(fpJar);
                if (fp.isFile()) {
                    urls.add(fp.toURI().toURL());
                    System.out.println("[SkBootstrap] frenchpress.jar pinned ahead of SK jars: " + fp);
                } else {
                    System.err.println("[SkBootstrap] -Dfrenchpress.jar set but missing: " + fp);
                }
            }

            for (File jar : skJars) {
                String name = jar.getName();
                if (EXCLUDE_FROM_SK.contains(name))                     { skippedExcluded++; continue; }
                if (SK_LWJGL_OVERRIDES.contains(name))                  { skippedLwjgl++;    continue; }
                if (name.startsWith("lwjgl-") && name.contains("-natives-")) { skippedNatives++; continue; }
                urls.add(jar.toURI().toURL());
                included++;
            }

            File lwjglDir = new File(appDir.getParentFile(), "lwjgl/jars");
            File[] ourLwjgl = lwjglDir.listFiles((dir, name) -> name.endsWith(".jar"));
            int androidLwjgl = 0;
            if (ourLwjgl != null) {
                for (File jar : ourLwjgl) { urls.add(jar.toURI().toURL()); androidLwjgl++; }
            }

            System.out.println("[SkBootstrap] SK classpath: " + included + " SK jars + "
                + androidLwjgl + " Android LWJGL jars; "
                + "skipped " + skippedExcluded + " excluded, "
                + skippedLwjgl + " replaced LWJGL, " + skippedNatives + " natives");
        } catch (Throwable t) {
            System.err.println("[SkBootstrap] failed to build classpath URLs:");
            t.printStackTrace();
            return null;
        }

        return new URLClassLoader(urls.toArray(new URL[0]), parent);
    }

    private static native void registerGlfwNatives(Class<?> glfwClass);

    /**
     * Forwards a boot-phase status string to the Android boot overlay. Registered
     * from native (sklauncher.c) alongside registerGlfwNatives; bridges the HotSpot
     * VM to ART's NativeBridge.onLaunchStatus, which the two VMs can't do directly.
     * Safe no-op if native registration hasn't happened yet (UnsatisfiedLinkError
     * is swallowed by callers).
     */
    static native void nativeLaunchStatus(String message);

    /** Tells the host the game cannot start and why, so it can show it instead of a stale status. */
    static native void nativeBootFailed(String message);

    /**
     * Toggles the Android Steam-login keep-alive (a foreground service that holds
     * the :game process alive while the user tabs out to approve the sign-in).
     * Bridges to ART's {@code NativeBridge.steamKeepAlive} exactly like
     * {@link #nativeLaunchStatus}. Reached via reflection from frenchpress's
     * {@code SteamSession.attempt}, which brackets its auth attempt with
     * {@code true}/{@code false}. Safe no-op until native registration runs.
     */
    static native void nativeSteamKeepAlive(boolean active);

    /**
     * Blocks the calling (HotSpot) thread while the Android UI collects a Steam
     * Guard authenticator (TOTP) code, then returns it ("" if none/cancelled).
     * Bridges to ART's {@code NativeBridge.promptForDeviceCode} the same way
     * {@link #nativeLaunchStatus} reaches the overlay. Reached via reflection
     * from {@code NativeBridgePrompt}, which runs in SK's classloader and so
     * must target THIS (system-loader) copy of SkBootstrap — the one whose
     * natives sklauncher.c registered.
     */
    static native String nativePromptDeviceCode(boolean prevWrong);

    /** Blocking Steam Guard email-code prompt. See {@link #nativePromptDeviceCode}. */
    static native String nativePromptEmailCode(String email, boolean prevWrong);

    private SkBootstrap() {} // static-only
}
