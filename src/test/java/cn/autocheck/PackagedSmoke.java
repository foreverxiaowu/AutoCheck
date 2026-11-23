package cn.autocheck;

import com.microsoft.playwright.Playwright;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import javax.swing.*;
import java.nio.file.*;
import java.util.*;
import static cn.autocheck.Models.*;

/** Run after packaging with only test classes and the distributable JAR on the classpath. */
public final class PackagedSmoke {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("autocheck-packaged-");
        Path workbookPath = directory.resolve("waybills.xlsx");
        try (var workbook = new XSSFWorkbook()) {
            workbook.createSheet().createRow(0).createCell(0).setCellValue("G10433030158");
            try (var output = Files.newOutputStream(workbookPath)) { workbook.write(output); }
        }
        if (!FilesService.importWaybills(workbookPath).equals(List.of("G10433030158"))) throw new AssertionError("packaged XLSX import failed");
        var store = new FilesService(directory);
        store.saveSnapshot(new Snapshot(1, List.of("G10433030158"), List.of()));
        if (store.loadSnapshot().waybills().size() != 1) throw new AssertionError("packaged JSON persistence failed");
        try (var playwright = Playwright.create(new Playwright.CreateOptions().setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")))) {
            if (playwright.chromium() == null) throw new AssertionError("packaged browser driver missing");
        }
        SwingUtilities.invokeAndWait(() -> {
            try {
                Main.configureAppearance();
                MainWindow window = new MainWindow(directory); window.setVisible(true);
                window.dispatchEvent(new java.awt.event.WindowEvent(window, java.awt.event.WindowEvent.WINDOW_CLOSING));
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        System.out.println("PACKAGED_SMOKE_OK: XLSX, JSON, browser driver, desktop window");
    }
}
