package pl;

import pl.data.FplClient;
import pl.data.LeagueData;
import pl.stats.MatchModel;
import pl.ui.ChartsPanel;
import pl.ui.FixturesPanel;
import pl.ui.ListTableModel.DataView;
import pl.ui.ModelCheckPanel;
import pl.ui.PlayersPanel;
import pl.ui.PredictorPanel;
import pl.ui.SimulatorPanel;
import pl.ui.TablePanel;
import pl.ui.Theme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;

/** Premier League Stats - entry point and main window. */
public class App extends JFrame {
    private final FplClient client = new FplClient();
    private final JLabel status = Theme.label("Loading...", Theme.SMALL, new Color(0xe9dcef));
    private final JButton refresh = new JButton("Refresh data");
    private final JTabbedPane tabs = new JTabbedPane();
    private final List<DataView> views;
    private final ModelCheckPanel modelCheck;
    private LeagueData data;
    private MatchModel.Settings settings = MatchModel.Settings.DEFAULT;

    public App() {
        super("Premier League Stats");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 720));
        setSize(1360, 860);
        setLocationRelativeTo(null);

        TablePanel table = new TablePanel();
        PredictorPanel predictor = new PredictorPanel();
        FixturesPanel fixtures = new FixturesPanel(f -> {
            predictor.showFixture(f.homeId(), f.awayId());
            tabs.setSelectedComponent(predictor);
        });
        PlayersPanel players = new PlayersPanel();
        ChartsPanel charts = new ChartsPanel();
        SimulatorPanel simulator = new SimulatorPanel();
        modelCheck = new ModelCheckPanel(this::applySettings);
        views = List.of(table, fixtures, players, charts, predictor, simulator, modelCheck);

        tabs.setFont(Theme.BOLD);
        tabs.addTab("League Table", table);
        tabs.addTab("Fixtures & Results", fixtures);
        tabs.addTab("Players", players);
        tabs.addTab("Charts", charts);
        tabs.addTab("Match Predictor", predictor);
        tabs.addTab("Season Simulator", simulator);
        tabs.addTab("Model Check", modelCheck);
        tabs.setEnabled(false);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.HEADER);
        header.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));
        JPanel titles = new JPanel(new BorderLayout());
        titles.setOpaque(false);
        titles.add(Theme.label("Premier League Stats", Theme.TITLE.deriveFont(20f), Color.WHITE), BorderLayout.NORTH);
        titles.add(status, BorderLayout.SOUTH);
        header.add(titles, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 6));
        actions.setOpaque(false);
        actions.add(refresh);
        header.add(actions, BorderLayout.EAST);
        refresh.addActionListener(e -> load());

        getContentPane().setBackground(Theme.SURFACE);
        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
    }

    private void load() {
        refresh.setEnabled(false);
        status.setText("Fetching data from the Fantasy Premier League API...");
        new SwingWorker<LeagueData, Void>() {
            @Override protected LeagueData doInBackground() throws Exception {
                return client.load();
            }
            @Override protected void done() {
                refresh.setEnabled(true);
                try {
                    data = get();
                    refit();
                    tabs.setEnabled(true);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    status.setText("Could not load data");
                    JOptionPane.showMessageDialog(App.this, cause.getMessage(), "Load failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /** Called from the Model Check tab: switch model settings and refresh every screen. */
    private void applySettings(MatchModel.Settings newSettings) {
        settings = newSettings;
        if (data != null) refit();
    }

    private void refit() {
        MatchModel model = MatchModel.fit(data, settings);
        for (DataView v : views) v.setData(data, model);
        status.setText(client.sourceDescription() + "   |   " + data.results().size() + " results, "
                + data.remaining().size() + " to play, " + data.players().size() + " players   |   Model: "
                + settings.describe() + String.format(" (rho %.3f)", model.rho()));
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) { }
        UIManager.put("ToolTip.font", Theme.SMALL);
        SwingUtilities.invokeLater(() -> {
            App app = new App();
            app.setVisible(true);
            app.load();
        });
    }
}
