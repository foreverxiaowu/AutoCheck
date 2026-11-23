package cn.autocheck;

import java.util.*;
import static cn.autocheck.Models.*;

/** Classifies the latest event's address, never the carrier's or recipient's country. */
public final class MainlandLocation {
    private MainlandLocation() {}
    private static final Set<String> CHINA = Set.of("CN", "CHN", "CHINA", "中国", "中國", "中国大陆", "中國大陸", "MAINLAND CHINA");
    private static final Set<String> OUTSIDE = Set.of("HK", "HKG", "MO", "MAC", "TW", "TWN");
    private static final List<String> NON_MAINLAND = List.of("香港", "澳门", "澳門", "台湾", "臺灣", "台灣", "台北", "臺北", "高雄", "新北", "HONG KONG", "HONGKONG", "MACAO", "MACAU", "TAIWAN", "TAIPEI");
    private static final List<String> MAINLAND_NAMES = List.of("北京", "天津", "上海", "重庆", "重慶", "河北", "山西", "辽宁", "遼寧", "吉林", "黑龙江", "黑龍江", "江苏", "江蘇", "浙江", "安徽", "福建", "江西", "山东", "山東", "河南", "湖北", "湖南", "广东", "廣東", "海南", "四川", "贵州", "貴州", "云南", "雲南", "陕西", "陝西", "甘肃", "甘肅", "青海", "内蒙古", "內蒙古", "广西", "廣西", "西藏", "宁夏", "寧夏", "新疆", "成都", "广州", "廣州", "深圳", "杭州", "武汉", "武漢", "南京", "苏州", "蘇州", "郑州", "鄭州", "西安", "青岛", "青島", "厦门", "廈門", "福州", "宁波", "寧波", "东莞", "東莞", "佛山", "长沙", "長沙");

    public static AreaFilter classify(Event event) {
        if (event == null) return AreaFilter.UNKNOWN;
        String country = clean(event.country()).toUpperCase(Locale.ROOT);
        String address = String.join(" ", clean(event.region()), clean(event.city()), event.location()).toUpperCase(Locale.ROOT);
        if (OUTSIDE.contains(country) || NON_MAINLAND.stream().anyMatch(address::contains)) return AreaFilter.OUTSIDE;
        if (CHINA.contains(country)) return AreaFilter.MAINLAND;
        // Only explicit country codes/names are decisive; placeholders are not foreign countries.
        if (Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA2).contains(country)
                || Set.of("JAPAN", "日本", "USA", "UNITED STATES", "美国", "韓国", "韩国", "KOREA").contains(country)) return AreaFilter.OUTSIDE;
        if (!country.isEmpty() && !Set.of("UNKNOWN", "UN", "ZZ", "--", "N/A").contains(country)) return AreaFilter.UNKNOWN;
        if (List.of("日本", "大阪", "东京", "東京", "JAPAN", "OSAKA", "TOKYO", "美国", "美國", "UNITED STATES", "韩国", "韓國", "KOREA", "新加坡", "SINGAPORE").stream().anyMatch(address::contains)) return AreaFilter.OUTSIDE;
        if (MAINLAND_NAMES.stream().anyMatch(address::contains) || address.contains("中国") || address.contains("中國") || address.matches(".*\\b(CHINA|CHENGDU|SHANGHAI|BEIJING|SHENZHEN|GUANGZHOU)\\b.*")) return AreaFilter.MAINLAND;
        return AreaFilter.UNKNOWN;
    }
}
