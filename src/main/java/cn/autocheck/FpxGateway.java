package cn.autocheck;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public final class FpxGateway implements TrackingGateway {
    public static final String ENDPOINT = "https://track.4px.com/track/v2/front/listTrackV3";
    private final HttpClient client;
    private final URI endpoint;
    public FpxGateway() { this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), URI.create(ENDPOINT)); }
    FpxGateway(HttpClient client, URI endpoint) { this.client = client; this.endpoint = endpoint; }
    @Override public int batchSize() { return 10; }
    @Override public List<Models.Result> parse(List<String> numbers, JsonNode root) throws Exception { return new FpxParser().parse(numbers, root); }
    @Override public Reply query(List<String> numbers, String ignoredZone, String ignoredCode) throws Exception {
        if (numbers.isEmpty() || numbers.size() > batchSize()) throw new IllegalArgumentException("4PX 每批必须为 1–10 个单号");
        for (String number : numbers) if (number == null || !number.matches("[A-Za-z0-9-]{5,50}")) throw new IllegalArgumentException("4PX 单号格式无效");
        String body = FilesService.JSON.writeValueAsString(Map.of("queryCodes", numbers, "language", "zh-cn", "translateLanguage", ""));
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json").header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return decode(response.statusCode(), response.body());
    }
    static Reply decode(int status, String body) {
        if (status >= 500 || status == 408 || status == 429) return new Reply(Kind.TRANSIENT_ERROR, null, "", "4PX 服务暂时不可用，HTTP " + status);
        if (status < 200 || status >= 300) return new Reply(Kind.ERROR, null, "", "4PX 请求失败，HTTP " + status);
        JsonNode root;
        try { root = FilesService.JSON.readTree(body); }
        catch (Exception e) { return new Reply(Kind.ERROR, null, "", "4PX 未返回有效 JSON，请检查网络或官网服务"); }
        if (root == null || !root.isObject() || !root.has("result")) return new Reply(Kind.ERROR, null, "", "4PX 响应格式异常");
        if (root.path("result").asInt(-1) != 1) return new Reply(Kind.ERROR, null, "", "4PX 查询失败：" + Models.clean(root.path("message").asText("")));
        if (!root.path("data").isArray()) return new Reply(Kind.ERROR, null, "", "4PX 响应缺少 data 数组");
        return new Reply(Kind.SUCCESS, root, "", "");
    }
    @Override public void close() {}
}
