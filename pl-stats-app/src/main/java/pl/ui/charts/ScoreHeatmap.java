package pl.ui.charts;

import pl.ui.Theme;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;

/** Grid of scoreline probabilities: rows = home goals, columns = away goals. */
public class ScoreHeatmap extends JComponent {
    private static final int SHOW = 6; // 0..5 goals each side
    private double[][] matrix;
    private String homeName = "Home", awayName = "Away";
    private int originX, originY, cell;

    public ScoreHeatmap() {
        setOpaque(true);
        setBackground(Theme.SURFACE);
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    public void setData(double[][] matrix, String homeName, String awayName) {
        this.matrix = matrix;
        this.homeName = homeName;
        this.awayName = awayName;
        repaint();
    }

    @Override public Dimension getPreferredSize() { return new Dimension(380, 360); }

    @Override public String getToolTipText(MouseEvent e) {
        if (matrix == null || cell == 0) return null;
        int a = (e.getX() - originX) / cell, h = (e.getY() - originY) / cell;
        if (e.getX() < originX || e.getY() < originY || a >= SHOW || h >= SHOW) return null;
        return String.format("<html><b>%s %d - %d %s</b><br>%s</html>", homeName, h, a, awayName, Theme.pct(matrix[h][a]));
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Theme.antialias(g);
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setFont(Theme.TITLE);
        g.setColor(Theme.TEXT);
        g.drawString("Scoreline probabilities", 12, 22);
        if (matrix == null) { g.dispose(); return; }

        double max = 0;
        for (int h = 0; h < SHOW; h++) for (int a = 0; a < SHOW; a++) max = Math.max(max, matrix[h][a]);

        int labelSpace = 40;
        cell = Math.max(24, Math.min((getWidth() - labelSpace - 24) / SHOW, (getHeight() - 84) / SHOW));
        originX = labelSpace + 12;
        originY = 64;

        g.setFont(Theme.SMALL);
        g.setColor(Theme.TEXT_SECONDARY);
        g.drawString(awayName + " goals →", originX, 44);
        // Rotated row axis label
        Graphics2D r = (Graphics2D) g.create();
        r.rotate(-Math.PI / 2);
        r.drawString(homeName + " goals →", -(originY + cell * SHOW), 16);
        r.dispose();

        FontMetrics fm = g.getFontMetrics();
        for (int i = 0; i < SHOW; i++) {
            String s = String.valueOf(i);
            g.setColor(Theme.TEXT_MUTED);
            g.drawString(s, originX + i * cell + cell / 2 - fm.stringWidth(s) / 2, originY - 6);
            g.drawString(s, originX - 12, originY + i * cell + cell / 2 + 4);
        }

        g.setFont(cell >= 44 ? Theme.SMALL : Theme.SMALL.deriveFont(9.5f));
        fm = g.getFontMetrics();
        for (int h = 0; h < SHOW; h++) {
            for (int a = 0; a < SHOW; a++) {
                double p = matrix[h][a];
                Color c = Theme.sequential(max == 0 ? 0 : p / max);
                int x = originX + a * cell, y = originY + h * cell;
                g.setColor(c);
                g.fillRoundRect(x + 1, y + 1, cell - 2, cell - 2, 6, 6); // 2px surface gap between cells
                String s = p < 0.0005 ? "" : String.format("%.1f", p * 100);
                g.setColor(Theme.inkOn(c));
                g.drawString(s, x + cell / 2 - fm.stringWidth(s) / 2, y + cell / 2 + fm.getAscent() / 2 - 2);
            }
        }
        g.setColor(Theme.TEXT_MUTED);
        g.setFont(Theme.SMALL);
        g.drawString("Values are % chance of each exact score", originX, originY + SHOW * cell + 16);
        g.dispose();
    }
}
