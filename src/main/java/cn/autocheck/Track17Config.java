package cn.autocheck;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static cn.autocheck.Models.clean;

public record Track17Config(List<Carrier> carriers, int selectedCode, int builtInCarriersVersion) {
    private static final int BUILT_IN_CARRIERS_VERSION = 1;
    private static final List<Carrier> ADDED_CARRIERS = List.of(
        new Carrier("中国邮政", 3011), new Carrier("递四方", 190094));

    public Track17Config(List<Carrier> carriers, int selectedCode) {
        this(carriers, selectedCode, BUILT_IN_CARRIERS_VERSION);
    }

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
        List<Carrier> carriers = new ArrayList<>();
        carriers.add(new Carrier("中通国际", 190175));
        carriers.addAll(ADDED_CARRIERS);
        return new Track17Config(carriers, 190175);
    }
    public static Path progressDirectory(Path root, int carrierCode) {
        if (carrierCode <= 0) throw new IllegalArgumentException("物流公司编码必须为正整数");
        return root.resolve("17track").resolve("carriers").resolve(Integer.toString(carrierCode));
    }
    public static Track17Config load(Path root) throws IOException {
        Track17Config saved = new FilesService(root.resolve("17track")).loadTrack17Config();
        if (saved == null) return defaults();
        if (saved.builtInCarriersVersion() >= BUILT_IN_CARRIERS_VERSION) return saved;
        List<Carrier> carriers = new ArrayList<>(saved.carriers());
        for (Carrier added : ADDED_CARRIERS) {
            if (carriers.stream().noneMatch(carrier -> carrier.code() == added.code())) carriers.add(added);
        }
        Track17Config upgraded = new Track17Config(carriers, saved.selectedCode());
        upgraded.save(root);
        return upgraded;
    }
    public void save(Path root) throws IOException { new FilesService(root.resolve("17track")).saveTrack17Config(this); }
}
