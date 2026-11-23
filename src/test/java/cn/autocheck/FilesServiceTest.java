package cn.autocheck;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import static cn.autocheck.Models.*;
import static org.junit.jupiter.api.Assertions.*;

class FilesServiceTest {
    @TempDir Path directory;

    @Test void importsDeduplicatesAndPreservesCaseAndLeadingZeroes() throws Exception {
        assertEquals(List.of("G10433030158", "0012345", "AB-1234"), FilesService.parseWaybills("\uFEFF运单号\nG10433030158，0012345; G10433030158\tAB-1234"));
        Path csv = directory.resolve("input.csv");
        Files.writeString(csv, "运单号\r\n\"0012345\"\r\nG10433030158", Charset.forName("GB18030"));
        assertEquals(List.of("0012345", "G10433030158"), FilesService.importWaybills(csv));
        assertThrows(IllegalArgumentException.class, () -> FilesService.parseWaybills("G10433030158\n无效内容"));
    }

    @Test void importsOnlyFirstExcelColumnAndRejectsFormulaCells() throws Exception {
        Path xlsx = directory.resolve("input.xlsx");
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("运单号");
            var row = sheet.createRow(1); row.createCell(0).setCellValue("0012345"); row.createCell(1).setCellValue("忽略这一列");
            try (var output = Files.newOutputStream(xlsx)) { workbook.write(output); }
            assertEquals(List.of("0012345"), FilesService.importWaybills(xlsx));
            sheet.createRow(2).createCell(0).setCellFormula("1+1");
            try (var output = Files.newOutputStream(xlsx)) { workbook.write(output); }
            assertThrows(java.io.IOException.class, () -> FilesService.importWaybills(xlsx));
        }
    }

    @Test void snapshotsAndRulesRoundTripWithoutLosingTheFullHistory() throws Exception {
        FilesService store = new FilesService(directory);
        List<Result> results = new TrajectoryParser().parse(TrajectoryParserTest.sampleWaybills(), TrajectoryParserTest.sample());
        Snapshot snapshot = new Snapshot(1, TrajectoryParserTest.sampleWaybills(), results);
        store.saveSnapshot(snapshot); assertEquals(snapshot, store.loadSnapshot());
        store.saveSettings(Settings.defaults()); assertEquals(Settings.defaults(), store.loadSettings());
        store.saveSnapshot(new Snapshot(1, List.of(), List.of()));
        assertTrue(store.loadSnapshot().results().isEmpty());
    }

    @Test void exportsUtf8BomEscapedCsvAndCurrentRuleNames() throws Exception {
        Path path = directory.resolve("export.csv");
        Event event = new Event("TEST1", "2026-11-23 10:00:00", "GMT+08:00", "中国", "=1+1,\"test\"", "", "", "");
        FilesService.exportCsv(path, List.of(new Result("TEST1", QueryState.OK, List.of(event), "", "now")), List.of(new Rule(true, "条件", "中国", "", MatchMode.EXACT)));
        String csv = Files.readString(path);
        assertTrue(csv.startsWith("\uFEFF")); assertTrue(csv.contains("\"'=1+1,\"\"test\"\"\"")); assertTrue(csv.contains("\"条件\""));
    }
}
