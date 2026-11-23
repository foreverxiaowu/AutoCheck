package cn.autocheck;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;

/** All Playwright calls run on the batch worker thread, including cleanup. */
public final class BrowserGateway implements TrackingGateway {
    public static final String ENDPOINT = "https://www.ytoglobal.com/api/ytoApi/getTrajectory";
    private final Path profile;
    private Playwright playwright;
    private BrowserContext context;
    private Page page;

    public BrowserGateway(Path profile) { this.profile = profile; }

    private void open() throws IOException {
        if (page != null) {
            if (page.isClosed()) throw new IOException("查询浏览器已关闭，请停止任务后重新开始");
            return;
        }
        // Installers include a browser. Development JARs can also use an installed Chrome/Edge.
        if (playwright == null)
            playwright = Playwright.create(new Playwright.CreateOptions().setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
        List<String> errors = new ArrayList<>();
        Path bundled = AppPaths.bundledBrowser();
        if (bundled != null) {
            if (!Files.isRegularFile(bundled)) throw new IOException("内置浏览器缺失，请重新安装程序：" + bundled);
            context = playwright.chromium().launchPersistentContext(profile, bundledBrowserOptions(bundled));
        } else for (String channel : List.of("chrome", "msedge")) {
            try {
                context = playwright.chromium().launchPersistentContext(profile,
                    new BrowserType.LaunchPersistentContextOptions().setChannel(channel)
                        .setHeadless(false).setLocale("zh-CN").setViewportSize(1280, 850));
                break;
            } catch (PlaywrightException e) { errors.add(channel + ": " + e.getMessage().split("\n")[0]); }
        }
        if (context == null) throw new IOException("无法启动 Chrome / Edge。请安装其中一个浏览器，且不要同时运行两个程序。" + String.join("；", errors));
        page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
        page.setDefaultTimeout(35000);
        page.navigate("https://www.ytoglobal.com/service", new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
    }

    static BrowserType.LaunchPersistentContextOptions bundledBrowserOptions(Path executable) {
        return new BrowserType.LaunchPersistentContextOptions().setExecutablePath(executable)
            .setHeadless(false).setLocale("zh-CN").setViewportSize(1280, 850);
    }

    @Override public Reply query(List<String> waybills, String timeZoneType, String verificationCode) throws Exception {
        if (waybills.isEmpty() || waybills.size() > 10) throw new IllegalArgumentException("每批必须为 1–10 个单号");
        open();
        if (!page.url().startsWith("https://www.ytoglobal.com/"))
            page.navigate("https://www.ytoglobal.com/service", new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        Map<String, Object> arguments = Map.of("endpoint", ENDPOINT, "code", verificationCode == null ? "" : verificationCode,
            "payload", Map.of("waybillNos", waybills, "language", "zh-CN", "timeZoneType", timeZoneType));
        // Use the visible browser's own cookies and network stack, so manual verification shares the same session.
        Object raw = page.evaluate("""
            async ({endpoint, payload, code}) => {
              const controller = new AbortController();
              const timeout = setTimeout(() => controller.abort(), 30000);
              try {
                const response = await fetch(endpoint, {
                  method: 'POST', credentials: 'include', signal: controller.signal,
                  headers: {'Content-Type': 'application/json', 'Accept': 'application/json, text/plain, */*', 'x-rate-limit-code': code},
                  body: JSON.stringify(payload)
                });
                return {status: response.status, body: await response.text()};
              } finally { clearTimeout(timeout); }
            }
            """, arguments);
        JsonNode response = FilesService.JSON.valueToTree(raw);
        return decode(response.path("status").asInt(), response.path("body").asText());
    }

    static Reply decode(int status, String body) {
        JsonNode root;
        try { root = FilesService.JSON.readTree(body); }
        catch (Exception e) { root = null; }
        if (root != null && root.isObject() && root.path("code").asInt() == 1000001) {
            String image = root.path("data").isTextual() ? root.path("data").asText() : root.path("pageData").asText("");
            if (image.isBlank()) return new Reply(Kind.BROWSER_VERIFICATION, null, "", "接口要求人工验证，未提供图片。请在浏览器官网完成查询验证。");
            return new Reply(Kind.IMAGE_CAPTCHA, null, image, "请输入圆通验证码；提交后将重试当前批次。");
        }
        if (status == 401 || status == 403 || status == 429 || (status >= 200 && status < 300 && (root == null || !root.isObject())))
            return new Reply(Kind.BROWSER_VERIFICATION, null, "", "HTTP " + status + "：请到查询浏览器完成官网验证，再点击继续。");
        if (status >= 500 || status == 408) return new Reply(Kind.TRANSIENT_ERROR, null, "", "圆通服务暂时不可用，HTTP " + status);
        if (status < 200 || status >= 300) return new Reply(Kind.ERROR, null, "", "请求失败，HTTP " + status);
        if (root == null || root.path("code").asInt() != 200)
            return new Reply(Kind.ERROR, root, "", root == null ? "响应为空" : TrajectoryParser.message(root));
        return new Reply(Kind.SUCCESS, root, "", "");
    }

    @Override public void close() {
        try { if (context != null) context.close(); }
        finally { if (playwright != null) playwright.close(); }
    }
}
