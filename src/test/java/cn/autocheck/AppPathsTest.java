package cn.autocheck;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class AppPathsTest {
    @TempDir Path directory;

    @Test void explicitPathsWorkWithoutDependingOnCurrentDirectory() {
        String oldBrowser = System.getProperty("autocheck.browser"), oldData = System.getProperty("autocheck.data");
        try {
            System.setProperty("autocheck.browser", directory.resolve("browser/chrome.exe").toString());
            System.setProperty("autocheck.data", directory.resolve("my-data").toString());
            assertEquals(directory.resolve("browser/chrome.exe"), AppPaths.bundledBrowser());
            assertEquals(directory.resolve("my-data"), AppPaths.dataDirectory());
        } finally { restore("autocheck.browser", oldBrowser); restore("autocheck.data", oldData); }
    }

    @Test void installedApplicationWritesToUserDataInsteadOfInstallationDirectory() {
        String old = System.getProperty("jpackage.app-path"), oldData = System.getProperty("autocheck.data");
        try {
            System.setProperty("jpackage.app-path", "C:/Program Files/AutoCheck/AutoCheck.exe");
            System.clearProperty("autocheck.data");
            Path expected = Path.of(System.getenv("LOCALAPPDATA"), "AutoCheck").toAbsolutePath();
            assertEquals(expected, AppPaths.dataDirectory());
            assertFalse(AppPaths.dataDirectory().startsWith(Path.of("C:/Program Files")));
        } finally { restore("jpackage.app-path", old); restore("autocheck.data", oldData); }
    }

    private static void restore(String key, String value) {
        if (value == null) System.clearProperty(key); else System.setProperty(key, value);
    }
}
