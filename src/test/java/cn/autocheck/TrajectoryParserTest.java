package cn.autocheck;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static cn.autocheck.Models.*;

class TrajectoryParserTest {
    static List<String> sampleWaybills() {
        return List.of("G10433030158", "G10434275624", "G10433399052", "G02959570948", "G10433328053", "G10433327429", "G10433225327", "G10433278875", "G10433327658", "G10433166982");
    }
    static com.fasterxml.jackson.databind.JsonNode sample() throws Exception {
        try (var input = TrajectoryParserTest.class.getResourceAsStream("/trajectory-response.json")) {
            return FilesService.JSON.readTree(input);
        }
    }

    @Test void groupsTheSupplied124EventsIntoTenWaybills() throws Exception {
        List<Result> results = new TrajectoryParser().parse(sampleWaybills(), sample());
        assertEquals(sampleWaybills(), results.stream().map(Result::waybillNo).toList());
        assertEquals(124, results.stream().mapToInt(r -> r.events().size()).sum());
        assertTrue(results.stream().allMatch(r -> r.state() == QueryState.OK));
        Result signed = results.stream().filter(r -> r.waybillNo().equals("G10433166982")).findFirst().orElseThrow();
        assertEquals("2026-11-17 17:39:56", signed.latest().eventTime());
        assertEquals("【四川省成都市驷马桥】 签收成功", signed.latest().description());
        assertEquals(List.of("成都签收"), matchedRules(signed, Settings.defaults().rules()));
    }

    @Test void sortsByInstantAndNeverMatchesAnOlderEvent() throws Exception {
        var root = FilesService.JSON.readTree("""
            {"code":200,"success":true,"data":{"status":"true","detailResponses":[
            {"waybillNo":"TEST01","eventTime":"2026-11-17 10:00:00","timeZone":"GMT+09:00","eventLocation":"中国","eventDetail":"快件正在清关"},
            {"waybillNo":"TEST01","eventTime":"2026-11-17 09:30:00","timeZone":"GMT+08:00","eventLocation":"四川省成都市驷马桥","eventDetail":"签收成功"}]}}
            """);
        Result result = new TrajectoryParser().parse(List.of("TEST01"), root).get(0);
        assertEquals("签收成功", result.latest().detail());
        assertEquals(List.of("成都签收"), matchedRules(result, Settings.defaults().rules()));
    }

    @Test void createsNoDataForMissingWaybillButRejectsFailedAndMalformedPayloads() throws Exception {
        var root = FilesService.JSON.readTree("{\"code\":200,\"data\":{\"detailResponses\":[]}}");
        assertEquals(QueryState.NO_DATA, new TrajectoryParser().parse(List.of("EMPTY1"), root).get(0).state());
        assertThrows(Exception.class, () -> new TrajectoryParser().parse(List.of("EMPTY1"), FilesService.JSON.readTree("{\"code\":200,\"data\":{\"status\":\"false\",\"detailResponses\":[]}}")));
        assertThrows(Exception.class, () -> new TrajectoryParser().parse(List.of("EMPTY1"), FilesService.JSON.readTree("{\"code\":200,\"data\":{\"detailResponses\":{}}}")));
        assertThrows(Exception.class, () -> new TrajectoryParser().parse(List.of("EMPTY1"), FilesService.JSON.readTree("{\"code\":200,\"data\":{\"detailResponses\":[{\"eventTime\":\"bad\"}]}}")));
    }

    @Test void combinesFieldsWithAndAndRulesWithOr() {
        Event event = new Event("TEST1", "", "", "中国海关", "快件正在清关", "", "", "");
        assertTrue(new Rule(true, "a", "中国", "清关", MatchMode.CONTAINS).matches(event));
        assertFalse(new Rule(true, "a", "中国", "清关", MatchMode.EXACT).matches(event));
        assertFalse(new Rule(true, "a", "四川", "清关", MatchMode.CONTAINS).matches(event));
        assertTrue(new Rule(true, "a", "", "快件正在清关", MatchMode.EXACT).matches(event));
        assertFalse(new Rule(true, "a", "", "", MatchMode.CONTAINS).matches(event));
        assertFalse(new Rule(false, "a", "", "清关", MatchMode.CONTAINS).matches(event));
    }
}
