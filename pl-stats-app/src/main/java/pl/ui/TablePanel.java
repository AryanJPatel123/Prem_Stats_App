package pl.ui;

import pl.data.LeagueData;
import pl.data.LeagueData.TableRow;
import pl.stats.MatchModel;
import pl.ui.ListTableModel.Col;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.util.List;

/** The league standings, computed from results, with model attack/defence ratings. */
public class TablePanel extends JPanel implements ListTableModel.DataView {
    private MatchModel model;
    private final ListTableModel<TableRow> tableModel;
    private final JLabel subtitle = Theme.label("", Theme.SMALL, Theme.TEXT_SECONDARY);

    public TablePanel() {
        super(new BorderLayout(0, 10));
        setBackground(Theme.SURFACE);
        setBorder(Theme.padding(16));

        tableModel = new ListTableModel<>(List.of(
                new Col<TableRow>("Pos", Integer.class, 50, r -> r.position, new ZoneRenderer()),
                new Col<TableRow>("Club", String.class, 200, r -> r.team.name()),
                new Col<TableRow>("P", Integer.class, 40, r -> r.played),
                new Col<TableRow>("W", Integer.class, 40, r -> r.won),
                new Col<TableRow>("D", Integer.class, 40, r -> r.drawn),
                new Col<TableRow>("L", Integer.class, 40, r -> r.lost),
                new Col<TableRow>("GF", Integer.class, 45, r -> r.goalsFor),
                new Col<TableRow>("GA", Integer.class, 45, r -> r.goalsAgainst),
                new Col<TableRow>("GD", Integer.class, 45, TableRow::goalDiff, new SignedRenderer()),
                new Col<TableRow>("Pts", Integer.class, 50, r -> r.points, boldNumber()),
                new Col<TableRow>("PPG", Double.class, 55, TableRow::pointsPerGame),
                new Col<TableRow>("Form (last 5)", String.class, 130, TableRow::lastFive, new Theme.FormRenderer()),
                new Col<TableRow>("Attack rating", Double.class, 95, r -> model == null ? 0 : model.attack(r.team.id())),
                new Col<TableRow>("Defence rating", Double.class, 100, r -> model == null ? 0 : model.defence(r.team.id()))
        ));
        JTable table = tableModel.createTable();

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(Theme.label("League table", Theme.TITLE, Theme.TEXT), BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.SOUTH);

        add(header, BorderLayout.NORTH);
        add(ListTableModel.scroll(table), BorderLayout.CENTER);
        add(legend(), BorderLayout.SOUTH);
    }

    @Override public void setData(LeagueData data, MatchModel model) {
        this.model = model;
        tableModel.setRows(data.table());
        subtitle.setText("After " + data.results().size() + " matches (up to gameweek " + data.lastCompletedGameweek()
                + ").  Click any column header to sort.  Attack > 1.00 scores more than average; Defence < 1.00 concedes less than average.");
    }

    private JPanel legend() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        p.setOpaque(false);
        p.add(swatch(Theme.ZONE_UCL, "Champions League (1-4)"));
        p.add(swatch(Theme.ZONE_EUROPE, "Europa / Conference League (5-7)"));
        p.add(swatch(Theme.ZONE_RELEGATION, "Relegation (18-20)"));
        return p;
    }

    private static JLabel swatch(Color c, String text) {
        JLabel l = Theme.label(text, Theme.SMALL, Theme.TEXT_SECONDARY);
        l.setIcon(new javax.swing.Icon() {
            public void paintIcon(Component comp, Graphics g, int x, int y) { g.setColor(c); g.fillRect(x, y, 4, 14); }
            public int getIconWidth() { return 8; }
            public int getIconHeight() { return 14; }
        });
        return l;
    }

    private static Theme.NumberRenderer boldNumber() {
        return new Theme.NumberRenderer("%d") {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int row, int col) {
                super.getTableCellRendererComponent(t, v, s, f, row, col);
                setFont(Theme.BOLD);
                return this;
            }
        };
    }

    /** Position number with a coloured stripe marking the table zone. */
    private static class ZoneRenderer extends Theme.NumberRenderer {
        private Color zone;
        ZoneRenderer() { super("%d"); setHorizontalAlignment(CENTER); }
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int row, int col) {
            super.getTableCellRendererComponent(t, v, s, f, row, col);
            int pos = v instanceof Integer i ? i : 0;
            int teams = t.getRowCount();
            zone = pos <= 4 ? Theme.ZONE_UCL : pos <= 7 ? Theme.ZONE_EUROPE : pos > teams - 3 ? Theme.ZONE_RELEGATION : null;
            return this;
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (zone != null) { g.setColor(zone); g.fillRect(0, 0, 4, getHeight()); }
        }
    }

    private static class SignedRenderer extends Theme.NumberRenderer {
        SignedRenderer() { super("%d"); }
        @Override protected void setValue(Object v) {
            int n = v instanceof Integer i ? i : 0;
            setText(n > 0 ? "+" + n : String.valueOf(n));
        }
    }
}
