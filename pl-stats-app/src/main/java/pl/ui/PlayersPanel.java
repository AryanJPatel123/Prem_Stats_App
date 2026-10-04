package pl.ui;

import pl.data.LeagueData;
import pl.data.Models.Player;
import pl.data.Models.Position;
import pl.data.Models.Team;
import pl.stats.MatchModel;
import pl.ui.ListTableModel.Col;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Every Premier League player's season stats, with search and filters. */
public class PlayersPanel extends JPanel implements ListTableModel.DataView {
    private LeagueData data;
    private final ListTableModel<Player> tableModel;
    private final JComboBox<Object> teamBox = new JComboBox<>();
    private final JComboBox<Object> posBox = new JComboBox<>();
    private final JTextField search = new JTextField(14);
    private final JSpinner minMinutes = new JSpinner(new SpinnerNumberModel(1, 0, 4000, 90));
    private final JLabel count = Theme.label("", Theme.SMALL, Theme.TEXT_SECONDARY);
    private boolean updating;

    public PlayersPanel() {
        super(new BorderLayout(0, 10));
        setBackground(Theme.SURFACE);
        setBorder(Theme.padding(16));

        tableModel = new ListTableModel<>(List.of(
                new Col<Player>("Player", String.class, 150, Player::webName),
                new Col<Player>("Club", String.class, 110, p -> data.team(p.teamId()).name()),
                new Col<Player>("Pos", String.class, 45, p -> p.position().name()),
                new Col<Player>("Mins", Integer.class, 55, Player::minutes),
                new Col<Player>("Starts", Integer.class, 50, Player::starts),
                new Col<Player>("Goals", Integer.class, 50, Player::goals),
                new Col<Player>("Assists", Integer.class, 55, Player::assists),
                new Col<Player>("G+A", Integer.class, 45, p -> p.goals() + p.assists()),
                new Col<Player>("xG", Double.class, 50, Player::xG),
                new Col<Player>("xA", Double.class, 50, Player::xA),
                new Col<Player>("G/90", Double.class, 50, Player::goalsPer90),
                new Col<Player>("xGI/90", Double.class, 55, Player::xgiPer90),
                new Col<Player>("Clean sheets", Integer.class, 80, Player::cleanSheets),
                new Col<Player>("Saves", Integer.class, 50, Player::saves),
                new Col<Player>("YC", Integer.class, 40, Player::yellowCards),
                new Col<Player>("RC", Integer.class, 40, Player::redCards),
                new Col<Player>("Bonus", Integer.class, 50, Player::bonus),
                new Col<Player>("FPL pts", Integer.class, 60, Player::totalPoints),
                new Col<Player>("Form", Double.class, 50, Player::form),
                new Col<Player>("Price £m", Double.class, 65, Player::price, new Theme.NumberRenderer("%.1f")),
                new Col<Player>("Owned %", Double.class, 65, Player::selectedByPct, new Theme.NumberRenderer("%.1f")),
                new Col<Player>("Status", String.class, 90, Player::availability)
        ));
        JTable table = tableModel.createTable();
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        // Default: most goal involvements first.
        table.getRowSorter().setSortKeys(List.of(new javax.swing.RowSorter.SortKey(7, javax.swing.SortOrder.DESCENDING)));

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filters.setOpaque(false);
        filters.add(Theme.label("Players", Theme.TITLE, Theme.TEXT));
        filters.add(javax.swing.Box.createHorizontalStrut(16));
        filters.add(Theme.label("Search", Theme.SMALL, Theme.TEXT_SECONDARY));
        filters.add(search);
        filters.add(Theme.label("Team", Theme.SMALL, Theme.TEXT_SECONDARY));
        filters.add(teamBox);
        filters.add(Theme.label("Position", Theme.SMALL, Theme.TEXT_SECONDARY));
        filters.add(posBox);
        filters.add(Theme.label("Min. minutes", Theme.SMALL, Theme.TEXT_SECONDARY));
        filters.add(minMinutes);
        filters.add(count);

        teamBox.addActionListener(e -> refilter());
        posBox.addActionListener(e -> refilter());
        minMinutes.addChangeListener(e -> refilter());
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refilter(); }
            public void removeUpdate(DocumentEvent e) { refilter(); }
            public void changedUpdate(DocumentEvent e) { refilter(); }
        });

        add(filters, BorderLayout.NORTH);
        add(ListTableModel.scroll(table), BorderLayout.CENTER);
        add(Theme.label("Click a column header to sort (click again to reverse). xG/xA = expected goals/assists. "
                + "Hover a row for the player's full name and injury news.", Theme.SMALL, Theme.TEXT_MUTED), BorderLayout.SOUTH);

        table.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override public void mouseMoved(java.awt.event.MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row < 0) { table.setToolTipText(null); return; }
                Player p = tableModel.rowAt(table.convertRowIndexToModel(row));
                String news = p.news() == null || p.news().isBlank() ? "" : "<br><i>" + p.news() + "</i>";
                table.setToolTipText("<html><b>" + p.fullName() + "</b> - " + p.position().label + news + "</html>");
            }
        });
    }

    @Override public void setData(LeagueData data, MatchModel model) {
        this.data = data;
        updating = true;
        teamBox.removeAllItems();
        teamBox.addItem("All teams");
        data.teams().forEach(teamBox::addItem);
        posBox.removeAllItems();
        posBox.addItem("All positions");
        for (Position p : Position.values()) posBox.addItem(p);
        updating = false;
        refilter();
    }

    private void refilter() {
        if (updating || data == null) return;
        Object team = teamBox.getSelectedItem();
        Object pos = posBox.getSelectedItem();
        int mins = (Integer) minMinutes.getValue();
        String q = fold(search.getText().trim());
        List<Player> rows = new ArrayList<>();
        for (Player p : data.players()) {
            if (team instanceof Team t && p.teamId() != t.id()) continue;
            if (pos instanceof Position ps && p.position() != ps) continue;
            if (p.minutes() < mins) continue;
            if (!q.isEmpty() && !fold(p.webName() + " " + p.fullName()).contains(q)) continue;
            rows.add(p);
        }
        tableModel.setRows(rows);
        count.setText(rows.size() + " players");
    }

    /** Lower-case and strip accents so "odegaard" finds "Ødegaard" and "martin" finds "Martín". */
    static String fold(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replace('ø', 'o').replace("æ", "ae").replace('ł', 'l');
    }
}
