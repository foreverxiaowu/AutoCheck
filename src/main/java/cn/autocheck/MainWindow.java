package cn.autocheck;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.nio.file.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import cn.autocheck.Models.Event;
import static cn.autocheck.Models.*;

public final class MainWindow extends JFrame {
    private static final Color NAVY = UiTheme.TEXT;
    private FilesService files;
    private FilesService settingsFiles;
    private Path data;
    private final Path rootData;
    private final JComboBox<String> source = new JComboBox<>(new String[]{"圆通国际", "17TRACK", "顺丰国际", "EWE", "4PX"});
    private final JComboBox<Track17Config.Carrier> carrier = new JComboBox<>();
    private final JButton configureCarriers = new JButton("物流公司配置");
    private final JCheckBox embedBrowser = new JCheckBox("内嵌官网（预览）");
    private final JLabel title = new JLabel();
    private final JLabel batchNote = new JLabel();
    private final JLabel ruleHint = new JLabel();
    private Track17Config track17Config = Track17Config.defaults();
    private boolean track17;
    private boolean sf;
    private boolean ewe;
    private boolean fpx;
    private EweRegions eweRegions = EweRegions.defaults();
    private List<String> matches(Result result) {
        return ewe ? matchedRules(result, settings.rules(), eweRegions::classify) : matchedRules(result, settings.rules());
    }
    private boolean progressOnly() { return track17 || sf || fpx; }
    private String sourceName() { return fpx ? "4PX" : ewe ? "EWE" : sf ? "顺丰国际" : track17 ? "17TRACK" : "圆通国际"; }
    private boolean switching;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "tracking-batch-worker"));
    private final JTextArea input = new JTextArea();
    private final JTextArea details = new JTextArea();
    private JScrollPane detailPane;
    private final JTextArea logs = new JTextArea();
    private final JLabel status = new JLabel("就绪 · 导入或粘贴运单号后开始查询");
    private final JLabel summary = new JLabel();
    private final JLabel totalValue = new JLabel("0"), completedValue = new JLabel("0"), matchedValue = new JLabel("0"), failedValue = new JLabel("0");
    private final JProgressBar progress = new JProgressBar();
    private final JButton start = new JButton("开始全部查询");
    private final JButton resume = new JButton("继续未完成 / 失败项");
    private final JButton pause = new JButton("暂停");
    private final JButton stop = new JButton("停止");
    private final JButton importButton = new JButton("导入 TXT / CSV / Excel");
    private final JTextField search = new JTextField(15);
    private final JLabel regionLabel = new JLabel("地区");
    private final JTextField regionSearch = new JTextField(7);
    private final JPanel regionToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
    private final JComboBox<AreaFilter> areaView = new JComboBox<>(AreaFilter.values());
    private final JButton configureRegions = new JButton("国内 / 境外关键词配置");
    private final JComboBox<String> view = new JComboBox<>(new String[]{"全部运单", "匹配条件", "未匹配条件", "查询失败", "暂无轨迹", "待查询"});
    private final JSpinner interval = new JSpinner(new SpinnerNumberModel(3, 1, 120, 1));
    private final JComboBox<String> timeZone = new JComboBox<>(new String[]{"寄件人视角", "收件人视角"});
    private final DefaultTableModel ruleModel = new DefaultTableModel(new Object[]{"启用", "条件名称", "地点", "物流信息", "匹配方式", "所在区域"}, 0) {
        @Override public Class<?> getColumnClass(int column) { return column == 0 ? Boolean.class : column == 5 ? AreaFilter.class : String.class; }
    };
    private final JTable ruleTable = new JTable(ruleModel);
    private final Set<String> checkedWaybills = new LinkedHashSet<>();
    private final DefaultTableModel resultModel = new DefaultTableModel(new Object[]{"勾选", "运单号", "查询状态", "最新时间", "最新地点", "物流进度", "匹配条件", "位置分类"}, 0) {
        @Override public Class<?> getColumnClass(int column) { return column == 0 ? Boolean.class : String.class; }
        @Override public boolean isCellEditable(int row, int column) { return column == 0 && row < displayed.size() && results.containsKey(displayed.get(row)); }
        @Override public void setValueAt(Object value, int row, int column) {
            if (column == 0 && row < displayed.size()) {
                String number = displayed.get(row);
                if (Boolean.TRUE.equals(value) && results.containsKey(number)) checkedWaybills.add(number);
                else checkedWaybills.remove(number);
            }
            super.setValueAt(value, row, column);
            updateSelectionSummary();
        }
    };
    private final JTable table = new ResponsiveTable(resultModel, 5);
    private final Map<String, Result> results = new LinkedHashMap<>();
    private List<String> waybills = List.of();
    private List<String> displayed = List.of();
    private Settings settings = Settings.defaults();
    private BatchRunner.Control control;
    private boolean running;
    private boolean closing;

    public MainWindow(Path data) throws IOException {
        super("AutoCheck · 圆通国际批量查询");
        setIconImages(AppIcons.images());
        this.rootData = data;
        this.data = data;
        this.files = new FilesService(data);
        this.settingsFiles = files;
        try { track17Config = Track17Config.load(rootData); }
        catch (Exception e) { log("物流公司配置读取失败，暂用默认配置：" + e.getMessage()); }
        updateCarriers();
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setSize(1400, 960);
        setMinimumSize(new Dimension(1180, 860));
        setLocationRelativeTo(null);
        build();
        restore();
        updateSourceControls();
        wire();
        rebuild();
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                closing = true;
                if (running) {
                    control.cancel();
                    status.setText("正在保存并关闭，请等待当前请求结束（最长约 30 秒）…");
                    setEnabled(false);
                } else { worker.shutdown(); dispose(); }
            }
        });
    }

    private void build() {
        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBackground(UiTheme.CANVAS);
        root.setBorder(new EmptyBorder(20, 22, 16, 22));
        setContentPane(root);
        JPanel header = UiTheme.hero();
        header.setBorder(new EmptyBorder(19, 24, 18, 24));
        JPanel brand = new JPanel(new BorderLayout()); brand.setOpaque(false);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 27)); title.setForeground(Color.WHITE);
        brand.add(title, BorderLayout.WEST);
        batchNote.setForeground(new Color(0xE1E5FF)); brand.add(batchNote, BorderLayout.EAST);
        header.add(brand, BorderLayout.NORTH);
        JPanel sources = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)); sources.setOpaque(false);
        source.setPreferredSize(new Dimension(150, 36));
        carrier.setPreferredSize(new Dimension(240, 36));
        sources.add(UiTheme.label("查询平台", 13, Font.PLAIN, Color.WHITE)); sources.add(source);
        sources.add(carrier); sources.add(configureCarriers);
        embedBrowser.setOpaque(false); embedBrowser.setForeground(Color.WHITE); embedBrowser.setName("embedBrowser");
        embedBrowser.setToolTipText("17TRACK / 顺丰 Windows 预览：在程序验证窗口内显示同一查询浏览器，可随时切回独立浏览器");
        sources.add(embedBrowser);
        input.setName("waybillInput"); start.setName("startQuery");
        header.add(sources, BorderLayout.CENTER);
        root.add(header, BorderLayout.NORTH);

        JPanel sidebar = UiTheme.card(new BorderLayout(0, 14));
        sidebar.setBorder(new EmptyBorder(19, 17, 17, 17));
        sidebar.setPreferredSize(new Dimension(280, 0));
        JPanel hint = new JPanel(new GridLayout(0, 1, 0, 8));
        hint.add(UiTheme.label("导入运单", 19, Font.BOLD, NAVY));
        hint.add(UiTheme.label("粘贴单号，或从文件批量导入", 12, Font.PLAIN, UiTheme.MUTED));
        UiTheme.button(importButton, UiTheme.BLUE_TINT, UiTheme.BLUE);
        hint.add(importButton);
        hint.add(UiTheme.label("每行一个，自动去重 · Excel 读取 A 列", 11, Font.PLAIN, UiTheme.MUTED));
        sidebar.add(hint, BorderLayout.NORTH);
        input.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        input.setBackground(new Color(0xF8F9FE)); input.setMargin(new Insets(12, 10, 12, 10));
        JScrollPane inputPane = new JScrollPane(input);
        inputPane.setBorder(BorderFactory.createLineBorder(new Color(0xE4E8F5)));
        sidebar.add(inputPane, BorderLayout.CENTER);
        JPanel actions = new JPanel(new GridLayout(0, 1, 0, 9));
        UiTheme.button(start, UiTheme.BLUE, Color.WHITE);
        start.setFont(start.getFont().deriveFont(Font.BOLD));
        UiTheme.button(resume, UiTheme.TEAL_TINT, UiTheme.TEAL);
        UiTheme.button(pause, UiTheme.AMBER_TINT, UiTheme.AMBER);
        UiTheme.button(stop, UiTheme.ROSE_TINT, UiTheme.ROSE);
        actions.add(start); actions.add(resume);
        JPanel taskActions = new JPanel(new GridLayout(1, 2, 7, 0));
        taskActions.add(pause); taskActions.add(stop);
        actions.add(taskActions);
        JLabel hint2 = new JLabel("<html>进度自动保存，随时继续查询。<br>“开始全部查询”会刷新所有单号。</html>");
        hint2.setFont(hint2.getFont().deriveFont(11f)); hint2.setForeground(UiTheme.MUTED);
        hint2.setBorder(new EmptyBorder(8, 3, 5, 3));
        actions.add(hint2);
        sidebar.add(actions, BorderLayout.SOUTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("查询结果", resultsPanel());
        tabs.addTab("筛选条件与设置", rulesPanel());
        logs.setEditable(false); logs.setLineWrap(true);
        logs.setBackground(new Color(0x25334E)); logs.setForeground(new Color(0xD1E4F7));
        logs.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13)); logs.setMargin(new Insets(16, 18, 16, 18));
        tabs.addTab("运行日志", new JScrollPane(logs));
        JSplitPane content = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, tabs);
        content.setDividerLocation(280); content.setResizeWeight(0); content.setDividerSize(16);
        content.setBorder(BorderFactory.createEmptyBorder()); content.setOpaque(false);
        root.add(content, BorderLayout.CENTER);

        JPanel footer = UiTheme.card(new BorderLayout(15, 0));
        footer.setBorder(new EmptyBorder(10, 16, 10, 16));
        status.setForeground(UiTheme.MUTED);
        footer.add(status, BorderLayout.CENTER);
        progress.setPreferredSize(new Dimension(200, 20)); progress.setStringPainted(true);
        footer.add(progress, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);
        pause.setEnabled(false); stop.setEnabled(false);
    }

    private JPanel resultsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(new EmptyBorder(8, 16, 10, 16));
        JPanel top = new JPanel(new BorderLayout(0, 5));
        JPanel statistics = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        statistics.setOpaque(false);
        statistics.add(UiTheme.statistic("全部运单", totalValue, UiTheme.BLUE, UiTheme.BLUE_TINT));
        statistics.add(UiTheme.statistic("已查询", completedValue, UiTheme.TEAL, UiTheme.TEAL_TINT));
        statistics.add(UiTheme.statistic("条件匹配", matchedValue, UiTheme.AMBER, UiTheme.AMBER_TINT));
        statistics.add(UiTheme.statistic("查询失败", failedValue, UiTheme.ROSE, UiTheme.ROSE_TINT));
        top.add(statistics, BorderLayout.NORTH);
        summary.setFont(summary.getFont().deriveFont(12f)); summary.setForeground(UiTheme.MUTED);
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        view.setPreferredSize(new Dimension(145, 34));
        search.setPreferredSize(new Dimension(155, 34));
        search.putClientProperty("JTextField.placeholderText", "单号 / 物流信息");
        search.setColumns(10);
        search.setName("resultSearch");
        toolbar.add(view); toolbar.add(new JLabel("搜索")); toolbar.add(search);
        areaView.setPreferredSize(new Dimension(125, 28));
        regionToolbar.add(new JLabel("位置分类")); regionToolbar.add(areaView);
        regionToolbar.add(regionLabel); regionToolbar.add(regionSearch); regionSearch.setName("regionSearch");
        regionToolbar.add(configureRegions); areaView.setName("areaView");
        configureRegions.setName("configureRegions");
        areaView.setToolTipText("国内按中国大陆判断；境外包含港澳台；只判断最新轨迹地点，无法确认时归为未知");
        regionSearch.setToolTipText("按最新轨迹的 Place 包含匹配，例如：重庆、苏州、Frankfurt");
        view.setName("resultView"); source.setName("querySource"); carrier.setName("carrier");
        JPanel selectionActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        JButton selectAll = new JButton("全选当前列表"), clear = new JButton("清空勾选"), copy = new JButton("复制勾选单号");
        selectAll.setName("checkAllVisible"); clear.setName("clearChecked"); copy.setName("copyChecked");
        JButton excel = new JButton("导出勾选 Excel"), json = new JButton("导出勾选完整轨迹");
        excel.setName("exportCheckedExcel"); json.setName("exportCheckedJson");
        for (JButton button : new JButton[]{selectAll,clear,copy,excel,json}) selectionActions.add(button);
        UiTheme.button(selectAll, UiTheme.BLUE_TINT, UiTheme.BLUE);
        UiTheme.button(clear, UiTheme.ROSE_TINT, UiTheme.ROSE);
        UiTheme.button(copy, UiTheme.BLUE_TINT, UiTheme.BLUE);
        UiTheme.button(excel, UiTheme.TEAL_TINT, UiTheme.TEAL);
        UiTheme.button(json, UiTheme.BLUE_TINT, UiTheme.BLUE);
        selectAll.addActionListener(e -> { finishChecking(); displayed.stream().filter(results::containsKey).forEach(checkedWaybills::add); rebuild(); });
        clear.addActionListener(e -> { finishChecking(); checkedWaybills.clear(); rebuild(); });
        copy.addActionListener(e -> copyChecked());
        excel.addActionListener(e -> export(false)); json.addActionListener(e -> export(true));
        selectionActions.setToolTipText("筛选不会取消勾选；复制和导出也包含被筛选隐藏的勾选项。切换平台或公司会清空勾选。");
        for (JButton button : new JButton[]{copy,excel,json}) button.setToolTipText("仅处理勾选结果，包括筛选后隐藏的勾选项；不勾选则不会复制或导出。");
        JPanel primaryFilters = new JPanel(new BorderLayout(0, 5));
        primaryFilters.add(toolbar, BorderLayout.NORTH); primaryFilters.add(selectionActions, BorderLayout.SOUTH);
        JPanel filters = new JPanel(new BorderLayout(0, 5));
        filters.add(primaryFilters, BorderLayout.NORTH); filters.add(regionToolbar, BorderLayout.CENTER);
        filters.add(summary, BorderLayout.SOUTH); top.add(filters, BorderLayout.CENTER); panel.add(top, BorderLayout.NORTH);
        table.setRowHeight(42); table.setAutoCreateRowSorter(true);
        table.setIntercellSpacing(new Dimension(0, 1)); table.setShowVerticalLines(false);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD, 12f));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {50, 180, 100, 170, 145, 260, 130, 100};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.getColumnModel().getColumn(0).setMinWidth(50); table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.setName("resultsTable");
        table.setDefaultRenderer(String.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(t, value, selected, focus, row, col);
                if (!selected) {
                    int index = t.convertRowIndexToModel(row);
                    Result result = results.get(displayed.get(index));
                    boolean match = result != null && !matches(result).isEmpty();
                    setBackground(row % 2 == 0 ? Color.WHITE : new Color(0xFAFBFE));
                    setForeground(NAVY);
                    if (col == 2) {
                        if (result == null) { setBackground(UiTheme.BLUE_TINT); setForeground(UiTheme.BLUE); }
                        else if (result.state() == QueryState.FAILED) { setBackground(UiTheme.ROSE_TINT); setForeground(UiTheme.ROSE); }
                        else if (result.state() == QueryState.NO_DATA) { setBackground(UiTheme.AMBER_TINT); setForeground(UiTheme.AMBER); }
                        else { setBackground(UiTheme.TEAL_TINT); setForeground(UiTheme.TEAL); }
                    } else if (col == 6 && match) { setBackground(UiTheme.AMBER_TINT); setForeground(UiTheme.AMBER); }
                }
                setToolTipText(value == null ? "" : value.toString());
                setBorder(new EmptyBorder(4, 10, 4, 10));
                setFont(t.getFont().deriveFont(col == 1 ? Font.BOLD : Font.PLAIN));
                return this;
            }
        });
        TableCellRenderer checkboxRenderer = table.getDefaultRenderer(Boolean.class);
        table.setDefaultRenderer(Boolean.class, (t, value, selected, focus, row, column) -> {
            Component component = checkboxRenderer.getTableCellRendererComponent(t,value,selected,focus,row,column);
            component.setEnabled(results.containsKey(displayed.get(t.convertRowIndexToModel(row))));
            return component;
        });
        details.setEditable(false); details.setLineWrap(true); details.setWrapStyleWord(true);
        details.setFont(details.getFont().deriveFont(13f));
        details.setBackground(new Color(0xF7F9FE));
        details.setMargin(new Insets(16, 18, 16, 18));
        details.setText("选择一条运单，查看完整物流轨迹。筛选只检查每个单号的最新一条轨迹。");
        detailPane = new JScrollPane(details);
        detailPane.setBorder(BorderFactory.createTitledBorder("完整轨迹（按时间倒序，保留原时区）"));
        detailPane.setMinimumSize(new Dimension(0, 110));
        JScrollPane tablePane = new JScrollPane(table);
        tablePane.setMinimumSize(new Dimension(0, 90));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tablePane, detailPane);
        split.setResizeWeight(0.85); split.setDividerLocation(340); split.setDividerSize(8);
        split.setBorder(BorderFactory.createEmptyBorder());
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JPanel rulesPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBorder(new EmptyBorder(15, 12, 12, 12));
        panel.add(ruleHint, BorderLayout.NORTH);
        ruleHint.setOpaque(true); ruleHint.setBackground(UiTheme.BLUE_TINT); ruleHint.setForeground(UiTheme.BLUE);
        ruleHint.setBorder(new EmptyBorder(12, 14, 12, 14));
        ruleTable.setRowHeight(40);
        ruleTable.getColumnModel().getColumn(0).setMaxWidth(60);
        ruleTable.getColumnModel().getColumn(4).setCellEditor(new DefaultCellEditor(new JComboBox<>(new String[]{"包含", "精确"})));
        ruleTable.getColumnModel().getColumn(5).setCellEditor(new DefaultCellEditor(new JComboBox<>(AreaFilter.values())));
        panel.add(new JScrollPane(ruleTable), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new GridLayout(0, 1, 0, 10));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("添加条件"), remove = new JButton("删除选中"), save = new JButton("保存并应用");
        UiTheme.button(add, UiTheme.BLUE_TINT, UiTheme.BLUE);
        UiTheme.button(remove, UiTheme.ROSE_TINT, UiTheme.ROSE);
        UiTheme.button(save, UiTheme.BLUE, Color.WHITE);
        add.addActionListener(e -> ruleModel.addRow(new Object[]{true, "新条件", "", "", "包含", AreaFilter.ANY}));
        remove.addActionListener(e -> { if (ruleTable.getSelectedRow() >= 0) ruleModel.removeRow(ruleTable.convertRowIndexToModel(ruleTable.getSelectedRow())); });
        save.addActionListener(e -> { try { saveSettings(); rebuild(); log("筛选条件已保存并应用"); } catch (Exception ex) { error(ex); } });
        buttons.add(add); buttons.add(remove); buttons.add(save); bottom.add(buttons);
        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT));
        options.add(new JLabel("批次间隔（秒）")); options.add(interval);
        options.add(new JLabel("    轨迹时区")); options.add(timeZone); bottom.add(options);
        JLabel help = new JLabel("<html>时间视角设置对下次查询生效。每批结果落盘后才进入下一批。<br>“暂无轨迹”表示接口成功但该单号没有记录；失败项可点击“继续”重试。</html>");
        help.setBorder(new EmptyBorder(5, 5, 8, 5)); bottom.add(help);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private void restore() {
        if (ewe) {
            try {
                EweRegions saved = files.loadEweRegions();
                eweRegions = saved == null ? EweRegions.defaults() : saved;
            } catch (Exception e) { eweRegions = EweRegions.defaults(); log("EWE 地区关键词读取失败，暂用默认值：" + e.getMessage()); }
        }
        try {
            Settings saved = settingsFiles.loadSettings();
            if (saved != null && saved.rules() != null) settings = saved;
        } catch (Exception e) { log("设置文件读取失败，已加载默认值：" + e.getMessage()); }
        if (progressOnly()) settings = settings.progressOnly();
        interval.setValue(Math.max(1, Math.min(120, settings.intervalSeconds())));
        timeZone.setSelectedIndex("consigneeView".equals(settings.timeZoneType()) ? 1 : 0);
        for (Rule rule : settings.rules()) ruleModel.addRow(new Object[]{rule.enabled(), rule.name(), rule.location(), rule.detail(), rule.mode() == MatchMode.EXACT ? "精确" : "包含", rule.area()});
        try {
            Snapshot snapshot = files.loadSnapshot();
            if (snapshot != null) {
                if (snapshot.version() != 1 || snapshot.waybills() == null || snapshot.results() == null) throw new IOException("不支持的进度文件格式");
                waybills = List.copyOf(snapshot.waybills());
                snapshot.results().forEach(r -> results.put(r.waybillNo(), r));
                input.setText(String.join("\n", waybills));
                log("已恢复上次进度：" + results.size() + " / " + waybills.size());
            }
        } catch (Exception e) { log("进度恢复失败（原文件未改动）：" + e.getMessage()); }
    }

    private void wire() {
        source.addActionListener(e -> switchWorkspace());
        carrier.addActionListener(e -> { if (track17) switchWorkspace(); });
        configureCarriers.addActionListener(e -> editCarriers());
        configureRegions.addActionListener(e -> editRegions());
        areaView.addActionListener(e -> rebuild());
        start.addActionListener(e -> begin(false)); resume.addActionListener(e -> begin(true));
        importButton.addActionListener(e -> importFile());
        stop.addActionListener(e -> { control.cancel(); stop.setEnabled(false); pause.setEnabled(false); status.setText("正在停止，将保留已返回的结果…"); });
        pause.addActionListener(e -> {
            if (control.isPaused()) { control.resume(); pause.setText("暂停"); status.setText("已继续查询"); }
            else { control.pause(); pause.setText("继续"); status.setText("已请求暂停，将在当前请求结束后暂停"); }
        });
        view.addActionListener(e -> rebuild());
        DocumentListener filterListener = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { rebuild(); }
            public void removeUpdate(DocumentEvent e) { rebuild(); }
            public void changedUpdate(DocumentEvent e) { rebuild(); }
        };
        search.getDocument().addDocumentListener(filterListener);
        regionSearch.getDocument().addDocumentListener(filterListener);
        table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showDetails(); });
    }

    private void updateCarriers() {
        boolean wasSwitching = switching;
        switching = true;
        carrier.removeAllItems();
        for (Track17Config.Carrier item : track17Config.carriers()) {
            carrier.addItem(item);
            if (item.code() == track17Config.selectedCode()) carrier.setSelectedItem(item);
        }
        switching = wasSwitching;
    }

    private void updateSourceControls() {
        title.setText("AutoCheck  /  " + sourceName());
        setTitle("AutoCheck · " + sourceName() + (track17 ? " · " + carrier.getSelectedItem() : "批量查询"));
        batchNote.setText("每批 " + (sf ? "20" : track17 ? "40" : "10") + " 单  ·  独立保存进度" + (fpx ? "  ·  物流条件筛选" : ewe ? "  ·  地区筛选" : "  ·  人工验证"));
        carrier.setVisible(track17); configureCarriers.setVisible(track17);
        embedBrowser.setVisible((track17 || sf) && WindowsBrowserDock.supported());
        regionToolbar.setVisible(ewe);
        regionLabel.setVisible(ewe); regionSearch.setVisible(ewe);
        timeZone.setEnabled(!progressOnly() && !ewe);
        timeZone.setToolTipText(ewe ? "EWE 未提供时区，保留官网原始时间" : progressOnly() ? "轨迹保留官网返回的原始时区" : "查询轨迹的时间视角");
        setColumnVisible(table, 4, !progressOnly(), 145);
        table.getColumnModel().getColumn(5).setPreferredWidth(progressOnly() ? 320 : ewe ? 200 : 260);
        setColumnVisible(table, 7, ewe, 100);
        detailPane.setBorder(BorderFactory.createTitledBorder(ewe ? "完整轨迹（按原始时间倒序，接口未提供时区）" : "完整轨迹（按时间倒序，保留原时区）"));
        setColumnVisible(ruleTable, 2, !progressOnly(), 150);
        setColumnVisible(ruleTable, 5, ewe, 110);
        ruleHint.setText(progressOnly()
            ? "<html>只匹配最新一条物流进度，不判断地点或地区。多个条件满足任一条即匹配。<br>支持包含和精确匹配；编辑后点击“保存并应用”。</html>"
            : ewe ? "<html>只匹配最新轨迹：所在区域、地点和物流信息同时满足；多条条件满足任意一条即匹配。<br>国内指中国大陆，境外含港澳台；地点填写“成都”可包含匹配“成都市”，未知或冲突地点不会猜测分类。<br>可在结果页配置国内 / 境外关键词；编辑条件后点击“保存并应用”。</html>"
            : "<html>只匹配最新轨迹：同一条件的地点和物流信息须同时满足；多个条件满足任一条即匹配。<br>字段留空表示不限，支持包含和精确匹配；编辑后点击“保存并应用”。</html>");
    }

    private static void setColumnVisible(JTable table, int index, boolean visible, int width) {
        TableColumn column = table.getColumnModel().getColumn(index);
        column.setMinWidth(visible ? 60 : 0);
        column.setMaxWidth(visible ? Integer.MAX_VALUE : 0);
        column.setPreferredWidth(visible ? width : 0);
        column.setWidth(visible ? width : 0);
    }

    private void saveWorkspace() throws IOException {
        List<String> draft = FilesService.parseWaybills(input.getText());
        saveSettings();
        files.saveSnapshot(new Snapshot(1, draft, draft.stream().map(results::get).filter(Objects::nonNull).toList()));
    }

    private void switchWorkspace() {
        if (switching || running) return;
        boolean next17 = source.getSelectedIndex() == 1;
        boolean nextSf = source.getSelectedIndex() == 2;
        boolean nextEwe = source.getSelectedIndex() == 3;
        boolean nextFpx = source.getSelectedIndex() == 4;
        Track17Config.Carrier selected = (Track17Config.Carrier) carrier.getSelectedItem();
        int code = selected == null ? track17Config.selectedCode() : selected.code();
        if (fpx == nextFpx && ewe == nextEwe && sf == nextSf && track17 == next17 && (!next17 || code == track17Config.selectedCode())) return;
        switching = true;
        try {
            saveWorkspace();
            Track17Config nextConfig = new Track17Config(track17Config.carriers(), code);
            if (next17) nextConfig.save(rootData);
            loadWorkspace(next17, nextSf, nextEwe, nextFpx, nextConfig);
        } catch (Exception e) {
            source.setSelectedIndex(fpx ? 4 : ewe ? 3 : sf ? 2 : track17 ? 1 : 0);
            updateCarriers();
            error(e);
        } finally { switching = false; }
    }

    private void loadWorkspace(boolean next17, boolean nextSf, boolean nextEwe, boolean nextFpx, Track17Config config) throws IOException {
        Path nextData = nextFpx ? rootData.resolve("4px") : nextEwe ? rootData.resolve("ewe") : nextSf ? rootData.resolve("sf") : next17 ? Track17Config.progressDirectory(rootData, config.selectedCode()) : rootData;
        FilesService nextFiles = new FilesService(nextData);
        FilesService nextSettings = next17 ? new FilesService(rootData.resolve("17track")) : nextFiles;
        track17 = next17; sf = nextSf; ewe = nextEwe; fpx = nextFpx; track17Config = config;
        data = nextData; files = nextFiles; settingsFiles = nextSettings;
        table.clearSelection(); resultModel.setRowCount(0); displayed = List.of();
        results.clear(); checkedWaybills.clear(); waybills = List.of(); input.setText(""); details.setText("选择运单查看完整轨迹。");
        ruleModel.setRowCount(0); settings = fpx ? Settings.fpxDefaults() : ewe ? Settings.eweDefaults() : sf ? Settings.sfDefaults() : track17 ? Settings.track17Defaults() : Settings.defaults();
        search.setText(""); regionSearch.setText(""); areaView.setSelectedItem(AreaFilter.ANY); view.setSelectedIndex(0);
        restore(); updateSourceControls(); rebuild();
        status.setText("已切换至 " + sourceName() + (track17 ? " / " + carrier.getSelectedItem() : "") + "，进度分别保存");
    }

    private void editCarriers() {
        if (running) return;
        DefaultTableModel model = new DefaultTableModel(new Object[]{"物流公司名称", "公司编码 fc"}, 0);
        track17Config.carriers().forEach(c -> model.addRow(new Object[]{c.name(), c.code()}));
        JTable editor = new JTable(model); editor.setRowHeight(30);
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.add(new JLabel("<html>每次查询选择一家物流公司，应用于本次导入的全部单号。<br>编码 0 表示默认识别；其他编码需与 17TRACK 官网一致。各配置进度分开保存。</html>"), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(editor); scroll.setPreferredSize(new Dimension(520, 220)); panel.add(scroll, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("添加公司"), remove = new JButton("删除选中公司");
        add.addActionListener(e -> model.addRow(new Object[]{"", ""}));
        remove.addActionListener(e -> { if (editor.getSelectedRow() >= 0) model.removeRow(editor.convertRowIndexToModel(editor.getSelectedRow())); });
        buttons.add(add); buttons.add(remove); panel.add(buttons, BorderLayout.SOUTH);
        while (JOptionPane.showConfirmDialog(this, panel, "17TRACK 物流公司配置", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                if (editor.isEditing()) editor.getCellEditor().stopCellEditing();
                List<Track17Config.Carrier> carriers = new ArrayList<>();
                for (int row = 0; row < model.getRowCount(); row++) {
                    String name = clean(Objects.toString(model.getValueAt(row, 0), ""));
                    int code;
                    try { code = Integer.parseInt(clean(Objects.toString(model.getValueAt(row, 1), ""))); }
                    catch (NumberFormatException e) { throw new IOException("第 " + (row + 1) + " 行 fc 必须是非负整数（默认识别填 0）"); }
                    carriers.add(new Track17Config.Carrier(name, code));
                }
                int code = carriers.stream().anyMatch(c -> c.code() == track17Config.selectedCode())
                    ? track17Config.selectedCode() : carriers.isEmpty() ? 0 : carriers.get(0).code();
                Track17Config next = new Track17Config(carriers, code);
                saveWorkspace();
                next.save(rootData);
                switching = true;
                loadWorkspace(true, false, false, false, next);
                updateCarriers(); updateSourceControls();
                log("物流公司配置已保存；删除配置不会删除原查询记录");
                return;
            } catch (Exception e) { error(e); }
            finally { switching = false; }
        }
    }

    private void editRegions() {
        if (!ewe || running) return;
        JTextArea domestic = new JTextArea(String.join("\n", eweRegions.domestic()), 15, 24);
        JTextArea overseas = new JTextArea(String.join("\n", eweRegions.overseas()), 15, 24);
        JPanel columns = new JPanel(new GridLayout(1, 2, 12, 0));
        JScrollPane in = new JScrollPane(domestic), out = new JScrollPane(overseas);
        in.setBorder(BorderFactory.createTitledBorder("国内（中国大陆）地点关键词"));
        out.setBorder(BorderFactory.createTitledBorder("境外（含港澳台）地点关键词"));
        columns.add(in); columns.add(out);
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.add(new JLabel("<html>每行一个关键词，也支持逗号分隔；英文不区分大小写。只检查最新轨迹的地点。<br>可添加城市别名，如“成都”“CHENGDU”。未命中或同时命中两类时显示未知。<br>英文词按完整词匹配，避免 DE 被误识别到其他单词内部。</html>"), BorderLayout.NORTH);
        panel.add(columns, BorderLayout.CENTER);
        JButton defaults = new JButton("填入默认关键词");
        defaults.addActionListener(e -> {
            domestic.setText(String.join("\n", EweRegions.defaults().domestic()));
            overseas.setText(String.join("\n", EweRegions.defaults().overseas()));
        });
        panel.add(defaults, BorderLayout.SOUTH);
        while (JOptionPane.showConfirmDialog(this, panel, "EWE 国内 / 境外关键词配置", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                EweRegions next = new EweRegions(EweRegions.parseWords(domestic.getText()), EweRegions.parseWords(overseas.getText()));
                files.saveEweRegions(next);
                eweRegions = next;
                rebuild();
                log("地区关键词已保存，已有结果已按新配置重新判断");
                return;
            } catch (Exception e) { error(e); }
        }
    }

    private void importFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("单号文件（TXT、CSV、XLSX、XLS）", "txt", "csv", "xlsx", "xls"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            List<String> imported = FilesService.importWaybills(chooser.getSelectedFile().toPath());
            input.setText(String.join("\n", imported));
            log("已导入 " + imported.size() + " 个唯一单号。点击开始查询，或继续已有进度。");
            status.setText("已导入 " + imported.size() + " 个单号，尚未开始查询");
        } catch (Exception e) { error(e); }
    }

    private void saveSettings() throws IOException {
        if (ruleTable.isEditing()) ruleTable.getCellEditor().stopCellEditing();
        List<Rule> rules = new ArrayList<>();
        for (int i = 0; i < ruleModel.getRowCount(); i++) {
            String name = value(i, 1), location = progressOnly() ? "" : value(i, 2), detail = value(i, 3);
            boolean enabled = Boolean.TRUE.equals(ruleModel.getValueAt(i, 0));
            if (name.isEmpty()) throw new IOException("第 " + (i + 1) + " 条条件缺少名称");
            AreaFilter area = !progressOnly() && ruleModel.getValueAt(i, 5) instanceof AreaFilter selected ? selected : AreaFilter.ANY;
            if (enabled && location.isEmpty() && detail.isEmpty() && area == AreaFilter.ANY) throw new IOException("条件“" + name + "”请填写" + (progressOnly() ? "物流进度" : ewe ? "所在区域、地点或物流信息" : "地点或物流信息"));
            rules.add(new Rule(enabled, name, location, detail, "精确".equals(value(i, 4)) ? MatchMode.EXACT : MatchMode.CONTAINS, area));
        }
        Settings next = new Settings((Integer) interval.getValue(), timeZone.getSelectedIndex() == 1 ? "consigneeView" : "senderView", List.copyOf(rules));
        settingsFiles.saveSettings(next);
        settings = next;
    }
    private String value(int row, int col) { return clean(Objects.toString(ruleModel.getValueAt(row, col), "")); }

    private void begin(boolean continuing) {
        if (running) return;
        try {
            List<String> imported = FilesService.parseWaybills(input.getText());
            if (imported.isEmpty()) throw new IllegalArgumentException("请先导入或粘贴快递单号");
            saveSettings();
            Map<String, Result> retained = new LinkedHashMap<>();
            if (continuing) for (String no : imported) if (results.containsKey(no)) retained.put(no, results.get(no));
            List<String> pending = imported.stream().filter(no -> !retained.containsKey(no) || retained.get(no).state() == QueryState.FAILED).toList();
            if (pending.isEmpty()) { status.setText("这些单号已查询完成；如需刷新，请点击“开始全部查询”"); return; }
            files.saveSnapshot(new Snapshot(1, imported, List.copyOf(retained.values())));
            checkedWaybills.retainAll(retained.keySet());
            waybills = imported; results.clear(); results.putAll(retained);
            input.setText(String.join("\n", imported));
            control = new BatchRunner.Control();
            setRunning(true); rebuild();
            Settings runSettings = settings;
            BatchRunner.Control runControl = control;
            boolean embedded = (track17 || sf) && embedBrowser.isSelected();
            worker.submit(() -> execute(imported, pending, retained, runSettings, runControl, embedded));
        } catch (Exception e) { error(e); }
    }

    private void execute(List<String> imported, List<String> pending, Map<String, Result> saved, Settings runSettings, BatchRunner.Control runControl, boolean embedded) {
        String outcome;
        java.util.function.Consumer<String> browserNotice=text -> SwingUtilities.invokeLater(() -> {status.setText(text);log(text);});
        try (TrackingGateway gateway = fpx ? new FpxGateway() : ewe ? new EweGateway() : sf
                ? new SfGateway(data.resolve("browser-profile"), embedded ? this : null, runControl, browserNotice) : track17
                ? new Track17Gateway(rootData.resolve("17track/browser-profile"), track17Config.selectedCode(), embedded ? this : null, runControl,
                    browserNotice)
                : new BrowserGateway(data.resolve("browser-profile"))) {
            new BatchRunner().run(pending, runSettings, gateway, runControl, new BatchRunner.Listener() {
                public void status(String text) { SwingUtilities.invokeLater(() -> { status.setText(text); log(text); }); }
                public BatchRunner.Answer verify(TrackingGateway.Reply reply) throws Exception {
                    if(gateway instanceof Track17Gateway g && g.hasEmbeddedBrowser())return g.verifyEmbedded(reply,runControl);
                    if(gateway instanceof SfGateway g && g.hasEmbeddedBrowser())return g.verifyEmbedded(reply,runControl);
                    return verification(reply, runControl);
                }
                public void completed(List<Result> batch) throws IOException {
                    batch.forEach(r -> saved.put(r.waybillNo(), r));
                    files.saveSnapshot(new Snapshot(1, imported, List.copyOf(saved.values())));
                    SwingUtilities.invokeLater(() -> { batch.forEach(r -> results.put(r.waybillNo(), r)); rebuild(); });
                }
            });
            outcome = runControl.isCancelled() ? "任务已停止，进度已保留" : "本轮查询完成，结果已保存；失败项可点击“继续”重试";
        } catch (CancellationException e) { outcome = "任务已停止，进度已保留"; }
        catch (Exception e) { outcome = "任务中断：" + clean(e.getMessage()); }
        String finalOutcome = outcome;
        SwingUtilities.invokeLater(() -> {
            setRunning(false); status.setText(finalOutcome); log(finalOutcome);
            if (closing) { worker.shutdown(); dispose(); }
        });
    }

    private BatchRunner.Answer verification(TrackingGateway.Reply reply, BatchRunner.Control runControl) throws Exception {
        AtomicReference<BatchRunner.Answer> answer = new AtomicReference<>(BatchRunner.Answer.stop());
        SwingUtilities.invokeAndWait(() -> {
            if (runControl.isCancelled() || closing) return;
            JPanel panel = new JPanel(new BorderLayout(10, 12));
            JLabel hint = new JLabel("<html>" + escapeHtml(reply.message()) + "<br>验证码错误会再次提示；取消将停止任务并保留进度。</html>");
            panel.add(hint, BorderLayout.NORTH);
            JTextField code = new JTextField(15);
            boolean imageVisible = false;
            if (reply.kind() == TrackingGateway.Kind.IMAGE_CAPTCHA) {
                try {
                    String encoded = reply.image().replaceFirst("^data:image/[^;]+;base64,", "");
                    var image = ImageIO.read(new ByteArrayInputStream(Base64.getMimeDecoder().decode(encoded)));
                    if (image == null) throw new IOException("图片无效");
                    Image scaled = image.getWidth() > 520 ? image.getScaledInstance(520, -1, Image.SCALE_SMOOTH) : image;
                    panel.add(new JLabel(new ImageIcon(scaled)), BorderLayout.CENTER);
                    panel.add(code, BorderLayout.SOUTH); imageVisible = true;
                } catch (Exception e) {
                    panel.add(new JLabel("图片无法显示，请在浏览器的官网查询页完成验证后继续。"), BorderLayout.CENTER);
                }
            }
            Object[] options = imageVisible ? new Object[]{"提交验证码", "刷新图片", "停止任务"} : new Object[]{"已在浏览器验证，继续", "停止任务"};
            if (imageVisible) SwingUtilities.invokeLater(code::requestFocusInWindow);
            int selected = JOptionPane.showOptionDialog(this, panel, "需要手动验证 · 当前批次已暂停", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
            if (selected == 0) {
                if (imageVisible && code.getText().isBlank()) answer.set(new BatchRunner.Answer("", false));
                else answer.set(new BatchRunner.Answer(code.getText().strip(), false));
            } else if (imageVisible && selected == 1) answer.set(new BatchRunner.Answer("", false));
        });
        return answer.get();
    }

    private void setRunning(boolean value) {
        running = value; start.setEnabled(!value); resume.setEnabled(!value); input.setEditable(!value);
        importButton.setEnabled(!value); pause.setEnabled(value); stop.setEnabled(value); pause.setText("暂停");
        source.setEnabled(!value); carrier.setEnabled(!value); configureCarriers.setEnabled(!value); configureRegions.setEnabled(!value);
        embedBrowser.setEnabled(!value);
    }

    private void rebuild() {
        finishChecking();
        String selected = selectedWaybill();
        List<String> rows = new ArrayList<>();
        String keyword = search.getText().strip().toLowerCase(Locale.ROOT);
        int filter = view.getSelectedIndex();
        for (String no : waybills) {
            Result result = results.get(no);
            List<String> matches = result == null ? List.of() : matches(result);
            if (filter == 1 && matches.isEmpty() || filter == 2 && (result == null || result.state() != QueryState.OK || !matches.isEmpty())
                || filter == 3 && (result == null || result.state() != QueryState.FAILED)
                || filter == 4 && (result == null || result.state() != QueryState.NO_DATA)
                || filter == 5 && result != null) continue;
            Event latest = result == null ? null : result.latest();
            if (ewe && areaView.getSelectedItem() != AreaFilter.ANY && (latest == null || eweRegions.classify(latest) != areaView.getSelectedItem())) continue;
            if (ewe && !regionSearch.getText().isBlank() && (latest == null || !latest.location().toLowerCase(Locale.ROOT).contains(regionSearch.getText().strip().toLowerCase(Locale.ROOT)))) continue;
            String text = no + " " + (latest == null ? "" : progressOnly() ? latest.detail() : latest.description()) + " " + String.join(" ", matches);
            if (!text.toLowerCase(Locale.ROOT).contains(keyword)) continue;
            rows.add(no);
        }
        table.clearSelection();
        table.getSelectionModel().setAnchorSelectionIndex(-1);
        table.getSelectionModel().setLeadSelectionIndex(-1);
        displayed = List.copyOf(rows);
        resultModel.setRowCount(0);
        for (String no : displayed) {
            Result result = results.get(no); Event event = result == null ? null : result.latest();
            resultModel.addRow(new Object[]{checkedWaybills.contains(no), no, result == null ? "待查询" : result.state().label,
                event == null ? "" : event.eventTime(), event == null ? "" : event.location(), event == null ? "" : event.detail(),
                result == null ? "" : String.join("；", matches(result)),
                ewe && event != null ? eweRegions.classify(event).label : ""});
        }
        if (selected != null && displayed.contains(selected)) {
            int index = table.convertRowIndexToView(displayed.indexOf(selected));
            table.setRowSelectionInterval(index, index);
        }
        long matched = results.values().stream().filter(r -> !matches(r).isEmpty()).count();
        long failed = results.values().stream().filter(r -> r.state() == QueryState.FAILED).count();
        totalValue.setText(Integer.toString(waybills.size())); completedValue.setText(Integer.toString(results.size()));
        matchedValue.setText(Long.toString(matched)); failedValue.setText(Long.toString(failed));
        updateSelectionSummary();
        progress.setMaximum(Math.max(1, waybills.size())); progress.setValue(results.size());
        progress.setString(results.size() + " / " + waybills.size());
    }

    private void showDetails() {
        String no = selectedWaybill();
        if (no == null) return;
        Result result = results.get(no);
        if (result == null) { details.setText(no + "\n待查询"); return; }
        StringBuilder text = new StringBuilder(no).append("  ·  ").append(result.state().label).append("\n");
        if (!clean(result.error()).isEmpty()) text.append("错误：").append(result.error()).append("\n");
        if (result.events().isEmpty()) text.append("暂无物流轨迹\n");
        for (Event event : result.events()) text.append("\n").append(event.eventTime()).append("  ").append(event.timeZone()).append("\n").append(progressOnly() ? event.detail() : event.description()).append("\n");
        details.setText(text.toString()); details.setCaretPosition(0);
    }

    private String selectedWaybill() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()
                || (table.getRowSorter() != null && row >= table.getRowSorter().getViewRowCount())) return null;
        int index = table.convertRowIndexToModel(row);
        return index >= 0 && index < displayed.size() ? displayed.get(index) : null;
    }

    private void finishChecking() {
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
    }

    /** Visible checked rows follow the current sort; hidden checked rows follow their input order. */
    List<Result> checkedResults() {
        finishChecking();
        Map<String, Result> chosen = new LinkedHashMap<>();
        for (int row = 0; row < table.getRowCount(); row++) {
            String number = displayed.get(table.convertRowIndexToModel(row));
            if (checkedWaybills.contains(number) && results.containsKey(number)) chosen.put(number,results.get(number));
        }
        for (String number : waybills)
            if (checkedWaybills.contains(number) && results.containsKey(number)) chosen.putIfAbsent(number,results.get(number));
        return List.copyOf(chosen.values());
    }

    private void updateSelectionSummary() {
        long visibleChecked = displayed.stream().filter(checkedWaybills::contains).count();
        long totalChecked = checkedWaybills.stream().filter(results::containsKey).count();
        summary.setText("显示 " + displayed.size() + " 条 · 已勾选 " + totalChecked
            + (totalChecked > visibleChecked ? "（隐藏 " + (totalChecked-visibleChecked) + " 条，也会复制/导出）" : "")
            + " · 点击行查看完整轨迹");
    }

    private void copyChecked() {
        List<Result> selected = checkedResults();
        if (selected.isEmpty()) { status.setText("请先勾选需要复制的查询结果"); return; }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(ResultExports.clipboardText(selected)),null);
            status.setText("已复制 " + selected.size() + " 个勾选单号，每行一个");
        } catch (Exception e) { error(e); }
    }

    private void export(boolean json) {
        List<Result> selected = checkedResults();
        if (selected.isEmpty()) { status.setText("请先勾选需要导出的查询结果"); return; }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(json ? "完整轨迹 JSON" : "Excel 工作簿（.xlsx）",json ? "json" : "xlsx"));
        chooser.setSelectedFile(new File(json ? "勾选物流完整轨迹.json" : "勾选物流结果.xlsx"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path path = chooser.getSelectedFile().toPath();
        String suffix = json ? ".json" : ".xlsx";
        if (!path.toString().toLowerCase(Locale.ROOT).endsWith(suffix)) path = Path.of(path + suffix);
        try {
            if (json && progressOnly()) FilesService.exportProgressJson(path, selected);
            else if (json) FilesService.JSON.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), selected);
            else ResultExports.writeXlsx(path, selected, settings.rules(), progressOnly(), ewe, ewe ? eweRegions::classify : MainlandLocation::classify);
            status.setText("已导出 " + selected.size() + " 条勾选结果：" + path.toAbsolutePath());
        } catch (Exception e) { error(e); }
    }

    private void error(Exception e) { JOptionPane.showMessageDialog(this, e.getMessage(), "操作未完成", JOptionPane.ERROR_MESSAGE); log("错误：" + e.getMessage()); }
    private void log(String text) { logs.append(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "  " + text + "\n"); logs.setCaretPosition(logs.getDocument().getLength()); }
    private static String escapeHtml(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
}
