package pl.data;

import pl.data.Models.Fixture;
import pl.data.Models.Team;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Past Premier League seasons from football-data.co.uk (free CSVs, no key), used for backtesting.
 * Each file has every result plus pre-match bookmaker odds, which give a market benchmark.
 * Completed seasons never change, so they are downloaded once and cached.
 */
public final class HistoricalData {
    private static final Path CACHE_DIR = Path.of(System.getProperty("user.home"), ".pl-stats-cache");
    private static final ZoneId UK = ZoneId.of("Europe/London");

    /** One season of results. marketProbs[i] is the bookmaker's {home, draw, away} for results[i], or null. */
    public record Season(String label, List<Fixture> results, List<Integer> teamIds, List<double[]> marketProbs) {
        public boolean hasMarket() { return marketProbs.stream().anyMatch(p -> p != null); }

        /** The current season from FPL data (no bookmaker odds in that API). */
        public static Season fromLeague(String label, LeagueData data) {
            List<Fixture> results = data.results().stream()
                    .filter(f -> f.kickoff() != null).sorted(Comparator.comparing(Fixture::kickoff)).toList();
            List<double[]> none = new ArrayList<>();
            results.forEach(f -> none.add(null));
            return new Season(label, results, data.teams().stream().map(Team::id).toList(), none);
        }
    }

    /** Season code used in football-data.co.uk URLs, and its display label. Newest first. */
    public static final Map<String, String> PAST_SEASONS = new LinkedHashMap<>();
    static {
        PAST_SEASONS.put("2526", "2025/26");
        PAST_SEASONS.put("2425", "2024/25");
        PAST_SEASONS.put("2324", "2023/24");
        PAST_SEASONS.put("2223", "2022/23");
        PAST_SEASONS.put("2122", "2021/22");
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static Season load(String code) throws IOException {
        Path cache = CACHE_DIR.resolve("E0-" + code + ".csv");
        String csv;
        if (Files.exists(cache)) {
            csv = Files.readString(cache, StandardCharsets.UTF_8);
        } else {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create("https://www.football-data.co.uk/mmz4281/" + code + "/E0.csv"))
                        .timeout(Duration.ofSeconds(30)).header("User-Agent", "PL-Stats-Desktop/1.0").GET().build();
                HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode());
                csv = res.body();
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
            Files.createDirectories(CACHE_DIR);
            Files.writeString(cache, csv, StandardCharsets.UTF_8);
        }
        return parse(PAST_SEASONS.getOrDefault(code, code), csv);
    }

    static Season parse(String label, String csv) {
        String[] lines = csv.replace("﻿", "").split("\r?\n");
        Map<String, Integer> col = new HashMap<>();
        String[] header = lines[0].split(",", -1);
        for (int i = 0; i < header.length; i++) col.put(header[i].trim(), i);

        // Pinnacle is the sharpest bookmaker; fall back to the market average if missing.
        String[][] oddsCols = {{"PSH", "PSD", "PSA"}, {"AvgH", "AvgD", "AvgA"}, {"B365H", "B365D", "B365A"}};
        Map<String, Integer> teamIds = new LinkedHashMap<>();
        List<Object[]> rows = new ArrayList<>(); // {Fixture, double[] market}
        for (int li = 1; li < lines.length; li++) {
            String[] c = lines[li].split(",", -1);
            String hg = get(c, col, "FTHG"), ag = get(c, col, "FTAG");
            if (hg.isEmpty() || ag.isEmpty()) continue;
            String home = get(c, col, "HomeTeam"), away = get(c, col, "AwayTeam");
            int hId = teamIds.computeIfAbsent(home, k -> teamIds.size() + 1);
            int aId = teamIds.computeIfAbsent(away, k -> teamIds.size() + 1);

            String d = get(c, col, "Date");
            LocalDate date = LocalDate.parse(d, DateTimeFormatter.ofPattern(d.length() == 8 ? "dd/MM/yy" : "dd/MM/yyyy"));
            String t = get(c, col, "Time");
            LocalTime time = t.isEmpty() ? LocalTime.of(15, 0) : LocalTime.parse(t);

            Fixture f = new Fixture(li, null, date.atTime(time).atZone(UK).toInstant(), hId, aId,
                    Integer.parseInt(hg), Integer.parseInt(ag), true, true, 0, 0);
            rows.add(new Object[]{f, impliedProbs(c, col, oddsCols)});
        }
        rows.sort(Comparator.comparing(r -> ((Fixture) r[0]).kickoff()));
        return new Season(label,
                rows.stream().map(r -> (Fixture) r[0]).toList(),
                new ArrayList<>(teamIds.values()),
                rows.stream().map(r -> (double[]) r[1]).toList());
    }

    /** Converts decimal odds to probabilities and removes the bookmaker's margin (normalise to 1). */
    private static double[] impliedProbs(String[] c, Map<String, Integer> col, String[][] candidates) {
        for (String[] names : candidates) {
            try {
                double h = 1 / Double.parseDouble(get(c, col, names[0]));
                double d = 1 / Double.parseDouble(get(c, col, names[1]));
                double a = 1 / Double.parseDouble(get(c, col, names[2]));
                double s = h + d + a;
                return new double[]{h / s, d / s, a / s};
            } catch (NumberFormatException ignored) { }
        }
        return null;
    }

    private static String get(String[] c, Map<String, Integer> col, String name) {
        Integer i = col.get(name);
        return i == null || i >= c.length ? "" : c[i].trim();
    }
}
