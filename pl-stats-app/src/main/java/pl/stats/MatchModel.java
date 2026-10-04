package pl.stats;

import pl.data.LeagueData;
import pl.data.Models.Fixture;
import pl.data.Models.Team;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Poisson goal model with a Dixon-Coles low-score correction and optional time-weighting.
 *
 * Expected goals for a fixture:
 *   home lambda = base * homeAdvantage * attack[home] * defence[away]
 *   away lambda = base *                 attack[away] * defence[home]
 *
 * attack > 1 means a team scores more than average; defence > 1 means it concedes more.
 * Ratings are fitted iteratively from this season's results so that opponent strength
 * is accounted for, and shrunk toward average with a few "phantom" average games so the
 * model is not thrown around by a small early-season sample.
 *
 * Time-weighting (Dixon & Coles, 1997): each result counts with weight 0.5^(age / half-life),
 * so recent form matters more than results from months ago.
 */
public final class MatchModel {
    public static final int MAX_GOALS = 10;

    /** Historical Premier League per-match averages. */
    private static final double PRIOR_HOME_GOALS = 1.55, PRIOR_AWAY_GOALS = 1.25;
    private static final double PRIOR_LEAGUE_MATCHES = 30;
    /** Typical Dixon-Coles rho value for Premier League; also the centre of the prior when rho has been fitted. */
    public static final double DEFAULT_RHO = -0.08;
    /** Prior standard deviation for fitted rho, most likely to be used when little data is available (early season) */
    private static final double RHO_PRIOR_SD = 0.05;

    public enum RhoMode {
        OFF("Off (only Poisson)"), FIXED("Fixed at -0.08"), FITTED("Fitted to data");

        public final String label;
        RhoMode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    /**
     * Model settings.
     *
     * @param halfLifeDays  days for a result's weighting to halve; 0 = no time-weighting
     * @param rhoMode       how the Dixon-Coles low-score correction is set
     * @param teamShrinkage "phantom" average matches added to each team's record. After that
     *                      many (weighted) real games a rating is half data, half average.
     *                      (phantom games are added games that reflect the average, to attempt to simulate a "regression to the mean")
     */
    public record Settings(double halfLifeDays, RhoMode rhoMode, double teamShrinkage) {
        /**
         * Chosen by backtesting on the 2021/22-2025/26 Premier League seasons (see Model Check tab):
         * time-weighting made this single-season model worse at every half-life tried (it throws away
         * scarce data), Dixon-Coles was neutral, and shrinkage of 6 scored best.
         */
        public static final Settings DEFAULT = new Settings(0, RhoMode.FITTED, 6);

        public boolean timeWeighted() { return halfLifeDays > 0; }

        public Settings withHalfLife(double days) { return new Settings(days, rhoMode, teamShrinkage); }
        public Settings withRho(RhoMode mode) { return new Settings(halfLifeDays, mode, teamShrinkage); }

        public String describe() {
            return (timeWeighted() ? "half-life " + (int) halfLifeDays + " days" : "no time-weighting")
                    + ", Dixon-Coles " + switch (rhoMode) {
                        case OFF -> "off";
                        case FIXED -> "fixed";
                        case FITTED -> "fitted";
                    };
        }
    }

    private final double base;
    private final double homeAdvantage;
    private final Map<Integer, Double> attack = new HashMap<>();
    private final Map<Integer, Double> defence = new HashMap<>();
    private final int matchesUsed;
    private final Settings settings;
    private double rho;

    private MatchModel(double base, double homeAdvantage, int matchesUsed, Settings settings) {
        this.base = base;
        this.homeAdvantage = homeAdvantage;
        this.matchesUsed = matchesUsed;
        this.settings = settings;
    }

    /** Fits on every played match this season, weighting by age relative to now. */
    public static MatchModel fit(LeagueData data, Settings settings) {
        return fit(data.results(), data.teams().stream().map(Team::id).toList(), Instant.now(), settings);
    }

    /**
     * Fits on the given results.
     *
     * @param asOf the moment predictions are made for; result ages (for time-weighting) are measured from here
     */
    public static MatchModel fit(List<Fixture> results, Collection<Integer> teamIds, Instant asOf, Settings settings) {
        int n = results.size();
        double[] w = new double[n];
        double wSum = 0, homeGoals = 0, awayGoals = 0;
        for (int i = 0; i < n; i++) {
            Fixture f = results.get(i);
            w[i] = weight(f, asOf, settings);
            wSum += w[i];
            homeGoals += w[i] * f.homeGoals();
            awayGoals += w[i] * f.awayGoals();
        }
        double avgHome = (homeGoals + PRIOR_LEAGUE_MATCHES * PRIOR_HOME_GOALS) / (wSum + PRIOR_LEAGUE_MATCHES);
        double avgAway = (awayGoals + PRIOR_LEAGUE_MATCHES * PRIOR_AWAY_GOALS) / (wSum + PRIOR_LEAGUE_MATCHES);

        MatchModel m = new MatchModel(avgAway, avgHome / avgAway, n, settings);
        List<Integer> ids = new ArrayList<>(teamIds);
        for (int id : ids) {
            m.attack.put(id, 1.0);
            m.defence.put(id, 1.0);
        }
        double prior = settings.teamShrinkage() * (avgHome + avgAway) / 2;

        for (int iter = 0; iter < 100; iter++) {
            Map<Integer, double[]> att = new HashMap<>(); // {weighted goals scored, expected given opponents}
            Map<Integer, double[]> def = new HashMap<>(); // {weighted goals conceded, expected given opponents}
            ids.forEach(id -> { att.put(id, new double[2]); def.put(id, new double[2]); });
            double hBase = m.base * m.homeAdvantage, aBase = m.base;
            for (int i = 0; i < n; i++) {
                Fixture f = results.get(i);
                double wi = w[i];
                att.get(f.homeId())[0] += wi * f.homeGoals();
                att.get(f.homeId())[1] += wi * hBase * m.defence.get(f.awayId());
                att.get(f.awayId())[0] += wi * f.awayGoals();
                att.get(f.awayId())[1] += wi * aBase * m.defence.get(f.homeId());
                def.get(f.homeId())[0] += wi * f.awayGoals();
                def.get(f.homeId())[1] += wi * aBase * m.attack.get(f.awayId());
                def.get(f.awayId())[0] += wi * f.homeGoals();
                def.get(f.awayId())[1] += wi * hBase * m.attack.get(f.homeId());
            }
            for (int id : ids) {
                m.attack.put(id, (att.get(id)[0] + prior) / (att.get(id)[1] + prior));
                m.defence.put(id, (def.get(id)[0] + prior) / (def.get(id)[1] + prior));
            }
            normalise(m.attack);
            normalise(m.defence);
        }

        m.rho = switch (settings.rhoMode()) {
            case OFF -> 0;
            case FIXED -> DEFAULT_RHO;
            case FITTED -> m.fitRho(results, w);
        };
        return m;
    }

    private static double weight(Fixture f, Instant asOf, Settings s) {
        if (!s.timeWeighted() || f.kickoff() == null) return 1;
        double ageDays = Math.max(0, Duration.between(f.kickoff(), asOf).toMinutes() / 1440.0);
        return Math.pow(0.5, ageDays / s.halfLifeDays());
    }

    /**
     * Maximum-likelihood rho given the fitted ratings. Only the tau term of the Dixon-Coles
     * likelihood depends on rho (tau leaves total probability unchanged), so we maximise
     *   sum_i w_i * log tau(h_i, a_i, lambda_i, mu_i, rho)  +  log N(rho; DEFAULT_RHO, RHO_PRIOR_SD)
     * by grid search, which is precise enough for a single parameter.
     */
    private double fitRho(List<Fixture> results, double[] w) { //w -> weights
        double best = DEFAULT_RHO, bestLl = Double.NEGATIVE_INFINITY;
        for (double r = -0.30; r <= 0.20 + 1e-9; r += 0.0025) { //r -> value of rho being tested
            double ll = -Math.pow(r - DEFAULT_RHO, 2) / (2 * RHO_PRIOR_SD * RHO_PRIOR_SD); //ll -> log likelihood
            for (int i = 0; i < results.size() && ll > Double.NEGATIVE_INFINITY; i++) {
                Fixture f = results.get(i);
                if (f.homeGoals() > 1 || f.awayGoals() > 1) continue; // tau = 1 there
                double t = tau(f.homeGoals(), f.awayGoals(), lambdaHome(f.homeId(), f.awayId()),
                        lambdaAway(f.homeId(), f.awayId()), r);
                ll = t <= 0 ? Double.NEGATIVE_INFINITY : ll + w[i] * Math.log(t);
            }
            if (ll > bestLl) { bestLl = ll; best = r; }
        }
        return best;
    }

    private static void normalise(Map<Integer, Double> ratings) {
        double mean = ratings.values().stream().mapToDouble(Double::doubleValue).average().orElse(1);
        ratings.replaceAll((k, v) -> v / mean);
    }

    public double attack(int teamId) { return attack.getOrDefault(teamId, 1.0); }
    public double defence(int teamId) { return defence.getOrDefault(teamId, 1.0); }
    public double homeAdvantage() { return homeAdvantage; }
    public double rho() { return rho; }
    public Settings settings() { return settings; }
    public int matchesUsed() { return matchesUsed; }

    private double lambdaHome(int homeId, int awayId) { return base * homeAdvantage * attack(homeId) * defence(awayId); }
    private double lambdaAway(int homeId, int awayId) { return base * attack(awayId) * defence(homeId); }

    public Prediction predict(int homeId, int awayId) {
        double lh = lambdaHome(homeId, awayId);
        double la = lambdaAway(homeId, awayId);

        double[][] p = new double[MAX_GOALS + 1][MAX_GOALS + 1];
        double total = 0;
        for (int h = 0; h <= MAX_GOALS; h++) {
            for (int a = 0; a <= MAX_GOALS; a++) {
                p[h][a] = poisson(lh, h) * poisson(la, a) * Math.max(0, tau(h, a, lh, la, rho));
                total += p[h][a];
            }
        }
        for (double[] row : p) for (int a = 0; a < row.length; a++) row[a] /= total;
        return new Prediction(homeId, awayId, lh, la, p);
    }

    private static double tau(int h, int a, double lh, double la, double rho) {
        if (h == 0 && a == 0) return 1 - lh * la * rho;
        if (h == 0 && a == 1) return 1 + lh * rho;
        if (h == 1 && a == 0) return 1 + la * rho;
        if (h == 1 && a == 1) return 1 - rho;
        return 1;
    }

    static double poisson(double lambda, int k) {
        double r = Math.exp(-lambda);
        for (int i = 1; i <= k; i++) r *= lambda / i;
        return r;
    }

    /** Full outcome distribution for one fixture. matrix[h][a] = P(home scores h, away scores a). */
    public record Prediction(int homeId, int awayId, double homeXg, double awayXg, double[][] matrix) {

        public double homeWin() { return sum((h, a) -> h > a); } //sums probability of every scoreline where the home team wins
        public double draw() { return sum((h, a) -> h == a); } //follows logic from above
        public double awayWin() { return sum((h, a) -> h < a); }
        public double over(double line) { return sum((h, a) -> h + a > line); }
        public double bothTeamsScore() { return sum((h, a) -> h > 0 && a > 0); }
        public double homeCleanSheet() { return sum((h, a) -> a == 0); }
        public double awayCleanSheet() { return sum((h, a) -> h == 0); }
        public double homeWinBy(int margin) { return sum((h, a) -> h - a >= margin); }
        public double awayWinBy(int margin) { return sum((h, a) -> a - h >= margin); }

        public double totalGoals(int k) { return sum((h, a) -> h + a == k); }

        /** The n most likely scorelines as {home, away, probability}. */
        public List<double[]> topScores(int n) {
            List<double[]> all = new ArrayList<>();
            for (int h = 0; h <= MAX_GOALS; h++)
                for (int a = 0; a <= MAX_GOALS; a++) all.add(new double[]{h, a, matrix[h][a]});
            all.sort((x, y) -> Double.compare(y[2], x[2]));
            return all.subList(0, n);
        }

        private double sum(ScorePredicate pred) {
            double s = 0;
            for (int h = 0; h <= MAX_GOALS; h++)
                for (int a = 0; a <= MAX_GOALS; a++) if (pred.test(h, a)) s += matrix[h][a];
            return s;
        }

        @FunctionalInterface
        private interface ScorePredicate { boolean test(int h, int a); }
    }
}
