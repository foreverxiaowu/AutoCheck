package cn.autocheck;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static cn.autocheck.Models.*;

public final class SfParser {
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public List<Result> parse(List<String> requested, JsonNode root) throws IOException {
        if (root == null || !root.has("code") || root.path("code").asInt(-1) != 0 || !root.path("result").isArray())
            throw new IOException("顺丰响应失败或 result 格式异常");
        Map<String, JsonNode> entries = new HashMap<>();
        Set<String> duplicates = new HashSet<>();
        for (JsonNode entry : root.path("result")) {
            String number = clean(entry.path("waybillNo").asText(""));
            if (entries.put(number, entry) != null) duplicates.add(number);
        }
        List<Result> results = new ArrayList<>();
        for (String number : requested) {
            try {
                JsonNode entry = entries.get(number);
                if (entry == null) throw new IOException("接口未返回此单号，请重试");
                if (duplicates.contains(number)) throw new IOException("接口返回重复单号");
                JsonNode history = entry.get("barNewList");
                if (history == null || (!history.isNull() && !history.isArray())) throw new IOException("barNewList 格式异常");
                Set<Event> events = new LinkedHashSet<>();
                for (JsonNode raw : history) {
                    String remark = clean(raw.path("remark").asText(""));
                    if (remark.isEmpty()) throw new IOException("物流事件缺少 remark");
                    String zone = clean(raw.path("localTimeZone").asText(""));
                    if (zone.isEmpty()) zone = clean(entry.path("timeZone").asText(""));
                    if (zone.isEmpty()) throw new IOException("物流事件缺少时区");
                    String date = clean(raw.path("barScanDt").asText(""));
                    String time = clean(raw.path("barScanTm").asText(""));
                    LocalDateTime local = LocalDateTime.parse(date + "T" + time);
                    Event event = new Event(number, local.format(DISPLAY), zone, "", remark, "",
                        clean(raw.path("opCode").asText("")), "");
                    TrajectoryParser.eventInstant(event);
                    events.add(event);
                }
                List<Event> sorted = events.stream().sorted(Comparator.comparing(TrajectoryParser::eventInstant).reversed()).toList();
                results.add(new Result(number, sorted.isEmpty() ? QueryState.NO_DATA : QueryState.OK, sorted, "", Instant.now().toString()));
            } catch (Exception e) {
                results.add(new Result(number, QueryState.FAILED, List.of(), clean(e.getMessage()), Instant.now().toString()));
            }
        }
        return List.copyOf(results);
    }
}
