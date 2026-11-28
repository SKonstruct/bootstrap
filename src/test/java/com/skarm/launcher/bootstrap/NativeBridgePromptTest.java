package com.skarm.launcher.bootstrap;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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

    @Test
    public void withoutThePasswordNativesOnlyTheQrCodeIsOffered() {
        NativeBridgePrompt prompt = new NativeBridgePrompt(null, null, null);
        assertFalse(prompt.isPasswordSignInRequested());
        assertNull(prompt.takeCredentials());
        assertNull(prompt.takeGuardCode());
        prompt.showCredentialsForm(null);
        prompt.showGuardCodeForm(false, null, false);
    }

    @Test
    public void theFormAndItsFlagsReachTheNative() throws Exception {
        NativeBridgePrompt prompt = withPasswordNatives();
        prompt.showCredentialsForm("password");
        assertEquals("credentials|password|false", form);
        prompt.showGuardCodeForm(true, "a***@b.c", true);
        assertEquals("guard-email|a***@b.c|true", form);
        prompt.showGuardCodeForm(false, null, false);
        assertEquals("guard-device|null|false", form);
    }

    @Test
    public void theSwitchToTheFormIsPassedThrough() throws Exception {
        passwordRequested = false;
        NativeBridgePrompt prompt = withPasswordNatives();
        assertFalse(prompt.isPasswordSignInRequested());
        passwordRequested = true;
        assertTrue(prompt.isPasswordSignInRequested());
    }

    @Test
    public void credentialsAreSplitAtTheFirstNewline() throws Exception {
        credentials = "user\npa\nss";
        assertArrayEquals(new String[] {"user", "pa\nss"}, withPasswordNatives().takeCredentials());
        credentials = "no separator";
        assertNull(withPasswordNatives().takeCredentials());
        credentials = null;
        assertNull(withPasswordNatives().takeCredentials());
    }

    @Test
    public void theGuardCodeIsPassedThrough() throws Exception {
        guardCode = "AB12C";
        assertEquals("AB12C", withPasswordNatives().takeGuardCode());
    }

    @Test
    public void aFailingPasswordNativeLeavesTheQrCodeAlone() throws Exception {
        NativeBridgePrompt prompt = new NativeBridgePrompt(null, null, null, null,
            method("failingCancelled"), method("failingTake"), null);
        assertFalse(prompt.isPasswordSignInRequested());
        assertNull(prompt.takeCredentials());
    }

    private static NativeBridgePrompt withPasswordNatives() throws Exception {
        return new NativeBridgePrompt(null, null, null,
            method("fakeForm", String.class, String.class, boolean.class),
            method("fakeRequested"), method("fakeCredentials"), method("fakeGuardCode"));
    }

    private static String form;
    private static boolean passwordRequested;
    private static String credentials;
    private static String guardCode;

    static void fakeForm(String kind, String detail, boolean retry) {
        form = kind + "|" + detail + "|" + retry;
    }

    static boolean fakeRequested() {
        return passwordRequested;
    }

    static String fakeCredentials() {
        return credentials;
    }

    static String fakeGuardCode() {
        return guardCode;
    }

    static String failingTake() {
        throw new RuntimeException("Test exception");
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
