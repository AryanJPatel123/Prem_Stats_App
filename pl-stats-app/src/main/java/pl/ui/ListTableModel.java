package pl.ui;

import pl.data.LeagueData;
import pl.stats.MatchModel;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.util.List;
import java.util.function.Function;

/**
 * Table model driven by column definitions, so each screen only declares
 * "column name, type, how to read it from a row". Sorting comes from TableRowSorter.
 */
public class ListTableModel<T> extends AbstractTableModel {

    public record Col<T>(String name, Class<?> type, int width, Function<T, Object> get, TableCellRenderer renderer) {
        public Col(String name, Class<?> type, int width, Function<T, Object> get) { this(name, type, width, get, null); }
    }

    /** Implemented by every tab so the main window can hand it fresh data. */
    public interface DataView { void setData(LeagueData data, MatchModel model); }

    private final List<Col<T>> cols;
    private List<T> rows = List.of();

    public ListTableModel(List<Col<T>> cols) { this.cols = cols; }

    public void setRows(List<T> rows) { this.rows = rows; fireTableDataChanged(); }
    public T rowAt(int modelRow) { return rows.get(modelRow); }
    public List<T> rows() { return rows; }

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return cols.size(); }
    @Override public String getColumnName(int c) { return cols.get(c).name(); }
    @Override public Class<?> getColumnClass(int c) { return cols.get(c).type(); }
    @Override public Object getValueAt(int r, int c) { return cols.get(c).get().apply(rows.get(r)); }

    /** Builds a styled, sortable JTable for this model and wraps it in a scroll pane. */
    public JTable createTable() {
        JTable table = new JTable(this);
        Theme.styleTable(table);
        table.setRowSorter(new TableRowSorter<>(this));
        for (int c = 0; c < cols.size(); c++) {
            Col<T> col = cols.get(c);
            if (col.width() > 0) table.getColumnModel().getColumn(c).setPreferredWidth(col.width());
            if (col.renderer() != null) table.getColumnModel().getColumn(c).setCellRenderer(col.renderer());
        }
        return table;
    }

    public static JScrollPane scroll(JTable table) {
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(javax.swing.BorderFactory.createLineBorder(Theme.GRID));
        sp.getViewport().setBackground(Theme.SURFACE);
        return sp;
    }

    /** Selected row's object, translating through the sorter. */
    public T selected(JTable table) {
        int v = table.getSelectedRow();
        return v < 0 ? null : rowAt(table.convertRowIndexToModel(v));
    }
}
