package cn.autocheck;

import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.FontUIResource;
import java.awt.*;

/** Shared desktop palette and components; tracking and persistence remain independent of styling. */
final class UiTheme {
    static final Color TEXT = new Color(0x24314C);
    static final Color MUTED = new Color(0x6F7D98);
    static final Color CANVAS = new Color(0xF3F5FC);
    static final Color BLUE = new Color(0x5365DF);
    static final Color TEAL = new Color(0x138272);
    static final Color AMBER = new Color(0xAF6B16);
    static final Color ROSE = new Color(0xC14764);
    static final Color BLUE_TINT = new Color(0xEDF0FF);
    static final Color TEAL_TINT = new Color(0xE6F6F0);
    static final Color AMBER_TINT = new Color(0xFFF3DC);
    static final Color ROSE_TINT = new Color(0xFCEAF0);

    static void install() throws Exception {
        UIManager.setLookAndFeel(new FlatLightLaf());
        UIManager.put("defaultFont", new FontUIResource("Microsoft YaHei", Font.PLAIN, 13));
        UIManager.put("Panel.background", Color.WHITE);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Component.accentColor", BLUE);
        UIManager.put("Component.focusColor", new Color(0xA6B1F6));
        UIManager.put("Component.borderColor", new Color(0xDCE2F0));
        UIManager.put("Button.arc", 12);
        UIManager.put("Component.arc", 12);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("Button.margin", new Insets(8, 14, 8, 14));
        UIManager.put("Component.minimumHeight", 34);
        UIManager.put("TabbedPane.underlineColor", BLUE);
        UIManager.put("TabbedPane.selectedBackground", BLUE_TINT);
        UIManager.put("TabbedPane.tabHeight", 46);
        UIManager.put("TabbedPane.tabInsets", new Insets(8, 20, 8, 20));
        UIManager.put("TableHeader.background", new Color(0xF5F7FC));
        UIManager.put("TableHeader.foreground", MUTED);
        UIManager.put("TableHeader.height", 40);
        UIManager.put("Table.selectionBackground", new Color(0xE8EDFF));
        UIManager.put("Table.selectionInactiveBackground", new Color(0xE8EDFF));
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("Table.selectionInactiveForeground", TEXT);
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("Table.gridColor", new Color(0xEEF1F8));
        UIManager.put("Table.focusCellHighlightBorder", BorderFactory.createEmptyBorder());
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("SplitPane.background", Color.WHITE);
        UIManager.put("SplitPaneDivider.draggingColor", new Color(0xD9DFFA));
        UIManager.put("ProgressBar.foreground", BLUE);
        UIManager.put("ProgressBar.background", BLUE_TINT);
        UIManager.put("ProgressBar.arc", 999);
        UIManager.put("TextArea.foreground", TEXT);
        UIManager.put("TabbedPane.background", Color.WHITE);
        UIManager.put("TabbedPane.contentAreaColor", Color.WHITE);
    }

    static JPanel card(LayoutManager layout) { return new Surface(layout, Color.WHITE, null); }
    static JPanel hero() { return new Surface(new BorderLayout(0, 14), new Color(0x3449B4), new Color(0x8054CD)); }
    static JLabel label(String text, int size, int weight, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(weight, (float) size));
        label.setForeground(color);
        return label;
    }
    static JPanel statistic(String caption, JLabel value, Color color, Color tint) {
        JPanel card = new Surface(new BorderLayout(0, 4), tint, null);
        card.setBorder(new EmptyBorder(10, 16, 10, 16));
        card.add(label(caption, 12, Font.PLAIN, color), BorderLayout.NORTH);
        value.setFont(value.getFont().deriveFont(Font.BOLD, 27f)); value.setForeground(color);
        card.add(value, BorderLayout.CENTER);
        return card;
    }
    static void button(JButton button, Color background, Color foreground) {
        button.setBackground(background); button.setForeground(foreground);
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.putClientProperty("JComponent.minimumHeight", 38);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    private static final class Surface extends JPanel {
        private final Color first, second;
        Surface(LayoutManager layout, Color first, Color second) {
            super(layout); this.first=first; this.second=second; setOpaque(false);
        }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g=(Graphics2D)graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(second == null ? first : new GradientPaint(0, 0, first, getWidth(), getHeight(), second));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 22, 22);
            g.dispose(); super.paintComponent(graphics);
        }
    }
}
