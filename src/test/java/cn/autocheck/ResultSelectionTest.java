package cn.autocheck;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import static cn.autocheck.Models.*;
import static org.junit.jupiter.api.Assertions.*;

class ResultSelectionTest {
    @TempDir Path directory;
    @Test void checkboxesFollowWaybillsAcrossSortFilterSelectAllAndSourceSwitch() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("autocheck.uiSmoke"));
        var numbers=List.of(ResultExportsTest.LONG_ID,"TESTB123","WAIT999");
        new FilesService(directory).saveSnapshot(new Snapshot(1,numbers,List.of(ResultExportsTest.success(),ResultExportsTest.failed())));
        SwingUtilities.invokeAndWait(()->{
            MainWindow window=null;
            try {
                Main.configureAppearance();window=new MainWindow(directory);window.setVisible(true);window.validate();
                JTable table=(JTable)find(window,"resultsTable");
                assertEquals(Boolean.class,table.getColumnClass(0));
                assertFalse(table.isCellEditable(2,0),"Pending rows must not be exported as query results");
                table.editCellAt(0,0);((JCheckBox)table.getEditorComponent()).doClick();
                table.setRowSelectionInterval(1,1);
                assertEquals(List.of(ResultExportsTest.LONG_ID),window.checkedResults().stream().map(Result::waybillNo).toList());
                table.getRowSorter().toggleSortOrder(1);table.getRowSorter().toggleSortOrder(1);
                int viewB=rowFor(table,"TESTB123");table.setValueAt(true,viewB,0);
                assertEquals(List.of("TESTB123",ResultExportsTest.LONG_ID),window.checkedResults().stream().map(Result::waybillNo).toList());
                JTextField search=(JTextField)find(window,"resultSearch");search.setText(ResultExportsTest.LONG_ID);
                assertEquals(1,table.getRowCount());
                assertEquals(2,window.checkedResults().size(),"Hidden checked results must stay checked");
                Path xlsx=directory.resolve("checked-ui.xlsx");
                ResultExports.writeXlsx(xlsx,window.checkedResults(),Settings.defaults().rules(),false,false,MainlandLocation::classify);
                assertEquals(List.of(ResultExportsTest.LONG_ID,"TESTB123"),FilesService.importWaybills(xlsx));
                ((JButton)find(window,"clearChecked")).doClick();assertTrue(window.checkedResults().isEmpty());
                ((JButton)find(window,"checkAllVisible")).doClick();
                assertEquals(List.of(ResultExportsTest.LONG_ID),window.checkedResults().stream().map(Result::waybillNo).toList());
                search.setText("");
                assertEquals(Boolean.TRUE,table.getValueAt(rowFor(table,ResultExportsTest.LONG_ID),0));
                assertEquals(Boolean.FALSE,table.getValueAt(rowFor(table,"TESTB123"),0));
                ((JComboBox<?>)find(window,"querySource")).setSelectedIndex(4);assertTrue(window.checkedResults().isEmpty());
                ((JComboBox<?>)find(window,"querySource")).setSelectedIndex(0);assertTrue(window.checkedResults().isEmpty());
            } catch(Exception e){throw new RuntimeException(e);}
            finally{if(window!=null)window.dispatchEvent(new WindowEvent(window,WindowEvent.WINDOW_CLOSING));}
        });
    }
    private static int rowFor(JTable table,String number) {
        for(int row=0;row<table.getRowCount();row++)if(table.getValueAt(row,1).equals(number))return row;
        throw new AssertionError("Missing waybill "+number);
    }
    private static Component find(Container root,String name) {
        for(Component child:root.getComponents()) {
            if(name.equals(child.getName()))return child;
            if(child instanceof Container container){Component found=find(container,name);if(found!=null)return found;}
        }
        return null;
    }
}
