package cn.autocheck;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.net.http.HttpClient;
import java.util.*;
import static cn.autocheck.Models.*;
import static cn.autocheck.TrackingGateway.*;
import static org.junit.jupiter.api.Assertions.*;

class FpxTest {
    static final String NUMBER="OC8011202512090018";
    static JsonNode sample() throws Exception {
        try(var input=FpxTest.class.getResourceAsStream("/fpx-response.json")) {return FilesService.JSON.readTree(input);}
    }
    @Test void parsesAllSuppliedEventsAndMatchesOnlyLatestDescriptionWithoutRegion() throws Exception {
        var result=new FpxParser().parse(List.of(NUMBER),sample()).get(0);
        assertEquals(QueryState.OK,result.state()); assertEquals(33,result.events().size());
        assertEquals("2026-01-25 16:27:09",result.latest().eventTime());
        assertEquals("+08:00",result.latest().timeZone());
        assertTrue(result.latest().detail().startsWith("您的快件已代收"));
        assertEquals(List.of("已代收"),matchedRules(result,Settings.fpxDefaults().rules()));
        assertTrue(result.events().stream().allMatch(e->e.location().isEmpty() && e.country().isEmpty()));
        assertEquals(QueryState.FAILED,new FpxParser().parse(List.of("8423091997104"),sample()).get(0).state());
    }
    @Test void sortsByAbsoluteTimeEvenIfTheLocalDisplayTimesOrInputOrderDiffer() throws Exception {
        ObjectNode root=(ObjectNode)sample();
        var tracks=((ObjectNode)root.path("data").get(0)).putArray("tracks");
        tracks.addObject().put("tkDate","2026-01-25T09:00:00.123+0000").put("tkDateStr","2026-01-25 17:00:00").put("tkDesc","最新");
        tracks.addObject().put("tkDate","2026-01-25T09:00:00.100Z").put("tkDateStr","2026-01-25 18:00:00").put("tkDesc","较旧");
        var result=new FpxParser().parse(List.of(NUMBER),root).get(0);
        assertEquals("最新",result.latest().detail());
        assertEquals("17:00:00",result.latest().eventTime().substring(11));
        tracks.removeAll();
        tracks.addObject().put("tkDate","2026-01-25T09:00:00Z").put("tkTimezone","UTC+7").put("tkDesc","指定时区");
        assertEquals("2026-01-25 16:00:00",new FpxParser().parse(List.of(NUMBER),root).get(0).latest().eventTime());
    }
    @Test void rejectsFailedMissingDuplicateAndMalformedResponsesInsteadOfInventingEmptySuccess() throws Exception {
        var root=FilesService.JSON.readTree("""
          {"result":1,"data":[
          {"queryCode":"EMPTY1","tracks":[]},
          {"queryCode":"BAD001","tracks":[{"tkDate":"invalid","tkDesc":"bad"}]},
          {"queryCode":"BAD002","tracks":[{"tkDate":"2026-01-25T09:00:00Z"}]},
          {"queryCode":"DUP001","tracks":[]},{"queryCode":"DUP001","tracks":[]}]}
          """);
        assertEquals(List.of(QueryState.NO_DATA,QueryState.FAILED,QueryState.FAILED,QueryState.FAILED,QueryState.FAILED),
          new FpxParser().parse(List.of("EMPTY1","BAD001","BAD002","DUP001","MISS01"),root).stream().map(Result::state).toList());
        assertThrows(Exception.class,()->new FpxParser().parse(List.of(NUMBER),FilesService.JSON.readTree("{\"result\":0,\"data\":[]}")));
        assertEquals(Kind.ERROR,FpxGateway.decode(200,"{\"result\":1,\"data\":{}}").kind());
        assertEquals(Kind.ERROR,FpxGateway.decode(200,"{\"result\":0,\"message\":\"fail\"}").kind());
        assertEquals(Kind.TRANSIENT_ERROR,FpxGateway.decode(503,"").kind());
    }
    @Test void sendsTheExactPostSchemaAndBatchesElevenNumbersIntoTenAndOne() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        List<JsonNode> requests=new ArrayList<>(); List<String> methods=new ArrayList<>();
        server.createContext("/track",exchange->{
            methods.add(exchange.getRequestMethod());
            JsonNode body=FilesService.JSON.readTree(exchange.getRequestBody()); requests.add(body);
            var root=FilesService.JSON.createObjectNode().put("result",1);var data=root.putArray("data");
            body.path("queryCodes").forEach(no->data.addObject().put("queryCode",no.asText()).putArray("tracks"));
            byte[] output=FilesService.JSON.writeValueAsBytes(root);
            exchange.getResponseHeaders().add("Content-Type","application/json; charset=utf-8");
            exchange.sendResponseHeaders(200,output.length);exchange.getResponseBody().write(output);exchange.close();
        });
        server.start();
        try(var gateway=new FpxGateway(HttpClient.newHttpClient(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/track"))) {
            var numbers=java.util.stream.IntStream.range(0,11).mapToObj(i->"FPX000"+i).toList();
            List<Result> saved=new ArrayList<>();
            new BatchRunner().run(numbers,new Settings(1,"",List.of()),gateway,new BatchRunner.Control(),new BatchRunner.Listener(){
                public void status(String message){}public BatchRunner.Answer verify(Reply reply){throw new AssertionError();}
                public void completed(List<Result> batch){saved.addAll(batch);}
            });
            assertEquals(List.of(10,1),requests.stream().map(r->r.path("queryCodes").size()).toList());
            assertEquals(List.of("POST","POST"),methods);
            assertTrue(requests.stream().allMatch(r->r.path("language").asText().equals("zh-cn") && r.path("translateLanguage").asText().isEmpty() && r.size()==3));
            assertEquals(numbers,saved.stream().map(Result::waybillNo).toList());
        } finally {server.stop(0);}
    }
    @Test void liveEndpointReturnsTheSuppliedWaybill() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("autocheck.fpxLiveSmoke"));
        try(var gateway=new FpxGateway()) {
            Reply reply=gateway.query(List.of(NUMBER),"","");
            assertEquals(Kind.SUCCESS,reply.kind(),reply.message());
            var result=gateway.parse(List.of(NUMBER),reply.payload()).get(0);
            assertEquals(QueryState.OK,result.state(),result.error());
            System.out.println("FPX_LIVE_OK: "+result.events().size()+" events");
        }
    }
}
