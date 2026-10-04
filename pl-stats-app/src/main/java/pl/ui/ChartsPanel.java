package pl.ui;

import pl.data.LeagueData;
import pl.data.LeagueData.TableRow;
import pl.data.Models.Player;
import pl.data.Models.Position;
import pl.data.Models.Team;
import pl.stats.MatchModel;
import pl.ui.charts.BarChart;
import pl.ui.charts.LineChart;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleFunction;
import java.util.function.ToDoubleFunction;

/** Interactive charts: team rankings, player leaderboards and the points race. */
public class ChartsPanel extends JPanel implements ListTableModel.DataView {

    private enum Kind { TEAM, PLAYER, PROGRESSION }

    private record ChartDef(String name, Kind kind, ToDoubleFunction<Object> value, DoubleFunction<String> fmt) {
        @Override public String toString() { return name; }
    }

    private static final DoubleFunction<String> INT = v -> String.format("%.0f", v);
    private static final DoubleFunction<String> DEC = v -> String.format("%.2f", v);

    private static ChartDef team(String name, ToDoubleFunction<TableRow> f, DoubleFunction<String> fmt) {
        return new ChartDef(name, Kind.TEAM, o -> f.applyAsDouble((TableRow) o), fmt);
    }

    private static ChartDef player(String name, ToDoubleFunction<Player> f, DoubleFunction<String> fmt) {
        return new ChartDef(name, Kind.PLAYER, o -> f.applyAsDouble((Player) o), fmt);
    }

    private LeagueData data;
    private MatchModel model;
    private final JComboBox<ChartDef> chartBox = new JComboBox<>();
    private final JComboBox<Object> teamBox = new JComboBox<>();
    private final JComboBox<Object> posBox = new JComboBox<>();
    private final JSpinner topN = new JSpinner(new SpinnerNumberModel(15, 5, 50, 5));
    private final JSpinner minMinutes = new JSpinner(new SpinnerNumberModel(270, 0, 3420, 90));
    private final JPanel playerFilters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    private final BarChart bar = new BarChart();
    private final LineChart line = new LineChart();
    private final JPanel cards = new JPanel(new CardLayout());
    private final JPanel teamChecks = new JPanel();
    /** Colour slot per selected team, so a team keeps its colour when others are toggled. */
    private final Map<Integer, Integer> colourSlot = new LinkedHashMap<>();
    private final Map<Integer, JCheckBox> checkByTeam = new LinkedHashMap<>();
    private boolean updating;

    public ChartsPanel() {
        super(new BorderLayout(0, 10));
        setBackground(Theme.SURFACE);
        setBorder(Theme.padding(16));

        List<ChartDef> defs = List.of(
                new ChartDef("Points race (cumulative points by gameweek)", Kind.PROGRESSION, o -> 0, INT),
                team("Teams - points", r -> r.points, INT),
                team("Teams - points per game", TableRow::pointsPerGame, DEC),
                team("Teams - goals scored", r -> r.goalsFor, INT),
                team("Teams - goals conceded", r -> r.goalsAgainst, INT),
                team("Teams - goal difference", TableRow::goalDiff, v -> (v > 0 ? "+" : "") + String.format("%.0f", v)),
                team("Teams - wins", r -> r.won, INT),
                team("Teams - attack rating (model)", r -> model.attack(r.team.id()), DEC),
                team("Teams - defensive strength (model, higher = better)", r -> 1 / model.defence(r.team.id()), DEC),
                player("Players - goals", Player::goals, INT),
                player("Players - assists", Player::assists, INT),
                player("Players - goal involvements (G+A)", p -> p.goals() + p.assists(), INT),
                player("Players - expected goals (xG)", Player::xG, DEC),
                player("Players - expected assists (xA)", Player::xA, DEC),
                player("Players - goals per 90", Player::goalsPer90, DEC),
                player("Players - xG + xA per 90", Player::xgiPer90, DEC),
                player("Players - goals minus xG (finishing)", p -> p.goals() - p.xG(), DEC),
                player("Players - FPL points", Player::totalPoints, INT),
                player("Players - clean sheets", Player::cleanSheets, INT),
                player("Players - saves", Player::saves, INT),
                player("Players - minutes", Player::minutes, INT),
                player("Players - yellow cards", Player::yellowCards, INT),
                player("Players - price (£m)", Player::price, v -> String.format("%.1f", v)));
        defs.forEach(chartBox::addItem);
        chartBox.setSelectedIndex(1);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        top.setOpaque(false);
        top.add(Theme.label("Chart", Theme.SMALL, Theme.TEXT_SECONDARY));
        top.add(chartBox);
        playerFilters.setOpaque(false);
        playerFilters.add(Theme.label("Team", Theme.SMALL, Theme.TEXT_SECONDARY));
        playerFilters.add(teamBox);
        playerFilters.add(Theme.label("Position", Theme.SMALL, Theme.TEXT_SECONDARY));
        playerFilters.add(posBox);
        playerFilters.add(Theme.label("Top", Theme.SMALL, Theme.TEXT_SECONDARY));
        playerFilters.add(topN);
        playerFilters.add(Theme.label("Min. minutes (per-90 charts)", Theme.SMALL, Theme.TEXT_SECONDARY));
        playerFilters.add(minMinutes);
        top.add(playerFilters);

        teamChecks.setLayout(new BoxLayout(teamChecks, BoxLayout.Y_AXIS));
        teamChecks.setBackground(Theme.SURFACE);
        JScrollPane checksScroll = new JScrollPane(teamChecks);
        checksScroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Theme.GRID), "Teams (max 8)"));
        checksScroll.setPreferredSize(new java.awt.Dimension(190, 400));

        JPanel lineCard = new JPanel(new BorderLayout(10, 0));
        lineCard.setOpaque(false);
        lineCard.add(checksScroll, BorderLayout.WEST);
        lineCard.add(framed(line), BorderLayout.CENTER);

        JScrollPane barScroll = new JScrollPane(bar);
        barScroll.setBorder(BorderFactory.createLineBorder(Theme.GRID));
        barScroll.getVerticalScrollBar().setUnitIncrement(16);
        cards.add(barScroll, "bar");
        cards.add(lineCard, "line");
        cards.setOpaque(false);

        chartBox.addActionListener(e -> redraw());
        teamBox.addActionListener(e -> redraw());
        posBox.addActionListener(e -> redraw());
        topN.addChangeListener(e -> redraw());
        minMinutes.addChangeListener(e -> redraw());

        add(top, BorderLayout.NORTH);
        add(cards, BorderLayout.CENTER);
        add(Theme.label("Hover over bars or lines for exact values.", Theme.SMALL, Theme.TEXT_MUTED), BorderLayout.SOUTH);
    }

    private static JComponent framed(JComponent c) {
        c.setBorder(BorderFactory.createLineBorder(Theme.GRID));
        return c;
    }

    @Override public void setData(LeagueData data, MatchModel model) {
        this.data = data;
        this.model = model;
        updating = true;
        teamBox.removeAllItems();
        teamBox.addItem("All teams");
        data.teams().forEach(teamBox::addItem);
        posBox.removeAllItems();
        posBox.addItem("All positions");
        for (Position p : Position.values()) posBox.addItem(p);

        // Default the points race to the current top four.
        teamChecks.removeAll();
        checkByTeam.clear();
        colourSlot.clear();
        List<TableRow> table = data.table();
        for (Team t : data.teams()) {
            JCheckBox cb = new JCheckBox(t.name());
            cb.setOpaque(false);
            cb.setFont(Theme.BASE);
            cb.addActionListener(e -> toggleTeam(t.id(), cb));
            checkByTeam.put(t.id(), cb);
            teamChecks.add(cb);
        }
        for (int i = 0; i < Math.min(4, table.size()); i++) {
            int id = table.get(i).team.id();
            checkByTeam.get(id).setSelected(true);
            colourSlot.put(id, freeSlot());
        }
        teamChecks.revalidate();
        updating = false;
        redraw();
    }

    private void toggleTeam(int teamId, JCheckBox cb) {
        if (cb.isSelected()) {
            int slot = freeSlot();
            if (slot < 0) { cb.setSelected(false); return; } // 8 series max: beyond that lines become unreadable
            colourSlot.put(teamId, slot);
        } else {
            colourSlot.remove(teamId);
        }
        redraw();
    }

    private int freeSlot() {
        for (int s = 0; s < Theme.SERIES.length; s++) if (!colourSlot.containsValue(s)) return s;
        return -1;
    }

    private void redraw() {
        if (updating || data == null) return;
        ChartDef def = (ChartDef) chartBox.getSelectedItem();
        if (def == null) return;
        playerFilters.setVisible(def.kind() == Kind.PLAYER);
        CardLayout cl = (CardLayout) cards.getLayout();

        switch (def.kind()) {
            case PROGRESSION -> {
                cl.show(cards, "line");
                Map<Integer, int[]> prog = data.pointsProgression();
                List<LineChart.Series> series = new ArrayList<>();
                colourSlot.forEach((id, slot) ->
                        series.add(new LineChart.Series(data.team(id).name(), Theme.SERIES[slot], prog.get(id))));
                line.setData("Points race", "Gameweek", series);
            }
            case TEAM -> {
                cl.show(cards, "bar");
                List<TableRow> rows = new ArrayList<>(data.table());
                rows.sort(Comparator.comparingDouble((TableRow r) -> def.value().applyAsDouble(r)).reversed());
                showBars(def, rows.stream().map(r -> r.team.name()).toList(),
                        rows.stream().mapToDouble(r -> def.value().applyAsDouble(r)).toArray(), Theme.PRIMARY);
            }
            case PLAYER -> {
                cl.show(cards, "bar");
                Object team = teamBox.getSelectedItem();
                Object pos = posBox.getSelectedItem();
                boolean per90 = def.name().contains("per 90");
                int minMins = per90 ? (Integer) minMinutes.getValue() : 1;
                List<Player> ps = new ArrayList<>();
                for (Player p : data.players()) {
                    if (team instanceof Team t && p.teamId() != t.id()) continue;
                    if (pos instanceof Position po && p.position() != po) continue;
                    if (p.minutes() < minMins) continue;
                    ps.add(p);
                }
                ps.sort(Comparator.comparingDouble((Player p) -> def.value().applyAsDouble(p)).reversed());
                ps = ps.subList(0, Math.min((Integer) topN.getValue(), ps.size()));
                showBars(def, ps.stream().map(p -> p.webName() + " (" + data.team(p.teamId()).shortName() + ")").toList(),
                        ps.stream().mapToDouble(p -> def.value().applyAsDouble(p)).toArray(), Theme.SERIES[2]);
            }
        }
    }

    private void showBars(ChartDef def, List<String> labels, double[] values, Color color) {
        String title = def.name().replaceFirst("^(Teams|Players) - ", "");
        title = Character.toUpperCase(title.charAt(0)) + title.substring(1);
        bar.setData(title, labels, values, color, def.fmt());
    }
}
