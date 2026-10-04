package pl.ui;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Colours, fonts and table styling shared by every screen. */
public final class Theme {
    private Theme() { }

    // Surfaces and ink
    public static final Color SURFACE = new Color(0xfcfcfb);
    public static final Color PANEL = new Color(0xf4f3f0);
    public static final Color TEXT = new Color(0x0b0b0b);
    public static final Color TEXT_SECONDARY = new Color(0x52514e);
    public static final Color TEXT_MUTED = new Color(0x8a8984);
    public static final Color GRID = new Color(0xe6e5e1);
    public static final Color ROW_ALT = new Color(0xf6f5f2);
    public static final Color SELECTION = new Color(0xcde2fb);
    public static final Color HEADER = new Color(0x37003c); // Premier League purple

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

    public static final Font BASE = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font BOLD = BASE.deriveFont(Font.BOLD);
    public static final Font SMALL = BASE.deriveFont(11.5f);
    public static final Font TITLE = BASE.deriveFont(Font.BOLD, 15f);
    public static final Font HERO = BASE.deriveFont(Font.BOLD, 26f);

    public static Border padding(int px) { return BorderFactory.createEmptyBorder(px, px, px, px); }

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

    // ------------------------------------------------------------------ tables

    public static void styleTable(JTable table) {
        table.setFont(BASE);
        table.setRowHeight(26);
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
        header.setFont(BOLD.deriveFont(12f));
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new HeaderRenderer(header.getDefaultRenderer()));
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
            int size = 18, gap = 3, x = 8, y = (getHeight() - size) / 2;
            g.setFont(BOLD.deriveFont(10.5f));
            for (char c : form.toCharArray()) {
                Color bg = c == 'W' ? GOOD : c == 'L' ? BAD : WARN;
                g.setColor(bg);
                g.fillRoundRect(x, y, size, size, 6, 6);
                g.setColor(Color.WHITE);
                String s = String.valueOf(c);
                int w = g.getFontMetrics().stringWidth(s);
                g.drawString(s, x + (size - w) / 2, y + 13);
                x += size + gap;
            }
        }
    }

    private static class HeaderRenderer implements TableCellRenderer {
        private final TableCellRenderer delegate;
        HeaderRenderer(TableCellRenderer delegate) { this.delegate = delegate; }
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col) {
            Component c = delegate.getTableCellRendererComponent(t, v, sel, focus, row, col);
            if (c instanceof JComponent jc) {
                jc.setFont(BOLD.deriveFont(12f));
                jc.setToolTipText("Click to sort by " + v);
            }
            return c;
        }
    }
}
