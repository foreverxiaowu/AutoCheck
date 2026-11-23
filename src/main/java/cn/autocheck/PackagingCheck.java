package cn.autocheck;

import com.microsoft.playwright.Playwright;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import javax.swing.*;
import java.nio.file.*;
import java.util.*;

/** Offline distribution check, explicitly invoked by --self-test <report.json>. */
final class PackagingCheck {
    private PackagingCheck() {}

    static void run(Path reportPath) throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        Path testData = Files.createTempDirectory("autocheck-distribution-check-");
        try {
            report.put("javaHome", System.getProperty("java.home"));
            report.put("javaVersion", System.getProperty("java.version"));
            report.put("applicationDirectory", AppPaths.applicationDirectory().toString());
            report.put("defaultDataDirectory", AppPaths.dataDirectory().toString());
            Path browser = AppPaths.bundledBrowser();
            if (browser == null || !Files.isRegularFile(browser)) throw new IllegalStateException("内置浏览器未找到");
            report.put("browserExecutable", browser.toString());
            Path xlsx = testData.resolve("input.xlsx");
            try (var workbook = new XSSFWorkbook()) {
                workbook.createSheet().createRow(0).createCell(0).setCellValue("G10433030158");
                try (var output = Files.newOutputStream(xlsx)) { workbook.write(output); }
            }
            if (!FilesService.importWaybills(xlsx).equals(List.of("G10433030158"))) throw new IllegalStateException("Excel 导入失败");
            var store = new FilesService(testData);
            var snapshot = new Models.Snapshot(1, List.of("G10433030158"), List.of());
            store.saveSnapshot(snapshot);
            if (!snapshot.equals(store.loadSnapshot())) throw new IllegalStateException("进度保存失败");
            report.put("excelAndPersistence", "passed");
            try (var playwright = Playwright.create(new Playwright.CreateOptions().setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
                 var context = playwright.chromium().launchPersistentContext(testData.resolve("profile"), BrowserGateway.bundledBrowserOptions(browser).setHeadless(true))) {
                var page = context.newPage();
                page.setContent("<html><head><title>AutoCheck self test</title></head><body>AutoCheck</body></html>");
                if (!"AutoCheck self test".equals(page.title())) throw new IllegalStateException("内置浏览器执行失败");
                report.put("browserVersion", context.browser().version());
                report.put("browserLaunch", "passed");
            }
            SwingUtilities.invokeAndWait(() -> {
                try {
                    MainWindow window = new MainWindow(testData); window.setVisible(true);
                    JComboBox<?> source = findSource(window);
                    if (source == null || source.getItemCount() != 3) throw new IllegalStateException("查询平台入口缺失");
                    source.setSelectedIndex(1);
                    if (!window.getTitle().contains("17TRACK")) throw new IllegalStateException("17TRACK 入口检查失败");
                    source.setSelectedIndex(2);
                    if (!window.getTitle().contains("顺丰")) throw new IllegalStateException("顺丰入口检查失败");
                    source.setSelectedIndex(0);
                    window.dispatchEvent(new java.awt.event.WindowEvent(window, java.awt.event.WindowEvent.WINDOW_CLOSING));
                } catch (Exception e) { throw new IllegalStateException(e); }
            });
            report.put("desktopWindow", "passed");
            report.put("trackingSources", List.of("YTO", "17TRACK", "SF International"));
            report.put("status", "passed");
        } catch (Exception e) {
            report.put("status", "failed"); report.put("error", e.toString());
            throw e;
        } finally {
            Path parent = reportPath.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            FilesService.JSON.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
        }
    }

    private static JComboBox<?> findSource(java.awt.Container root) {
        for (java.awt.Component child : root.getComponents()) {
            if (child instanceof JComboBox<?> box && "querySource".equals(child.getName())) return box;
            if (child instanceof java.awt.Container container) {
                JComboBox<?> found = findSource(container);
                if (found != null) return found;
            }
        }
        return null;
    }
}
