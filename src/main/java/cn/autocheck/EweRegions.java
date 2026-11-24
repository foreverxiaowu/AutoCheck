package cn.autocheck;

import java.util.*;
import java.util.regex.Pattern;
import static cn.autocheck.Models.*;

/** User-maintained hints for EWE's free-text Place; unknown/conflicting locations stay unknown. */
public record EweRegions(List<String> domestic, List<String> overseas) {
    public EweRegions {
        domestic = normalize(domestic);
        overseas = normalize(overseas);
        Set<String> both = new HashSet<>(domestic);
        both.retainAll(overseas);
        if (!both.isEmpty()) throw new IllegalArgumentException("国内和境外不能配置同一关键词：" + String.join("、", both));
    }
    private static List<String> normalize(List<String> words) {
        if (words == null) throw new IllegalArgumentException("地区关键词列表不能为空");
        return words.stream().map(Models::clean).filter(s -> !s.isEmpty()).map(s -> s.toUpperCase(Locale.ROOT)).distinct().toList();
    }
    public static List<String> parseWords(String input) {
        return Arrays.asList(input.split("[\\r\\n,，;；]+"));
    }
    public AreaFilter classify(Event event) {
        if (event == null || event.location().isEmpty()) return AreaFilter.UNKNOWN;
        String place = event.location().toUpperCase(Locale.ROOT);
        boolean in = domestic.stream().anyMatch(word -> contains(place, word));
        boolean out = overseas.stream().anyMatch(word -> contains(place, word));
        if (in == out) return AreaFilter.UNKNOWN;
        return in ? AreaFilter.MAINLAND : AreaFilter.OUTSIDE;
    }
    private static boolean contains(String place, String word) {
        // Avoid matching country abbreviations or English city names inside unrelated words.
        if (word.matches("[A-Z0-9 .'-]+"))
            return Pattern.compile("(?<![A-Z0-9])" + Pattern.quote(word) + "(?![A-Z0-9])").matcher(place).find();
        return place.contains(word);
    }
    public static EweRegions defaults() {
        return new EweRegions(parseWords("中国,CHINA,CN,中国大陆,中国境内,北京,天津,上海,重庆,河北,山西,辽宁,吉林,黑龙江,江苏,浙江,安徽,福建,江西,山东,河南,湖北,湖南,广东,海南,四川,贵州,云南,陕西,甘肃,青海,内蒙古,广西,西藏,宁夏,新疆,成都,苏州,无锡,广州,深圳,杭州,南京,武汉,郑州,西安,青岛,厦门,福州,宁波,东莞,佛山,长沙,CHENGDU,SUZHOU,WUXI,SHANGHAI,BEIJING,CHONGQING,SHENZHEN,GUANGZHOU,MAINLAND CHINA"),
            parseWords("德国,法兰克福,GERMANY,DEUTSCHLAND,FRANKFURT,DE,澳大利亚,澳洲,悉尼,墨尔本,布里斯班,AUSTRALIA,SYDNEY,MELBOURNE,BRISBANE,AU,美国,洛杉矶,纽约,UNITED STATES,USA,US,LOS ANGELES,NEW YORK,日本,东京,大阪,JAPAN,TOKYO,OSAKA,JP,英国,伦敦,UNITED KINGDOM,LONDON,UK,GB,法国,巴黎,FRANCE,PARIS,FR,加拿大,CANADA,CA,新西兰,NEW ZEALAND,NZ,新加坡,SINGAPORE,SG,韩国,KOREA,KR,香港,澳门,澳門,台湾,臺灣,台灣,台北,臺北,HONG KONG,MACAU,MACAO,TAIWAN,HK,MO,TW,老挝,LAOS,LA,泰国,THAILAND,TH,越南,VIETNAM,VN"));
    }
}
