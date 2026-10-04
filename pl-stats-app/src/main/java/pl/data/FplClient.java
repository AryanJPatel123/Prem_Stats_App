package pl.data;

import pl.data.Models.Fixture;
import pl.data.Models.Player;
import pl.data.Models.Position;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads Premier League data from the official Fantasy Premier League public API
 * (no API key required). Responses are cached on disk so the app still works offline.
 *
 *   bootstrap-static : teams, players and their season stats, gameweeks
 *   fixtures         : every fixture of the season, with scores for played games
 */
public final class FplClient {
    private static final String BASE = "https://fantasy.premierleague.com/api/";
    private static final Path CACHE_DIR = Path.of(System.getProperty("user.home"), ".pl-stats-cache");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Where the data came from, shown in the status bar. */
    private String sourceDescription = "";

    public String sourceDescription() { return sourceDescription; }

    public LeagueData load() throws IOException {
        boolean[] fromCache = new boolean[1];
        String bootstrap = fetch("bootstrap-static/", "bootstrap.json", fromCache);
        String fixtures = fetch("fixtures/", "fixtures.json", fromCache);
        sourceDescription = fromCache[0]
                ? "Offline - showing cached data from " + Files.getLastModifiedTime(CACHE_DIR.resolve("fixtures.json"))
                : "Live data from fantasy.premierleague.com";
        return parse(bootstrap, fixtures);
    }

    private String fetch(String endpoint, String cacheName, boolean[] fromCache) throws IOException {
        Path cache = CACHE_DIR.resolve(cacheName);
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + endpoint))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "PL-Stats-Desktop/1.0")
                    .GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode() + " from " + endpoint);
            Files.createDirectories(CACHE_DIR);
            Files.writeString(cache, res.body(), StandardCharsets.UTF_8);
            return res.body();
        } catch (IOException | InterruptedException e) {
            if (Files.exists(cache)) {
                fromCache[0] = true;
                return Files.readString(cache, StandardCharsets.UTF_8);
            }
            throw new IOException("Could not reach the FPL API and no cached copy exists: " + e.getMessage(), e);
        }
    }

    static LeagueData parse(String bootstrapJson, String fixturesJson) {
        Map<String, Object> root = Json.obj(Json.parse(bootstrapJson));

        List<Team> teams = new ArrayList<>();
        for (Object o : Json.arr(root.get("teams"))) {
            Map<String, Object> t = Json.obj(o);
            teams.add(new Team(Json.integer(t, "id"), Json.str(t, "name"), Json.str(t, "short_name")));
        }

        List<Player> players = new ArrayList<>();
        for (Object o : Json.arr(root.get("elements"))) {
            Map<String, Object> p = Json.obj(o);
            players.add(new Player(
                    Json.integer(p, "id"),
                    Json.str(p, "web_name"),
                    Json.str(p, "first_name") + " " + Json.str(p, "second_name"),
                    Json.integer(p, "team"),
                    Position.fromFplType(Json.integer(p, "element_type")),
                    Json.integer(p, "minutes"),
                    Json.integer(p, "starts"),
                    Json.integer(p, "goals_scored"),
                    Json.integer(p, "assists"),
                    Json.integer(p, "clean_sheets"),
                    Json.integer(p, "goals_conceded"),
                    Json.integer(p, "yellow_cards"),
                    Json.integer(p, "red_cards"),
                    Json.integer(p, "saves"),
                    Json.integer(p, "bonus"),
                    Json.integer(p, "total_points"),
                    Json.integer(p, "now_cost") / 10.0,
                    Json.dbl(p, "form"),
                    Json.dbl(p, "expected_goals"),
                    Json.dbl(p, "expected_assists"),
                    Json.dbl(p, "selected_by_percent"),
                    Json.str(p, "status"),
                    Json.str(p, "news")));
        }

        List<Fixture> fixtures = new ArrayList<>();
        for (Object o : Json.arr(Json.parse(fixturesJson))) {
            Map<String, Object> f = Json.obj(o);
            String kickoff = Json.str(f, "kickoff_time");
            // "finished_provisional" flips at the final whistle; "finished" waits for bonus points.
            boolean finished = Json.bool(f, "finished") || Json.bool(f, "finished_provisional");
            fixtures.add(new Fixture(
                    Json.integer(f, "id"),
                    Json.intOrNull(f, "event"),
                    kickoff == null ? null : Instant.parse(kickoff),
                    Json.integer(f, "team_h"),
                    Json.integer(f, "team_a"),
                    Json.intOrNull(f, "team_h_score"),
                    Json.intOrNull(f, "team_a_score"),
                    finished,
                    Json.bool(f, "started"),
                    Json.integer(f, "team_h_difficulty"),
                    Json.integer(f, "team_a_difficulty")));
        }

        return new LeagueData(teams, players, fixtures);
    }
}
