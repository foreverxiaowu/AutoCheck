package cn.autocheck;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public interface TrackingGateway extends AutoCloseable {
    enum Kind { SUCCESS, IMAGE_CAPTCHA, BROWSER_VERIFICATION, ERROR, TRANSIENT_ERROR }
    record Reply(Kind kind, JsonNode payload, String image, String message) {}
    Reply query(List<String> waybills, String timeZoneType, String verificationCode) throws Exception;
    default int batchSize() { return 10; }
    default List<Models.Result> parse(List<String> waybills, JsonNode payload) throws Exception {
        return new TrajectoryParser().parse(waybills, payload);
    }
    @Override void close();
}
