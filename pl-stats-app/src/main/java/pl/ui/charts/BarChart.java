package pl.ui.charts;

import pl.ui.Theme;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;

/**
 * Horizontal bar chart (ranked lists read best horizontally: long labels, easy comparison).
 * Supports negative values, hover highlight and tooltips.
 */
public class BarChart extends JComponent {
    private String title = "";
    private List<String> labels = List.of();
    private double[] values = new double[0];
    private Color color = Theme.PRIMARY;
    private DoubleFunction<String> format = v -> String.format("%.0f", v);
    private final List<Rectangle> hitAreas = new ArrayList<>();
    private int hover = -1;

    public BarChart() {
        setOpaque(true);
        setBackground(Theme.SURFACE);
        ToolTipManager.sharedInstance().registerComponent(this);
        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { setHover(indexAt(e.getX(), e.getY())); }
            @Override public void mouseExited(MouseEvent e) { setHover(-1); }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    public void setData(String title, List<String> labels, double[] values, Color color, DoubleFunction<String> format) {
        this.title = title;
        this.labels = labels;
        this.values = values;
        this.color = color;
        this.format = format;
        this.hover = -1;
        revalidate();
        repaint();
    }

    private void setHover(int i) { if (i != hover) { hover = i; repaint(); } }

    private int indexAt(int x, int y) {
        for (int i = 0; i < hitAreas.size(); i++) if (hitAreas.get(i).contains(x, y)) return i;
        return -1;
    }

    @Override public String getToolTipText(MouseEvent e) {
        int i = indexAt(e.getX(), e.getY());
        return i < 0 ? null : "<html><b>" + labels.get(i) + "</b><br>" + title + ": " + format.apply(values[i]) + "</html>";
    }

    @Override public Dimension getPreferredSize() {
        return new Dimension(500, Math.max(200, 56 + labels.size() * 26));
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Theme.antialias(g);
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        hitAreas.clear();

        g.setFont(Theme.TITLE);
        g.setColor(Theme.TEXT);
        g.drawString(title, 16, 26);
        if (labels.isEmpty()) {
            g.setFont(Theme.BASE);
            g.setColor(Theme.TEXT_MUTED);
            g.drawString("No data for this selection.", 16, 56);
            g.dispose();
            return;
        }

        g.setFont(Theme.BASE);
        FontMetrics fm = g.getFontMetrics();
        int labelW = 0;
        for (String l : labels) labelW = Math.max(labelW, fm.stringWidth(l));
        int valueW = 0;
        for (double v : values) valueW = Math.max(valueW, fm.stringWidth(format.apply(v)));

        int top = 44, rowH = Math.max(18, Math.min(34, (getHeight() - top - 12) / labels.size()));
        int left = 16 + labelW + 12, right = getWidth() - 16 - valueW - 8;
        double min = 0, max = 0;
        for (double v : values) { min = Math.min(min, v); max = Math.max(max, v); }
        if (max == min) max = min + 1;
        double scale = (right - left) / (max - min);
        int zeroX = (int) Math.round(left + (0 - min) * scale);

        // Recessive gridlines at "nice" ticks
        g.setFont(Theme.SMALL);
        double step = niceStep((max - min) / 5);
        g.setStroke(new BasicStroke(1f));
        for (double t = Math.ceil(min / step) * step; t <= max + 1e-9; t += step) {
            int x = (int) Math.round(left + (t - min) * scale);
            g.setColor(Theme.GRID);
            g.drawLine(x, top - 4, x, top + rowH * labels.size());
            g.setColor(Theme.TEXT_MUTED);
            String s = format.apply(t);
            g.drawString(s, x - g.getFontMetrics().stringWidth(s) / 2, top + rowH * labels.size() + 14);
        }

        int barH = Math.max(8, (int) (rowH * 0.62));
        for (int i = 0; i < labels.size(); i++) {
            int y = top + i * rowH;
            double v = values[i];
            int x1 = (int) Math.round(left + (Math.min(0, v) - min) * scale);
            int x2 = (int) Math.round(left + (Math.max(0, v) - min) * scale);
            Color fill = v < 0 ? Theme.SERIES[7] : color;
            if (hover >= 0 && hover != i) fill = blend(fill, Theme.SURFACE, 0.45);
            g.setColor(fill);
            int by = y + (rowH - barH) / 2;
            int w = Math.max(2, x2 - x1);
            // Square at the baseline, 4px rounded at the data end.
            g.fill(new RoundRectangle2D.Double(x1, by, w, barH, 8, 8));
            if (v >= 0) g.fillRect(x1, by, Math.min(4, w), barH);
            else g.fillRect(x2 - Math.min(4, w), by, Math.min(4, w), barH);

            g.setFont(i == hover ? Theme.BOLD : Theme.BASE);
            fm = g.getFontMetrics();
            g.setColor(Theme.TEXT);
            int textY = y + rowH / 2 + fm.getAscent() / 2 - 2;
            g.drawString(labels.get(i), left - 12 - fm.stringWidth(labels.get(i)), textY);
            g.setColor(Theme.TEXT_SECONDARY);
            String vs = format.apply(v);
            if (v >= 0) g.drawString(vs, x2 + 6, textY);
            else g.drawString(vs, x1 - 6 - fm.stringWidth(vs), textY);

            hitAreas.add(new Rectangle(0, y, getWidth(), rowH));
        }
        // Baseline
        g.setColor(Theme.TEXT_MUTED);
        g.drawLine(zeroX, top - 4, zeroX, top + rowH * labels.size());
        g.dispose();
    }

    static double niceStep(double raw) {
        if (raw <= 0) return 1;
        double mag = Math.pow(10, Math.floor(Math.log10(raw)));
        double n = raw / mag;
        return (n < 1.5 ? 1 : n < 3 ? 2 : n < 7 ? 5 : 10) * mag;
    }

    static Color blend(Color a, Color b, double t) {
        return new Color(
                (int) (a.getRed() * (1 - t) + b.getRed() * t),
                (int) (a.getGreen() * (1 - t) + b.getGreen() * t),
                (int) (a.getBlue() * (1 - t) + b.getBlue() * t));
    }
}
