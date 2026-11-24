package com.skarm.launcher.bootstrap;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import org.junit.Before;
import org.junit.Test;

public class NativeBridgePromptTest {

    private static String[] shown;
    private static boolean hidden;

    @Before
    public void reset() {
        shown = null;
        hidden = false;
    }

    private static Method method(String name, Class<?>... params) throws Exception {
        return NativeBridgePromptTest.class.getDeclaredMethod(name, params);
    }

    @Test
    public void withNoBridgeTheSignInReportsCancelled() {
        NativeBridgePrompt prompt = new NativeBridgePrompt(null, null, null);
        assertTrue(prompt.isSignInCancelled());
    }

    @Test
    public void withNoBridgeShowAndHideAreNoOps() {
        NativeBridgePrompt prompt = new NativeBridgePrompt(null, null, null);
        prompt.showSignIn("https://s.team/q/1/2", "10\n01");
        prompt.hideSignIn();
    }

    @Test
    public void theChallengeAndGridReachTheNative() throws Exception {
        NativeBridgePrompt prompt = new NativeBridgePrompt(
            method("fakeShow", String.class, String.class), null, null);
        prompt.showSignIn("https://s.team/q/1/2", "10\n01");
        assertArrayEquals(new String[] {"https://s.team/q/1/2", "10\n01"}, shown);
    }

    @Test
    public void hideReachesTheNative() throws Exception {
        NativeBridgePrompt prompt = new NativeBridgePrompt(null, method("fakeHide"), null);
        prompt.hideSignIn();
        assertTrue(hidden);
    }

    @Test
    public void theCancelFlagIsPassedThrough() throws Exception {
        assertFalse(new NativeBridgePrompt(null, null, method("notCancelled")).isSignInCancelled());
        assertTrue(new NativeBridgePrompt(null, null, method("cancelled")).isSignInCancelled());
    }

    @Test
    public void aFailingCancelPollEndsTheSignIn() throws Exception {
        assertTrue(new NativeBridgePrompt(null, null, method("failingCancelled")).isSignInCancelled());
    }

    @Test
    public void aFailingShowDoesNotThrow() throws Exception {
        new NativeBridgePrompt(method("failingShow", String.class, String.class), null, null)
            .showSignIn("u", "1");
    }

    static void fakeShow(String url, String modules) {
        shown = new String[] {url, modules};
    }

    static void fakeHide() {
        hidden = true;
    }

    static boolean notCancelled() {
        return false;
    }

    static boolean cancelled() {
        return true;
    }

    static boolean failingCancelled() {
        throw new RuntimeException("Test exception");
    }

    static void failingShow(String url, String modules) {
        throw new RuntimeException("Test exception");
    }
}
