package com.skarm.launcher.bootstrap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;

public class SkBootstrapTest {
    private static File manifest(String text) throws Exception {
        File f = File.createTempFile("getdown", ".txt");
        f.deleteOnExit();
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    @Test
    public void httpAppbaseBecomesHttpsAndKeepsThePlaceholder() throws Exception {
        File f = manifest("version = 1\nappbase = http://gamemedia2.spiralknights.com/spiral/%VERSION%\nlatest = http://x/y\n");
        assertEquals("https://gamemedia2.spiralknights.com/spiral/%VERSION%", SkBootstrap.httpsAppbase(f));
    }

    @Test
    public void anAppbaseThatIsAlreadyHttpsNeedsNoOverride() throws Exception {
        assertNull(SkBootstrap.httpsAppbase(manifest("appbase = https://a.example/%VERSION%\n")));
    }

    @Test
    public void similarKeysAndMissingFilesAreIgnored() throws Exception {
        assertNull(SkBootstrap.httpsAppbase(manifest("appbase_distro = http://a.example\nlatest = http://b.example\n")));
        assertNull(SkBootstrap.httpsAppbase(new File("/nonexistent/getdown.txt")));
    }

    @Test
    public void spacingAroundTheEqualsSignDoesNotMatter() throws Exception {
        assertEquals("https://a.example/v", SkBootstrap.httpsAppbase(manifest("appbase=http://a.example/v")));
    }

    @Test
    public void rewritesOnlyAppbaseAndLatestToHttps() throws Exception {
        File f = manifest("version = 1\nappbase = http://gamemedia2.spiralknights.com/spiral/%VERSION%\n"
            + "latest = http://gamemedia2.spiralknights.com/spiral/latest/getdown.txt\n"
            + "ui.name = http://not-a-url-we-own\ncode = http://x/y.jar\n");
        assertEquals(true, SkBootstrap.rewriteManifestToHttps(f));
        String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        org.junit.Assert.assertTrue(text.contains("appbase = https://gamemedia2.spiralknights.com/spiral/%VERSION%"));
        org.junit.Assert.assertTrue(text.contains("latest = https://gamemedia2.spiralknights.com/spiral/latest/getdown.txt"));
        org.junit.Assert.assertTrue(text.contains("ui.name = http://not-a-url-we-own"));
        org.junit.Assert.assertTrue(text.contains("code = http://x/y.jar"));
        assertEquals(false, SkBootstrap.rewriteManifestToHttps(f)); // already https: nothing to do
        assertEquals(false, SkBootstrap.rewriteManifestToHttps(new File("/nonexistent/getdown.txt")));
    }
}
