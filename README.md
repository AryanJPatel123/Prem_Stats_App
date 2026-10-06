# Premier League Stats (Java)

A desktop app that pulls live Premier League data, shows it in sortable tables and
interactive charts, and calculates match and season probabilities.

**Disclaimer: The majority of the code provided here has been written by claude, apart from the mathematical functions and models which have been calculated and written by me, as well as some minor bug fixes when required. All sources for model calculations can be found at the end of this page.**

**To View code written entirely by me, refer to Project Euler**

**Requirements:** JDK 17 or newer. No other libraries — just Swing and the JDK HTTP client.

## Open in IntelliJ IDEA

1. **File → Open…** and select this folder (the one containing `pom.xml`). Choose **Open as Project** / trust it if asked.
2. IntelliJ detects the Maven project automatically. If it asks for a JDK, pick any JDK 17+ (File → Project Structure → SDK).
3. Open `src/main/java/pl/App.java` and click the green ▶ next to `main`.

To build a runnable jar inside IntelliJ: Maven tool window → Lifecycle → `package` → `target/pl-stats.jar`.

## Run without IntelliJ

```
run.bat        # launches pl-stats.jar (builds it first if missing)
build.bat      # recompiles pl-stats.jar using only the JDK (no Maven needed)
```

or: `java -jar pl-stats.jar`, or with Maven: `mvn compile exec:java`

## Data source

The official **Fantasy Premier League public API** (no key or sign-up needed):

- `https://fantasy.premierleague.com/api/bootstrap-static/` – teams, every player's season stats
- `https://fantasy.premierleague.com/api/fixtures/` – all 380 fixtures with scores

Responses are cached in `%USERPROFILE%\.pl-stats-cache\`, so the app still opens offline
using the last download.

Past seasons for the **Model Check** tab come from **football-data.co.uk** (free CSVs of every
Premier League result plus bookmaker odds), e.g. `https://www.football-data.co.uk/mmz4281/2526/E0.csv`.
They are downloaded once and cached.

## Screens

| Tab | What it does |
|---|---|
| League Table | Standings computed from results, form badges, zone markers, model attack/defence ratings |
| Fixtures & Results | Filter by team / gameweek / played-or-upcoming; win/draw/loss odds for upcoming games; double-click to open in the predictor |
| Players | All players with goals, assists, xG, xA, per-90 stats, cards, FPL points, price; search (accent-insensitive), filter by team/position/minutes |
| Charts | Team rankings (points, goals, GD, ratings…), player leaderboards (goals, xG, per-90, finishing vs xG…) filterable by team/position, and a points-race line chart |
| Match Predictor | Any home v away pairing: win/draw/loss, expected goals, over 1.5/2.5/3.5, both teams to score, clean sheets, win by 2+, fair odds, most likely scores, full scoreline heatmap; plus a whole-gameweek prediction table |
| Season Simulator | Monte Carlo simulation of the rest of the season: title, top 4, top 7, relegation chances, expected points and position, per-team finishing-position distribution |
| Model Check | Backtests the match model on the last 5 seasons (or this one): every match predicted using only earlier results, scored by RPS, accuracy and log loss, compared against Pinnacle's odds. Change the model settings and apply them to every tab |

Every table sorts by clicking its column headers.

## How the probabilities work

`pl.stats.MatchModel` is a **Poisson goals model with a Dixon-Coles correction**:

- home expected goals = `base × homeAdvantage × attack[home] × defence[away]`
- away expected goals = `base × attack[away] × defence[home]`

Attack/defence ratings are fitted iteratively from this season's results (so a goal against
a strong defence counts for more), and shrunk toward average using "phantom" average games
per team (default 6) so early-season results don't swing the ratings too far. The Dixon-Coles
term corrects plain Poisson's under-estimate of 0-0 and 1-1 draws; its strength (rho) is fitted
by maximum likelihood with a gentle prior around -0.08. Optional time-weighting gives each
result weight `0.5^(age / half-life)`. The resulting 11×11 scoreline matrix gives every market
(1X2, over/under, BTTS, clean sheets, exact scores).

**Default settings were chosen by backtest** (2021/22-2025/26, 1,650 matches after skipping the
first 50 of each season):

| Model | RPS (lower = better) | Accuracy |
|---|---|---|
| No team ratings (home advantage only) | 0.2331 | 44.4% |
| Plain Poisson | 0.2063 | 52.2% |
| Poisson + Dixon-Coles (fitted rho), the default | 0.2064 | 52.1% |
| + time-weighting, 180-day half-life | 0.2063 | 53.1% |
| Bookmaker (Pinnacle) | 0.1976 | 55.0% |

Dixon-Coles and time-weighting make no measurable difference for a single-season model (short
half-lives made it worse). Shrinkage matters more: 6 beat the original 10. The bookmaker remains
about 4% better on RPS.

`pl.stats.SeasonSimulator` samples a scoreline for every remaining fixture from that matrix,
10,000 times by default, starting from the current table.

**Limitations:** it uses the current season only (the FPL API has no history), so it's least
reliable in the first few gameweeks. It ignores injuries, transfers and fixture congestion.
It's a statistical model for fun and analysis, not betting advice.

## Further reading

The maths behind each part of the app, with where it lives in the code.

| Part of the app | Code | Reading |
|---|---|---|
| Expected goals and team attack/defence ratings | `MatchModel.fit()`, `predict()` | [Predicting Football Results With Statistical Modelling](https://dashee87.github.io/football/python/predicting-football-results-with-statistical-modelling/) (dashee87) |
| Match Predictor stats from the scoreline grid (1X2, over/under, BTTS, clean sheets, likely scores) | `MatchModel.Prediction` | [Predicting Football Results With Statistical Modelling](https://dashee87.github.io/football/python/predicting-football-results-with-statistical-modelling/) (dashee87) |
| Dixon-Coles low-score correction and fitted rho | `MatchModel.tau()`, `fitRho()` | [Predicting Football Results With Statistical Modelling: Dixon-Coles and Time-Weighting](https://dashee87.github.io/football/python/predicting-football-results-with-statistical-modelling-dixon-coles-and-time-weighting/) (dashee87) |
| Time-weighting (half-life) | `MatchModel.weight()` | [Dixon-Coles and Time-Weighting](https://dashee87.github.io/football/python/predicting-football-results-with-statistical-modelling-dixon-coles-and-time-weighting/) (dashee87), time-weighting section |
| Shrinkage ("phantom games") | `Settings.teamShrinkage`, `prior` in `MatchModel.fit()` | [Regression to the Mean and Team Wins](https://www.footballperspective.com/regression-to-the-mean-and-team-wins/) (Football Perspective) |
| Season Simulator (Monte Carlo) | `SeasonSimulator` | [Monte Carlo Simulation in Football Explained](https://www.sportmonks.com/glossary/monte-carlo-simulation/) (Sportmonks) |
| Fair odds and removing the bookmaker's margin | `PredictorPanel.card()`, `HistoricalData.impliedProbs()` | [From Biased Odds to Fair Probabilities: Removing the Bookmaker's Overround](https://pena.lt/y/2025/09/14/from-biased-odds-to-fair-probabilities/) (pena.lt) |
| Backtesting and scoring (RPS, accuracy, log loss) | `Backtest` | Constantinou & Fenton, [Evaluating the Predictive Accuracy of Association Football Forecasting](https://www.eecs.qmul.ac.uk/~norman/papers/evaluating_predictive_accuracy_football.pdf) |
| How well these models perform | Model Check tab | Ley, Van de Wiele & Van Eetvelde (2019), [Ranking soccer teams on the basis of their current strength: a comparison of maximum likelihood approaches](https://orbilu.uni.lu/bitstream/10993/57869/1/ley-et-al-2019-ranking-soccer-teams-on-the-basis-of-their-current-strength-a-comparison-of-maximum-likelihood-approaches.pdf), *Statistical Modelling* |

## Code layout

```
pom.xml                                   Maven build (no dependencies)
src/main/java/pl/App.java                 main window, async loading
src/main/java/pl/data/                    Json parser, FplClient (HTTP + cache), Models, LeagueData,
                                          HistoricalData (past seasons for backtesting)
src/main/java/pl/stats/                   MatchModel, SeasonSimulator, Backtest
src/main/java/pl/ui/                      one panel per tab (incl. ModelCheckPanel), Theme, ListTableModel
src/main/java/pl/ui/charts/               BarChart, LineChart, ScoreHeatmap, OutcomeBar (Java2D)
```
