package pl.data;

import pl.data.Models.Fixture;
import pl.data.Models.Player;
import pl.data.Models.Team;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All season data plus derived views (league table, points progression). */
public final class LeagueData {
    private final List<Team> teams;
    private final Map<Integer, Team> teamsById = new LinkedHashMap<>();
    private final List<Player> players;
    private final List<Fixture> fixtures;

    public LeagueData(List<Team> teams, List<Player> players, List<Fixture> fixtures) {
        this.teams = teams.stream().sorted(Comparator.comparing(Team::name)).toList();
        this.teams.forEach(t -> teamsById.put(t.id(), t));
        this.players = List.copyOf(players);
        // Unscheduled fixtures (no kickoff time yet) go last.
        this.fixtures = fixtures.stream()
                .sorted(Comparator.comparing(Fixture::kickoff, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public List<Team> teams() { return teams; }
    public Team team(int id) { return teamsById.get(id); }
    public List<Player> players() { return players; }
    public List<Fixture> fixtures() { return fixtures; }

    public List<Fixture> results() { return fixtures.stream().filter(Fixture::hasResult).toList(); }

    public List<Fixture> remaining() { return fixtures.stream().filter(f -> !f.hasResult()).toList(); }

    /** The gameweek of the next unplayed fixture, or the last gameweek if the season is over. */
    public int nextGameweek() {
        return remaining().stream().map(Fixture::gameweek).filter(g -> g != null)
                .findFirst().orElse(maxGameweek());
    }

    public int maxGameweek() {
        return fixtures.stream().map(Fixture::gameweek).filter(g -> g != null).max(Integer::compare).orElse(38);
    }

    public int lastCompletedGameweek() {
        return results().stream().map(Fixture::gameweek).filter(g -> g != null).max(Integer::compare).orElse(0);
    }

    // ------------------------------------------------------------------ league table

    public static final class TableRow {
        public final Team team;
        public int played, won, drawn, lost, goalsFor, goalsAgainst, points;
        public int position;
        /** Most recent result last, e.g. "WWDLW". */
        public final StringBuilder form = new StringBuilder();

        TableRow(Team team) { this.team = team; }

        public int goalDiff() { return goalsFor - goalsAgainst; }
        public double pointsPerGame() { return played == 0 ? 0 : (double) points / played; }

        public String lastFive() {
            return form.length() <= 5 ? form.toString() : form.substring(form.length() - 5);
        }

        void record(int scored, int conceded) {
            played++;
            goalsFor += scored;
            goalsAgainst += conceded;
            if (scored > conceded) { won++; points += 3; form.append('W'); }
            else if (scored == conceded) { drawn++; points += 1; form.append('D'); }
            else { lost++; form.append('L'); }
        }
    }

    public static final Comparator<TableRow> TABLE_ORDER = Comparator
            .comparingInt((TableRow r) -> r.points).reversed()
            .thenComparing(Comparator.comparingInt(TableRow::goalDiff).reversed())
            .thenComparing(Comparator.comparingInt((TableRow r) -> r.goalsFor).reversed())
            .thenComparing(r -> r.team.name());

    /** League table built from played fixtures (optionally only up to a given kickoff). */
    public List<TableRow> table() { return tableUntil(null); }

    public List<TableRow> tableUntil(Instant until) {
        Map<Integer, TableRow> rows = new HashMap<>();
        teams.forEach(t -> rows.put(t.id(), new TableRow(t)));
        for (Fixture f : fixtures) {
            if (!f.hasResult()) continue;
            if (until != null && f.kickoff() != null && f.kickoff().isAfter(until)) continue;
            rows.get(f.homeId()).record(f.homeGoals(), f.awayGoals());
            rows.get(f.awayId()).record(f.awayGoals(), f.homeGoals());
        }
        List<TableRow> sorted = new ArrayList<>(rows.values());
        sorted.sort(TABLE_ORDER);
        for (int i = 0; i < sorted.size(); i++) sorted.get(i).position = i + 1;
        return sorted;
    }

    /**
     * Cumulative points after each gameweek for every team.
     * Result: teamId -> array where index g holds points after gameweek g (index 0 = 0 points).
     */
    public Map<Integer, int[]> pointsProgression() {
        int last = lastCompletedGameweek();
        Map<Integer, int[]> perGw = new HashMap<>();
        teams.forEach(t -> perGw.put(t.id(), new int[last + 1]));
        for (Fixture f : results()) {
            if (f.gameweek() == null) continue;
            int g = f.gameweek();
            int h = f.homeGoals(), a = f.awayGoals();
            perGw.get(f.homeId())[g] += h > a ? 3 : h == a ? 1 : 0;
            perGw.get(f.awayId())[g] += a > h ? 3 : h == a ? 1 : 0;
        }
        perGw.values().forEach(arr -> { for (int g = 1; g < arr.length; g++) arr[g] += arr[g - 1]; });
        return perGw;
    }
}
