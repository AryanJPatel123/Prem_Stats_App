package pl.ui.charts;

import pl.ui.Theme;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Multi-series line chart with a hover crosshair that lists every series' value. */
public class LineChart extends JComponent {
    public record Series(String name, Color color, int[] values) { }

    private String title = "";
    private String xLabel = "";
    private List<Series> series = List.of();
    private int hoverX = -1;
    // Plot geometry from the last paint, used for hover hit-testing.
    private int left, right, top, bottom, points;

    public LineChart() {
        setOpaque(true);
        setBackground(Theme.SURFACE);
        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { updateHover(e.getX()); }
            @Override public void mouseExited(MouseEvent e) { hoverX = -1; repaint(); }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    public void setData(String title, String xLabel, List<Series> series) {
        this.title = title;
        this.xLabel = xLabel;
        this.series = series;
        repaint();
    }

    private void updateHover(int mx) {
        int idx = -1;
        if (points > 1 && mx >= left - 10 && mx <= right + 10) {
            idx = (int) Math.round((mx - left) / (double) (right - left) * (points - 1));
            idx = Math.max(0, Math.min(points - 1, idx));
        }
        if (idx != hoverX) { hoverX = idx; repaint(); }
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Theme.antialias(g);
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());

        g.setFont(Theme.TITLE);
        g.setColor(Theme.TEXT);
        g.drawString(title, 16, 26);

        if (series.isEmpty() || series.get(0).values().length < 2) {
            g.setFont(Theme.BASE);
            g.setColor(Theme.TEXT_MUTED);
            g.drawString(series.isEmpty() ? "Tick one or more teams on the left to plot them."
                    : "Not enough completed gameweeks yet.", 16, 56);
            g.dispose();
            return;
        }

        // Legend row (always present for 2+ series)
        g.setFont(Theme.SMALL);
        FontMetrics fm = g.getFontMetrics();
        int lx = 16, ly = 46;
        for (Series s : series) {
            int w = 14 + fm.stringWidth(s.name()) + 16;
            if (lx + w > getWidth() - 16) { lx = 16; ly += 18; }
            g.setColor(s.color());
            g.fillRoundRect(lx, ly - 9, 10, 10, 3, 3);
            g.setColor(Theme.TEXT_SECONDARY);
            g.drawString(s.name(), lx + 14, ly);
            lx += w;
        }

        points = series.get(0).values().length;
        int max = 1;
        for (Series s : series) for (int v : s.values()) max = Math.max(max, v);
        double step = BarChart.niceStep(max / 5.0);
        double yMax = Math.ceil(max / step) * step;

        int labelRoom = 0;
        g.setFont(Theme.SMALL);
        for (Series s : series) labelRoom = Math.max(labelRoom, g.getFontMetrics().stringWidth(s.name()));
        boolean directLabels = series.size() <= 4;

        left = 48;
        right = getWidth() - 20 - (directLabels ? labelRoom + 10 : 0);
        top = ly + 18;
        bottom = getHeight() - 40;

        // Grid and y axis labels
        for (double t = 0; t <= yMax + 1e-9; t += step) {
            int y = yFor(t, yMax);
            g.setColor(Theme.GRID);
            g.drawLine(left, y, right, y);
            g.setColor(Theme.TEXT_MUTED);
            String s = String.format("%.0f", t);
            g.drawString(s, left - 8 - g.getFontMetrics().stringWidth(s), y + 4);
        }
        // x axis labels
        int every = Math.max(1, (int) Math.ceil(points / 15.0));
        for (int i = 0; i < points; i += every) {
            String s = String.valueOf(i);
            g.drawString(s, xFor(i) - g.getFontMetrics().stringWidth(s) / 2, bottom + 16);
        }
        g.setColor(Theme.TEXT_SECONDARY);
        g.drawString(xLabel, (left + right) / 2 - g.getFontMetrics().stringWidth(xLabel) / 2, bottom + 32);

        // Lines (2px)
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Series s : series) {
            Path2D path = new Path2D.Double();
            for (int i = 0; i < points; i++) {
                if (i == 0) path.moveTo(xFor(i), yFor(s.values()[i], yMax));
                else path.lineTo(xFor(i), yFor(s.values()[i], yMax));
            }
            g.setColor(s.color());
            g.draw(path);
        }

        // Direct end labels, nudged apart so they don't collide
        if (directLabels) {
            List<int[]> ends = new ArrayList<>(); // {y, seriesIndex}
            for (int i = 0; i < series.size(); i++)
                ends.add(new int[]{yFor(series.get(i).values()[points - 1], yMax), i});
            ends.sort(Comparator.comparingInt(a -> a[0]));
            for (int i = 1; i < ends.size(); i++)
                if (ends.get(i)[0] - ends.get(i - 1)[0] < 14) ends.get(i)[0] = ends.get(i - 1)[0] + 14;
            g.setFont(Theme.SMALL);
            g.setColor(Theme.TEXT_SECONDARY);
            for (int[] e : ends) g.drawString(series.get(e[1]).name(), right + 8, e[0] + 4);
        }

        // Hover crosshair + tooltip box
        if (hoverX >= 0) {
            int x = xFor(hoverX);
            g.setStroke(new BasicStroke(1f));
            g.setColor(Theme.TEXT_MUTED);
            g.drawLine(x, top, x, bottom);
            List<Series> sorted = new ArrayList<>(series);
            sorted.sort((a, b) -> b.values()[hoverX] - a.values()[hoverX]);
            for (Series s : series) {
                int y = yFor(s.values()[hoverX], yMax);
                g.setColor(Theme.SURFACE);
                g.fillOval(x - 6, y - 6, 12, 12);
                g.setColor(s.color());
                g.fillOval(x - 4, y - 4, 8, 8);
            }
            g.setFont(Theme.SMALL);
            fm = g.getFontMetrics();
            int boxW = 0;
            for (Series s : sorted) boxW = Math.max(boxW, fm.stringWidth(s.name() + "  " + s.values()[hoverX] + " pts"));
            boxW += 34;
            int boxH = 24 + sorted.size() * 16;
            int bx = x + 12 + boxW > getWidth() ? x - 12 - boxW : x + 12;
            int by = top + 4;
            g.setColor(new Color(255, 255, 255, 240));
            g.fillRoundRect(bx, by, boxW, boxH, 8, 8);
            g.setColor(Theme.GRID);
            g.drawRoundRect(bx, by, boxW, boxH, 8, 8);
            g.setColor(Theme.TEXT);
            g.setFont(Theme.BOLD.deriveFont(11.5f));
            g.drawString(xLabel + " " + hoverX, bx + 10, by + 16);
            g.setFont(Theme.SMALL);
            int ry = by + 32;
            for (Series s : sorted) {
                g.setColor(s.color());
                g.fillRoundRect(bx + 10, ry - 9, 9, 9, 3, 3);
                g.setColor(Theme.TEXT);
                g.drawString(s.name(), bx + 24, ry);
                String v = s.values()[hoverX] + " pts";
                g.drawString(v, bx + boxW - 10 - fm.stringWidth(v), ry);
                ry += 16;
            }
        }
        g.dispose();
    }

    private int xFor(int i) { return left + (int) Math.round((right - left) * i / (double) (points - 1)); }
    private int yFor(double v, double yMax) { return bottom - (int) Math.round((bottom - top) * v / yMax); }
}
