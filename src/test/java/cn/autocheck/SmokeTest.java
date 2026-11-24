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
        var eweNumbers=java.util.List.of(EweTest.NUMBER);
        new FilesService(directory.resolve("ewe")).saveSnapshot(new Snapshot(1,eweNumbers,new EweParser().parse(eweNumbers,EweTest.sample())));
        var fpxNumbers=java.util.List.of(FpxTest.NUMBER);
        new FilesService(directory.resolve("4px")).saveSnapshot(new Snapshot(1,fpxNumbers,new FpxParser().parse(fpxNumbers,FpxTest.sample())));
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
                assertEquals(7, table.getColumnCount());
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
                source.setSelectedIndex(3); assertEquals(1,table.getRowCount());
                assertEquals("苏州市 虎丘区",table.getValueAt(0,3));
                assertEquals("中国大陆",table.getValueAt(0,6));
                JComboBox<?> areaView=(JComboBox<?>)findNamed(window,"areaView");
                areaView.setSelectedItem(AreaFilter.OUTSIDE); assertEquals(0,table.getRowCount());
                areaView.setSelectedItem(AreaFilter.MAINLAND); assertEquals(1,table.getRowCount());
                JTextField region=(JTextField)findNamed(window,"regionSearch");
                assertTrue(region.isVisible());
                region.setText("重庆"); assertEquals(0,table.getRowCount());
                region.setText("苏州"); assertEquals(1,table.getRowCount());
                table.setRowSelectionInterval(0,0); window.validate();
                Graphics2D eweGraphics=image.createGraphics(); window.paint(eweGraphics); eweGraphics.dispose();
                ImageIO.write(image,"png",Path.of("target","ewe-ui-smoke.png").toFile());
                window.setSize(1180,800); window.validate();
                BufferedImage compact = new BufferedImage(window.getWidth(),window.getHeight(),BufferedImage.TYPE_INT_RGB);
                Graphics2D compactGraphics=compact.createGraphics();window.paint(compactGraphics);compactGraphics.dispose();
                ImageIO.write(compact,"png",Path.of("target","ui-compact-smoke.png").toFile());
                JTabbedPane tabs=find(window,JTabbedPane.class); tabs.setSelectedIndex(1);window.validate();
                Graphics2D rulesGraphics=compact.createGraphics();window.paint(rulesGraphics);rulesGraphics.dispose();
                ImageIO.write(compact,"png",Path.of("target","ui-rules-smoke.png").toFile());
                tabs.setSelectedIndex(0);window.setSize(1400,960);window.validate();
                source.setSelectedIndex(4); assertEquals(1,table.getRowCount());
                assertEquals(FpxTest.NUMBER,table.getValueAt(0,0));
                assertTrue(table.getValueAt(0,4).toString().startsWith("您的快件已代收"));
                assertFalse(region.isVisible());
                assertEquals(0,table.getColumnModel().getColumn(3).getMaxWidth());
                assertEquals(0,table.getColumnModel().getColumn(6).getMaxWidth());
                table.setRowSelectionInterval(0,0);window.validate();
                Graphics2D fpxGraphics=image.createGraphics();window.paint(fpxGraphics);fpxGraphics.dispose();
                ImageIO.write(image,"png",Path.of("target","4px-ui-smoke.png").toFile());
                int regularProgressWidth=table.getColumnModel().getColumn(4).getWidth();
                window.setSize(2500,1050);window.validate();
                int viewportWidth=((JViewport)table.getParent()).getExtentSize().width;
                assertEquals(viewportWidth,table.getColumnModel().getTotalColumnWidth(),"Wide table must fill the result viewport");
                assertTrue(table.getColumnModel().getColumn(4).getWidth()>regularProgressWidth,"Logistics detail must use additional window width");
                assertEquals(0,table.getColumnModel().getColumn(3).getWidth());
                assertEquals(0,table.getColumnModel().getColumn(6).getWidth());
                BufferedImage wide=new BufferedImage(window.getWidth(),window.getHeight(),BufferedImage.TYPE_INT_RGB);
                Graphics2D wideGraphics=wide.createGraphics();window.paint(wideGraphics);wideGraphics.dispose();
                ImageIO.write(wide,"png",Path.of("target","ui-wide-smoke.png").toFile());
                window.setSize(1180,800);window.validate();
                assertTrue(table.getColumnModel().getColumn(0).getWidth()>=180,"Shrinking must keep waybills readable");
                window.setSize(1400,960);window.validate();
                assertEquals(((JViewport)table.getParent()).getExtentSize().width,table.getColumnModel().getTotalColumnWidth(),"Restoring the window must recalculate widths");
                source.setSelectedIndex(3);assertEquals(1,table.getRowCount());
                source.setSelectedIndex(0); assertEquals(1, table.getRowCount());
                assertFalse(region.isVisible());
                assertEquals(0,table.getColumnModel().getColumn(6).getMaxWidth());
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
