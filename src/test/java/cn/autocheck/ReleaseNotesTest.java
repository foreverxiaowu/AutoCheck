package cn.autocheck;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ReleaseNotesTest {
    @TempDir Path data;

    @Test void storesAcknowledgmentAcrossRestartsWithoutChangingOtherUserData() throws Exception {
        FilesService files=new FilesService(data);
        assertNull(files.loadReleaseNotesState());
        files.saveSettings(Models.Settings.defaults());
        byte[] settings=Files.readAllBytes(data.resolve("settings.json"));
        ReleaseNotes.State state=new ReleaseNotes.State(Set.of("1.1.0"));
        assertFalse(state.hasSeenCurrent());
        files.saveReleaseNotesState(state.acknowledgeCurrent());
        ReleaseNotes.State restored=new FilesService(data).loadReleaseNotesState();
        assertTrue(restored.hasSeenCurrent());
        assertEquals(Set.of("1.1.0","1.2.1"),restored.acknowledgedVersions());
        assertArrayEquals(settings,Files.readAllBytes(data.resolve("settings.json")));
    }

    @Test void continueButtonShowsTheNoticeOnlyOnce() throws Exception { verifyShownOnce(false); }
    @Test void closingTheNoticeAlsoAcknowledgesIt() throws Exception { verifyShownOnce(true); }

    private void verifyShownOnce(boolean closeWithX) throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("autocheck.uiSmoke"));
        AtomicReference<Throwable> error=new AtomicReference<>();
        SwingUtilities.invokeAndWait(()->{
            JFrame owner=new JFrame("Release notes check");
            AtomicInteger shown=new AtomicInteger();
            Timer timer=new Timer(100,null);
            try {
                Main.configureAppearance();owner.setSize(1000,750);owner.setVisible(true);
                timer.addActionListener(e->{
                    for(Window window:Window.getWindows()) {
                        if(!(window instanceof JDialog dialog) || !"releaseNotesDialog".equals(dialog.getName()) || !dialog.isShowing())continue;
                        try {
                            shown.incrementAndGet();assertTrue(dialog.getTitle().contains("V1.2.1"));
                            assertFalse(new FilesService(data).loadReleaseNotesState()!=null,"Do not mark read before the notice is dismissed");
                            if(!closeWithX) {
                                BufferedImage image=new BufferedImage(dialog.getWidth(),dialog.getHeight(),BufferedImage.TYPE_INT_RGB);
                                Graphics2D graphics=image.createGraphics();dialog.paint(graphics);graphics.dispose();
                                ImageIO.write(image,"png",Path.of("target","release-notes-preview.png").toFile());
                            }
                        }catch(Throwable failure){error.compareAndSet(null,failure);}
                        if(closeWithX)dialog.dispatchEvent(new WindowEvent(dialog,WindowEvent.WINDOW_CLOSING));
                        else findContinue(dialog).doClick();
                    }
                });
                timer.start();
                ReleaseNotes.showIfNeeded(owner,data);
                assertTrue(new FilesService(data).loadReleaseNotesState().hasSeenCurrent());
                ReleaseNotes.showIfNeeded(owner,data); // Read the saved state again, as a fresh startup would.
                assertEquals(1,shown.get(),"Subsequent startups must not display the notice again");
            }catch(Throwable failure){error.compareAndSet(null,failure);}
            finally{timer.stop();owner.dispose();}
        });
        if(error.get()!=null)throw new AssertionError(error.get());
    }

    private JButton findContinue(Container root) {
        for(Component child:root.getComponents()) {
            if(child instanceof JButton button && "releaseNotesContinue".equals(button.getName()))return button;
            if(child instanceof Container container){JButton found=findContinue(container);if(found!=null)return found;}
        }
        return null;
    }
}
