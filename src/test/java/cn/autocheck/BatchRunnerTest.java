package cn.autocheck;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static cn.autocheck.Models.*;
import static cn.autocheck.TrackingGateway.*;

class BatchRunnerTest {
    static Reply emptySuccess() throws Exception {
        return new Reply(Kind.SUCCESS, FilesService.JSON.readTree("{\"code\":200,\"data\":{\"detailResponses\":[]}}"), "", "");
    }
    static abstract class Gateway implements TrackingGateway { public void close() {} }

    @Test void captchaRetriesTheSameBatchWithTheHumanCodeAndThenMovesToNextTen() throws Exception {
        List<String> input = new ArrayList<>();
        for (int i = 0; i < 21; i++) input.add("TEST" + i);
        List<List<String>> sent = new ArrayList<>(); List<String> codes = new ArrayList<>();
        List<Result> persisted = new ArrayList<>(); AtomicInteger verified = new AtomicInteger();
        try (Gateway gateway = new Gateway() {
            public Reply query(List<String> nos, String zone, String code) throws Exception {
                sent.add(nos); codes.add(code);
                return sent.size() == 1 ? new Reply(Kind.IMAGE_CAPTCHA, null, "image", "verify") : emptySuccess();
            }
        }) {
            new BatchRunner().run(input, new Settings(1, "senderView", List.of()), gateway, new BatchRunner.Control(), new BatchRunner.Listener() {
                public void status(String message) {}
                public BatchRunner.Answer verify(Reply reply) { verified.incrementAndGet(); return new BatchRunner.Answer("4821", false); }
                public void completed(List<Result> batch) { persisted.addAll(batch); }
            });
        }
        assertEquals(List.of(10, 10, 10, 1), sent.stream().map(List::size).toList());
        assertEquals(sent.get(0), sent.get(1));
        assertEquals(List.of("", "4821", "", ""), codes);
        assertEquals(1, verified.get()); assertEquals(21, persisted.size());
    }

    @Test void cancellingCaptchaDoesNotMarkBatchAsFinished() {
        List<Result> persisted = new ArrayList<>();
        assertThrows(CancellationException.class, () -> new BatchRunner().run(List.of("TEST1"), Settings.defaults(), new Gateway() {
            public Reply query(List<String> ids, String zone, String code) { return new Reply(Kind.IMAGE_CAPTCHA, null, "image", "verify"); }
        }, new BatchRunner.Control(), new BatchRunner.Listener() {
            public void status(String message) {}
            public BatchRunner.Answer verify(Reply reply) { return BatchRunner.Answer.stop(); }
            public void completed(List<Result> batch) { persisted.addAll(batch); }
        }));
        assertTrue(persisted.isEmpty());
    }

    @Test void writeFailureStopsBeforeTheNextRequest() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<String> input = new ArrayList<>(); for (int i = 0; i < 11; i++) input.add("TEST" + i);
        Exception error = assertThrows(Exception.class, () -> new BatchRunner().run(input, Settings.defaults(), new Gateway() {
            public Reply query(List<String> ids, String zone, String code) throws Exception { calls.incrementAndGet(); return emptySuccess(); }
        }, new BatchRunner.Control(), new BatchRunner.Listener() {
            public void status(String message) {}
            public BatchRunner.Answer verify(Reply reply) { fail("Unexpected CAPTCHA"); return null; }
            public void completed(List<Result> batch) throws Exception { throw new java.io.IOException("disk full"); }
        }));
        assertEquals("disk full", error.getMessage()); assertEquals(1, calls.get());
    }

    @Test void stoppingDuringARequestStillSavesTheReturnedBatch() throws Exception {
        var control = new BatchRunner.Control(); List<Result> saved = new ArrayList<>();
        new BatchRunner().run(List.of("TEST1"), Settings.defaults(), new Gateway() {
            public Reply query(List<String> ids, String zone, String code) throws Exception { control.cancel(); return emptySuccess(); }
        }, control, new BatchRunner.Listener() {
            public void status(String message) {}
            public BatchRunner.Answer verify(Reply reply) { return BatchRunner.Answer.stop(); }
            public void completed(List<Result> batch) { saved.addAll(batch); }
        });
        assertEquals(1, saved.size());
    }

    @Test void decoderSeparatesImageCaptchaHtmlChallengeRateLimitAndErrors() {
        assertEquals(Kind.IMAGE_CAPTCHA, BrowserGateway.decode(200, "{\"code\":1000001,\"data\":\"base64\"}").kind());
        assertEquals(Kind.BROWSER_VERIFICATION, BrowserGateway.decode(200, "<html>verify</html>").kind());
        assertEquals(Kind.BROWSER_VERIFICATION, BrowserGateway.decode(429, "{}").kind());
        assertEquals(Kind.TRANSIENT_ERROR, BrowserGateway.decode(503, "").kind());
        assertEquals(Kind.ERROR, BrowserGateway.decode(200, "{\"code\":400,\"message\":\"failed\"}").kind());
    }

    @Test void retriesTransientFailuresOnlyTwiceAndMarksTheBatchFailed() throws Exception {
        AtomicInteger calls = new AtomicInteger(); List<Result> saved = new ArrayList<>();
        new BatchRunner().run(List.of("TEST1"), new Settings(1, "senderView", List.of()), new Gateway() {
            public Reply query(List<String> ids, String zone, String code) {
                calls.incrementAndGet(); return new Reply(Kind.TRANSIENT_ERROR, null, "", "HTTP 503");
            }
        }, new BatchRunner.Control(), new BatchRunner.Listener() {
            public void status(String message) {}
            public BatchRunner.Answer verify(Reply reply) { fail("Unexpected CAPTCHA"); return null; }
            public void completed(List<Result> batch) { saved.addAll(batch); }
        });
        assertEquals(3, calls.get()); assertEquals(QueryState.FAILED, saved.get(0).state());
        assertEquals("HTTP 503", saved.get(0).error());
    }

    @Test void cancellationWakesAPausedWorker() throws Exception {
        var control = new BatchRunner.Control(); control.pause();
        var entered = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var future = executor.submit(() -> {
                entered.countDown();
                assertThrows(CancellationException.class, control::checkpoint);
            });
            assertTrue(entered.await(1, java.util.concurrent.TimeUnit.SECONDS));
            control.cancel(); future.get(1, java.util.concurrent.TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
    }
}
