package cn.autocheck;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static cn.autocheck.Models.*;
import static cn.autocheck.TrackingGateway.*;
import static org.junit.jupiter.api.Assertions.*;

class EweTest {
    static final String NUMBER = "PTYY00000006580";
    static JsonNode sample() throws Exception {
        try(var input=EweTest.class.getResourceAsStream("/ewe-response.json")) { return FilesService.JSON.readTree(input); }
    }
    @Test void parsesTwentyEventsAndFiltersOnlyLatestPlaceAndMessage() throws Exception {
        var result=new EweParser().parse(List.of(NUMBER),sample()).get(0);
        assertEquals(QueryState.OK,result.state()); assertEquals(20,result.events().size());
        assertEquals("2026-11-24 12:32:00",result.latest().eventTime());
        assertEquals("苏州市 虎丘区",result.latest().location()); assertEquals("",result.latest().timeZone());
        assertTrue(result.latest().detail().startsWith("快件已领取"));
        assertEquals(List.of("已领取"),matchedRules(result,Settings.eweDefaults().rules()));
        assertTrue(new Rule(true,"苏州领取","苏州","已领取",MatchMode.CONTAINS).matches(result.latest()));
        assertFalse(new Rule(true,"重庆清关","重庆","清关中",MatchMode.CONTAINS).matches(result.latest()));
        assertEquals("国内转运公司：圆通 单号：YT2550767274280",result.events().get(10).detail());
    }
    @Test void malformedMissingAndEmptyWaybillsAreHandledIndividually() throws Exception {
        var root=FilesService.JSON.readTree("""
          {"Status":0,"Payload":[
          {"ConsignmentNo":"EMPTY1","Details":[]},
          {"ConsignmentNo":"BAD001","Details":[{"DateString":"2026-02-30 08:00","Message":"坏时间"}]},
          {"ConsignmentNo":"BAD002","Details":[{"DateString":"2026-11-23 08:00"}]},
          {"ConsignmentNo":"DUP001","Details":[]},{"ConsignmentNo":"DUP001","Details":[]}]}
          """);
        var results=new EweParser().parse(List.of("EMPTY1","BAD001","BAD002","DUP001","MISS01"),root);
        assertEquals(List.of(QueryState.NO_DATA,QueryState.FAILED,QueryState.FAILED,QueryState.FAILED,QueryState.FAILED),results.stream().map(Result::state).toList());
        assertThrows(Exception.class,()->new EweParser().parse(List.of(NUMBER),FilesService.JSON.readTree("{\"Status\":1,\"Payload\":[]}")));
        assertEquals(Kind.ERROR,EweGateway.decode(200,"{\"Status\":0,\"Payload\":{}}").kind());
        assertEquals(Kind.ERROR,EweGateway.decode(200,"<html>error</html>").kind());
        assertEquals(Kind.TRANSIENT_ERROR,EweGateway.decode(429,"").kind());
    }
    @Test void sendsTenNumbersInOneGetAndPersistsElevenInTwoBatches() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        List<List<String>> requests=new ArrayList<>();
        server.createContext("/tracking/",exchange->{
            List<String> numbers=Arrays.asList(exchange.getRequestURI().getPath().substring("/tracking/".length()).split(","));
            requests.add(numbers);
            var root=FilesService.JSON.createObjectNode().put("Status",0); var payload=root.putArray("Payload");
            numbers.forEach(no->payload.addObject().put("ConsignmentNo",no).putArray("Details"));
            byte[] body=FilesService.JSON.writeValueAsBytes(root);
            exchange.getResponseHeaders().add("Content-Type","application/json; charset=utf-8");
            exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try(var gateway=new EweGateway(HttpClient.newHttpClient(),"http://127.0.0.1:"+server.getAddress().getPort()+"/tracking/")) {
            var numbers=java.util.stream.IntStream.range(0,11).mapToObj(i->"EWE000"+i).toList();
            List<Result> saved=new ArrayList<>();
            new BatchRunner().run(numbers,new Settings(1,"",List.of()),gateway,new BatchRunner.Control(),new BatchRunner.Listener(){
                public void status(String text){} public BatchRunner.Answer verify(Reply reply){throw new AssertionError();}
                public void completed(List<Result> batch){saved.addAll(batch);}
            });
            assertEquals(List.of(10,1),requests.stream().map(List::size).toList());
            assertEquals(numbers,saved.stream().map(Result::waybillNo).toList());
            assertTrue(saved.stream().allMatch(r->r.state()==QueryState.NO_DATA));
            assertThrows(IllegalArgumentException.class,()->gateway.query(numbers,"",""));
        } finally {server.stop(0);}
    }
    @Test void liveEndpointReturnsAnExplicitResult() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("autocheck.eweLiveSmoke"));
        try(var gateway=new EweGateway()) {
            var reply=gateway.query(List.of(NUMBER),"","");
            assertEquals(Kind.SUCCESS,reply.kind(),reply.message());
            var result=gateway.parse(List.of(NUMBER),reply.payload()).get(0);
            assertEquals(QueryState.OK,result.state());
            System.out.println("EWE_LIVE_OK: "+result.events().size()+" events");
        }
    }
}
