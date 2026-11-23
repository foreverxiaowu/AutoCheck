package cn.autocheck;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.*;

public final class Models {
    private Models() {}

    public enum QueryState {
        OK("查询成功"), NO_DATA("暂无轨迹"), FAILED("查询失败");
        public final String label;
        QueryState(String label) { this.label = label; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(String waybillNo, String eventTime, String timeZone, String eventLocation,
                        String eventDetail, String originalTracking, String eventCode, String orgName) {
        public String location() { return clean(eventLocation).isEmpty() ? clean(orgName) : clean(eventLocation); }
        public String detail() { return clean(eventDetail).isEmpty() ? clean(originalTracking) : clean(eventDetail); }
        public String description() { return "【" + location() + "】 " + detail(); }
    }

    public record Result(String waybillNo, QueryState state, List<Event> events, String error, String queriedAt) {
        public Event latest() { return events == null || events.isEmpty() ? null : events.get(0); }
    }

    public enum MatchMode { CONTAINS, EXACT }

    public record Rule(boolean enabled, String name, String location, String detail, MatchMode mode) {
        public boolean matches(Event event) {
            if (!enabled || event == null || (clean(location).isEmpty() && clean(detail).isEmpty())) return false;
            return match(event.location(), location) && match(event.detail(), detail);
        }
        private boolean match(String value, String condition) {
            String needle = clean(condition);
            return needle.isEmpty() || (mode == MatchMode.EXACT ? value.equals(needle) : value.contains(needle));
        }
    }

    public record Settings(int intervalSeconds, String timeZoneType, List<Rule> rules) {
        public static Settings defaults() {
            return new Settings(3, "senderView", List.of(
                new Rule(true, "清关中", "中国", "快件正在清关", MatchMode.CONTAINS),
                new Rule(true, "成都签收", "四川省成都市驷马桥", "签收成功", MatchMode.CONTAINS),
                new Rule(true, "海关审核", "海关审核资料", "快件在转运中", MatchMode.CONTAINS)));
        }
    }

    public record Snapshot(int version, List<String> waybills, List<Result> results) {}

    public static String clean(String value) {
        return value == null || value.equalsIgnoreCase("null") ? "" : value.strip();
    }

    public static List<String> matchedRules(Result result, List<Rule> rules) {
        if (result == null || result.state() != QueryState.OK) return List.of();
        return rules.stream().filter(r -> r.matches(result.latest())).map(Rule::name).toList();
    }
}
