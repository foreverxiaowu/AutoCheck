package cn.autocheck;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import cn.autocheck.Models.Event;
import static cn.autocheck.Models.*;

/** Native Excel export keeps identifiers as text and respects each provider's visible information. */
final class ResultExports {
    static void writeXlsx(Path path, List<Result> results, List<Rule> rules, boolean progressOnly,
                          boolean includeArea, Function<Event, AreaFilter> classifier) throws IOException {
        List<String> headers = new ArrayList<>(List.of("运单号","查询状态","最新时间","时区"));
        if (!progressOnly) headers.add("最新地点");
        headers.addAll(List.of("物流进度","匹配条件"));
        if (includeArea) headers.add("位置分类");
        headers.addAll(List.of("错误信息","查询时间"));
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("物流结果");
            Font headerFont=workbook.createFont(); headerFont.setBold(true); headerFont.setColor(IndexedColors.WHITE.getIndex());
            CellStyle heading=workbook.createCellStyle(); heading.setFont(headerFont);
            heading.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex()); heading.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            heading.setVerticalAlignment(VerticalAlignment.CENTER);
            Font bodyFont=workbook.createFont(); bodyFont.setFontName("Microsoft YaHei"); bodyFont.setFontHeightInPoints((short)11);
            CellStyle body=workbook.createCellStyle(); body.setFont(bodyFont); body.setWrapText(true);
            body.setVerticalAlignment(VerticalAlignment.TOP); body.setDataFormat(workbook.createDataFormat().getFormat("@"));
            Row first=sheet.createRow(0); first.setHeightInPoints(28);
            for(int column=0;column<headers.size();column++) {
                Cell cell=first.createCell(column,CellType.STRING); cell.setCellValue(headers.get(column)); cell.setCellStyle(heading);
                int width=switch(headers.get(column)) {
                    case "运单号" -> 26;
                    case "物流进度" -> 85;
                    case "最新时间","查询时间","最新地点","错误信息" -> 28;
                    default -> 20;
                };
                sheet.setColumnWidth(column,width*256);
            }
            List<Rule> exportRules=progressOnly ? new Settings(3,"",rules).progressOnly().rules() : rules;
            int index=1;
            for(Result result:results) {
                Event event=result.latest();
                List<String> values=new ArrayList<>(List.of(result.waybillNo(),result.state().label,
                    event==null ? "" : clean(event.eventTime()),event==null ? "" : clean(event.timeZone())));
                if(!progressOnly) values.add(event==null ? "" : event.location());
                values.add(event==null ? "" : event.detail());
                values.add(String.join("；",matchedRules(result,exportRules,classifier)));
                if(includeArea) values.add(event==null ? "" : classifier.apply(event).label);
                values.add(clean(result.error()));values.add(clean(result.queriedAt()));
                Row row=sheet.createRow(index++); row.setHeightInPoints(54);
                for(int column=0;column<values.size();column++) {
                    String value=values.get(column);
                    if(value.length()>32767) throw new IOException("运单 "+result.waybillNo()+" 的“"+headers.get(column)+"”超过 Excel 单元格长度限制");
                    Cell cell=row.createCell(column,CellType.STRING);cell.setCellValue(value);cell.setCellStyle(body);
                }
            }
            sheet.createFreezePane(1,1);
            sheet.setAutoFilter(new CellRangeAddress(0,Math.max(0,index-1),0,headers.size()-1));
            try(OutputStream output=Files.newOutputStream(path)) {workbook.write(output);}
        }
    }

    static String clipboardText(List<Result> results) {
        return String.join("\r\n",results.stream().map(Result::waybillNo).toList());
    }
}
