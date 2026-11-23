package cn.autocheck;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import static cn.autocheck.Models.*;
import static org.junit.jupiter.api.Assertions.*;

class SmokeTest {
    @TempDir Path directory;

    @Test void desktopRendersTheSuppliedTenWaybills() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("autocheck.uiSmoke"));
        FilesService files = new FilesService(directory);
        files.saveSnapshot(new Snapshot(1, TrajectoryParserTest.sampleWaybills(), new TrajectoryParser().parse(TrajectoryParserTest.sampleWaybills(), TrajectoryParserTest.sample())));
        SwingUtilities.invokeAndWait(() -> {
            MainWindow window = null;
            try {
                Main.configureAppearance();
                window = new MainWindow(directory); window.setVisible(true); window.validate();
                JTable table = find(window, JTable.class);
                assertNotNull(table); assertEquals(10, table.getRowCount());
                table.setRowSelectionInterval(0, 0);
                JComboBox<?> view = (JComboBox<?>) findNamed(window, "resultView");
                assertNotNull(view); view.setSelectedIndex(2); assertEquals(0, table.getRowCount());
                view.setSelectedIndex(1); assertEquals(10, table.getRowCount());
                view.setSelectedIndex(0); table.setRowSelectionInterval(0, 0);
                BufferedImage image = new BufferedImage(window.getWidth(), window.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics(); window.paint(graphics); graphics.dispose();
                ImageIO.write(image, "png", Path.of("target", "ui-smoke.png").toFile());
            } catch (Exception e) { throw new RuntimeException(e); }
            finally { if (window != null) window.dispatchEvent(new java.awt.event.WindowEvent(window, java.awt.event.WindowEvent.WINDOW_CLOSING)); }
        });
    }

    @Test void track17WindowRestoresIndependentCarrierProgressAndSwitchesBackToYto() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("autocheck.uiSmoke"));
        new FilesService(directory).saveSnapshot(new Snapshot(1, java.util.List.of("YTO001"), java.util.List.of()));
        new Track17Config(java.util.List.of(new Track17Config.Carrier("中通国际", 190175), new Track17Config.Carrier("其他公司", 12345)), 190175).save(directory);
        new FilesService(Track17Config.progressDirectory(directory, 190175)).saveSnapshot(new Snapshot(1, Track17Test.NUMBERS, new Track17Parser().parse(Track17Test.NUMBERS, Track17Test.sample())));
        var sfNumbers = java.util.List.of("SF5158351178464");
        new FilesService(directory.resolve("sf")).saveSnapshot(new Snapshot(1, sfNumbers, new SfParser().parse(sfNumbers, SfTest.sample())));
        SwingUtilities.invokeAndWait(() -> {
            MainWindow window = null;
            try {
                Main.configureAppearance();
                window = new MainWindow(directory); window.setVisible(true); window.validate();
                JTable table = find(window, JTable.class);
                JComboBox<?> source = (JComboBox<?>) findNamed(window, "querySource");
                JComboBox<?> carrier = (JComboBox<?>) findNamed(window, "carrier");
                assertEquals(1, table.getRowCount());
                source.setSelectedIndex(1);
                assertEquals(2, table.getRowCount());
                assertEquals(6, table.getColumnCount());
                assertEquals(0, table.getColumnModel().getColumn(3).getMaxWidth());
                table.setRowSelectionInterval(0, 0);
                window.validate();
                BufferedImage image = new BufferedImage(window.getWidth(), window.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics(); window.paint(graphics); graphics.dispose();
                ImageIO.write(image, "png", Path.of("target", "17track-ui-smoke.png").toFile());
                carrier.setSelectedIndex(1); assertEquals(0, table.getRowCount());
                carrier.setSelectedIndex(0); assertEquals(2, table.getRowCount());
                source.setSelectedIndex(2); assertEquals(1, table.getRowCount());
                assertEquals("客户已取消寄件", table.getValueAt(0, 4));
                assertFalse(carrier.isVisible());
                table.setRowSelectionInterval(0,0); window.validate();
                Graphics2D sfGraphics = image.createGraphics(); window.paint(sfGraphics); sfGraphics.dispose();
                ImageIO.write(image, "png", Path.of("target", "sf-ui-smoke.png").toFile());
                source.setSelectedIndex(1); assertEquals(2, table.getRowCount());
                source.setSelectedIndex(0); assertEquals(1, table.getRowCount());
                assertTrue(table.getColumnModel().getColumn(3).getMaxWidth() > 0);
                assertEquals("YTO001", table.getValueAt(0, 0));
            } catch (Exception e) { throw new RuntimeException(e); }
            finally { if (window != null) window.dispatchEvent(new java.awt.event.WindowEvent(window, java.awt.event.WindowEvent.WINDOW_CLOSING)); }
        });
    }

    @Test void sfLiveReturnsResultsOrManualVerification() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("autocheck.sfLiveSmoke"));
        try (TrackingGateway gateway = new SfGateway(directory.resolve("sf-profile"))) {
            var reply = gateway.query(java.util.List.of("SF5158351178464"), "", "");
            System.out.println("SF_LIVE_RESULT=" + reply.kind());
            assertTrue(reply.kind() == TrackingGateway.Kind.SUCCESS || reply.kind() == TrackingGateway.Kind.BROWSER_VERIFICATION, reply.message());
            if (reply.kind() == TrackingGateway.Kind.SUCCESS) assertEquals(QueryState.OK, gateway.parse(java.util.List.of("SF5158351178464"), reply.payload()).get(0).state());
        }
    }

    @Test void track17LiveEndpointReturnsResultsOrManualVerification() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("autocheck.track17LiveSmoke"));
        try (TrackingGateway gateway = new Track17Gateway(directory.resolve("17track-profile"), 190175)) {
            var reply = gateway.query(Track17Test.NUMBERS, "", "");
            System.out.println("TRACK17_LIVE_RESULT=" + reply.kind());
            assertTrue(reply.kind() == TrackingGateway.Kind.SUCCESS || reply.kind() == TrackingGateway.Kind.BROWSER_VERIFICATION, reply.message());
            if (reply.kind() == TrackingGateway.Kind.SUCCESS) {
                var results = gateway.parse(Track17Test.NUMBERS, reply.payload());
                assertEquals(2, results.size());
                assertTrue(results.stream().allMatch(r -> r.state() != QueryState.FAILED));
            }
        }
    }

    private static Component findNamed(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container container) { Component found = findNamed(container, name); if (found != null) return found; }
        }
        return null;
    }

    @Test void liveEndpointReturnsAnExplicitResultOrManualVerification() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("autocheck.liveSmoke"));
        try (TrackingGateway gateway = new BrowserGateway(directory.resolve("browser-profile"))) {
            var reply = gateway.query(TrajectoryParserTest.sampleWaybills(), "senderView", "");
            System.out.println("LIVE_ENDPOINT_RESULT=" + reply.kind() + " " + reply.message());
            assertNotEquals(TrackingGateway.Kind.ERROR, reply.kind(), reply.message());
            if (reply.kind() == TrackingGateway.Kind.SUCCESS) {
                var parsed = new TrajectoryParser().parse(TrajectoryParserTest.sampleWaybills(), reply.payload());
                System.out.println("LIVE_WAYBILLS=" + parsed.size() + " LIVE_EVENTS=" + parsed.stream().mapToInt(r -> r.events().size()).sum());
                assertEquals(10, parsed.size());
            }
        }
    }

    private static <T extends Component> T find(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container container) { T found = find(container, type); if (found != null) return found; }
        }
        return null;
    }
}
