package cn.autocheck;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static cn.autocheck.Models.*;
import static org.junit.jupiter.api.Assertions.*;

class EweRegionsTest {
    @TempDir Path directory;
    private Event event(String place, String message) { return new Event("TEST001", "2026-11-24 12:00:00", "", place, message, "", "", ""); }
    @Test void classifiesCurrentPlaceAndDoesNotInferLocationFromProgressOrUnknownWords() {
        EweRegions regions = EweRegions.defaults();
        for (String place : List.of("成都市 金牛区", "江苏省 苏州市", "重庆", "Chengdu SC China", "中国"))
            assertEquals(AreaFilter.MAINLAND, regions.classify(event(place,"")),place);
        for (String place : List.of("Frankfurt HE Germany", "法兰克福", "Sydney NSW", "香港", "Taiwan", "HK"))
            assertEquals(AreaFilter.OUTSIDE, regions.classify(event(place,"")),place);
        for (String place : List.of("仓库001", "", "ABCDE", "中国香港", "Frankfurt / 成都"))
            assertEquals(AreaFilter.UNKNOWN, regions.classify(event(place,"清关中，中国，成都")),place);
        assertEquals(AreaFilter.UNKNOWN,regions.classify(null));
        Result history=new Result("TEST001",QueryState.OK,List.of(event("Frankfurt HE Germany","运输中"),event("成都市","清关中")),"","now");
        Rule abroad=new Rule(true,"当前境外","","",MatchMode.CONTAINS,AreaFilter.OUTSIDE);
        Rule domestic=new Rule(true,"当前国内","","",MatchMode.CONTAINS,AreaFilter.MAINLAND);
        assertEquals(List.of("当前境外"),matchedRules(history,List.of(abroad,domestic),regions::classify));
    }
    @Test void userKeywordsPersistAndApplyToExistingResultsRulesAndExports() throws Exception {
        EweRegions configured = new EweRegions(List.of("成都","TESTCITY"),List.of("海外仓A"));
        FilesService store = new FilesService(directory);
        store.saveEweRegions(configured);
        assertEquals(configured, store.loadEweRegions());
        Result result = new Result("TEST001",QueryState.OK,List.of(event("TestCity hub","清关中")),"", "now");
        Rule domestic = new Rule(true,"国内清关","","清关中",MatchMode.CONTAINS,AreaFilter.MAINLAND);
        assertTrue(matchedRules(result,List.of(domestic),EweRegions.defaults()::classify).isEmpty());
        assertEquals(List.of("国内清关"),matchedRules(result,List.of(domestic),configured::classify));
        Path csv=directory.resolve("export.csv");
        FilesService.exportCsv(csv,List.of(result),List.of(domestic),false,configured::classify);
        String text=Files.readString(csv);
        assertTrue(text.contains("国内清关")); assertTrue(text.contains("中国大陆"));
        Rule city = new Rule(true,"成都清关","成都","清关中",MatchMode.CONTAINS,AreaFilter.MAINLAND);
        assertTrue(city.matches(event("成都市 金牛区","清关中"),configured::classify));
        assertFalse(city.matches(event("成都 海外仓A","清关中"),configured::classify));
        store.saveSettings(new Settings(3,"",List.of(city)));
        assertEquals(city,store.loadSettings().rules().get(0));
    }
    @Test void configurationNormalizesWordsRejectsConflictsAndPermitsUnknownOnly() {
        EweRegions regions=new EweRegions(EweRegions.parseWords("成都,Chengdu\nCHENGDU；重庆"),List.of("Germany"));
        assertEquals(List.of("成都","CHENGDU","重庆"),regions.domestic());
        assertThrows(IllegalArgumentException.class,()->new EweRegions(List.of("chengdu"),List.of("CHENGDU")));
        assertEquals(AreaFilter.UNKNOWN,new EweRegions(List.of(),List.of()).classify(event("成都","")));
    }
}
