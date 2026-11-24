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
                        String eventDetail, String originalTracking, String eventCode, String orgName,
                        String country, String region, String city) {
        public Event(String waybillNo, String eventTime, String timeZone, String eventLocation,
                     String eventDetail, String originalTracking, String eventCode, String orgName) {
            this(waybillNo, eventTime, timeZone, eventLocation, eventDetail, originalTracking, eventCode, orgName, "", "", "");
        }
        public String location() { return clean(eventLocation).isEmpty() ? clean(orgName) : clean(eventLocation); }
        public String detail() { return clean(eventDetail).isEmpty() ? clean(originalTracking) : clean(eventDetail); }
        public String description() { return "【" + location() + "】 " + detail(); }
    }

    public record Result(String waybillNo, QueryState state, List<Event> events, String error, String queriedAt) {
        public Event latest() { return events == null || events.isEmpty() ? null : events.get(0); }
    }

    public enum MatchMode { CONTAINS, EXACT }

    public enum AreaFilter {
        ANY("不限"), MAINLAND("中国大陆"), OUTSIDE("大陆以外"), UNKNOWN("地址未知");
        public final String label;
        AreaFilter(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public record Rule(boolean enabled, String name, String location, String detail, MatchMode mode, AreaFilter area) {
        public Rule(boolean enabled, String name, String location, String detail, MatchMode mode) {
            this(enabled, name, location, detail, mode, AreaFilter.ANY);
        }
        public Rule { if (area == null) area = AreaFilter.ANY; }
        public boolean matches(Event event) {
            return matches(event, MainlandLocation::classify);
        }
        public boolean matches(Event event, java.util.function.Function<Event, AreaFilter> classifier) {
            if (!enabled || event == null || (area == AreaFilter.ANY && clean(location).isEmpty() && clean(detail).isEmpty())) return false;
            return (area == AreaFilter.ANY || classifier.apply(event) == area)
                && match(event.location(), location) && match(event.detail(), detail);
        }
        private boolean match(String value, String condition) {
            String needle = clean(condition);
            return needle.isEmpty() || (mode == MatchMode.EXACT ? value.equals(needle) : value.contains(needle));
        }
    }

    public record Settings(int intervalSeconds, String timeZoneType, List<Rule> rules) {
        public static Settings fpxDefaults() {
            return new Settings(3, "", List.of(
                new Rule(true, "清关中", "", "正在清关中", MatchMode.CONTAINS),
                new Rule(true, "清关完成", "", "清关完成", MatchMode.CONTAINS),
                new Rule(true, "派送中", "", "正在派送", MatchMode.CONTAINS),
                new Rule(true, "已代收", "", "已代收", MatchMode.CONTAINS)));
        }
        public static Settings eweDefaults() {
            return new Settings(3, "", List.of(
                new Rule(true, "包裹清关中", "", "包裹清关中", MatchMode.CONTAINS),
                new Rule(true, "已到达中国", "", "干线航班已到达", MatchMode.CONTAINS),
                new Rule(true, "已领取", "", "快件已领取", MatchMode.CONTAINS)));
        }
        public static Settings sfDefaults() {
            return new Settings(3, "", List.of(
                new Rule(true, "清关进行中", "", "清关进行中", MatchMode.CONTAINS),
                new Rule(true, "海关查验", "", "海关查验", MatchMode.CONTAINS)));
        }
        public static Settings track17Defaults() {
            return new Settings(3, "senderView", List.of(
                new Rule(true, "清关持续中", "", "清关持续中", MatchMode.CONTAINS),
                new Rule(true, "快件已开始清关", "", "快件已开始清关", MatchMode.CONTAINS),
                new Rule(true, "成都青龙场", "", "成都青龙场", MatchMode.CONTAINS)));
        }
        /** Migrate previous 17TRACK settings so hidden address constraints cannot affect progress matching. */
        public Settings progressOnly() {
            List<Rule> progressRules = rules.stream()
                .filter(r -> !clean(r.detail()).isEmpty() || !clean(r.location()).isEmpty())
                .map(r -> new Rule(r.enabled(), r.name(), "", clean(r.detail()).isEmpty() ? r.location() : r.detail(), r.mode()))
                .toList();
            return new Settings(intervalSeconds, timeZoneType, progressRules);
        }
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
        return matchedRules(result, rules, MainlandLocation::classify);
    }
    public static List<String> matchedRules(Result result, List<Rule> rules, java.util.function.Function<Event, AreaFilter> classifier) {
        if (result == null || result.state() != QueryState.OK) return List.of();
        return rules.stream().filter(r -> r.matches(result.latest(), classifier)).map(Rule::name).toList();
    }
}
