package cn.autocheck;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static cn.autocheck.Models.*;
import static cn.autocheck.TrackingGateway.*;

class SfTest {
    static JsonNode sample() throws Exception {
        try (var input = SfTest.class.getResourceAsStream("/sf-response.json")) { return FilesService.JSON.readTree(input); }
    }
    @Test void suppliedHistoryUsesLatestRemarkAndDoesNotMatchOldCustomsEvents() throws Exception {
        Result result = new SfParser().parse(List.of("SF5158351178464"), sample()).get(0);
        assertEquals(QueryState.OK, result.state()); assertEquals(7, result.events().size());
        assertEquals("客户已取消寄件", result.latest().detail());
        assertEquals("2026-11-21 20:24:10", result.latest().eventTime());
        assertEquals("UTC+7", result.latest().timeZone());
        assertTrue(matchedRules(result, Settings.sfDefaults().rules()).isEmpty());
        assertEquals("2026-09-19 07:17:34", result.events().get(3).eventTime());
    }
    @Test void sortsByInstantAndSeparatesMissingInvalidAndEmptyResults() throws Exception {
        JsonNode root = FilesService.JSON.readTree("""
          {"code":0,"result":[
            {"waybillNo":"SF001","timeZone":"UTC+8","barNewList":[
              {"barScanDt":"2026-11-23","barScanTm":"10:00:00","localTimeZone":"UTC+9","remark":"旧记录"},
              {"barScanDt":"2026-11-23","barScanTm":"09:30:00","remark":"清关进行中"}]},
            {"waybillNo":"SF002","barNewList":[]},
            {"waybillNo":"SF003","barNewList":[{"barScanDt":"bad","remark":"bad"}]}]}
          """);
        var results = new SfParser().parse(List.of("SF001","SF002","SF003","SF004"), root);
        assertEquals(List.of(QueryState.OK, QueryState.NO_DATA, QueryState.FAILED, QueryState.FAILED), results.stream().map(Result::state).toList());
        assertEquals(List.of("清关进行中"), matchedRules(results.get(0), Settings.sfDefaults().rules()));
        assertThrows(Exception.class, () -> new SfParser().parse(List.of("SF001"), FilesService.JSON.readTree("{\"code\":1,\"result\":[]}")));
    }
    @Test void validatesCurrentBatchAndDecodesServiceAndVerificationErrors() {
        String endpoint = "https://www.sf-international.com/site/core-service/web/bills/SF001%2CSF002/routes?app=bill";
        assertTrue(SfGateway.belongsToBatch(endpoint, List.of("SF001","SF002")));
        assertFalse(SfGateway.belongsToBatch(endpoint, List.of("SF001")));
        assertFalse(SfGateway.belongsToBatch(endpoint.replace("www.sf-international.com","example.com"), List.of("SF001","SF002")));
        assertEquals(Kind.BROWSER_VERIFICATION, SfGateway.decode(200,"{\"code\":401,\"message\":\"验证码已失效\"}").kind());
        assertEquals(Kind.TRANSIENT_ERROR, SfGateway.decode(503, "").kind());
        assertEquals(Kind.ERROR, SfGateway.decode(200,"{\"code\":0,\"result\":{}}").kind());
    }
    @Test void batchesTwentyAndUsesSfParser() throws Exception {
        List<Integer> sizes = new ArrayList<>(); List<Result> saved = new ArrayList<>();
        TrackingGateway gateway = new TrackingGateway() {
            public int batchSize() { return 20; }
            public Reply query(List<String> batch, String zone, String code) {
                sizes.add(batch.size()); var root = FilesService.JSON.createObjectNode().put("code",0); var list = root.putArray("result");
                batch.forEach(no -> list.addObject().put("waybillNo",no).putArray("barNewList"));
                return new Reply(Kind.SUCCESS,root,"","");
            }
            public List<Result> parse(List<String> batch, JsonNode root) throws Exception { return new SfParser().parse(batch,root); }
            public void close() {}
        };
        var numbers = java.util.stream.IntStream.range(0,21).mapToObj(i->"SF000"+i).toList();
        new BatchRunner().run(numbers,new Settings(1,"",List.of()),gateway,new BatchRunner.Control(),new BatchRunner.Listener(){
            public void status(String text){} public BatchRunner.Answer verify(Reply reply){throw new AssertionError();}
            public void completed(List<Result> batch){saved.addAll(batch);}
        });
        assertEquals(List.of(20,1),sizes); assertEquals(21,saved.size());
        assertTrue(saved.stream().allMatch(r->r.state()==QueryState.NO_DATA));
    }
    @Test void browserSubmitsCurrentBatchAndResumesAfterHumanVerification() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("autocheck.browserSmoke"));
        try(var pw=Playwright.create(new Playwright.CreateOptions().setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD","1")));
            var browser=pw.chromium().launch(new BrowserType.LaunchOptions().setChannel("msedge").setHeadless(true))) {
            Page page=browser.newPage(); AtomicInteger documents=new AtomicInteger(), calls=new AtomicInteger();
            page.route("**/*",route->{
                if(route.request().url().contains("/routes")) {
                    try {route.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(calls.incrementAndGet()==1
                        ? "{\"code\":403,\"message\":\"请完成验证码\"}" : FilesService.JSON.writeValueAsString(sample())));}
                    catch(Exception e){throw new RuntimeException(e);}
                } else { documents.incrementAndGet(); route.fulfill(new Route.FulfillOptions().setContentType("text/html").setBody("""
                    <meta charset="UTF-8"><textarea id="waybill_ids"></textarea><button onclick="submitBatch()">查 询</button><script>
                    window.submitBatch=()=>fetch('/site/core-service/web/bills/'+document.getElementById('waybill_ids').value+'/routes?app=bill');
                    </script>
                    """)); }
            });
            try(var gateway=new SfGateway(page)) {
                var numbers=List.of("SF5158351178464");
                Reply first = gateway.query(numbers,"","");
                assertEquals(Kind.BROWSER_VERIFICATION,first.kind());
                assertEquals(1,calls.get(),first.message());
                assertEquals(numbers.get(0),page.locator("#waybill_ids").inputValue());
                page.evaluate("()=>submitBatch()");
                Reply reply=gateway.query(numbers,"",""); assertEquals(Kind.SUCCESS,reply.kind(), "calls="+calls.get()+" documents="+documents.get()+" message="+reply.message());
                assertEquals("客户已取消寄件",gateway.parse(numbers,reply.payload()).get(0).latest().detail());
                assertEquals(1,documents.get());
            }
        }
    }
}
