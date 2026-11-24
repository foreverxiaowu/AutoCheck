package cn.autocheck;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.time.*;
import java.time.format.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static cn.autocheck.Models.*;

public final class FpxParser {
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
    private record TimedEvent(Instant instant, Event event) {}

    public List<Result> parse(List<String> requested, JsonNode root) throws IOException {
        if (root == null || root.path("result").asInt(-1) != 1 || !root.path("data").isArray()) throw new IOException("4PX 响应失败或 data 格式异常");
        Map<String, JsonNode> entries = new HashMap<>();
        Set<String> duplicates = new HashSet<>();
        for (JsonNode entry : root.path("data")) {
            String number = clean(entry.path("queryCode").asText(""));
            if (entries.put(number, entry) != null) duplicates.add(number);
        }
        List<Result> results = new ArrayList<>();
        for (String number : requested) {
            try {
                JsonNode entry = entries.get(number);
                if (entry == null) throw new IOException("接口未返回此查询单号，请重试");
                if (duplicates.contains(number)) throw new IOException("接口返回重复查询单号，无法确定轨迹");
                JsonNode history = entry.get("tracks");
                if (history == null || (!history.isNull() && !history.isArray())) throw new IOException("tracks 格式异常");
                List<TimedEvent> events = new ArrayList<>();
                for (JsonNode raw : history) {
                    String description = clean(raw.path("tkDesc").asText(""));
                    if (description.isEmpty()) throw new IOException("物流事件缺少 tkDesc");
                    OffsetDateTime stamp;
                    try { stamp = OffsetDateTime.parse(clean(raw.path("tkDate").asText("")).replaceFirst("([+-]\\d{2})(\\d{2})$", "$1:$2")); }
                    catch (DateTimeException e) { throw new IOException("4PX 轨迹时间无法识别，无法确定最新物流进度", e); }
                    Instant instant = stamp.toInstant();
                    ZonedDateTime displayed = stamp.toZonedDateTime();
                    String zone = clean(raw.path("tkTimezone").asText(""));
                    if (!zone.isEmpty()) {
                        try { displayed = instant.atZone(ZoneId.of(zone)); }
                        catch (DateTimeException e) { throw new IOException("4PX 轨迹时区无法识别：" + zone, e); }
                    } else {
                        // Recover the offset from the API's paired absolute and display times, not geography.
                        // If the display text is missing/invalid, keep the authoritative tkDate's own offset.
                        try {
                            LocalDateTime local = LocalDateTime.parse(clean(raw.path("tkDateStr").asText("")), LOCAL);
                            long difference = Duration.between(instant.truncatedTo(ChronoUnit.SECONDS), local.toInstant(ZoneOffset.UTC)).getSeconds();
                            if (Math.abs(difference) <= 18 * 3600 && difference % 60 == 0)
                                displayed = instant.atZone(ZoneOffset.ofTotalSeconds((int) difference));
                        } catch (DateTimeException ignored) {}
                    }
                    Event event = new Event(number, displayed.format(LOCAL), displayed.getOffset().getId(), "", description,
                        "", clean(raw.path("tkCode").asText("")), "");
                    events.add(new TimedEvent(instant, event));
                }
                // Sort on absolute time, retaining the response order for equal timestamps.
                events.sort(Comparator.comparing(TimedEvent::instant).reversed());
                List<Event> sorted = events.stream().map(TimedEvent::event).distinct().toList();
                results.add(new Result(number, sorted.isEmpty() ? QueryState.NO_DATA : QueryState.OK, sorted, "", Instant.now().toString()));
            } catch (Exception e) {
                results.add(new Result(number, QueryState.FAILED, List.of(), clean(e.getMessage()), Instant.now().toString()));
            }
        }
        return List.copyOf(results);
    }
}
