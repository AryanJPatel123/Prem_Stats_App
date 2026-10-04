package pl.ui;

import pl.data.HistoricalData;
import pl.data.HistoricalData.Season;
import pl.data.LeagueData;
import pl.stats.Backtest;
import pl.stats.Backtest.Score;
import pl.stats.MatchModel;
import pl.stats.MatchModel.RhoMode;
import pl.stats.MatchModel.Settings;
import pl.ui.ListTableModel.Col;
import pl.ui.charts.BarChart;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Backtests the match model on past seasons (and this one), compares settings against each other
 * and against bookmaker odds, and lets the user apply the settings they prefer.
 */
public class ModelCheckPanel extends JPanel implements ListTableModel.DataView {
    private static final String POOLED = "Last 5 seasons (2021/22 - 2025/26)";
    private static final String CURRENT = "This season so far";
    private static final Object[] HALF_LIVES = {"Off", 60, 90, 120, 180, 365};

    private LeagueData data;
    private final Consumer<Settings> apply;

    private final JComboBox<String> seasonBox = new JComboBox<>();
    private final JSpinner skip = new JSpinner(new SpinnerNumberModel(50, 0, 300, 10));
    private final JButton run = new JButton("Run backtest");
    private final JProgressBar progress = new JProgressBar(0, 100);

    private final JComboBox<Object> halfLifeBox = new JComboBox<>(HALF_LIVES);
    private final JComboBox<RhoMode> rhoBox = new JComboBox<>(RhoMode.values());
    private final JSpinner shrinkage = new JSpinner(new SpinnerNumberModel(6.0, 0.0, 30.0, 1.0));
    private final JLabel current = Theme.label(" ", Theme.SMALL, Theme.TEXT_SECONDARY);

    private final ListTableModel<Score> tableModel;
    private final BarChart chart = new BarChart();
    private double baselineRps = Double.NaN;

    public ModelCheckPanel(Consumer<Settings> apply) {
        super(new BorderLayout(0, 10));
        this.apply = apply;
        setBackground(Theme.SURFACE);
        setBorder(Theme.padding(16));

        seasonBox.addItem(POOLED);
        HistoricalData.PAST_SEASONS.values().forEach(seasonBox::addItem);
        seasonBox.addItem(CURRENT);
        // This season has few matches, so don't skip many by default.
        seasonBox.addActionListener(e -> skip.setValue(CURRENT.equals(seasonBox.getSelectedItem()) ? 20 : 50));

        Settings d = Settings.DEFAULT;
        halfLifeBox.setSelectedItem(d.timeWeighted() ? (int) d.halfLifeDays() : "Off");
        rhoBox.setSelectedItem(d.rhoMode());
        shrinkage.setValue(d.teamShrinkage());

        JPanel testRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        testRow.setOpaque(false);
        testRow.add(Theme.label("Model check", Theme.TITLE, Theme.TEXT));
        testRow.add(Box.createHorizontalStrut(16));
        testRow.add(Theme.label("Test on", Theme.SMALL, Theme.TEXT_SECONDARY));
        testRow.add(seasonBox);
        testRow.add(Theme.label("Skip first", Theme.SMALL, Theme.TEXT_SECONDARY));
        testRow.add(skip);
        testRow.add(Theme.label("matches of each season", Theme.SMALL, Theme.TEXT_SECONDARY));
        testRow.add(run);
        progress.setStringPainted(true);
        progress.setString("Not run yet");
        progress.setPreferredSize(new java.awt.Dimension(170, 22));
        testRow.add(progress);

        JPanel settingsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        settingsRow.setOpaque(false);
        settingsRow.add(Theme.label("Your settings:", Theme.BOLD, Theme.TEXT));
        settingsRow.add(Theme.label("Time-weighting half-life (days)", Theme.SMALL, Theme.TEXT_SECONDARY));
        settingsRow.add(halfLifeBox);
        settingsRow.add(Theme.label("Dixon-Coles", Theme.SMALL, Theme.TEXT_SECONDARY));
        settingsRow.add(rhoBox);
        settingsRow.add(Theme.label("Shrinkage (phantom games)", Theme.SMALL, Theme.TEXT_SECONDARY));
        settingsRow.add(shrinkage);
        JButton applyBtn = new JButton("Use for predictions");
        applyBtn.setToolTipText("Refits the model with these settings and updates every tab");
        applyBtn.addActionListener(e -> apply.accept(selectedSettings()));
        settingsRow.add(applyBtn);
        JButton reset = new JButton("Reset to recommended");
        reset.addActionListener(e -> {
            halfLifeBox.setSelectedItem(d.timeWeighted() ? (int) d.halfLifeDays() : "Off");
            rhoBox.setSelectedItem(d.rhoMode());
            shrinkage.setValue(d.teamShrinkage());
            apply.accept(d);
        });
        settingsRow.add(reset);

        JPanel top = new JPanel(new GridLayout(3, 1, 0, 6));
        top.setOpaque(false);
        top.add(testRow);
        top.add(settingsRow);
        top.add(current);

        Theme.NumberRenderer four = new Theme.NumberRenderer("%.4f");
        tableModel = new ListTableModel<>(List.of(
                new Col<Score>("Model", String.class, 300, Score::label),
                new Col<Score>("Matches", Integer.class, 70, Score::matches),
                new Col<Score>("RPS (lower = better)", Double.class, 130, s -> nanToNull(s.rps()), four),
                new Col<Score>("Better than no-ratings by", Double.class, 160, s -> improvement(s), new Theme.NumberRenderer("%.1f%%")),
                new Col<Score>("Accuracy", Double.class, 80, s -> nanToNull(s.accuracy()), new Theme.PercentRenderer()),
                new Col<Score>("Log loss", Double.class, 80, s -> nanToNull(s.logLoss()), four),
                new Col<Score>("Exact-score log loss", Double.class, 130, s -> nanToNull(s.scoreLogLoss()), four)
        ));
        JScrollPane chartScroll = new JScrollPane(chart);
        chartScroll.setBorder(BorderFactory.createLineBorder(Theme.GRID));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, ListTableModel.scroll(tableModel.createTable()), chartScroll);
        split.setResizeWeight(0.45);
        split.setBorder(null);

        run.addActionListener(e -> runBacktest());

        add(top, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(Theme.label("<html>Each match is predicted using only results from before that day, exactly as the app would have "
                + "predicted it at the time. RPS (Ranked Probability Score) is the standard football forecasting score: typical good models "
                + "land around 0.20-0.21. Bookmaker = Pinnacle's pre-match odds with their margin removed (past seasons only; data from "
                + "football-data.co.uk).</html>", Theme.SMALL, Theme.TEXT_MUTED), BorderLayout.SOUTH);
    }

    @Override public void setData(LeagueData data, MatchModel model) {
        this.data = data;
        current.setText("Predictions currently use: " + model.settings().describe()
                + String.format(", shrinkage %.0f  (fitted rho this season: %.3f)", model.settings().teamShrinkage(), model.rho()));
    }

    private Settings selectedSettings() {
        Object hl = halfLifeBox.getSelectedItem();
        return new Settings(hl instanceof Integer i ? i : 0, (RhoMode) rhoBox.getSelectedItem(), (Double) shrinkage.getValue());
    }

    private Double improvement(Score s) {
        return Double.isNaN(s.rps()) || Double.isNaN(baselineRps) ? null : (baselineRps - s.rps()) / baselineRps * 100;
    }

    private static Double nanToNull(double v) { return Double.isNaN(v) ? null : v; }

    private void runBacktest() {
        String choice = (String) seasonBox.getSelectedItem();
        int skipN = (Integer) skip.getValue();
        Settings mine = selectedSettings();
        double sh = mine.teamShrinkage();
        run.setEnabled(false);
        progress.setValue(0);
        progress.setString("Loading seasons...");

        new SwingWorker<List<Score>, Void>() {
            @Override protected List<Score> doInBackground() throws Exception {
                List<Season> seasons = new ArrayList<>();
                if (CURRENT.equals(choice)) {
                    seasons.add(Season.fromLeague(CURRENT, data));
                } else {
                    for (var e : HistoricalData.PAST_SEASONS.entrySet())
                        if (POOLED.equals(choice) || e.getValue().equals(choice)) seasons.add(HistoricalData.load(e.getKey()));
                }

                List<Object[]> configs = new ArrayList<>(); // {label, Settings}
                configs.add(new Object[]{"No team ratings (home advantage only)", new Settings(0, RhoMode.OFF, 1e9)});
                configs.add(new Object[]{"Plain Poisson", new Settings(0, RhoMode.OFF, sh)});
                configs.add(new Object[]{"Poisson + Dixon-Coles (rho fixed)", new Settings(0, RhoMode.FIXED, sh)});
                configs.add(new Object[]{"Poisson + Dixon-Coles (rho fitted)", new Settings(0, RhoMode.FITTED, sh)});
                configs.add(new Object[]{"+ time-weighting, 90-day half-life", new Settings(90, RhoMode.FITTED, sh)});
                configs.add(new Object[]{"+ time-weighting, 180-day half-life", new Settings(180, RhoMode.FITTED, sh)});
                configs.add(new Object[]{"Your settings (" + mine.describe() + ")", mine});

                List<Score> scores = new ArrayList<>();
                for (int i = 0; i < configs.size(); i++) {
                    scores.add(Backtest.model((String) configs.get(i)[0], seasons, (Settings) configs.get(i)[1], skipN));
                    setProgress(100 * (i + 1) / configs.size());
                }
                if (seasons.stream().anyMatch(Season::hasMarket))
                    scores.add(Backtest.market("Bookmaker (Pinnacle odds)", seasons, skipN));
                return scores;
            }
            {
                addPropertyChangeListener(e -> {
                    if ("progress".equals(e.getPropertyName())) {
                        progress.setValue((Integer) e.getNewValue());
                        progress.setString("Testing... " + e.getNewValue() + "%");
                    }
                });
            }
            @Override protected void done() {
                run.setEnabled(true);
                try {
                    List<Score> scores = get();
                    baselineRps = scores.get(0).rps();
                    tableModel.setRows(scores);
                    progress.setString(String.format("%,d matches tested", scores.get(0).matches()));
                    List<Score> charted = Double.isNaN(baselineRps) ? List.of()
                            : scores.subList(1, scores.size()).stream().filter(s -> !Double.isNaN(s.rps())).toList();
                    chart.setData("RPS improvement over a model with no team ratings (%, higher = better)",
                            charted.stream().map(Score::label).toList(),
                            charted.stream().mapToDouble(s -> improvement(s)).toArray(),
                            Theme.PRIMARY, v -> String.format("%.1f%%", v));
                } catch (Exception ex) {
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    progress.setString("Failed");
                    javax.swing.JOptionPane.showMessageDialog(ModelCheckPanel.this,
                            "Backtest failed: " + c.getMessage(), "Model check", javax.swing.JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}
