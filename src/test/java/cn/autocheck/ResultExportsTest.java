package cn.autocheck;

import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static cn.autocheck.Models.*;
import static org.junit.jupiter.api.Assertions.*;

class ResultExportsTest {
    @TempDir Path directory;
    static final String LONG_ID="00123456789012345678";
    static Result success() {
        return new Result(LONG_ID,QueryState.OK,List.of(new Event(LONG_ID,"2026-11-25 12:00:00","+08:00","成都市","=1+1\n物流清关中", "","","")),"","2026-11-25T04:00:00Z");
    }
    static Result failed() {return new Result("TESTB123",QueryState.FAILED,List.of(),"服务超时","now");}
    @Test void excelUsesTextIdentifiersAndSafeStringCellsAndOnlySuppliedRows() throws Exception {
        Path path=directory.resolve("checked.xlsx");
        var rules=List.of(new Rule(true,"国内清关","成都","清关中",MatchMode.CONTAINS,AreaFilter.MAINLAND));
        ResultExports.writeXlsx(path,List.of(success(),failed()),rules,false,true,EweRegions.defaults()::classify);
        try(var workbook=WorkbookFactory.create(path.toFile())) {
            Sheet sheet=workbook.getSheetAt(0);
            assertEquals(2,sheet.getLastRowNum());
            assertEquals(CellType.STRING,sheet.getRow(1).getCell(0).getCellType());
            assertEquals(LONG_ID,sheet.getRow(1).getCell(0).getStringCellValue());
            int detail=column(sheet,"物流进度"),match=column(sheet,"匹配条件");
            assertEquals(CellType.STRING,sheet.getRow(1).getCell(detail).getCellType());
            assertEquals("=1+1\n物流清关中",sheet.getRow(1).getCell(detail).getStringCellValue());
            assertTrue(sheet.getRow(1).getCell(detail).getCellStyle().getWrapText());
            assertEquals("国内清关",sheet.getRow(1).getCell(match).getStringCellValue());
            assertEquals("中国大陆",sheet.getRow(1).getCell(column(sheet,"位置分类")).getStringCellValue());
            assertEquals("服务超时",sheet.getRow(2).getCell(column(sheet,"错误信息")).getStringCellValue());
            assertNotNull(sheet.getPaneInformation());
        }
        assertEquals(List.of(LONG_ID,"TESTB123"),FilesService.importWaybills(path));
    }
    @Test void progressOnlyExportDoesNotExposeLocationAndClipboardContainsOnlyWaybills() throws Exception {
        Path path=directory.resolve("progress.xlsx");
        ResultExports.writeXlsx(path,List.of(success()),List.of(),true,false,MainlandLocation::classify);
        try(var workbook=WorkbookFactory.create(path.toFile())) {
            Sheet sheet=workbook.getSheetAt(0);
            assertEquals(-1,column(sheet,"最新地点")); assertEquals(-1,column(sheet,"位置分类"));
            for(Row row:sheet)for(Cell cell:row)assertFalse(cell.toString().contains("成都市"));
        }
        assertEquals(LONG_ID+"\r\nTESTB123",ResultExports.clipboardText(List.of(success(),failed())));
        assertEquals(LONG_ID,ResultExports.clipboardText(List.of(success())));
        assertEquals("",ResultExports.clipboardText(List.of()));
    }
    private static int column(Sheet sheet,String name) {
        for(Cell cell:sheet.getRow(0))if(cell.getStringCellValue().equals(name))return cell.getColumnIndex();
        return -1;
    }
}
