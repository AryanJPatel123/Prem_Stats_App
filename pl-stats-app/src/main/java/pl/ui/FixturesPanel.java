package pl.ui;

import pl.data.LeagueData;
import pl.data.Models.Fixture;
import pl.data.Models.Team;
import pl.stats.MatchModel;
import pl.stats.MatchModel.Prediction;
import pl.ui.ListTableModel.Col;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Results and upcoming fixtures, filterable by team, gameweek and status. */
public class FixturesPanel extends JPanel implements ListTableModel.DataView {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM yyyy  HH:mm");
    private static final String ALL_TEAMS = "All teams", ALL_GWS = "All gameweeks";

    private LeagueData data;
    private final Map<Integer, Prediction> predictions = new HashMap<>();
    private final ListTableModel<Fixture> tableModel;
    private final JTable table;
    private final JComboBox<Object> teamBox = new JComboBox<>();
    private final JComboBox<String> gwBox = new JComboBox<>();
    private final JComboBox<String> statusBox = new JComboBox<>(new String[]{"All matches", "Results", "Upcoming"});
    private final JLabel count = Theme.label("", Theme.SMALL, Theme.TEXT_SECONDARY);
    private boolean updating;

    public FixturesPanel(Consumer<Fixture> openInPredictor) {
        super(new BorderLayout(0, 10));
        setBackground(Theme.SURFACE);
        setBorder(Theme.padding(16));

        Theme.StripedRenderer centred = new Theme.StripedRenderer();
        centred.setHorizontalAlignment(SwingConstants.CENTER);
        Theme.StripedRenderer right = new Theme.StripedRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        Theme.StripedRenderer when = new Theme.StripedRenderer() {
            @Override protected void setValue(Object v) { setText(v instanceof LocalDateTime t ? WHEN.format(t) : "TBC"); }
        };

        tableModel = new ListTableModel<>(List.of(
                new Col<Fixture>("GW", Integer.class, 45, Fixture::gameweek),
                new Col<Fixture>("Kick-off (local time)", LocalDateTime.class, 170,
                        f -> f.kickoff() == null ? null : LocalDateTime.ofInstant(f.kickoff(), ZoneId.systemDefault()), when),
                new Col<Fixture>("Home", String.class, 150, f -> data.team(f.homeId()).name(), right),
                new Col<Fixture>("Score", String.class, 70, this::scoreText, centred),
                new Col<Fixture>("Away", String.class, 150, f -> data.team(f.awayId()).name()),
                new Col<Fixture>("Home win", Double.class, 80, f -> prob(f, Prediction::homeWin), new Theme.PercentRenderer()),
                new Col<Fixture>("Draw", Double.class, 70, f -> prob(f, Prediction::draw), new Theme.PercentRenderer()),
                new Col<Fixture>("Away win", Double.class, 80, f -> prob(f, Prediction::awayWin), new Theme.PercentRenderer()),
                new Col<Fixture>("Most likely score", String.class, 120, this::likelyScore, centred),
                new Col<Fixture>("Difficulty (H/A)", String.class, 110,
                        f -> f.homeDifficulty() + " / " + f.awayDifficulty(), centred)
        ));
        table = tableModel.createTable();
        table.setToolTipText("Double-click a fixture to open it in the Match Predictor");
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    Fixture f = tableModel.selected(table);
                    if (f != null) openInPredictor.accept(f);
                }
            }
        });

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filters.setOpaque(false);
        filters.add(Theme.label("Fixtures & results", Theme.TITLE, Theme.TEXT));
        filters.add(javax.swing.Box.createHorizontalStrut(16));
        filters.add(Theme.label("Team", Theme.SMALL, Theme.TEXT_SECONDARY));
        filters.add(teamBox);
        filters.add(Theme.label("Gameweek", Theme.SMALL, Theme.TEXT_SECONDARY));
        filters.add(gwBox);
        filters.add(Theme.label("Show", Theme.SMALL, Theme.TEXT_SECONDARY));
        filters.add(statusBox);
        JButton predict = new JButton("Open in predictor");
        predict.addActionListener(e -> {
            Fixture f = tableModel.selected(table);
            if (f != null) openInPredictor.accept(f);
        });
        filters.add(predict);
        filters.add(count);

        teamBox.addActionListener(e -> refilter());
        gwBox.addActionListener(e -> refilter());
        statusBox.addActionListener(e -> refilter());

        add(filters, BorderLayout.NORTH);
        add(ListTableModel.scroll(table), BorderLayout.CENTER);
        add(Theme.label("Win/draw probabilities come from the Poisson model (see Match Predictor) and are shown for unplayed fixtures. "
                + "Difficulty is FPL's 1-5 fixture difficulty rating.", Theme.SMALL, Theme.TEXT_MUTED), BorderLayout.SOUTH);
    }

    @Override public void setData(LeagueData data, MatchModel model) {
        this.data = data;
        predictions.clear();
        for (Fixture f : data.remaining()) predictions.put(f.id(), model.predict(f.homeId(), f.awayId()));

        updating = true;
        teamBox.removeAllItems();
        teamBox.addItem(ALL_TEAMS);
        data.teams().forEach(teamBox::addItem);
        gwBox.removeAllItems();
        gwBox.addItem(ALL_GWS);
        for (int g = 1; g <= data.maxGameweek(); g++) gwBox.addItem("Gameweek " + g);
        gwBox.setSelectedItem("Gameweek " + data.nextGameweek());
        updating = false;
        refilter();
    }

    private void refilter() {
        if (updating || data == null) return;
        Object team = teamBox.getSelectedItem();
        String gw = (String) gwBox.getSelectedItem();
        int status = statusBox.getSelectedIndex();
        List<Fixture> rows = new ArrayList<>();
        for (Fixture f : data.fixtures()) {
            if (team instanceof Team t && !f.involves(t.id())) continue;
            if (gw != null && !ALL_GWS.equals(gw) && (f.gameweek() == null || !gw.equals("Gameweek " + f.gameweek()))) continue;
            if (status == 1 && !f.hasResult()) continue;
            if (status == 2 && f.hasResult()) continue;
            rows.add(f);
        }
        tableModel.setRows(rows);
        count.setText(rows.size() + " matches");
    }

    private String scoreText(Fixture f) {
        if (f.homeGoals() != null && f.awayGoals() != null)
            return f.homeGoals() + " - " + f.awayGoals() + (f.finished() ? "" : " (live)");
        return "v";
    }

    private Double prob(Fixture f, java.util.function.ToDoubleFunction<Prediction> pick) {
        Prediction p = predictions.get(f.id());
        return p == null ? null : pick.applyAsDouble(p);
    }

    private String likelyScore(Fixture f) {
        Prediction p = predictions.get(f.id());
        if (p == null) return "";
        double[] top = p.topScores(1).get(0);
        return (int) top[0] + " - " + (int) top[1];
    }
}
