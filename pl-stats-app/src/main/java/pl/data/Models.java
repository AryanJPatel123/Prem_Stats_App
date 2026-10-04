package pl.data;

import java.time.Instant;

/** Plain data types for the app. Kept in one file because they are tiny. */
public final class Models {
    private Models() { }

    public enum Position {
        GKP("Goalkeeper"), DEF("Defender"), MID("Midfielder"), FWD("Forward");

        public final String label;
        Position(String label) { this.label = label; }

        public static Position fromFplType(int t) {
            return switch (t) {
                case 1 -> GKP;
                case 2 -> DEF;
                case 3 -> MID;
                default -> FWD;
            };
        }
    }

    public record Team(int id, String name, String shortName) {
        @Override public String toString() { return name; }
    }

    public record Player(
            int id, String webName, String fullName, int teamId, Position position,
            int minutes, int starts, int goals, int assists, int cleanSheets, int goalsConceded,
            int yellowCards, int redCards, int saves, int bonus, int totalPoints,
            double price, double form, double xG, double xA, double selectedByPct,
            String status, String news) {

        public double goalsPer90() { return minutes == 0 ? 0 : goals * 90.0 / minutes; }
        public double xgiPer90() { return minutes == 0 ? 0 : (xG + xA) * 90.0 / minutes; }

        /** Readable availability, from FPL's single-letter status code. */
        public String availability() {
            return switch (status == null ? "" : status) {
                case "a" -> "Available";
                case "d" -> "Doubtful";
                case "i" -> "Injured";
                case "s" -> "Suspended";
                case "u" -> "Unavailable";
                case "n" -> "On loan";
                default -> "-";
            };
        }
    }

    public record Fixture(
            int id, Integer gameweek, Instant kickoff, int homeId, int awayId,
            Integer homeGoals, Integer awayGoals, boolean finished, boolean started,
            int homeDifficulty, int awayDifficulty) {

        public boolean hasResult() { return finished && homeGoals != null && awayGoals != null; }
        public boolean involves(int teamId) { return homeId == teamId || awayId == teamId; }
    }
}
