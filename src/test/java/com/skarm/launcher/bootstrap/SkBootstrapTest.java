package com.skarm.launcher.bootstrap;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class SkBootstrapTest {
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
    public void extraTxtYieldsOnlyDashDProperties() {
        java.util.Map<String, String> props = SkBootstrap.extraProperties(java.util.Arrays.asList(
            "-Xmx1G", "-Dusername=knight", "  -Dpassword=p=w ", "-XX:+UseG1GC", "-D=bad", "plain"));
        assertEquals(2, props.size());
        assertEquals("knight", props.get("username"));
        assertEquals("p=w", props.get("password"));
    }
}
