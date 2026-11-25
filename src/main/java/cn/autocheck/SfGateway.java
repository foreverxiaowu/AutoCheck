package cn.autocheck;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.awt.Frame;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

/** The official page obtains current verification tickets; tickets are never persisted or replayed. */
public final class SfGateway implements TrackingGateway {
    public static final String TRACKING_URL = "https://www.sf-international.com/cn/sc/support/querySupport/waybill";
    private static final String ROUTE_PREFIX = "/site/core-service/web/bills/";
    private final Path profile;
    private Playwright playwright;
    private BrowserContext context;
    private Page page;
    private List<String> pending = List.of();
    private final Map<String, JsonNode> completed = new LinkedHashMap<>();
    private Reply signal;
    private boolean waitingForHuman;
    private Frame owner;
    private BatchRunner.Control control;
    private Consumer<String> notice=message -> {};
    private EmbeddedBrowserWindow embedded;

    public SfGateway(Path profile) { this.profile = profile; }
    SfGateway(Path profile,Frame owner,BatchRunner.Control control,Consumer<String> notice) {
        this(profile);this.owner=owner;this.control=control;this.notice=notice;
    }
    boolean hasEmbeddedBrowser() {return embedded!=null;}
    BatchRunner.Answer verifyEmbedded(Reply reply,BatchRunner.Control control) throws Exception {
        return embedded.verify(reply.message(),control);
    }
    private BrowserType.LaunchPersistentContextOptions windowOptions(BrowserType.LaunchPersistentContextOptions options) {
        return EmbeddedBrowserWindow.launchOptions(options,owner!=null);
    }
    SfGateway(Page page) { this(Path.of(".")); this.page = page; page.onResponse(this::receive); }
    @Override public int batchSize() { return 20; }
    @Override public List<Models.Result> parse(List<String> numbers, JsonNode root) throws Exception { return new SfParser().parse(numbers, root); }

    private void open() throws IOException {
        if (page != null) {
            if (page.isClosed()) throw new IOException("顺丰查询浏览器已关闭，请停止任务后重新开始");
            return;
        }
        playwright = Playwright.create(new Playwright.CreateOptions().setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
        Path bundled = AppPaths.bundledBrowser();
        if (bundled != null) {
            if (!Files.isRegularFile(bundled)) throw new IOException("内置浏览器缺失：" + bundled);
            context = playwright.chromium().launchPersistentContext(profile, windowOptions(BrowserGateway.bundledBrowserOptions(bundled)));
        } else {
            List<String> errors = new ArrayList<>();
            for (String channel : List.of("chrome", "msedge")) {
                try {
                    context = playwright.chromium().launchPersistentContext(profile,
                        windowOptions(new BrowserType.LaunchPersistentContextOptions().setChannel(channel).setHeadless(false)
                            .setLocale("zh-CN").setTimezoneId("Asia/Shanghai").setViewportSize(1280, 850)));
                    break;
                } catch (PlaywrightException e) { errors.add(channel + ": " + e.getMessage().split("\n")[0]); }
            }
            if (context == null) throw new IOException("无法启动 Chrome / Edge：" + String.join("；", errors));
        }
        page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
        page.setDefaultTimeout(10000);
        page.onResponse(this::receive);
        embedded=EmbeddedBrowserWindow.openAttached(page,owner,control,"顺丰国际",notice);
    }

    static boolean belongsToBatch(String url, List<String> numbers) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            if (!"www.sf-international.com".equals(uri.getHost()) || !path.startsWith(ROUTE_PREFIX) || !path.endsWith("/routes")) return false;
            String ids = path.substring(ROUTE_PREFIX.length(), path.length() - "/routes".length());
            List<String> requested = Arrays.asList(ids.split("[,;\\s]+"));
            return !requested.isEmpty() && numbers.containsAll(requested);
        } catch (Exception e) { return false; }
    }

    private void receive(Response response) {
        if (!belongsToBatch(response.url(), pending)) return;
        try {
            Reply reply = decode(response.status(), response.text());
            if (reply.kind() != Kind.SUCCESS) { signal = reply; return; }
            signal = null;
            for (JsonNode entry : reply.payload().path("result")) {
                String number = entry.path("waybillNo").asText();
                if (pending.contains(number)) completed.put(number, entry);
            }
        } catch (Exception e) { signal = new Reply(Kind.TRANSIENT_ERROR, null, "", "顺丰响应读取失败"); }
    }

    @Override public Reply query(List<String> numbers, String ignoredZone, String ignoredCode) throws Exception {
        if (numbers.isEmpty() || numbers.size() > 20) throw new IllegalArgumentException("顺丰每批必须为 1–20 个单号");
        open();
        boolean continuing = waitingForHuman && pending.equals(numbers);
        signal = null;
        if (!continuing) {
            pending = List.copyOf(numbers); completed.clear();
            try {
                try {
                    page.navigate(TRACKING_URL, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(30000));
                } catch(TimeoutError e) {
                    // Slow ancillary resources must not discard a tracking form that is already ready.
                    if(!page.locator("#waybill_ids").isVisible())throw e;
                }
                Locator agree = page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName(Pattern.compile("^同\\s*意$")));
                if (agree.count() > 0 && agree.first().isVisible()) agree.first().click();
                page.locator("#waybill_ids").fill(String.join(",", numbers));
                page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName(Pattern.compile("^查\\s*询$"))).click();
            } catch (TimeoutError e) { return manual("官网加载或查询控件超时，请在顺丰浏览器确认本批单号并点击查询、完成验证后继续。"); }
        }
        long deadline = System.nanoTime() + 30_000_000_000L;
        do {
            if(control!=null && control.isCancelled())throw new CancellationException("任务已停止");
            page.waitForTimeout(100);
            if (signal != null) {
                if (signal.kind() == Kind.BROWSER_VERIFICATION) {
                    waitingForHuman = true;
                    // A verification response can still be queued when the human clicks Continue.
                    // Keep pumping the existing page so its verification callback can submit the batch.
                    if (continuing) { signal = null; continue; }
                }
                return signal;
            }
            if (completed.keySet().containsAll(pending)) return combined();
        } while (System.nanoTime() < deadline);
        if (!completed.isEmpty()) return combined();
        return manual("请在顺丰官网窗口完成安全验证或手机号校验，并确认已点击查询；本批单号：" + String.join(", ", pending));
    }

    private Reply manual(String message) { waitingForHuman = true; return new Reply(Kind.BROWSER_VERIFICATION, null, "", message); }
    private Reply combined() {
        waitingForHuman = false;
        var root = FilesService.JSON.createObjectNode().put("code", 0);
        var result = root.putArray("result");
        pending.stream().map(completed::get).filter(Objects::nonNull).forEach(result::add);
        return new Reply(Kind.SUCCESS, root, "", "");
    }
    static Reply decode(int status, String body) {
        if (status >= 500 || status == 408) return new Reply(Kind.TRANSIENT_ERROR, null, "", "顺丰服务暂时不可用，HTTP " + status);
        if (status == 401 || status == 403 || status == 429) return new Reply(Kind.BROWSER_VERIFICATION, null, "", "请到顺丰官网窗口完成验证后继续");
        if (status < 200 || status >= 300) return new Reply(Kind.ERROR, null, "", "顺丰请求失败，HTTP " + status);
        JsonNode root;
        try { root = FilesService.JSON.readTree(body); }
        catch (Exception e) { return new Reply(Kind.BROWSER_VERIFICATION, null, "", "顺丰未返回 JSON，请检查官网并完成验证"); }
        if (root == null || !root.isObject() || !root.has("code")) return new Reply(Kind.ERROR, null, "", "顺丰响应格式异常");
        if (root.path("code").asInt(-1) != 0) {
            String message = Models.clean(root.path("message").asText("")) + " " + Models.clean(root.path("detailMessage").asText(""));
            boolean verification = Pattern.compile("验证|校验|手机|captcha|ticket|token", Pattern.CASE_INSENSITIVE).matcher(message).find();
            return new Reply(verification ? Kind.BROWSER_VERIFICATION : Kind.ERROR, null, "", "顺丰 code=" + root.path("code").asText() + "：" + message);
        }
        if (!root.path("result").isArray()) return new Reply(Kind.ERROR, null, "", "顺丰响应缺少 result 数组");
        return new Reply(Kind.SUCCESS, root, "", "");
    }
    @Override public void close() {
        try { if(embedded!=null)embedded.close(); }
        finally {
            try { if (context != null) context.close(); }
            finally { if (playwright != null) playwright.close(); }
        }
    }
}
