package cn.autocheck;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/** Version-specific notice, acknowledged once per user's application data directory. */
final class ReleaseNotes {
    static final String VERSION="1.2.1";
    record State(Set<String> acknowledgedVersions) {
        State { acknowledgedVersions=acknowledgedVersions==null ? Set.of() : Set.copyOf(acknowledgedVersions); }
        boolean hasSeenCurrent() { return acknowledgedVersions.contains(VERSION); }
        State acknowledgeCurrent() {
            Set<String> versions=new LinkedHashSet<>(acknowledgedVersions);versions.add(VERSION);
            return new State(versions);
        }
    }
    private ReleaseNotes() {}

    static void showIfNeeded(Frame owner,Path data) {
        FilesService files;
        try { files=new FilesService(data); }
        catch(IOException e) { System.err.println("无法读取更新说明状态："+e.getMessage());return; }
        State loaded=null;
        try { loaded=files.loadReleaseNotesState(); }
        catch(IOException e) { System.err.println("更新说明状态读取失败，将重新显示："+e.getMessage()); }
        State state=loaded==null ? new State(Set.of()) : loaded;
        if(state.hasSeenCurrent())return;
        Runnable acknowledge=()->{
            try { files.saveReleaseNotesState(state.acknowledgeCurrent()); }
            catch(IOException e) { System.err.println("更新说明已读状态保存失败："+e.getMessage()); }
        };
        createDialog(owner,acknowledge).setVisible(true);
    }

    private static JDialog createDialog(Frame owner,Runnable acknowledge) {
        JDialog dialog=new JDialog(owner,"AutoCheck · V"+VERSION+" 版本更新",Dialog.ModalityType.DOCUMENT_MODAL);
        dialog.setName("releaseNotesDialog");dialog.setIconImages(AppIcons.images());
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        JPanel root=new JPanel(new BorderLayout(0,14));root.setBackground(UiTheme.CANVAS);
        root.setBorder(new EmptyBorder(16,18,16,18));
        JPanel header=UiTheme.hero();header.setLayout(new GridLayout(0,1,0,5));header.setBorder(new EmptyBorder(15,18,15,18));
        header.add(UiTheme.label("V"+VERSION+" 版本更新",24,Font.BOLD,Color.WHITE));
        header.add(UiTheme.label("查询更方便，物流信息展示更清晰",13,Font.PLAIN,new Color(0xE1E5FF)));
        root.add(header,BorderLayout.NORTH);

        JPanel features=new JPanel();features.setLayout(new BoxLayout(features,BoxLayout.Y_AXIS));features.setOpaque(false);
        addFeature(features,"查询结果支持多选","每条结果前增加勾选框，可全选当前列表或清空勾选。");
        addFeature(features,"一键复制勾选单号","仅复制所选物流单号，每行一个，方便直接粘贴使用。");
        addFeature(features,"按勾选导出 Excel","支持导出 .xlsx 表格，保留前导零和长单号，也可导出完整轨迹。");
        addFeature(features,"17TRACK / 顺丰内嵌官网验证","勾选“内嵌官网（预览）”，在程序内手动完成验证，随时可切回独立浏览器。");
        addFeature(features,"17TRACK 新增物流公司","新增“默认（0）”和“洋包裹（101107）”，“默认”支持自动识别物流公司。");
        addFeature(features,"物流显示区域更大","统计信息改为单行小标签，压缩留白，让物流列表展示更多内容。");
        JScrollPane scroll=new JScrollPane(features);scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);scroll.getViewport().setOpaque(false);scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);root.add(scroll,BorderLayout.CENTER);

        JPanel footer=new JPanel(new BorderLayout(12,0));footer.setOpaque(false);
        footer.add(UiTheme.label("本版本更新说明仅在首次打开时显示",12,Font.PLAIN,UiTheme.MUTED),BorderLayout.CENTER);
        JButton start=new JButton("我知道了，开始使用");start.setName("releaseNotesContinue");
        UiTheme.button(start,UiTheme.BLUE,Color.WHITE);footer.add(start,BorderLayout.EAST);root.add(footer,BorderLayout.SOUTH);
        boolean[] acknowledged={false};
        Runnable dismiss=()->{
            if(!acknowledged[0]){acknowledged[0]=true;acknowledge.run();}
            dialog.dispose();
        };
        start.addActionListener(e->dismiss.run());
        dialog.addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){dismiss.run();}});
        dialog.setContentPane(root);dialog.getRootPane().setDefaultButton(start);
        Rectangle screen=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        dialog.setSize(Math.min(760,screen.width-40),Math.min(650,screen.height-48));
        dialog.setLocationRelativeTo(owner);
        dialog.setLocation(Math.max(screen.x,Math.min(dialog.getX(),screen.x+screen.width-dialog.getWidth())),
            Math.max(screen.y,Math.min(dialog.getY(),screen.y+screen.height-dialog.getHeight())));
        return dialog;
    }

    private static void addFeature(JPanel features,String title,String description) {
        JPanel item=UiTheme.card(new BorderLayout(0,4));item.setBorder(new EmptyBorder(10,14,10,14));
        item.add(UiTheme.label(title,14,Font.BOLD,UiTheme.TEXT),BorderLayout.NORTH);
        item.add(UiTheme.label("<html>"+description+"</html>",12,Font.PLAIN,UiTheme.MUTED),BorderLayout.CENTER);
        item.setAlignmentX(Component.LEFT_ALIGNMENT);item.setMaximumSize(new Dimension(Integer.MAX_VALUE,item.getPreferredSize().height));
        features.add(item);features.add(Box.createVerticalStrut(7));
    }
}
