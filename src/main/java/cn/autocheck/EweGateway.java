package cn.autocheck;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import static cn.autocheck.Models.clean;

/** Public EWE tracking API; a batch is encoded as comma-separated consignment numbers. */
public final class EweGateway implements TrackingGateway {
    public static final String ENDPOINT = "https://www.epintl.com.au/oms/api/tracking/emetro/";
    private final HttpClient client;
    private final String endpoint;
    public EweGateway() { this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), ENDPOINT); }
    EweGateway(HttpClient client, String endpoint) { this.client = client; this.endpoint = endpoint; }
    @Override public int batchSize() { return 10; }
    @Override public List<Models.Result> parse(List<String> numbers, JsonNode root) throws Exception { return new EweParser().parse(numbers, root); }
    @Override public Reply query(List<String> numbers, String ignoredZone, String ignoredCode) throws Exception {
        if (numbers.isEmpty() || numbers.size() > 10) throw new IllegalArgumentException("EWE 每批必须为 1–10 个单号");
        for (String number : numbers) if (!number.matches("[A-Za-z0-9-]{5,50}")) throw new IllegalArgumentException("EWE 单号格式无效");
        URI uri = URI.create(endpoint + URLEncoder.encode(String.join(",", numbers), StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json").GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return decode(response.statusCode(), response.body());
    }
    static Reply decode(int status, String body) {
        if (status >= 500 || status == 408 || status == 429) return new Reply(Kind.TRANSIENT_ERROR, null, "", "EWE 服务暂时不可用，HTTP " + status);
        if (status < 200 || status >= 300) return new Reply(Kind.ERROR, null, "", "EWE 请求失败，HTTP " + status);
        JsonNode root;
        try { root = FilesService.JSON.readTree(body); }
        catch (Exception e) { return new Reply(Kind.ERROR, null, "", "EWE 未返回有效 JSON，请检查网络或官网服务"); }
        if (root == null || !root.isObject() || !root.has("Status")) return new Reply(Kind.ERROR, null, "", "EWE 响应格式异常");
        if (root.path("Status").asInt(-1) != 0 || !clean(root.path("Error").asText("")).isEmpty())
            return new Reply(Kind.ERROR, null, "", "EWE 查询失败：" + clean(root.path("Message").asText("")) + " " + clean(root.path("Error").asText("")));
        if (!root.path("Payload").isArray()) return new Reply(Kind.ERROR, null, "", "EWE 响应缺少 Payload 数组");
        return new Reply(Kind.SUCCESS, root, "", "");
    }
    @Override public void close() {}
}
