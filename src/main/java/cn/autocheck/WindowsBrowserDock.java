package cn.autocheck;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.*;
import java.awt.Canvas;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/** Windows-only preview: hosts this task's existing Chromium window, preserving its browser session. */
final class WindowsBrowserDock {
    private static final int GWL_STYLE=-16, WS_CHILD=0x40000000, WS_POPUP=0x80000000;
    private static final int WS_CAPTION=0x00C00000, WS_THICKFRAME=0x00040000;
    private final HWND browser, host, originalParent;
    private final int originalStyle;
    private boolean attached;

    static boolean supported() { return Platform.isWindows(); }
    static HWND find(String uniqueTitle) {
        AtomicReference<HWND> found=new AtomicReference<>();
        User32.INSTANCE.EnumWindows((window,data)->{
            char[] title=new char[1024]; User32.INSTANCE.GetWindowText(window,title,title.length);
            if(Native.toString(title).contains(uniqueTitle)) {found.set(window);return false;}
            return true;
        },null);
        return found.get();
    }
    WindowsBrowserDock(Canvas canvas,HWND browser) throws IOException {
        if(!canvas.isDisplayable()) throw new IOException("内嵌浏览器容器尚未准备好");
        this.browser=browser;host=new HWND(Native.getComponentPointer(canvas));
        originalParent=User32.INSTANCE.GetParent(browser);
        originalStyle=User32.INSTANCE.GetWindowLong(browser,GWL_STYLE);
        User32.INSTANCE.SetWindowLong(browser,GWL_STYLE,(originalStyle & ~(WS_POPUP|WS_CAPTION|WS_THICKFRAME))|WS_CHILD);
        User32.INSTANCE.SetParent(browser,host);
        if(!host.equals(User32.INSTANCE.GetParent(browser))) {
            int error=Native.getLastError();
            User32.INSTANCE.SetParent(browser,originalParent);
            User32.INSTANCE.SetWindowLong(browser,GWL_STYLE,originalStyle);
            throw new IOException("Windows 未能嵌入查询窗口（错误 "+error+"）");
        }
        attached=true;
        User32.INSTANCE.SetWindowPos(browser,null,0,0,0,0,0x27); // FRAMECHANGED | NOMOVE | NOSIZE | NOZORDER
        resize();User32.INSTANCE.ShowWindow(browser,5);
    }
    void resize() {
        if(!attached || !User32.INSTANCE.IsWindow(browser))return;
        RECT bounds=new RECT();
        if(User32.INSTANCE.GetClientRect(host,bounds))
            User32.INSTANCE.MoveWindow(browser,0,0,Math.max(1,bounds.right-bounds.left),Math.max(1,bounds.bottom-bounds.top),true);
    }
    boolean attached() { return attached && User32.INSTANCE.IsWindow(browser) && host.equals(User32.INSTANCE.GetParent(browser)); }
    void detach(boolean show) {
        if(!attached)return;
        attached=false;
        if(!User32.INSTANCE.IsWindow(browser))return;
        User32.INSTANCE.SetParent(browser,originalParent);
        User32.INSTANCE.SetWindowLong(browser,GWL_STYLE,originalStyle);
        User32.INSTANCE.SetWindowPos(browser,null,80,80,1200,850,0x24);
        User32.INSTANCE.ShowWindow(browser,show?5:0);
    }
}
