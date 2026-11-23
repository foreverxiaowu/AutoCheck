package cn.autocheck;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static cn.autocheck.Models.clean;

public record Track17Config(List<Carrier> carriers, int selectedCode) {
    public record Carrier(String name, int code) {
        @Override public String toString() { return name + " (" + code + ")"; }
    }
    public Track17Config {
        if (carriers == null || carriers.isEmpty()) throw new IllegalArgumentException("至少配置一家物流公司");
        carriers = List.copyOf(carriers);
        Set<Integer> codes = new HashSet<>();
        for (Carrier carrier : carriers) {
            if (carrier == null || clean(carrier.name()).isEmpty() || carrier.code() <= 0 || !codes.add(carrier.code()))
                throw new IllegalArgumentException("物流公司名称不能为空，fc 必须是唯一的正整数");
        }
        if (!codes.contains(selectedCode)) throw new IllegalArgumentException("所选物流公司不在配置列表中");
    }
    public static Track17Config defaults() {
        return new Track17Config(List.of(new Carrier("中通国际", 190175)), 190175);
    }
    public static Path progressDirectory(Path root, int carrierCode) {
        if (carrierCode <= 0) throw new IllegalArgumentException("物流公司编码必须为正整数");
        return root.resolve("17track").resolve("carriers").resolve(Integer.toString(carrierCode));
    }
    public static Track17Config load(Path root) throws IOException {
        Track17Config saved = new FilesService(root.resolve("17track")).loadTrack17Config();
        return saved == null ? defaults() : saved;
    }
    public void save(Path root) throws IOException { new FilesService(root.resolve("17track")).saveTrack17Config(this); }
}
