package cn.autocheck;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class MainExitTest {
    @TempDir Path directory;

    @Test void closingTheWindowExitsWithBackgroundThreadsAndAllowsAnotherLaunch() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        var files = new FilesService(directory);
        var snapshot = new Models.Snapshot(1, List.of("TEST12345"), List.of());
        files.saveSnapshot(snapshot);
        for (int run = 0; run < 2; run++) {
            Path output = directory.resolve("process-" + run + ".log");
            Path marker = directory.resolve("shutdown-" + run + ".txt");
            Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-Dautocheck.data=" + directory,
                "-cp", System.getProperty("java.class.path"), Probe.class.getName(), marker.toString())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
            try {
                assertTrue(process.waitFor(20, TimeUnit.SECONDS),
                    "Window closed but JVM remained alive: " + Files.readString(output));
                assertEquals(0, process.exitValue(), Files.readString(output));
                assertEquals("closed", Files.readString(marker));
                try (var channel = FileChannel.open(directory.resolve("app.lock"), StandardOpenOption.WRITE);
                     var lock = channel.tryLock()) {
                    assertNotNull(lock, "Exiting must release the application lock");
                }
                assertEquals(snapshot, files.loadSnapshot(), "Closing must retain saved progress");
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    process.waitFor(5, TimeUnit.SECONDS);
                }
            }
        }
    }

    public static final class Probe {
        public static void main(String[] args) throws Exception {
            Main.main(new String[0]);
            SwingUtilities.invokeAndWait(() -> {
                MainWindow window = null;
                for (Window candidate : Window.getWindows()) {
                    if (candidate instanceof MainWindow main && main.isDisplayable()) window = main;
                }
                if (window == null) throw new AssertionError("Main did not open its window");
                // Reproduce runtimes where a non-daemon library thread survives closing the UI.
                Thread background = new Thread(() -> {
                    try { new CountDownLatch(1).await(); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }, "simulated-runtime-background-thread");
                background.setDaemon(false);
                background.start();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try { Files.writeString(Path.of(args[0]), "closed"); }
                    catch (Exception e) { throw new RuntimeException(e); }
                }));
                window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
            });
        }
    }
}
