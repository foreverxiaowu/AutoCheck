package cn.autocheck;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static cn.autocheck.TrackingGateway.*;

/** Opt-in local browser tests: all network traffic is fulfilled locally; no website CAPTCHA is solved. */
class Track17BrowserTest {
    @Test void defaultDetectionAcceptsResolvedCarriersForTheCurrentBatch() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("autocheck.browserSmoke"));
        try(Playwright pw=Playwright.create(new Playwright.CreateOptions().setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD","1")));
            Browser browser=pw.chromium().launch(new BrowserType.LaunchOptions().setChannel("msedge").setHeadless(true))) {
            Page page=browser.newPage();AtomicInteger requests=new AtomicInteger();
            page.route("**/*",route->{
                if(route.request().url().equals(Track17Gateway.ENDPOINT)) {
                    int call=requests.incrementAndGet();
                    try {
                        var payload=Track17Test.sample().deepCopy();
                        ((com.fasterxml.jackson.databind.node.ObjectNode)payload.path("shipments").get(1)).put("carrier",101107);
                        route.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(call==1
                            ? "{\"meta\":{\"code\":200},\"shipments\":[{\"number\":\"77122503283603\",\"code\":100}]}"
                            :FilesService.JSON.writeValueAsString(payload)));
                    }catch(Exception e){throw new RuntimeException(e);}
                } else route.fulfill(new Route.FulfillOptions().setContentType("text/html").setBody("""
                    <script>
                    const params=new URLSearchParams(location.hash.slice(1));
                    window.initialCarrier=Number(params.get('fc'));
                    const submit=fc=>fetch('/track/restapi',{method:'POST',headers:{'Content-Type':'application/json'},
                      body:JSON.stringify({data:params.get('nums').split(',').map(num=>({num,fc,sc:0}))})});
                    (async()=>{await submit(initialCarrier);await submit(190175);})();
                    </script>
                    """));
            });
            try(var gateway=new Track17Gateway(page,0)) {
                var reply=gateway.query(Track17Test.NUMBERS,"","");
                assertEquals(Kind.SUCCESS,reply.kind(),reply.message());
                assertEquals(0,((Number)page.evaluate("initialCarrier")).intValue());
                assertEquals(2,requests.get());
                assertTrue(gateway.parse(Track17Test.NUMBERS,reply.payload()).stream().allMatch(r->r.state()==Models.QueryState.OK));
            }
        }
    }
    @Test void keepsThePageAndBatchAcrossManualVerificationAndCollectsPollingResponses() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("autocheck.browserSmoke"));
        try (Playwright pw = Playwright.create(new Playwright.CreateOptions().setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
             Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setChannel("msedge").setHeadless(true))) {
            Page page = browser.newPage();
            AtomicInteger documents = new AtomicInteger(), requests = new AtomicInteger();
            page.route("**/*", route -> {
                if (route.request().url().equals(Track17Gateway.ENDPOINT)) {
                    int call = requests.incrementAndGet();
                    String body = switch (call) {
                        case 1 -> "{\"meta\":{\"code\":-14},\"shipments\":[]}";
                        case 2 -> "{\"meta\":{\"code\":200},\"shipments\":[{\"number\":\"77122503283603\",\"code\":100}]}";
                        default -> {
                            try { yield FilesService.JSON.writeValueAsString(Track17Test.sample()); }
                            catch (Exception e) { throw new RuntimeException(e); }
                        }
                    };
                    route.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(body));
                } else {
                    documents.incrementAndGet();
                    route.fulfill(new Route.FulfillOptions().setContentType("text/html").setBody("""
                        <html><body><script>
                        const params = new URLSearchParams(location.hash.slice(1));
                        window.submitBatch = () => fetch('/track/restapi', {method:'POST', headers:{'Content-Type':'application/json'},
                          body:JSON.stringify({data:params.get('nums').split(',').map(num => ({num,fc:Number(params.get('fc')),sc:0})),guid:'',timeZoneOffset:-480,sign:'local-test'})});
                        submitBatch();
                        </script></body></html>
                        """));
                }
            });
            try (var gateway = new Track17Gateway(page, 190175)) {
                assertEquals(Kind.BROWSER_VERIFICATION, gateway.query(Track17Test.NUMBERS, "", "").kind());
                // Simulates the normal website callback after the human has completed verification.
                page.evaluate("() => { setTimeout(async () => { await submitBatch(); await submitBatch(); }, 100); }");
                page.waitForTimeout(500); // The website can finish before the user presses Continue.
                Reply reply = gateway.query(Track17Test.NUMBERS, "", "");
                assertEquals(Kind.SUCCESS, reply.kind());
                assertEquals(1, documents.get());
                assertEquals(3, requests.get());
                assertEquals(2, gateway.parse(Track17Test.NUMBERS, reply.payload()).size());
                assertTrue(page.url().contains("fc=190175"));
            }
        }
    }
}
