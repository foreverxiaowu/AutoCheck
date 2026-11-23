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
                JComboBox<?> view = find(window, JComboBox.class);
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
