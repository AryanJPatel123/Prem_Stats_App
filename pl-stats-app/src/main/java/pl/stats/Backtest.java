package pl.stats;

import pl.data.HistoricalData.Season;
import pl.data.Models.Fixture;
import pl.stats.MatchModel.Prediction;
import pl.stats.MatchModel.Settings;

import java.time.ZoneId;
import java.util.List;

/**
 * Honest out-of-sample testing: every match is predicted by a model fitted only on results
 * from before that match day, exactly as the app would have predicted it at the time.
 *
 * Scores (averaged over matches):
 *   RPS      - Ranked Probability Score for home/draw/away. Lower is better; the standard football metric.
 *   Accuracy - share of matches where the most likely outcome happened.
 *   Log loss - minus the log of the probability given to what actually happened. Lower is better;
 *              punishes confident mistakes hard.
 *   Score log loss - the same, for the exact final score (where the Dixon-Coles correction matters).
 */
public final class Backtest {
    private Backtest() { }

    public record Score(String label, int matches, double rps, double accuracy, double logLoss, double scoreLogLoss) { }

    /** Running totals for one model across one or more seasons. */
    private static final class Totals {
        int n, correct, scored;
        double rps, logLoss, scoreLogLoss;

        void add(double[] p, int outcome) { // outcome: 0 home, 1 draw, 2 away
            double[] o = new double[3];
            o[outcome] = 1;
            double c1 = p[0] - o[0], c2 = p[0] + p[1] - o[0] - o[1];
            rps += (c1 * c1 + c2 * c2) / 2;
            logLoss += -Math.log(Math.max(1e-12, p[outcome]));
            int pick = p[0] >= p[1] && p[0] >= p[2] ? 0 : p[1] >= p[2] ? 1 : 2;
            if (pick == outcome) correct++;
            n++;
        }

        void addScore(double[][] matrix, Fixture f) {
            int h = Math.min(f.homeGoals(), MatchModel.MAX_GOALS), a = Math.min(f.awayGoals(), MatchModel.MAX_GOALS);
            scoreLogLoss += -Math.log(Math.max(1e-12, matrix[h][a]));
            scored++;
        }

        Score score(String label) {
            return n == 0 ? new Score(label, 0, Double.NaN, Double.NaN, Double.NaN, Double.NaN)
                    : new Score(label, n, rps / n, (double) correct / n, logLoss / n,
                            scored == 0 ? Double.NaN : scoreLogLoss / scored);
        }
    }

    private static int outcome(Fixture f) {
        return f.homeGoals() > f.awayGoals() ? 0 : f.homeGoals().equals(f.awayGoals()) ? 1 : 2;
    }

    /**
     * Scores the model with the given settings, pooled across seasons.
     *
     * @param skip number of matches at the start of each season that are not scored
     *             (the model has almost nothing to go on yet)
     */
    public static Score model(String label, List<Season> seasons, Settings settings, int skip) {
        Totals t = new Totals();
        ZoneId zone = ZoneId.of("Europe/London");
        for (Season s : seasons) {
            List<Fixture> res = s.results();
            int i = 0;
            while (i < res.size()) {
                // One fit per match day, using only matches from earlier days.
                var day = res.get(i).kickoff().atZone(zone).toLocalDate();
                int j = i;
                while (j < res.size() && res.get(j).kickoff().atZone(zone).toLocalDate().equals(day)) j++;
                if (j > skip) {
                    MatchModel m = MatchModel.fit(res.subList(0, i), s.teamIds(), res.get(i).kickoff(), settings);
                    for (int k = Math.max(i, skip); k < j; k++) {
                        Fixture f = res.get(k);
                        Prediction p = m.predict(f.homeId(), f.awayId());
                        t.add(new double[]{p.homeWin(), p.draw(), p.awayWin()}, outcome(f));
                        t.addScore(p.matrix(), f);
                    }
                }
                i = j;
            }
        }
        return t.score(label);
    }

    /** Scores the bookmaker's (margin-free) probabilities on the same matches. */
    public static Score market(String label, List<Season> seasons, int skip) {
        Totals t = new Totals();
        for (Season s : seasons) {
            for (int k = skip; k < s.results().size(); k++) {
                double[] p = s.marketProbs().get(k);
                if (p != null) t.add(p, outcome(s.results().get(k)));
            }
        }
        return t.score(label);
    }
}
