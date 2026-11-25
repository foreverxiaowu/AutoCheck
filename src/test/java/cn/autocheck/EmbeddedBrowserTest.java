package cn.autocheck;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in native integration test; all page content is local and contains no CAPTCHA. */
class EmbeddedBrowserTest {
    @TempDir Path profile;
    @Test void hostsResizesAndDetachesTheSameInteractivePage() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("autocheck.embeddedSmoke") && WindowsBrowserDock.supported());
        JFrame[] owner=new JFrame[1];
        SwingUtilities.invokeAndWait(()->{owner[0]=new JFrame("Native browser test");owner[0].setSize(1000,750);owner[0].setVisible(true);});
        BatchRunner.Control control=new BatchRunner.Control();
        try(Playwright pw=Playwright.create(new Playwright.CreateOptions().setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD","1")));
            BrowserContext context=pw.chromium().launchPersistentContext(profile,new BrowserType.LaunchPersistentContextOptions()
                .setChannel("msedge").setHeadless(false).setViewportSize(null).setArgs(java.util.List.of("--app=data:text/html,", "--test-type=")));
            EmbeddedBrowserWindow host=EmbeddedBrowserWindow.open(owner[0],control::cancel,"顺丰国际")) {
            SwingUtilities.invokeAndWait(()->assertTrue(java.util.Arrays.stream(Window.getWindows())
                .anyMatch(w->w instanceof JDialog d && d.isVisible() && d.getTitle().contains("顺丰国际"))));
            Page page=context.pages().get(0);
            String token="AutoCheck-test-"+UUID.randomUUID();
            page.setContent("<html><title>"+token+"</title><body><input id='entry'><button onclick='window.clicked=true'>Test</button></body></html>");
            var nativeWindow=WindowsBrowserDock.find(token);
            for(int i=0;nativeWindow==null && i<50;i++){page.waitForTimeout(100);nativeWindow=WindowsBrowserDock.find(token);}
            assertNotNull(nativeWindow,"task-specific Chromium window");
            host.attach(nativeWindow);assertTrue(host.isAttached());
            page.locator("#entry").fill("same session");page.locator("button").click();
            assertEquals(true,page.evaluate("window.clicked"));
            int initialWidth=((Number)page.evaluate("innerWidth")).intValue();
            host.resizeForTest(950,650);page.waitForTimeout(300);
            assertTrue(((Number)page.evaluate("innerWidth")).intValue()<initialWidth,"native child follows host resize");
            SwingUtilities.invokeLater(()->{Timer timer=new Timer(400,e->{for(Window window:Window.getWindows())clickNamed(window,"verificationContinue");});timer.setRepeats(false);timer.start();});
            assertFalse(host.verify("local verification test",control).cancel());
            assertEquals("same session",page.locator("#entry").inputValue());
            SwingUtilities.invokeAndWait(()->{for(Window window:Window.getWindows())clickNamed(window,"detachBrowser");});
            assertFalse(host.isAttached());assertEquals("same session",page.locator("#entry").inputValue());
        } finally {SwingUtilities.invokeAndWait(()->owner[0].dispose());}
    }
    private static void clickNamed(Component c,String name) {
        if(c instanceof JButton button && name.equals(c.getName()))button.doClick();
        if(c instanceof Container parent)for(Component child:parent.getComponents())clickNamed(child,name);
    }
}
