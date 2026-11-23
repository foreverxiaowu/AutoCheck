package cn.autocheck;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static cn.autocheck.Models.*;

public final class Track17Parser {
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public List<Result> parse(List<String> requested, JsonNode root) throws IOException {
        if (root == null || !root.path("shipments").isArray()) throw new IOException("17TRACK 响应缺少 shipments 数组");
        if (root.has("meta") && root.path("meta").path("code").asInt() != 200)
            throw new IOException("17TRACK 查询失败：" + root.path("meta").path("code").asText());
        Map<String, JsonNode> shipments = new HashMap<>();
        Set<String> duplicates = new HashSet<>();
        for (JsonNode shipment : root.path("shipments")) {
            String number = text(shipment, "number");
            if (shipments.put(number, shipment) != null) duplicates.add(number);
        }
        List<Result> results = new ArrayList<>();
        for (String number : requested) {
            try {
                JsonNode entry = shipments.get(number);
                if (entry == null) throw new IOException("接口未返回此单号，请重试");
                if (duplicates.contains(number)) throw new IOException("接口返回重复单号，无法确定所属物流公司");
                if (entry.path("code").asInt() != 200) throw new IOException("单号查询失败，code=" + entry.path("code").asText("未知"));
                JsonNode shipment = entry.get("shipment");
                if (shipment == null || (!shipment.isNull() && !shipment.isObject())) throw new IOException("shipment 格式异常");
                Map<String, Event> events = new LinkedHashMap<>();
                add(events, number, shipment.path("latest_event"));
                JsonNode providers = shipment.path("tracking").path("providers");
                if (!providers.isMissingNode() && !providers.isNull() && !providers.isArray()) throw new IOException("providers 格式异常");
                boolean failedSync = false;
                for (JsonNode provider : providers) {
                    String sync = text(provider, "latest_sync_status");
                    if (!sync.isEmpty() && !sync.equals("Success")) failedSync = true;
                    JsonNode history = provider.path("events");
                    if (!history.isMissingNode() && !history.isNull() && !history.isArray()) throw new IOException("events 格式异常");
                    for (JsonNode event : history) add(events, number, event);
                }
                if (events.isEmpty() && failedSync) throw new IOException("物流公司同步未成功，请稍后重试");
                List<Event> sorted = events.values().stream().sorted(Comparator.comparing(TrajectoryParser::eventInstant).reversed()).toList();
                results.add(new Result(number, sorted.isEmpty() ? QueryState.NO_DATA : QueryState.OK, sorted, "", Instant.now().toString()));
            } catch (Exception e) {
                results.add(new Result(number, QueryState.FAILED, List.of(), clean(e.getMessage()), Instant.now().toString()));
            }
        }
        return List.copyOf(results);
    }

    private static void add(Map<String, Event> events, String number, JsonNode raw) throws IOException {
        if (raw.isMissingNode() || raw.isNull()) return;
        if (!raw.isObject()) throw new IOException("物流事件格式异常");
        String description = text(raw, "description");
        if (description.isEmpty()) throw new IOException("物流事件缺少描述");
        OffsetDateTime time;
        String iso = text(raw, "time_iso");
        String utc = text(raw, "time_utc");
        try {
            time = OffsetDateTime.parse(iso.isEmpty() ? utc : iso);
        } catch (RuntimeException e) {
            if (utc.isEmpty()) throw new IOException("物流事件缺少有效时区时间，无法判断最新轨迹", e);
            try { time = OffsetDateTime.parse(utc); }
            catch (RuntimeException invalid) { throw new IOException("物流时间格式无法识别", invalid); }
        }
        JsonNode address = raw.path("address");
        String location = text(raw, "location");
        if (location.isEmpty()) location = String.join(" ", text(address, "country"), text(address, "state"), text(address, "city"), text(address, "street")).strip();
        Event event = new Event(number, time.format(DISPLAY), time.getOffset().getId(), location,
            description, "", text(raw, "sub_status"), "", text(address, "country"), text(address, "state"), text(address, "city"));
        events.putIfAbsent(time.toInstant() + "\n" + location + "\n" + description, event);
    }

    private static String text(JsonNode node, String field) { return clean(node.path(field).asText("")); }
}
