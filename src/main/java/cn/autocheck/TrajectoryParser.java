package cn.autocheck;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static cn.autocheck.Models.*;

/** The API returns a flat event list across all requested waybills. */
public final class TrajectoryParser {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public List<Result> parse(List<String> requested, JsonNode root) throws IOException {
        JsonNode data = root.path("data");
        if (root.path("code").asInt() != 200 || isFalse(root.get("success"))
                || !data.isObject() || isFalse(data.get("success")) || isFalse(data.get("status"))) {
            throw new IOException("接口返回失败：" + message(root));
        }
        JsonNode details = data.get("detailResponses");
        // A missing/null list is what the official UI treats as no records; an object is a protocol error.
        if (details != null && !details.isNull() && !details.isArray()) throw new IOException("物流轨迹格式异常，未返回数组");
        Map<String, List<Event>> grouped = new LinkedHashMap<>();
        requested.forEach(no -> grouped.put(no, new ArrayList<>()));
        if (details != null && details.isArray()) {
            for (JsonNode node : details) {
                if (!node.isObject()) throw new IOException("物流事件格式异常");
                String no = clean(node.path("waybillNo").asText(""));
                if (no.isEmpty()) no = clean(node.path("referenceChangeNo").asText(""));
                if (no.isEmpty()) throw new IOException("物流事件缺少运单号，无法安全归属查询结果");
                if (!grouped.containsKey(no)) continue;
                Event event = new Event(no, field(node,"eventTime"), field(node,"timeZone"),
                    field(node,"eventLocation"), field(node,"eventDetail"), field(node,"originalTracking"),
                    field(node,"eventCode"), field(node,"orgName"));
                if (clean(event.eventTime()).isEmpty() || event.detail().isEmpty())
                    throw new IOException("物流事件缺少时间或状态，无法确定最新轨迹");
                eventInstant(event); // Reject invalid timestamps rather than silently choosing the wrong latest event.
                if (!grouped.get(no).contains(event)) grouped.get(no).add(event);
            }
        }
        List<Result> results = new ArrayList<>();
        String now = Instant.now().toString();
        for (var entry : grouped.entrySet()) {
            entry.getValue().sort(Comparator.comparing(TrajectoryParser::eventInstant).reversed());
            results.add(new Result(entry.getKey(), entry.getValue().isEmpty() ? QueryState.NO_DATA : QueryState.OK,
                List.copyOf(entry.getValue()), "", now));
        }
        return results;
    }

    static Instant eventInstant(Event event) {
        try {
            LocalDateTime local = LocalDateTime.parse(event.eventTime(), FORMAT);
            String zone = clean(event.timeZone());
            return local.atZone(zone.isEmpty() ? ZoneOffset.UTC : ZoneId.of(zone)).toInstant();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("轨迹时间或时区格式无法识别：" + event.eventTime() + " / " + event.timeZone(), e);
        }
    }

    private static String field(JsonNode node, String field) { return clean(node.path(field).asText("")); }
    private static boolean isFalse(JsonNode value) { return value != null && value.asText().equalsIgnoreCase("false"); }
    public static String message(JsonNode root) {
        for (String key : List.of("message", "msg")) {
            String text = clean(root.path(key).asText(""));
            if (!text.isEmpty()) return text.substring(0, Math.min(250, text.length()));
        }
        String text = clean(root.path("data").path("message").asText(""));
        return text.isEmpty() ? "code=" + root.path("code").asText("未知") : text;
    }
}
