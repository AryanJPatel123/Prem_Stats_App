package pl.ui;

import pl.data.LeagueData;
import pl.data.LeagueData.TableRow;
import pl.data.Models.Fixture;
import pl.data.Models.Team;
import pl.stats.MatchModel;
import pl.stats.MatchModel.Prediction;
import pl.ui.ListTableModel.Col;
import pl.ui.charts.OutcomeBar;
import pl.ui.charts.ScoreHeatmap;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;

/** Head-to-head probabilities for any pairing, plus a whole-gameweek prediction table. */
public class PredictorPanel extends JPanel implements ListTableModel.DataView {
    private LeagueData data;
    private MatchModel model;
    private final JComboBox<Team> homeBox = new JComboBox<>();
    private final JComboBox<Team> awayBox = new JComboBox<>();
    private final OutcomeBar outcome = new OutcomeBar();
    private final ScoreHeatmap heatmap = new ScoreHeatmap();
    private final JLabel headline = Theme.label(" ", Theme.HERO, Theme.TEXT);
    private final JLabel context = Theme.label(" ", Theme.SMALL, Theme.TEXT_SECONDARY);
    private final JPanel statGrid = new JPanel(new GridLayout(2, 4, 10, 10));
    private final JPanel topScores = new JPanel();
    private final JComboBox<String> gwBox = new JComboBox<>();
    private final ListTableModel<Object[]> gwModel; // {Fixture, Prediction}
    private boolean updating;

    public PredictorPanel() {
        super(new BorderLayout(0, 10));
        setBackground(Theme.SURFACE);
        setBorder(Theme.padding(16));

        JPanel pick = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        pick.setOpaque(false);
        pick.add(Theme.label("Match predictor", Theme.TITLE, Theme.TEXT));
        pick.add(Box.createHorizontalStrut(16));
        pick.add(Theme.label("Home", Theme.SMALL, Theme.TEXT_SECONDARY));
        pick.add(homeBox);
        pick.add(Theme.label("v", Theme.BOLD, Theme.TEXT));
        pick.add(awayBox);
        pick.add(Theme.label("Away", Theme.SMALL, Theme.TEXT_SECONDARY));
        JButton swap = new JButton("Swap home/away");
        swap.addActionListener(e -> {
            Object h = homeBox.getSelectedItem();
            updating = true;
            homeBox.setSelectedItem(awayBox.getSelectedItem());
            awayBox.setSelectedItem(h);
            updating = false;
            predict();
        });
        pick.add(swap);
        homeBox.addActionListener(e -> predict());
        awayBox.addActionListener(e -> predict());

        // ---- left: headline, outcome bar, stat cards, top scores
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);
        headline.setAlignmentX(Component.LEFT_ALIGNMENT);
        context.setAlignmentX(Component.LEFT_ALIGNMENT);
        outcome.setAlignmentX(Component.LEFT_ALIGNMENT);
        outcome.setMaximumSize(new Dimension(Integer.MAX_VALUE, 92));
        statGrid.setOpaque(false);
        statGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        statGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        topScores.setLayout(new BoxLayout(topScores, BoxLayout.Y_AXIS));
        topScores.setOpaque(false);
        topScores.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(headline);
        left.add(context);
        left.add(Box.createVerticalStrut(14));
        left.add(outcome);
        left.add(Box.createVerticalStrut(10));
        left.add(statGrid);
        left.add(Box.createVerticalStrut(14));
        left.add(topScores);
        left.add(Box.createVerticalGlue());

        JPanel matchArea = new JPanel(new BorderLayout(20, 0));
        matchArea.setOpaque(false);
        matchArea.add(left, BorderLayout.CENTER);
        heatmap.setBorder(BorderFactory.createLineBorder(Theme.GRID));
        matchArea.add(heatmap, BorderLayout.EAST);

        // ---- bottom: gameweek predictions
        Theme.StripedRenderer right = new Theme.StripedRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        Theme.StripedRenderer centre = new Theme.StripedRenderer();
        centre.setHorizontalAlignment(SwingConstants.CENTER);
        gwModel = new ListTableModel<>(List.of(
                new Col<Object[]>("Home", String.class, 140, r -> data.team(fx(r).homeId()).name(), right),
                new Col<Object[]>("Away", String.class, 140, r -> data.team(fx(r).awayId()).name()),
                new Col<Object[]>("Home win", Double.class, 75, r -> pr(r).homeWin(), new Theme.PercentRenderer()),
                new Col<Object[]>("Draw", Double.class, 65, r -> pr(r).draw(), new Theme.PercentRenderer()),
                new Col<Object[]>("Away win", Double.class, 75, r -> pr(r).awayWin(), new Theme.PercentRenderer()),
                new Col<Object[]>("xG (H-A)", String.class, 80,
                        r -> String.format("%.2f - %.2f", pr(r).homeXg(), pr(r).awayXg()), centre),
                new Col<Object[]>("Likely score", String.class, 85, r -> score(pr(r).topScores(1).get(0)), centre),
                new Col<Object[]>("Over 2.5", Double.class, 70, r -> pr(r).over(2.5), new Theme.PercentRenderer()),
                new Col<Object[]>("BTTS", Double.class, 60, r -> pr(r).bothTeamsScore(), new Theme.PercentRenderer()),
                new Col<Object[]>("Result", String.class, 70, r -> fx(r).hasResult()
                        ? fx(r).homeGoals() + " - " + fx(r).awayGoals() : "", centre)
        ));
        JTable gwTable = gwModel.createTable();
        gwTable.getSelectionModel().addListSelectionListener(e -> {
            Object[] r = gwModel.selected(gwTable);
            if (!e.getValueIsAdjusting() && r != null) showFixture(fx(r).homeId(), fx(r).awayId());
        });
        gwBox.addActionListener(e -> fillGameweek());

        JPanel gwPanel = new JPanel(new BorderLayout(0, 6));
        gwPanel.setOpaque(false);
        JPanel gwHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        gwHeader.setOpaque(false);
        gwHeader.add(Theme.label("Gameweek predictions", Theme.TITLE, Theme.TEXT));
        gwHeader.add(gwBox);
        gwHeader.add(Theme.label("Select a row to load it above.", Theme.SMALL, Theme.TEXT_MUTED));
        gwPanel.add(gwHeader, BorderLayout.NORTH);
        gwPanel.add(ListTableModel.scroll(gwTable), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, matchArea, gwPanel);
        split.setResizeWeight(0.55);
        split.setBorder(null);
        split.setOpaque(false);

        add(pick, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
    }

    private static Fixture fx(Object[] r) { return (Fixture) r[0]; }
    private static Prediction pr(Object[] r) { return (Prediction) r[1]; }
    private static String score(double[] s) { return (int) s[0] + " - " + (int) s[1]; }

    @Override public void setData(LeagueData data, MatchModel model) {
        this.data = data;
        this.model = model;
        updating = true;
        Object h = homeBox.getSelectedItem(), a = awayBox.getSelectedItem();
        homeBox.removeAllItems();
        awayBox.removeAllItems();
        data.teams().forEach(t -> { homeBox.addItem(t); awayBox.addItem(t); });
        gwBox.removeAllItems();
        for (int g = 1; g <= data.maxGameweek(); g++) gwBox.addItem("Gameweek " + g);
        gwBox.setSelectedItem("Gameweek " + data.nextGameweek());
        updating = false;
        fillGameweek();

        // Default to the first upcoming fixture.
        if (h instanceof Team ht && a instanceof Team at) showFixture(ht.id(), at.id());
        else data.remaining().stream().findFirst().ifPresentOrElse(
                f -> showFixture(f.homeId(), f.awayId()),
                () -> { homeBox.setSelectedIndex(0); awayBox.setSelectedIndex(1); });
    }

    public void showFixture(int homeId, int awayId) {
        updating = true;
        homeBox.setSelectedItem(data.team(homeId));
        awayBox.setSelectedItem(data.team(awayId));
        updating = false;
        predict();
    }

    private void fillGameweek() {
        if (updating || data == null || gwBox.getSelectedItem() == null) return;
        String gw = (String) gwBox.getSelectedItem();
        gwModel.setRows(data.fixtures().stream()
                .filter(f -> f.gameweek() != null && gw.equals("Gameweek " + f.gameweek()))
                .map(f -> new Object[]{f, model.predict(f.homeId(), f.awayId())})
                .toList());
    }

    private void predict() {
        if (updating || data == null) return;
        Team home = (Team) homeBox.getSelectedItem(), away = (Team) awayBox.getSelectedItem();
        if (home == null || away == null) return;
        if (home.id() == away.id()) {
            headline.setText("Pick two different teams");
            return;
        }
        Prediction p = model.predict(home.id(), away.id());
        headline.setText(home.name() + "  v  " + away.name());

        TableRow hr = row(home.id()), ar = row(away.id());
        context.setText(String.format(
                "<html>Expected goals: <b>%.2f - %.2f</b> &nbsp;&middot;&nbsp; Table: %s %s, %s %s "
                        + "&nbsp;&middot;&nbsp; Attack/defence ratings: %s %.2f/%.2f, %s %.2f/%.2f</html>",
                p.homeXg(), p.awayXg(), home.shortName(), ordinal(hr.position), away.shortName(), ordinal(ar.position),
                home.shortName(), model.attack(home.id()), model.defence(home.id()),
                away.shortName(), model.attack(away.id()), model.defence(away.id())));

        outcome.setData(home.name(), p.homeWin(), p.draw(), p.awayWin(), away.name());
        heatmap.setData(p.matrix(), home.shortName(), away.shortName());

        statGrid.removeAll();
        statGrid.add(card("Over 1.5 goals", p.over(1.5)));
        statGrid.add(card("Over 2.5 goals", p.over(2.5)));
        statGrid.add(card("Over 3.5 goals", p.over(3.5)));
        statGrid.add(card("Both teams score", p.bothTeamsScore()));
        statGrid.add(card(home.shortName() + " clean sheet", p.homeCleanSheet()));
        statGrid.add(card(away.shortName() + " clean sheet", p.awayCleanSheet()));
        statGrid.add(card(home.shortName() + " win by 2+", p.homeWinBy(2)));
        statGrid.add(card(away.shortName() + " win by 2+", p.awayWinBy(2)));

        topScores.removeAll();
        topScores.add(Theme.label("Most likely scores", Theme.BOLD, Theme.TEXT));
        topScores.add(Box.createVerticalStrut(4));
        for (double[] s : p.topScores(5)) {
            topScores.add(Theme.label(String.format("%s %d - %d %s     %s", home.shortName(), (int) s[0], (int) s[1],
                    away.shortName(), Theme.pct(s[2])), Theme.BASE, Theme.TEXT_SECONDARY));
        }
        statGrid.revalidate();
        topScores.revalidate();
        repaint();
    }

    private TableRow row(int teamId) {
        return data.table().stream().filter(r -> r.team.id() == teamId).findFirst().orElseThrow();
    }

    private static String ordinal(int n) {
        String suf = (n % 100 >= 11 && n % 100 <= 13) ? "th" : switch (n % 10) {
            case 1 -> "st"; case 2 -> "nd"; case 3 -> "rd"; default -> "th";
        };
        return n + suf;
    }

    /** A probability "stat tile": label, big number, and the fair decimal odds it implies. */
    private static JPanel card(String label, double p) {
        JPanel c = new JPanel(new BorderLayout());
        c.setBackground(Theme.PANEL);
        c.setBorder(Theme.padding(10));
        c.add(Theme.label(label, Theme.SMALL, Theme.TEXT_SECONDARY), BorderLayout.NORTH);
        c.add(Theme.label(Theme.pct(p), Theme.BASE.deriveFont(java.awt.Font.BOLD, 20f), Theme.TEXT), BorderLayout.CENTER);
        c.add(Theme.label(p > 0 ? String.format("Fair odds %.2f", 1 / p) : " ", Theme.SMALL, Theme.TEXT_MUTED),
                BorderLayout.SOUTH);
        return c;
    }
}
