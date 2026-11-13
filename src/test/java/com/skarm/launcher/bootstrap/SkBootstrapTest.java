package com.skarm.launcher.bootstrap;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class SkBootstrapTest {
    @Test
    public void aFailureNamesTheRealErrorNotTheReflectionWrapper() {
        Throwable wrapped = new java.lang.reflect.InvocationTargetException(
            new NoSuchMethodError("'boolean a.B.c(long, int)'"));
        String line = SkBootstrap.describe(wrapped);
        assertEquals(true, line.startsWith("java.lang.NoSuchMethodError: 'boolean a.B.c(long, int)' at "));
        assertEquals(false, line.contains("InvocationTargetException"));
        assertEquals(false, line.contains("\n"));
    }

    @Test
    public void aFailureLineIsCapped() {
        assertEquals(400, SkBootstrap.describe(new RuntimeException("x".repeat(2000))).length());
    }

    @Test
    public void aSavedLoginBecomesUsernameThenPassword() {
        assertArrayEquals(new String[] {"knight", "pw"}, SkBootstrap.withSavedLogin(new String[0], "knight", "pw"));
    }

    @Test
    public void explicitArgumentsOrAHalfLoginLeaveTheArgumentsAlone() {
        String[] given = {"a", "b"};
        assertSame(given, SkBootstrap.withSavedLogin(given, "knight", "pw"));
        assertEquals(0, SkBootstrap.withSavedLogin(new String[0], "knight", "").length);
        assertEquals(0, SkBootstrap.withSavedLogin(new String[0], null, "pw").length);
    }

    @Test
    public void aLoginForAnotherServerIsWithheld() {
        String[] none = new String[0];
        assertArrayEquals(new String[] {"knight", "pw"},
            SkBootstrap.withSavedLogin(none, "knight", "pw", "10.0.2.2", "10.0.2.2"));
        // Named server differs from the game's config, or the config could not be read.
        assertEquals(0, SkBootstrap.withSavedLogin(none, "knight", "pw", "10.0.2.2", "game.spiralknights.com").length);
        assertEquals(0, SkBootstrap.withSavedLogin(none, "knight", "pw", "10.0.2.2", null).length);
        // A launcher that does not name a server keeps the previous behavior.
        assertArrayEquals(new String[] {"knight", "pw"}, SkBootstrap.withSavedLogin(none, "knight", "pw", null, "x"));
        assertArrayEquals(new String[] {"knight", "pw"}, SkBootstrap.withSavedLogin(none, "knight", "pw", "", null));
    }

    @Test
    public void extraTxtYieldsOnlyDashDProperties() {
        java.util.Map<String, String> props = SkBootstrap.extraProperties(java.util.Arrays.asList(
            "-Xmx1G", "-Dusername=knight", "  -Dpassword=p=w ", "-XX:+UseG1GC", "-D=bad", "plain"));
        assertEquals(2, props.size());
        assertEquals("knight", props.get("username"));
        assertEquals("p=w", props.get("password"));
    }
}
