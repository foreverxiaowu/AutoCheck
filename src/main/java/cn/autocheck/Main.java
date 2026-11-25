package cn.autocheck;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.channels.*;
import java.nio.file.*;

public final class Main {
    public static void main(String[] args) {
        try {
            configureAppearance();
            if (args.length == 2 && args[0].equals("--self-test")) {
                PackagingCheck.run(Path.of(args[1]));
                return;
            }
            Path data = AppPaths.dataDirectory();
            Files.createDirectories(data);
            FileChannel lockChannel = FileChannel.open(data.resolve("app.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = lockChannel.tryLock();
            if (lock == null) { lockChannel.close(); throw new IllegalStateException("程序已在运行，请使用已打开的窗口。"); }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { lock.release(); lockChannel.close(); } catch (Exception ignored) {}
            }));
            SwingUtilities.invokeLater(() -> {
                try {
                    MainWindow window = new MainWindow(data);
                    window.addWindowListener(new WindowAdapter() {
                        @Override public void windowClosed(WindowEvent event) {
                            // MainWindow waits for the query, persistence and browser cleanup before disposal.
                            // Disposing Swing windows alone need not stop native/runtime background threads.
                            System.exit(0);
                        }
                    });
                    window.setVisible(true);
                    SwingUtilities.invokeLater(() -> {
                        if (window.isDisplayable()) ReleaseNotes.showIfNeeded(window, data);
                    });
                }
                catch (Exception e) { fail(e); }
            });
        } catch (Exception e) { fail(e); }
    }

    static void configureAppearance() throws Exception {
        UiTheme.install();
    }

    private static void fail(Exception e) {
        e.printStackTrace();
        JOptionPane.showMessageDialog(null, e.getMessage(), "启动失败", JOptionPane.ERROR_MESSAGE);
        System.exit(1);
    }
}
