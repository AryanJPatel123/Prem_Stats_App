package pl.stats;

import pl.data.LeagueData;
import pl.data.LeagueData.TableRow;
import pl.data.Models.Fixture;
import pl.data.Models.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.IntConsumer;

/**
 * Monte Carlo simulation of the rest of the season. Every remaining fixture is played out
 * by sampling a scoreline from {@link MatchModel}, starting from the current table.
 */
public final class SeasonSimulator {

    public record TeamOutlook(Team team, int currentPoints, double expectedPoints, double expectedPosition,
                              double[] positionProbs) {
        public double champion() { return positionProbs[0]; }
        public double topFour() { return range(0, 4); }
        public double topSeven() { return range(0, 7); }
        public double relegated() { return range(positionProbs.length - 3, positionProbs.length); }

        private double range(int from, int to) {
            double s = 0;
            for (int i = from; i < to && i < positionProbs.length; i++) s += positionProbs[i];
            return s;
        }
    }

    /** @param progress receives a 0-100 percentage as simulations complete (may be null). */
    public static List<TeamOutlook> run(LeagueData data, MatchModel model, int simulations, IntConsumer progress) {
        List<TableRow> table = data.table();
        int n = table.size();
        Map<Integer, Integer> index = new HashMap<>();
        int[] basePts = new int[n], baseGd = new int[n], baseGf = new int[n];
        for (int i = 0; i < n; i++) {
            TableRow r = table.get(i);
            index.put(r.team.id(), i);
            basePts[i] = r.points;
            baseGd[i] = r.goalDiff();
            baseGf[i] = r.goalsFor;
        }

        // Pre-compute a cumulative scoreline distribution for each remaining fixture.
        List<Fixture> remaining = data.remaining();
        int cells = (MatchModel.MAX_GOALS + 1) * (MatchModel.MAX_GOALS + 1);
        double[][] cumulative = new double[remaining.size()][cells];
        int[] home = new int[remaining.size()], away = new int[remaining.size()];
        for (int f = 0; f < remaining.size(); f++) {
            Fixture fx = remaining.get(f);
            home[f] = index.get(fx.homeId());
            away[f] = index.get(fx.awayId());
            double[][] m = model.predict(fx.homeId(), fx.awayId()).matrix();
            double acc = 0;
            for (int c = 0; c < cells; c++) {
                acc += m[c / (MatchModel.MAX_GOALS + 1)][c % (MatchModel.MAX_GOALS + 1)];
                cumulative[f][c] = acc;
            }
        }

        SplittableRandom rng = new SplittableRandom();
        long[][] positionCounts = new long[n][n];
        long[] pointsSum = new long[n];
        int[] pts = new int[n], gd = new int[n], gf = new int[n];
        double[] tiebreak = new double[n];
        Integer[] order = new Integer[n];

        for (int s = 0; s < simulations; s++) {
            System.arraycopy(basePts, 0, pts, 0, n);
            System.arraycopy(baseGd, 0, gd, 0, n);
            System.arraycopy(baseGf, 0, gf, 0, n);
            for (int f = 0; f < remaining.size(); f++) {
                int cell = sample(cumulative[f], rng.nextDouble());
                int hg = cell / (MatchModel.MAX_GOALS + 1), ag = cell % (MatchModel.MAX_GOALS + 1);
                int h = home[f], a = away[f];
                gf[h] += hg; gf[a] += ag;
                gd[h] += hg - ag; gd[a] += ag - hg;
                if (hg > ag) pts[h] += 3;
                else if (hg < ag) pts[a] += 3;
                else { pts[h]++; pts[a]++; }
            }
            for (int i = 0; i < n; i++) { order[i] = i; tiebreak[i] = rng.nextDouble(); }
            java.util.Arrays.sort(order, (x, y) -> {
                if (pts[x] != pts[y]) return pts[y] - pts[x];
                if (gd[x] != gd[y]) return gd[y] - gd[x];
                if (gf[x] != gf[y]) return gf[y] - gf[x];
                return Double.compare(tiebreak[x], tiebreak[y]);
            });
            for (int pos = 0; pos < n; pos++) positionCounts[order[pos]][pos]++;
            for (int i = 0; i < n; i++) pointsSum[i] += pts[i];

            if (progress != null && (s + 1) % Math.max(1, simulations / 100) == 0) {
                progress.accept((int) (100L * (s + 1) / simulations));
            }
        }

        List<TeamOutlook> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double[] probs = new double[n];
            double expPos = 0;
            for (int pos = 0; pos < n; pos++) {
                probs[pos] = (double) positionCounts[i][pos] / simulations;
                expPos += probs[pos] * (pos + 1);
            }
            out.add(new TeamOutlook(table.get(i).team, basePts[i], (double) pointsSum[i] / simulations, expPos, probs));
        }
        out.sort((x, y) -> Double.compare(x.expectedPosition(), y.expectedPosition()));
        return out;
    }

    private static int sample(double[] cumulative, double u) {
        int lo = 0, hi = cumulative.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cumulative[mid] < u) lo = mid + 1; else hi = mid;
        }
        return lo;
    }
}
