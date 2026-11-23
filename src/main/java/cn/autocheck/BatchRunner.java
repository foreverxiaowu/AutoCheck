package cn.autocheck;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CancellationException;
import static cn.autocheck.Models.*;
import static cn.autocheck.TrackingGateway.*;

public final class BatchRunner {
    public record Answer(String code, boolean cancel) {
        public static Answer stop() { return new Answer("", true); }
    }
    public interface Listener {
        void status(String message);
        Answer verify(Reply reply) throws Exception;
        /** Must persist before returning; a write error stops the job instead of losing progress silently. */
        void completed(List<Result> batch) throws Exception;
    }
    public static final class Control {
        private volatile boolean cancelled;
        private boolean paused;
        public synchronized void pause() { paused = true; }
        public synchronized void resume() { paused = false; notifyAll(); }
        public synchronized boolean isPaused() { return paused; }
        public synchronized void cancel() { cancelled = true; paused = false; notifyAll(); }
        public boolean isCancelled() { return cancelled; }
        public synchronized void checkpoint() throws InterruptedException {
            while (paused && !cancelled) wait(200);
            if (cancelled) throw new CancellationException("任务已停止，进度已保留");
        }
        void delay(long millis) throws InterruptedException {
            long remaining = millis;
            while (remaining > 0) {
                checkpoint();
                long step = Math.min(remaining, 100);
                Thread.sleep(step);
                remaining -= step;
            }
            checkpoint();
        }
    }

    public void run(List<String> waybills, Settings settings, TrackingGateway gateway, Control control, Listener listener) throws Exception {
        if (settings.intervalSeconds() < 1 || settings.intervalSeconds() > 120) throw new IllegalArgumentException("批次间隔须为 1–120 秒");
        int batchSize = gateway.batchSize();
        if (batchSize < 1 || batchSize > 40) throw new IllegalArgumentException("无效批次数量");
        for (int offset = 0; offset < waybills.size(); offset += batchSize) {
            control.checkpoint();
            List<String> batch = List.copyOf(waybills.subList(offset, Math.min(offset + batchSize, waybills.size())));
            listener.status("正在查询第 " + (offset / batchSize + 1) + "/" + ((waybills.size() + batchSize - 1) / batchSize) + " 批（" + batch.size() + " 个单号）");
            String code = "";
            int retries = 0;
            List<Result> results;
            while (true) {
                control.checkpoint();
                Reply reply;
                String submittedCode = code;
                code = ""; // A human-entered code is consumed by one request, even when that request times out.
                try { reply = gateway.query(batch, settings.timeZoneType(), submittedCode); }
                catch (Exception e) {
                    control.checkpoint();
                    if (retries++ < 2) {
                        listener.status("网络或浏览器异常，稍后重试当前批次（" + retries + "/2）");
                        control.delay(Math.max(2000, settings.intervalSeconds() * 1000L));
                        continue;
                    }
                    results = failures(batch, "查询失败：" + clean(e.getMessage()));
                    break;
                }
                if (reply.kind() == Kind.IMAGE_CAPTCHA || reply.kind() == Kind.BROWSER_VERIFICATION) {
                    listener.status("等待手动验证，当前批次尚未完成");
                    Answer answer = listener.verify(reply);
                    if (answer == null || answer.cancel()) { control.cancel(); control.checkpoint(); }
                    code = clean(answer.code());
                    retries = 0;
                    continue;
                }
                if (reply.kind() == Kind.TRANSIENT_ERROR && retries++ < 2) {
                    listener.status("服务异常，稍后重试当前批次（" + retries + "/2）");
                    control.delay(Math.max(2000, settings.intervalSeconds() * 1000L));
                    continue;
                }
                if (reply.kind() != Kind.SUCCESS) { results = failures(batch, reply.message()); break; }
                try { results = gateway.parse(batch, reply.payload()); }
                catch (Exception e) { results = failures(batch, clean(e.getMessage())); }
                break;
            }
            // Even if Stop was clicked while the request was in flight, preserve the response already received.
            listener.completed(results);
            if (offset + batchSize < waybills.size()) control.delay(settings.intervalSeconds() * 1000L);
        }
    }

    private static List<Result> failures(List<String> batch, String message) {
        return batch.stream().map(no -> new Result(no, QueryState.FAILED, List.of(), message, Instant.now().toString())).toList();
    }
}
