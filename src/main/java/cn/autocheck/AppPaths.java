package cn.autocheck;

import java.nio.file.*;

/** Locate bundled resources independently of the shortcut's working directory. */
public final class AppPaths {
    private AppPaths() {}

    public static Path applicationDirectory() {
        try {
            Path location = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath();
            return Files.isDirectory(location) ? location : location.getParent();
        } catch (Exception e) { throw new IllegalStateException("无法定位程序目录", e); }
    }

    public static Path bundledBrowser() {
        String configured = System.getProperty("autocheck.browser");
        if (configured != null && !configured.isBlank()) return Path.of(configured).toAbsolutePath();
        Path browser = applicationDirectory().resolve("browser/chrome.exe");
        return Files.isRegularFile(browser) ? browser : null;
    }

    public static Path dataDirectory() {
        String configured = System.getProperty("autocheck.data");
        if (configured != null && !configured.isBlank()) return Path.of(configured).toAbsolutePath();
        boolean installed = System.getProperty("jpackage.app-path") != null || bundledBrowser() != null;
        if (!installed) return Path.of("data").toAbsolutePath();
        String localAppData = System.getenv("LOCALAPPDATA");
        Path userData = localAppData == null || localAppData.isBlank()
            ? Path.of(System.getProperty("user.home"), "AppData", "Local") : Path.of(localAppData);
        return userData.resolve("AutoCheck").toAbsolutePath();
    }
}
