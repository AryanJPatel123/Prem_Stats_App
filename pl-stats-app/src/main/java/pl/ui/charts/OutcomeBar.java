package pl.ui.charts;

import pl.ui.Theme;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

/** A single 100% stacked bar: home win / draw / away win, each segment labelled. */
public class OutcomeBar extends JComponent {
    private static final Color HOME = Theme.SERIES[0], DRAW = Theme.NEUTRAL, AWAY = Theme.SERIES[1];
    private double home, draw, away;
    private String homeName = "", awayName = "";

    public OutcomeBar() { setOpaque(false); }

    public void setData(String homeName, double home, double draw, double away, String awayName) {
        this.homeName = homeName;
        this.awayName = awayName;
        this.home = home;
        this.draw = draw;
        this.away = away;
        repaint();
    }

    @Override public Dimension getPreferredSize() { return new Dimension(600, 92); }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Theme.antialias(g);
        double total = home + draw + away;
        if (total <= 0) { g.dispose(); return; }

        int x = 0, w = getWidth(), barY = 30, barH = 30;
        int wH = (int) Math.round(w * home / total);
        int wD = (int) Math.round(w * draw / total);
        int wA = w - wH - wD;

        // Big headline numbers above each segment
        String[] names = {homeName + " win", "Draw", awayName + " win"};
        double[] vals = {home, draw, away};
        int[] starts = {0, wH, wH + wD};
        int[] widths = {wH, wD, wA};
        Color[] cols = {HOME, DRAW, AWAY};

        g.setColor(cols[0]);
        g.fillRoundRect(x, barY, wH + 8, barH, 8, 8);
        g.setColor(cols[2]);
        g.fillRoundRect(wH + wD - 8, barY, wA + 8, barH, 8, 8);
        g.setColor(cols[1]);
        g.fillRect(wH, barY, wD, barH);
        // 2px surface gaps between segments
        g.setColor(Theme.SURFACE);
        g.fillRect(wH - 1, barY, 2, barH);
        g.fillRect(wH + wD - 1, barY, 2, barH);

        for (int i = 0; i < 3; i++) {
            String pct = String.format("%.0f%%", vals[i] * 100);
            g.setFont(Theme.BOLD);
            FontMetrics fm = g.getFontMetrics();
            if (widths[i] > fm.stringWidth(pct) + 10) {
                g.setColor(Theme.inkOn(cols[i]));
                g.drawString(pct, starts[i] + widths[i] / 2 - fm.stringWidth(pct) / 2, barY + 20);
            }
            g.setFont(Theme.SMALL);
            fm = g.getFontMetrics();
            g.setColor(Theme.TEXT_SECONDARY);
            String label = names[i] + "  " + Theme.pct(vals[i]);
            int lx = i == 0 ? 0 : i == 2 ? w - fm.stringWidth(label) : w / 2 - fm.stringWidth(label) / 2;
            g.drawString(label, lx, i == 1 ? barY + barH + 18 : 18);
        }
        g.dispose();
    }
}
