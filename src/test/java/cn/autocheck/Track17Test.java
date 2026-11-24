package cn.autocheck;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static cn.autocheck.Models.*;
import static cn.autocheck.TrackingGateway.*;
import static org.junit.jupiter.api.Assertions.*;

class Track17Test {
    @TempDir Path directory;
    static final List<String> NUMBERS = List.of("77122503283603", "77122507021947");
    static JsonNode sample() throws Exception {
        try (var input = Track17Test.class.getResourceAsStream("/17track-response.json")) { return FilesService.JSON.readTree(input); }
    }

    @Test void parsesSuppliedShipmentsWithoutConfusingTheCarrierCountryWithParcelLocation() throws Exception {
        List<Result> results = new Track17Parser().parse(NUMBERS, sample());
        assertEquals(NUMBERS, results.stream().map(Result::waybillNo).toList());
        assertTrue(results.stream().allMatch(r -> r.state() == QueryState.OK));
        assertEquals(List.of(3, 3), results.stream().map(r -> r.events().size()).toList());
        assertEquals("2026-10-07 15:43:27", results.get(0).latest().eventTime());
        assertEquals("JP", results.get(0).latest().country());
        assertEquals(AreaFilter.OUTSIDE, MainlandLocation.classify(results.get(0).latest()));
        assertTrue(matchedRules(results.get(0), Settings.track17Defaults().rules()).isEmpty());
        FilesService store = new FilesService(directory);
        Snapshot saved = new Snapshot(1, NUMBERS, results);
        store.saveSnapshot(saved); assertEquals(saved, store.loadSnapshot());
    }

    @Test void comparesRealInstantsAcrossProvidersAndUsesOnlyLatestEventForRules() throws Exception {
        ObjectNode root = (ObjectNode) sample();
        ObjectNode shipment = (ObjectNode) root.path("shipments").get(0).path("shipment");
        ObjectNode latest = (ObjectNode) shipment.path("latest_event");
        latest.put("time_iso", "2026-10-07T15:43:27+09:00");
        latest.put("description", "清关持续中"); latest.put("location", "成都青龙场");
        ((ObjectNode) latest.path("address")).put("country", "CN").put("city", "成都");
        Result result = new Track17Parser().parse(NUMBERS, root).get(0);
        assertEquals("JP", result.latest().country());
        assertTrue(matchedRules(result, Settings.track17Defaults().rules()).isEmpty());
        latest.put("time_iso", "2026-10-08T15:43:27+08:00");
        result = new Track17Parser().parse(NUMBERS, root).get(0);
        assertEquals(List.of("清关持续中"), matchedRules(result, Settings.track17Defaults().rules()));
    }

    @Test void distinguishesMissingProcessingFailedEmptyAndMalformedShipmentsIndividually() throws Exception {
        JsonNode root = FilesService.JSON.readTree("""
            {"meta":{"code":200},"shipments":[
            {"number":"EMPTY1","code":200,"shipment":null},
            {"number":"WAIT01","code":100},
            {"number":"FAIL01","code":404},
            {"number":"BAD001","code":200,"shipment":{"latest_event":{"description":"bad","time_iso":"invalid"}}}]}
            """);
        var results = new Track17Parser().parse(List.of("EMPTY1", "WAIT01", "FAIL01", "BAD001", "MISS01"), root);
        assertEquals(List.of(QueryState.NO_DATA, QueryState.FAILED, QueryState.FAILED, QueryState.FAILED, QueryState.FAILED), results.stream().map(Result::state).toList());
        assertThrows(Exception.class, () -> new Track17Parser().parse(NUMBERS, FilesService.JSON.readTree("{\"shipments\":{}}")));
        assertThrows(Exception.class, () -> new Track17Parser().parse(NUMBERS, FilesService.JSON.readTree("{\"meta\":{\"code\":-14},\"shipments\":[]}")));
    }

    @Test void mainlandClassificationExcludesHongKongMacaoTaiwanAndUnknownAddresses() {
        assertEquals(AreaFilter.MAINLAND, area("CN", "四川", "成都", "成都青龙场"));
        assertEquals(AreaFilter.OUTSIDE, area("CN", "香港", "", ""));
        assertEquals(AreaFilter.OUTSIDE, area("CN", "", "台北", ""));
        for (String country : List.of("HK", "MO", "TW", "JP", "US", "DE")) assertEquals(AreaFilter.OUTSIDE, area(country, "", "", ""));
        assertEquals(AreaFilter.OUTSIDE, area("JP", "", "", "成都仓日本分站"));
        assertEquals(AreaFilter.MAINLAND, area("", "", "成都", ""));
        assertEquals(AreaFilter.UNKNOWN, area("", "", "", "仓库01"));
        assertEquals(AreaFilter.UNKNOWN, area("UNKNOWN", "", "", ""));
        assertEquals(AreaFilter.UNKNOWN, MainlandLocation.classify(null));
    }

    private AreaFilter area(String country, String region, String city, String location) {
        return MainlandLocation.classify(new Event("TEST1", "", "", location, "", "", "", "", country, region, city));
    }

    @Test void areaAndTextConditionsAreCombinedAndOldSettingsRemainReadable() throws Exception {
        Event event = new Event("TEST1", "", "", "成都青龙场", "快件已开始清关", "", "", "", "CN", "四川", "成都");
        assertTrue(new Rule(true, "test", "", "快件已开始清关", MatchMode.EXACT, AreaFilter.MAINLAND).matches(event));
        assertFalse(new Rule(true, "test", "", "快件已开始清关", MatchMode.EXACT, AreaFilter.OUTSIDE).matches(event));
        Rule legacy = FilesService.JSON.readValue("{\"enabled\":true,\"name\":\"old\",\"location\":\"成都\",\"detail\":\"\",\"mode\":\"CONTAINS\"}", Rule.class);
        assertEquals(AreaFilter.ANY, legacy.area()); assertTrue(legacy.matches(event));
        Event legacyEvent = FilesService.JSON.readValue("{\"waybillNo\":\"TEST1\",\"eventLocation\":\"成都\"}", Event.class);
        assertEquals(AreaFilter.MAINLAND, MainlandLocation.classify(legacyEvent));
    }

    @Test void separatesProviderAndCarrierProgressAndPersistsConfiguration() throws Exception {
        var config = new Track17Config(List.of(new Track17Config.Carrier("中通国际", 190175), new Track17Config.Carrier("测试公司", 12345)), 12345);
        config.save(directory); assertEquals(config, Track17Config.load(directory));
        FilesService yto = new FilesService(directory);
        FilesService a = new FilesService(Track17Config.progressDirectory(directory, 190175));
        FilesService b = new FilesService(Track17Config.progressDirectory(directory, 12345));
        yto.saveSnapshot(new Snapshot(1, List.of("YTO001"), List.of()));
        a.saveSnapshot(new Snapshot(1, NUMBERS, List.of()));
        assertNull(b.loadSnapshot());
        assertEquals(List.of("YTO001"), yto.loadSnapshot().waybills());
        assertEquals(NUMBERS, a.loadSnapshot().waybills());
        assertThrows(IllegalArgumentException.class, () -> new Track17Config(List.of(new Track17Config.Carrier("a", 1), new Track17Config.Carrier("b", 1)), 1));
        assertThrows(IllegalArgumentException.class, () -> new Track17Config(List.of(new Track17Config.Carrier("", 1)), 1));
    }

    @Test void upgradesExistingCarriersOnceWithoutOverwritingChoicesOrRestoringDeletedPresets() throws Exception {
        Path configPath = directory.resolve("17track/carriers.json");
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, """
            {"carriers":[{"name":"自定义公司","code":12345},{"name":"我的邮政","code":3011}],"selectedCode":12345}
            """);
        Track17Config upgraded = Track17Config.load(directory);
        assertEquals(12345, upgraded.selectedCode());
        assertEquals(List.of(new Track17Config.Carrier("自定义公司", 12345),
            new Track17Config.Carrier("我的邮政", 3011), new Track17Config.Carrier("递四方", 190094)), upgraded.carriers());
        assertEquals(upgraded, Track17Config.load(directory));

        Track17Config edited = new Track17Config(List.of(upgraded.carriers().get(0)), 12345);
        edited.save(directory);
        assertEquals(edited, Track17Config.load(directory), "Presets deleted after upgrading must stay deleted");
    }

    @Test void batchesFortyAndRetriesManualVerificationWithoutSavingIncompleteBatch() throws Exception {
        List<String> numbers = java.util.stream.IntStream.range(0, 81).mapToObj(i -> "TEST" + i).toList();
        List<List<String>> calls = new ArrayList<>(); List<Result> saved = new ArrayList<>();
        TrackingGateway gateway = new TrackingGateway() {
            public int batchSize() { return 40; }
            public Reply query(List<String> batch, String zone, String code) {
                calls.add(batch);
                if (calls.size() == 1) return new Reply(Kind.BROWSER_VERIFICATION, null, "", "verify");
                ObjectNode root = FilesService.JSON.createObjectNode(); var shipments = root.putArray("shipments");
                batch.forEach(no -> shipments.addObject().put("number", no).put("code", 200).putNull("shipment"));
                return new Reply(Kind.SUCCESS, root, "", "");
            }
            public List<Result> parse(List<String> batch, JsonNode root) throws Exception { return new Track17Parser().parse(batch, root); }
            public void close() {}
        };
        new BatchRunner().run(numbers, new Settings(1, "senderView", List.of()), gateway, new BatchRunner.Control(), new BatchRunner.Listener() {
            public void status(String text) {}
            public BatchRunner.Answer verify(Reply reply) { assertTrue(saved.isEmpty()); return new BatchRunner.Answer("", false); }
            public void completed(List<Result> batch) { saved.addAll(batch); }
        });
        assertEquals(List.of(40, 40, 40, 1), calls.stream().map(List::size).toList());
        assertEquals(calls.get(0), calls.get(1));
        assertEquals(numbers, saved.stream().map(Result::waybillNo).toList());
    }

    @Test void identifiesVerificationAndMatchesOnlyTheSelectedCarrierAndBatch() {
        for (int code : List.of(-8, -11, -13, -14)) assertEquals(Kind.BROWSER_VERIFICATION, Track17Gateway.decode(200, "{\"meta\":{\"code\":" + code + "},\"shipments\":[]}").kind());
        assertEquals(Kind.TRANSIENT_ERROR, Track17Gateway.decode(503, "").kind());
        assertEquals(Kind.BROWSER_VERIFICATION, Track17Gateway.decode(403, "").kind());
        assertEquals(Kind.ERROR, Track17Gateway.decode(200, "{}").kind());
        assertEquals(Kind.SUCCESS, Track17Gateway.decode(200, "{\"meta\":{\"code\":200},\"shipments\":[]}").kind());
        String body = "{\"data\":[{\"num\":\"77122503283603\",\"fc\":190175,\"sc\":0}]}";
        assertTrue(Track17Gateway.belongsToBatch(body, NUMBERS, 190175));
        assertFalse(Track17Gateway.belongsToBatch(body, NUMBERS, 12345));
        assertFalse(Track17Gateway.belongsToBatch(body, List.of("OTHER1"), 190175));
        assertFalse(Track17Gateway.belongsToBatch("{\"data\":[]}", NUMBERS, 190175));
    }

    @Test void migratesOldAddressRulesAndExportsOnlyProgress() throws Exception {
        Settings old = new Settings(7, "senderView", List.of(
            new Rule(true, "位于中国大陆", "", "", MatchMode.CONTAINS, AreaFilter.MAINLAND),
            new Rule(true, "清关持续中", "中国", "清关持续中", MatchMode.CONTAINS, AreaFilter.MAINLAND),
            new Rule(true, "成都青龙场", "成都青龙场", "", MatchMode.CONTAINS)));
        Settings migrated = old.progressOnly();
        assertEquals(7, migrated.intervalSeconds());
        assertEquals(List.of("清关持续中", "成都青龙场"), migrated.rules().stream().map(Rule::name).toList());
        assertTrue(migrated.rules().stream().allMatch(r -> r.location().isEmpty() && r.area() == AreaFilter.ANY));
        assertEquals(migrated, migrated.progressOnly());
        Event event = new Event("TEST1", "2026-11-23 12:00:00", "+08:00", "位置专用字段", "成都青龙场，清关持续中", "", "", "", "JP", "", "");
        Result result = new Result("TEST1", QueryState.OK, List.of(event), "", "now");
        assertEquals(List.of("清关持续中", "成都青龙场"), matchedRules(result, migrated.rules()));
        Path csv = directory.resolve("progress.csv"), json = directory.resolve("progress.json");
        FilesService.exportCsv(csv, List.of(result), old.rules(), true);
        FilesService.exportProgressJson(json, List.of(result));
        String exported = Files.readString(csv);
        assertTrue(exported.contains("成都青龙场，清关持续中"));
        assertFalse(exported.contains("位置专用字段")); assertFalse(exported.contains("最新地点")); assertFalse(exported.contains("所在区域"));
        JsonNode saved = FilesService.JSON.readTree(json.toFile()).get(0).path("events").get(0);
        assertEquals(event.detail(), saved.path("eventDetail").asText());
        assertFalse(saved.has("eventLocation")); assertFalse(saved.has("country"));
    }
}
