package pl.ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

/** Colours, fonts and table styling shared by every screen. */
public final class Theme {
    private Theme() { }

    // Surfaces and ink
    public static final Color PAGE = new Color(0xf5f4f7);      // window background behind cards
    public static final Color SURFACE = Color.WHITE;           // cards, tables, charts
    public static final Color PANEL = new Color(0xf7f5f8);
    public static final Color TEXT = new Color(0x1a1320);
    public static final Color TEXT_SECONDARY = new Color(0x5b5463);
    public static final Color TEXT_MUTED = new Color(0x8f8996);
    public static final Color GRID = new Color(0xebe8ee);
    public static final Color BORDER = new Color(0xdcd8e0);
    public static final Color ROW_ALT = new Color(0xfaf9fb);
    public static final Color SELECTION = new Color(0xefe4f2);
    public static final Color HOVER = new Color(0xf4eff6);
    public static final Color TRACK = new Color(0xe9e5ec);
    public static final Color HEADER = new Color(0x37003c);       // Premier League purple
    public static final Color ACCENT = HEADER;
    public static final Color ACCENT_HOVER = new Color(0x51145a);
    public static final Color ACCENT_SOFT = new Color(0xb995c1);
    public static final Color HIGHLIGHT = new Color(0x00ff85);    // Premier League green

    /** Categorical series colours, assigned in this fixed order. */
    public static final Color[] SERIES = {
            new Color(0x2a78d6), new Color(0xeb6834), new Color(0x1baf7a), new Color(0xeda100),
            new Color(0xe87ba4), new Color(0x008300), new Color(0x4a3aa7), new Color(0xe34948)
    };
    public static final Color PRIMARY = SERIES[0];
    public static final Color NEUTRAL = new Color(0xb5b4ae);

    /** Blue sequential ramp, light to dark, for heatmaps. */
    public static final Color[] SEQUENTIAL = {
            new Color(0xf4f8fd), new Color(0xcde2fb), new Color(0xb7d3f6), new Color(0x9ec5f4),
            new Color(0x86b6ef), new Color(0x6da7ec), new Color(0x5598e7), new Color(0x3987e5),
            new Color(0x2a78d6), new Color(0x256abf), new Color(0x1c5cab), new Color(0x184f95),
            new Color(0x104281), new Color(0x0d366b)
    };

    // Status colours (always shown with a letter/label, never colour alone)
    public static final Color GOOD = new Color(0x1f8a4c);
    public static final Color WARN = new Color(0x9a9890);
    public static final Color BAD = new Color(0xd03b3b);

    // Zone markers in the league table
    public static final Color ZONE_UCL = new Color(0x2a78d6);
    public static final Color ZONE_EUROPE = new Color(0xeb6834);
    public static final Color ZONE_RELEGATION = new Color(0xe34948);

    // Whole-point sizes only: fractional sizes make Segoe UI mis-space letters ("tim e") and clip labels.
    public static final Font BASE = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font BOLD = BASE.deriveFont(Font.BOLD);
    public static final Font SEMIBOLD = semibold(13);
    public static final Font SMALL = BASE.deriveFont(12f);
    public static final Font TITLE = semibold(17);
    public static final Font HERO = BASE.deriveFont(Font.BOLD, 28f);

    private static Font semibold(int size) {
        Font f = new Font("Segoe UI Semibold", Font.PLAIN, size);
        return f.getFamily().equals(Font.DIALOG) ? BASE.deriveFont(Font.BOLD, (float) size) : f;
    }

    public static Border padding(int px) { return BorderFactory.createEmptyBorder(px, px, px, px); }

    /** Hairline outline for scroll panes and charts sitting on the page background. */
    public static Border outline() { return BorderFactory.createLineBorder(BORDER); }

    public static void antialias(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    public static JLabel label(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        return l;
    }

    /** Readable text colour on top of a filled background. */
    public static Color inkOn(Color bg) {
        double lum = (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) / 255;
        return lum > 0.6 ? TEXT : Color.WHITE;
    }

    public static Color sequential(double t) {
        t = Math.max(0, Math.min(1, t));
        return SEQUENTIAL[(int) Math.round(t * (SEQUENTIAL.length - 1))];
    }

    public static String pct(double p) {
        if (p <= 0) return "-";
        if (p < 0.001) return "<0.1%";
        if (p > 0.999 && p < 1) return ">99.9%";
        return String.format("%.1f%%", p * 100);
    }

    /** A white card with a rounded hairline outline, for tiles and grouped content. */
    public static class Card extends JPanel {
        public Card(LayoutManager layout) {
            super(layout);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            antialias(g2);
            g2.setColor(SURFACE);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.setColor(BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ tables

    public static void styleTable(JTable table) {
        table.setFont(BASE);
        table.setRowHeight(30);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(false);
        table.setGridColor(GRID);
        table.setSelectionBackground(SELECTION);
        table.setSelectionForeground(TEXT);
        table.setFillsViewportHeight(true);
        table.setBackground(SURFACE);
        table.setAutoCreateRowSorter(false);
        table.setDefaultRenderer(Object.class, new StripedRenderer());
        table.setDefaultRenderer(String.class, new StripedRenderer());
        table.setDefaultRenderer(Integer.class, new NumberRenderer("%d"));
        table.setDefaultRenderer(Double.class, new NumberRenderer("%.2f"));

        JTableHeader header = table.getTableHeader();
        header.setFont(SEMIBOLD.deriveFont(12f));
        header.setBackground(SURFACE);
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new HeaderRenderer());
        header.setPreferredSize(new Dimension(0, 34));
    }

    public static class StripedRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col) {
            super.getTableCellRendererComponent(t, v, sel, false, row, col);
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            if (!sel) setBackground(row % 2 == 0 ? SURFACE : ROW_ALT);
            setForeground(TEXT);
            return this;
        }
    }

    public static class NumberRenderer extends StripedRenderer {
        private final String format;
        public NumberRenderer(String format) { this.format = format; setHorizontalAlignment(SwingConstants.RIGHT); }
        @Override protected void setValue(Object v) {
            setText(v == null ? "" : v instanceof Number n && format.contains("d") ? String.format(format, n.intValue())
                    : v instanceof Number n ? String.format(format, n.doubleValue()) : v.toString());
        }
    }

    public static class PercentRenderer extends StripedRenderer {
        public PercentRenderer() { setHorizontalAlignment(SwingConstants.RIGHT); }
        @Override protected void setValue(Object v) { setText(v instanceof Double d ? pct(d) : ""); }
    }

    /** Draws W/D/L form as small coloured badges that still carry their letter. */
    public static class FormRenderer extends StripedRenderer {
        private String form = "";
        @Override protected void setValue(Object v) { form = v == null ? "" : v.toString(); setText(""); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            antialias(g);
            int size = 18, gap = 4, x = 8, y = (getHeight() - size) / 2;
            g.setFont(BOLD.deriveFont(11f));
            FontMetrics fm = g.getFontMetrics();
            for (char c : form.toCharArray()) {
                Color bg = c == 'W' ? GOOD : c == 'L' ? BAD : WARN;
                g.setColor(bg);
                g.fillRoundRect(x, y, size, size, 9, 9);
                g.setColor(Color.WHITE);
                String s = String.valueOf(c);
                g.drawString(s, x + (size - fm.stringWidth(s)) / 2, y + (size - fm.getHeight()) / 2 + fm.getAscent());
                x += size + gap;
            }
        }
    }

    /** Flat column header: muted label aligned like its data, hairline underneath, arrow on the sorted column. */
    private static class HeaderRenderer extends DefaultTableCellRenderer {
        private final Border border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER), BorderFactory.createEmptyBorder(0, 8, 0, 8));
        private SortOrder order;

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col) {
            super.getTableCellRendererComponent(t, v, false, false, row, col);
            setFont(SEMIBOLD.deriveFont(12f));
            setForeground(TEXT_SECONDARY);
            setBackground(SURFACE);
            setBorder(border);

            TableCellRenderer cell = t.getColumnModel().getColumn(col).getCellRenderer();
            if (cell == null) cell = t.getDefaultRenderer(t.getColumnClass(col));
            int align = cell instanceof JLabel jl ? jl.getHorizontalAlignment() : LEFT;
            if (align == LEADING) align = LEFT;
            setHorizontalAlignment(align);
            setHorizontalTextPosition(align == RIGHT ? LEFT : RIGHT);

            order = null;
            RowSorter<?> sorter = t.getRowSorter();
            if (sorter != null) {
                for (RowSorter.SortKey key : sorter.getSortKeys()) {
                    if (key.getColumn() == t.convertColumnIndexToModel(col)) { order = key.getSortOrder(); break; }
                }
            }
            if (order == SortOrder.ASCENDING || order == SortOrder.DESCENDING) setForeground(TEXT);
            else order = null;
            setToolTipText("Click to sort by " + v);
            return this;
        }

        /** The sorted column gets a purple underline and a small arrow above its label (takes no text width). */
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (order == null) return;
            Graphics2D g2 = (Graphics2D) g.create();
            antialias(g2);
            g2.setColor(HEADER);
            g2.fillRect(0, getHeight() - 2, getWidth(), 2);
            int cx = getWidth() / 2, y = 3;
            boolean up = order == SortOrder.ASCENDING;
            g2.fillPolygon(new int[]{cx - 4, cx + 4, cx}, up ? new int[]{y + 4, y + 4, y} : new int[]{y, y, y + 4}, 3);
            g2.dispose();
        }
    }
}
