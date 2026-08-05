package quiz.server;

public final class Scoring {

    /** Points for a correct answer given instantly. */
    public static final int MAX_POINTS = 1000;

    /** The share of the points that speed decides; the rest is for being right. */
    private static final double SPEED_SHARE = 0.5;

    private Scoring() {
    }
    public static int points(boolean correct, long elapsedNanos, long limitNanos) {
        if (!correct) {
            return 0;
        }
        if (limitNanos <= 0) {
            return MAX_POINTS;
        }

        // Clamped, because an answer can arrive marginally after the deadline and
        // still be scored, and because clock jitter can make elapsed negative.
        double fraction = Math.clamp((double) elapsedNanos / limitNanos, 0.0, 1.0);
        return (int) Math.round(MAX_POINTS * (1.0 - SPEED_SHARE * fraction));
    }
}
