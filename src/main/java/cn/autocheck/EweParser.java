package cn.autocheck;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.time.*;
import java.time.format.*;
import java.util.*;
import static cn.autocheck.Models.*;

public final class EweParser {
    private static final DateTimeFormatter INPUT = new DateTimeFormatterBuilder()
        .appendPattern("uuuu-MM-dd HH:mm").optionalStart().appendPattern(":ss").optionalEnd()
        .toFormatter().withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private record TimedEvent(LocalDateTime time, int order, Event event) {}

    public List<Result> parse(List<String> requested, JsonNode root) throws IOException {
        if (root == null || !root.has("Status") || root.path("Status").asInt(-1) != 0
                || !clean(root.path("Error").asText("")).isEmpty() || !root.path("Payload").isArray())
            throw new IOException("EWE 响应失败或 Payload 格式异常");
        Map<String, JsonNode> entries = new HashMap<>();
        Set<String> duplicates = new HashSet<>();
        for (JsonNode entry : root.path("Payload")) {
            String number = clean(entry.path("ConsignmentNo").asText(""));
            if (entries.put(number, entry) != null) duplicates.add(number);
        }
        List<Result> results = new ArrayList<>();
        for (String number : requested) {
            try {
                JsonNode entry = entries.get(number);
                if (entry == null) throw new IOException("接口未返回此单号，请重试");
                if (duplicates.contains(number)) throw new IOException("接口返回重复单号，无法确定轨迹");
                JsonNode history = entry.get("Details");
                if (history == null || (!history.isNull() && !history.isArray())) throw new IOException("Details 格式异常");
                List<TimedEvent> events = new ArrayList<>();
                int order = 0;
                for (JsonNode raw : history) {
                    String message = clean(raw.path("Message").asText(""));
                    if (message.isEmpty()) throw new IOException("物流事件缺少 Message");
                    LocalDateTime time;
                    try { time = LocalDateTime.parse(clean(raw.path("DateString").asText("")), INPUT); }
                    catch (DateTimeException e) { throw new IOException("EWE 轨迹时间无法识别，无法确定最新物流进度", e); }
                    Event event = new Event(number, time.format(DISPLAY), "", clean(raw.path("Place").asText("")),
                        message, "", clean(raw.path("PositionCode").asText("")), "");
                    events.add(new TimedEvent(time, order++, event));
                }
                // The official response is oldest first. For equal minute timestamps keep its last event first.
                events.sort(Comparator.comparing(TimedEvent::time).thenComparingInt(TimedEvent::order).reversed());
                List<Event> sorted = events.stream().map(TimedEvent::event).distinct().toList();
                if (sorted.isEmpty() && !clean(entry.path("Status").asText("")).isEmpty())
                    throw new IOException("返回状态但缺少轨迹明细，请重试");
                results.add(new Result(number, sorted.isEmpty() ? QueryState.NO_DATA : QueryState.OK, sorted, "", Instant.now().toString()));
            } catch (Exception e) {
                results.add(new Result(number, QueryState.FAILED, List.of(), clean(e.getMessage()), Instant.now().toString()));
            }
        }
        return List.copyOf(results);
    }
}
