package cn.autocheck;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import static cn.autocheck.Models.*;

public final class FilesService {
    public static final ObjectMapper JSON = new ObjectMapper();
    private final Path directory;
    public FilesService(Path directory) throws IOException { this.directory = directory; Files.createDirectories(directory); }

    public static List<String> parseWaybills(String input) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        // Unicode whitespace includes NBSP copied from web pages and narrow/full-width spaces.
        // Keep separators between identifiers instead of deleting them and joining two waybills.
        for (String token : input.replace('\uFEFF', ' ').split("(?U)[\\s,，;；\\\"'、]+")) {
            if (token.isBlank() || Set.of("单号", "快递单号", "运单号", "waybillNo", "waybillNos").contains(token)) continue;
            if (!token.matches("[A-Za-z0-9-]{5,50}") || !token.matches(".*[0-9].*"))
                throw new IllegalArgumentException("无效单号：" + token.substring(0, Math.min(token.length(), 50)) + "。请仅导入单号列。");
            result.add(token);
        }
        return List.copyOf(result);
    }

    public static List<String> importWaybills(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
            StringBuilder input = new StringBuilder();
            try (InputStream stream = Files.newInputStream(path); Workbook workbook = WorkbookFactory.create(stream)) {
                if (workbook.getNumberOfSheets() == 0) return List.of();
                DataFormatter formatter = new DataFormatter(Locale.ROOT);
                for (Row row : workbook.getSheetAt(0)) {
                    Cell cell = row.getCell(0);
                    if (cell != null && cell.getCellType() == CellType.FORMULA)
                        throw new IOException("单号列不能使用公式，请先粘贴为文本值");
                    input.append(cell == null ? "" : formatter.formatCellValue(cell)).append('\n');
                }
            }
            return parseWaybills(input.toString());
        }
        byte[] bytes = Files.readAllBytes(path);
        String input;
        try { input = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException e) { input = Charset.forName("GB18030").newDecoder().decode(ByteBuffer.wrap(bytes)).toString(); }
        return parseWaybills(input);
    }

    public synchronized void saveSnapshot(Snapshot snapshot) throws IOException { atomicWrite(directory.resolve("progress.json"), snapshot); }
    public synchronized void saveSettings(Settings settings) throws IOException { atomicWrite(directory.resolve("settings.json"), settings); }
    public Snapshot loadSnapshot() throws IOException { return read("progress.json", Snapshot.class); }
    public Settings loadSettings() throws IOException { return read("settings.json", Settings.class); }
    public EweRegions loadEweRegions() throws IOException { return read("regions.json", EweRegions.class); }
    public synchronized void saveEweRegions(EweRegions regions) throws IOException { atomicWrite(directory.resolve("regions.json"), regions); }
    public Track17Config loadTrack17Config() throws IOException { return read("carriers.json", Track17Config.class); }
    public synchronized void saveTrack17Config(Track17Config config) throws IOException { atomicWrite(directory.resolve("carriers.json"), config); }
    ReleaseNotes.State loadReleaseNotesState() throws IOException { return read("release-notes.json", ReleaseNotes.State.class); }
    synchronized void saveReleaseNotesState(ReleaseNotes.State state) throws IOException { atomicWrite(directory.resolve("release-notes.json"), state); }
    private <T> T read(String name, Class<T> type) throws IOException {
        Path path = directory.resolve(name);
        return Files.exists(path) ? JSON.readValue(path.toFile(), type) : null;
    }
    private void atomicWrite(Path target, Object value) throws IOException {
        Path temporary = Files.createTempFile(directory, "save-", ".tmp");
        try {
            JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    public static void exportCsv(Path path, List<Result> results, List<Rule> rules) throws IOException {
        exportCsv(path, results, rules, false);
    }

    public static void exportCsv(Path path, List<Result> results, List<Rule> rules, boolean progressOnly) throws IOException {
        exportCsv(path, results, rules, progressOnly, MainlandLocation::classify);
    }
    public static void exportCsv(Path path, List<Result> results, List<Rule> rules, boolean progressOnly,
                                 java.util.function.Function<Event, AreaFilter> classifier) throws IOException {
        List<Rule> exportRules = progressOnly ? new Settings(3, "", rules).progressOnly().rules() : rules;
        try (BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            out.write('\uFEFF');
            writeRow(out, progressOnly
                ? List.of("运单号", "查询状态", "最新时间", "时区", "物流进度", "匹配条件", "错误信息", "查询时间")
                : List.of("运单号", "查询状态", "最新时间", "时区", "最新地点", "最新物流信息", "匹配条件", "错误信息", "查询时间", "国家/地区", "所在区域"));
            for (Result result : results) {
                Event event = result.latest();
                List<String> row = new ArrayList<>(List.of(result.waybillNo(), result.state().label,
                    event == null ? "" : event.eventTime(), event == null ? "" : event.timeZone()));
                if (!progressOnly) row.add(event == null ? "" : event.location());
                row.add(event == null ? "" : event.detail());
                row.add(String.join("；", matchedRules(result, exportRules, classifier)));
                row.add(clean(result.error())); row.add(clean(result.queriedAt()));
                if (!progressOnly) { row.add(event == null ? "" : clean(event.country())); row.add(event == null ? "" : classifier.apply(event).label); }
                writeRow(out, row);
            }
        }
    }

    public static void exportProgressJson(Path path, List<Result> results) throws IOException {
        var output = JSON.createArrayNode();
        for (Result result : results) {
            var node = output.addObject();
            node.put("waybillNo", result.waybillNo()).put("state", result.state().name())
                .put("error", clean(result.error())).put("queriedAt", clean(result.queriedAt()));
            var events = node.putArray("events");
            for (Event event : result.events()) events.addObject()
                .put("eventTime", clean(event.eventTime())).put("timeZone", clean(event.timeZone()))
                .put("eventDetail", event.detail()).put("eventCode", clean(event.eventCode()));
        }
        JSON.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), output);
    }
    private static void writeRow(Writer out, List<String> values) throws IOException {
        List<String> escaped = new ArrayList<>();
        for (String value : values) {
            String safe = clean(value);
            if (!safe.isEmpty() && "=+@-\t\r".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
            escaped.add("\"" + safe.replace("\"", "\"\"") + "\"");
        }
        out.write(String.join(",", escaped) + "\r\n");
    }
}
