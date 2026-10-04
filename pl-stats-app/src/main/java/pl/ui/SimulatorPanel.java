package pl.ui;

import pl.data.LeagueData;
import pl.stats.MatchModel;
import pl.stats.SeasonSimulator;
import pl.stats.SeasonSimulator.TeamOutlook;
import pl.ui.ListTableModel.Col;
import pl.ui.charts.BarChart;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

/** Monte Carlo season outcomes: title, top four, relegation, expected points. */
public class SimulatorPanel extends JPanel implements ListTableModel.DataView {
    private LeagueData data;
    private MatchModel model;
    private List<TeamOutlook> results = List.of();
    private final ListTableModel<TeamOutlook> tableModel;
    private final JTable table;
    private final JSpinner sims = new JSpinner(new SpinnerNumberModel(10000, 1000, 200000, 5000));
    private final JButton run = new JButton("Run simulation");
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JComboBox<String> chartMetric = new JComboBox<>(new String[]{
            "Title chance", "Top-four chance", "Relegation chance", "Expected final points", "Selected team: finishing position"});
    private final BarChart chart = new BarChart();

    public SimulatorPanel() {
        super(new BorderLayout(0, 10));
        setBackground(Theme.SURFACE);
        setBorder(Theme.padding(16));

        tableModel = new ListTableModel<>(List.of(
                new Col<TeamOutlook>("Club", String.class, 160, o -> o.team().name()),
                new Col<TeamOutlook>("Pts now", Integer.class, 60, TeamOutlook::currentPoints),
                new Col<TeamOutlook>("Exp. pts", Double.class, 70, TeamOutlook::expectedPoints,
                        new Theme.NumberRenderer("%.1f")),
                new Col<TeamOutlook>("Exp. pos", Double.class, 70, TeamOutlook::expectedPosition,
                        new Theme.NumberRenderer("%.1f")),
                new Col<TeamOutlook>("Title", Double.class, 70, TeamOutlook::champion, new Theme.PercentRenderer()),
                new Col<TeamOutlook>("Top 4", Double.class, 70, TeamOutlook::topFour, new Theme.PercentRenderer()),
                new Col<TeamOutlook>("Top 7", Double.class, 70, TeamOutlook::topSeven, new Theme.PercentRenderer()),
                new Col<TeamOutlook>("Relegated", Double.class, 80, TeamOutlook::relegated, new Theme.PercentRenderer())
        ));
        table = tableModel.createTable();
        table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) redrawChart(); });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        top.setOpaque(false);
        top.add(Theme.label("Season simulator", Theme.TITLE, Theme.TEXT));
        top.add(javax.swing.Box.createHorizontalStrut(16));
        top.add(Theme.label("Simulations", Theme.SMALL, Theme.TEXT_SECONDARY));
        top.add(sims);
        top.add(run);
        progress.setStringPainted(true);
        progress.setPreferredSize(new java.awt.Dimension(160, 22));
        top.add(progress);
        top.add(Theme.label("Chart", Theme.SMALL, Theme.TEXT_SECONDARY));
        top.add(chartMetric);
        run.addActionListener(e -> simulate());
        chartMetric.addActionListener(e -> redrawChart());

        JScrollPane chartScroll = new JScrollPane(chart);
        chartScroll.setBorder(BorderFactory.createLineBorder(Theme.GRID));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, ListTableModel.scroll(table), chartScroll);
        split.setResizeWeight(0.55);
        split.setBorder(null);

        add(top, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(Theme.label("Plays out every remaining fixture thousands of times using the match model, starting from the current table. "
                + "Top 7 = European places. Ties are broken on goal difference, then goals scored.", Theme.SMALL, Theme.TEXT_MUTED), BorderLayout.SOUTH);
    }

    @Override public void setData(LeagueData data, MatchModel model) {
        this.data = data;
        this.model = model;
        simulate();
    }

    private void simulate() {
        if (data == null) return;
        int n = (Integer) sims.getValue();
        run.setEnabled(false);
        progress.setValue(0);
        new SwingWorker<List<TeamOutlook>, Integer>() {
            @Override protected List<TeamOutlook> doInBackground() {
                return SeasonSimulator.run(data, model, n, this::setProgress);
            }
            {
                addPropertyChangeListener(e -> { if ("progress".equals(e.getPropertyName())) progress.setValue((Integer) e.getNewValue()); });
            }
            @Override protected void done() {
                try {
                    results = get();
                    tableModel.setRows(results);
                    progress.setString(String.format("%,d seasons", n));
                    redrawChart();
                } catch (Exception ex) {
                    progress.setString("Failed: " + ex.getMessage());
                } finally {
                    run.setEnabled(true);
                }
            }
        }.execute();
    }

    private void redrawChart() {
        if (results.isEmpty()) return;
        int metric = chartMetric.getSelectedIndex();
        if (metric == 4) {
            TeamOutlook sel = tableModel.selected(table);
            if (sel == null) sel = results.get(0);
            List<String> labels = new ArrayList<>();
            double[] vals = new double[sel.positionProbs().length];
            for (int i = 0; i < vals.length; i++) {
                labels.add(ordinal(i + 1));
                vals[i] = sel.positionProbs()[i];
            }
            chart.setData(sel.team().name() + " - chance of each finishing position (select a row)",
                    labels, vals, Theme.SERIES[6], v -> v == 0 ? "0%" : Theme.pct(v));
            return;
        }
        ToDoubleFunction<TeamOutlook> f = switch (metric) {
            case 0 -> TeamOutlook::champion;
            case 1 -> TeamOutlook::topFour;
            case 2 -> TeamOutlook::relegated;
            default -> TeamOutlook::expectedPoints;
        };
        List<TeamOutlook> sorted = new ArrayList<>(results);
        sorted.sort(Comparator.comparingDouble(f).reversed());
        if (metric != 3) sorted.removeIf(o -> f.applyAsDouble(o) < 0.0005); // hide teams with ~no chance
        chart.setData((String) chartMetric.getSelectedItem(),
                sorted.stream().map(o -> o.team().name()).toList(),
                sorted.stream().mapToDouble(f).toArray(),
                metric == 2 ? Theme.SERIES[7] : Theme.PRIMARY,
                metric == 3 ? v -> String.format("%.1f", v) : v -> v == 0 ? "0%" : Theme.pct(v));
    }

    private static String ordinal(int n) {
        String suf = (n % 100 >= 11 && n % 100 <= 13) ? "th" : switch (n % 10) {
            case 1 -> "st"; case 2 -> "nd"; case 3 -> "rd"; default -> "th";
        };
        return n + suf;
    }
}
