package cn.autocheck;

import com.sun.jna.platform.win32.WinDef.HWND;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;
import java.util.List;
import java.util.function.Consumer;
import java.io.IOException;

/** App-owned manual verification window. All UI/native operations run on the Swing EDT. */
final class EmbeddedBrowserWindow implements AutoCloseable {
    private final JDialog dialog;
    private final Canvas canvas=new Canvas();
    private final JPanel browserPanel=new JPanel(new BorderLayout());
    private final JLabel hint=new JLabel("正在加载当前批次的官网查询页面…");
    private final JButton proceed=new JButton("验证完成，继续查询");
    private final JButton detach=new JButton("切回独立浏览器");
    private final Runnable cancel;
    private WindowsBrowserDock dock;
    private boolean cancelled,approved,closed;

    private EmbeddedBrowserWindow(Frame owner,Runnable cancel,String source) {
        this.cancel=cancel;
        dialog=new JDialog(owner,"AutoCheck · "+source+" 官网验证（内嵌预览）",false);
        dialog.setIconImages(AppIcons.images());dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        JPanel root=new JPanel(new BorderLayout(0,12));root.setBorder(new EmptyBorder(16,16,14,16));root.setBackground(UiTheme.CANVAS);
        JPanel header=new JPanel(new GridLayout(0,1,0,5));header.setOpaque(false);
        header.add(UiTheme.label(source+" · 官网验证",20,Font.BOLD,UiTheme.TEXT));
        header.add(UiTheme.label("请在下方官网页面手动完成验证。此页面与当前查询共用同一会话。",12,Font.PLAIN,UiTheme.MUTED));
        root.add(header,BorderLayout.NORTH);
        canvas.setBackground(Color.WHITE);browserPanel.add(canvas,BorderLayout.CENTER);root.add(browserPanel,BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout(10,9));footer.setOpaque(false);
        hint.setForeground(UiTheme.MUTED);footer.add(hint,BorderLayout.NORTH);
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));buttons.setOpaque(false);
        JButton stop=new JButton("停止查询");
        UiTheme.button(detach,UiTheme.BLUE_TINT,UiTheme.BLUE);UiTheme.button(stop,UiTheme.ROSE_TINT,UiTheme.ROSE);UiTheme.button(proceed,UiTheme.BLUE,Color.WHITE);
        proceed.setEnabled(false);detach.setEnabled(false);
        proceed.setName("verificationContinue"); detach.setName("detachBrowser"); stop.setName("verificationStop");
        buttons.add(detach);buttons.add(stop);buttons.add(proceed);footer.add(buttons,BorderLayout.CENTER);root.add(footer,BorderLayout.SOUTH);
        dialog.setContentPane(root);dialog.setMinimumSize(new Dimension(900,600));
        Rectangle screen=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        dialog.setSize(Math.min(1280,screen.width-40),Math.min(1100,screen.height-48));dialog.setLocationRelativeTo(owner);
        // Keep a taller verification page on screen even when its owner is shorter.
        dialog.setLocation(Math.max(screen.x,Math.min(dialog.getX(),screen.x+screen.width-dialog.getWidth())),
            Math.max(screen.y,Math.min(dialog.getY(),screen.y+screen.height-dialog.getHeight())));
        canvas.addComponentListener(new ComponentAdapter(){@Override public void componentResized(ComponentEvent event){if(dock!=null)dock.resize();}});
        proceed.addActionListener(e->{approved=true;dialog.setVisible(false);});
        stop.addActionListener(e->cancel());
        dialog.addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent event){cancel();}});
        detach.addActionListener(e->{
            if(dock!=null){dock.detach(true);dock=null;}
            canvas.setVisible(false);detach.setEnabled(false);
            hint.setText("已切回独立浏览器；请在那里完成验证，再点击右侧继续查询。");
        });
        dialog.setVisible(true);
    }
    static EmbeddedBrowserWindow open(Frame owner,Runnable cancel,String source) throws Exception {
        return onEdt(()->new EmbeddedBrowserWindow(owner,cancel,source));
    }
    static BrowserType.LaunchPersistentContextOptions launchOptions(BrowserType.LaunchPersistentContextOptions options,boolean requested) {
        return requested && WindowsBrowserDock.supported()
            ? options.setViewportSize(null).setArgs(List.of("--app=data:text/html,", "--test-type=")) : options;
    }
    static EmbeddedBrowserWindow openAttached(Page page,Frame owner,BatchRunner.Control control,String source,Consumer<String> notice) {
        if(owner==null || !WindowsBrowserDock.supported())return null;
        EmbeddedBrowserWindow host=null;
        try {
            // Identify this query's window before loading the website; retain the same Page and session.
            String token="AutoCheck-browser-"+UUID.randomUUID();
            page.evaluate("title => document.title = title",token);
            host=open(owner,control::cancel,source);
            HWND window=null;
            long deadline=System.nanoTime()+5_000_000_000L;
            while(window==null && System.nanoTime()<deadline) {
                window=WindowsBrowserDock.find(token);
                if(window==null)page.waitForTimeout(100);
            }
            if(window==null)throw new IOException("未找到本次查询的浏览器窗口");
            host.attach(window);
            notice.accept(source+" 官网已放入程序验证窗口（预览）；请手动完成安全验证。");
            return host;
        } catch(Exception e) {
            if(host!=null)host.close();
            notice.accept(source+" 内嵌预览未能启动，已使用独立浏览器："+Models.clean(e.getMessage()));
            return null;
        }
    }
    void attach(HWND window) throws Exception {
        onEdt(()->{dock=new WindowsBrowserDock(canvas,window);detach.setEnabled(true);return null;});
    }
    boolean isAttached() throws Exception { return onEdt(()->dock!=null && dock.attached()); }
    void resizeForTest(int width,int height) throws Exception {onEdt(()->{dialog.setSize(width,height);dialog.validate();if(dock!=null)dock.resize();return null;});}
    private void cancel() {cancelled=true;cancel.run();dialog.setVisible(false);}
    BatchRunner.Answer verify(String message,BatchRunner.Control control) throws Exception {
        return onEdt(()->{
            if(closed || cancelled || control.isCancelled())return BatchRunner.Answer.stop();
            approved=false;hint.setText("请完成官网安全验证后点击“验证完成，继续查询”；停止会保留已查询进度。");
            proceed.setEnabled(true);
            dialog.setVisible(false);dialog.setModalityType(Dialog.ModalityType.DOCUMENT_MODAL);
            dialog.setVisible(true); // Keeps the embedded browser interactive while the main window waits.
            proceed.setEnabled(false);
            dialog.setModalityType(Dialog.ModalityType.MODELESS);
            if(!cancelled && !control.isCancelled() && !closed){dialog.setVisible(true);if(dock!=null)dock.resize();hint.setText("正在继续当前批次查询…");}
            return approved && !control.isCancelled() ? new BatchRunner.Answer("",false) : BatchRunner.Answer.stop();
        });
    }
    @Override public void close() {
        try {onEdt(()->{closed=true;if(dock!=null){dock.detach(false);dock=null;}dialog.dispose();return null;});}
        catch(Exception e){throw new IllegalStateException("关闭内嵌验证窗口失败",e);}
    }
    private interface UiCall<T>{T call() throws Exception;}
    private static <T>T onEdt(UiCall<T> action) throws Exception {
        if(SwingUtilities.isEventDispatchThread())return action.call();
        AtomicReference<T> result=new AtomicReference<>();AtomicReference<Exception> error=new AtomicReference<>();
        SwingUtilities.invokeAndWait(()->{try{result.set(action.call());}catch(Exception e){error.set(e);}});
        if(error.get()!=null)throw error.get();return result.get();
    }
}
