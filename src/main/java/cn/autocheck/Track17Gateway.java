package cn.autocheck;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Uses the public tracking page's normal query flow, including its fresh sign and manual CAPTCHA. */
public final class Track17Gateway implements TrackingGateway {
    public static final String ENDPOINT = "https://t.17track.net/track/restapi";
    private final Path profile;
    private final int carrierCode;
    private Playwright playwright;
    private BrowserContext context;
    private Page page;
    private List<String> pending = List.of();
    private final Map<String, JsonNode> completed = new LinkedHashMap<>();
    private Reply signal;
    private boolean verifying;

    public Track17Gateway(Path profile, int carrierCode) {
        if (carrierCode <= 0) throw new IllegalArgumentException("请选择有效的物流公司");
        this.profile = profile;
        this.carrierCode = carrierCode;
    }
    Track17Gateway(Page page, int carrierCode) {
        this(Path.of("."), carrierCode);
        this.page = page;
        page.onResponse(this::receive);
    }
    @Override public int batchSize() { return 40; }
    @Override public List<Models.Result> parse(List<String> waybills, JsonNode payload) throws Exception {
        return new Track17Parser().parse(waybills, payload);
    }

    static String trackingUrl(List<String> numbers, int carrierCode) {
        return "https://t.17track.net/zh-cn#nums=" + URLEncoder.encode(String.join(",", numbers), StandardCharsets.UTF_8) + "&fc=" + carrierCode;
    }

    private void open() throws IOException {
        if (page != null) {
            if (page.isClosed()) throw new IOException("17TRACK 查询浏览器已关闭，请停止后重新开始");
            return;
        }
        playwright = Playwright.create(new Playwright.CreateOptions().setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
        Path bundled = AppPaths.bundledBrowser();
        if (bundled != null) {
            if (!Files.isRegularFile(bundled)) throw new IOException("内置浏览器缺失：" + bundled);
            context = playwright.chromium().launchPersistentContext(profile, BrowserGateway.bundledBrowserOptions(bundled));
        } else {
            List<String> errors = new ArrayList<>();
            for (String channel : List.of("chrome", "msedge")) {
                try {
                    context = playwright.chromium().launchPersistentContext(profile,
                        new BrowserType.LaunchPersistentContextOptions().setChannel(channel).setHeadless(false)
                            .setLocale("zh-CN").setTimezoneId("Asia/Shanghai").setViewportSize(1280, 850));
                    break;
                } catch (PlaywrightException e) { errors.add(channel + ": " + e.getMessage().split("\n")[0]); }
            }
            if (context == null) throw new IOException("无法启动 Chrome / Edge：" + String.join("；", errors));
        }
        page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
        page.setDefaultTimeout(30000);
        page.onResponse(this::receive);
    }

    private void receive(Response response) {
        if (!response.url().equals(ENDPOINT) || !response.request().method().equals("POST")) return;
        try {
            // Ignore demo responses, other carriers and queries the user enters outside the current batch.
            if (!belongsToBatch(response.request().postData(), pending, carrierCode)) return;
            Reply reply = decode(response.status(), response.text());
            if (reply.kind() != Kind.SUCCESS) {
                signal = reply;
                verifying = reply.kind() == Kind.BROWSER_VERIFICATION;
                return;
            }
            verifying = false;
            signal = null;
            for (JsonNode shipment : reply.payload().path("shipments")) {
                String number = shipment.path("number").asText();
                if (!pending.contains(number)) continue;
                if (shipment.hasNonNull("carrier") && shipment.path("carrier").asInt() != carrierCode) {
                    signal = new Reply(Kind.ERROR, null, "", "返回的物流公司与所选公司不一致，请检查 fc 配置");
                    continue;
                }
                // 100 means processing; the official page polls until a final result arrives.
                if (shipment.path("code").asInt() != 100) completed.put(number, shipment);
            }
        } catch (Exception e) { signal = new Reply(Kind.TRANSIENT_ERROR, null, "", "读取 17TRACK 响应失败：" + Models.clean(e.getMessage())); }
    }

    static boolean belongsToBatch(String body, List<String> pending, int carrierCode) {
        try {
            JsonNode data = FilesService.JSON.readTree(body).path("data");
            if (!data.isArray() || data.isEmpty()) return false;
            for (JsonNode item : data) {
                if (!pending.contains(item.path("num").asText()) || item.path("fc").asInt() != carrierCode || item.path("sc").asInt() != 0) return false;
            }
            return true;
        } catch (Exception e) { return false; }
    }

    @Override public Reply query(List<String> waybills, String ignoredZone, String ignoredCode) throws Exception {
        if (waybills.isEmpty() || waybills.size() > 40) throw new IllegalArgumentException("17TRACK 每批必须为 1–40 个单号");
        open();
        boolean resumeVerification = verifying && pending.equals(waybills);
        signal = null;
        if (!resumeVerification) {
            pending = List.copyOf(waybills);
            completed.clear();
            verifying = false;
            page.navigate("about:blank");
            try {
                page.navigate(trackingUrl(waybills, carrierCode), new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(30000));
            } catch (TimeoutError e) { return verificationReply("官网加载超时，请在浏览器确认页面是否要求验证，完成后继续。"); }
        }
        long deadline = System.nanoTime() + 30_000_000_000L;
        do {
            // Pump browser events: manual verification may already have produced the original batch's response.
            page.waitForTimeout(100);
            if (signal != null) return signal;
            if (completed.keySet().containsAll(pending)) return combined();
        } while (System.nanoTime() < deadline);
        if (verifying) return verificationReply("请在 17TRACK 浏览器内完成安全验证；当前批次保持等待。");
        if (!completed.isEmpty()) return combined();
        return new Reply(Kind.TRANSIENT_ERROR, null, "", "30 秒内未收到当前单号的有效响应；请检查网络或官网页面。");
    }

    private Reply verificationReply(String message) {
        verifying = true;
        return new Reply(Kind.BROWSER_VERIFICATION, null, "", message);
    }
    private Reply combined() {
        ObjectNode root = FilesService.JSON.createObjectNode();
        var shipments = root.putArray("shipments");
        pending.stream().map(completed::get).filter(Objects::nonNull).forEach(shipments::add);
        return new Reply(Kind.SUCCESS, root, "", "");
    }
    static Reply decode(int status, String body) {
        if (status >= 500 || status == 408) return new Reply(Kind.TRANSIENT_ERROR, null, "", "17TRACK 服务暂时不可用，HTTP " + status);
        if (status == 401 || status == 403 || status == 429) return new Reply(Kind.BROWSER_VERIFICATION, null, "", "请在 17TRACK 浏览器内完成验证后继续（HTTP " + status + "）。");
        if (status < 200 || status >= 300) return new Reply(Kind.ERROR, null, "", "17TRACK 请求失败，HTTP " + status);
        JsonNode root;
        try { root = FilesService.JSON.readTree(body); }
        catch (Exception e) { return new Reply(Kind.BROWSER_VERIFICATION, null, "", "官网未返回 JSON，请在 17TRACK 浏览器内检查并完成验证。"); }
        if (root == null || !root.isObject()) return new Reply(Kind.ERROR, null, "", "17TRACK 返回格式异常");
        int code = root.has("meta") ? root.path("meta").path("code").asInt() : 200;
        if (Set.of(-8, -11, -13, -14).contains(code)) return new Reply(Kind.BROWSER_VERIFICATION, null, "", "17TRACK 要求人工安全验证，请到已打开的官网窗口完成验证，再点击继续。");
        if (code == -1) return new Reply(Kind.TRANSIENT_ERROR, null, "", "17TRACK 网络异常，请稍后重试");
        if (code != 200) return new Reply(Kind.ERROR, null, "", "17TRACK 查询失败，code=" + code + " " + root.path("meta").path("message").asText(""));
        if (!root.path("shipments").isArray()) return new Reply(Kind.ERROR, null, "", "17TRACK 响应缺少 shipments 数组");
        return new Reply(Kind.SUCCESS, root, "", "");
    }

    @Override public void close() {
        try { if (context != null) context.close(); }
        finally { if (playwright != null) playwright.close(); }
    }
}
