package cn.autocheck;

import javax.swing.*;
import javax.swing.table.*;

/** Fill a wide viewport with the primary content column; scroll when preferred columns no longer fit. */
final class ResponsiveTable extends JTable {
    private final int contentColumn;

    ResponsiveTable(TableModel model, int contentColumn) {
        super(model);
        this.contentColumn = contentColumn;
        setAutoResizeMode(AUTO_RESIZE_OFF);
    }

    @Override public boolean getScrollableTracksViewportWidth() {
        return getParent() instanceof JViewport viewport && viewport.getWidth() >= getPreferredSize().width;
    }

    @Override public void doLayout() {
        if (getTableHeader() != null && getTableHeader().getResizingColumn() != null) {
            super.doLayout(); // Let a deliberate header drag update the user's preferred column widths.
            return;
        }
        TableColumnModel columns = getColumnModel();
        int preferredTotal = 0;
        for (int i = 0; i < columns.getColumnCount(); i++) preferredTotal += columns.getColumn(i).getPreferredWidth();
        int extra = Math.max(0, getWidth() - preferredTotal);
        for (int i = 0; i < columns.getColumnCount(); i++) {
            TableColumn column = columns.getColumn(i);
            int width = column.getPreferredWidth();
            if (column.getModelIndex() == contentColumn && column.getMaxWidth() > 0) width += extra;
            column.setWidth(width);
        }
    }
}
