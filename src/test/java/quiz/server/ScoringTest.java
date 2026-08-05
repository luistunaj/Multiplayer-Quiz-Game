package quiz.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class ScoringTest {

    private static final long LIMIT = TimeUnit.SECONDS.toNanos(20);

    @Test
    void wrongAnswerScoresNothingHoweverFast() {
        assertEquals(0, Scoring.points(false, 0, LIMIT));
    }

    @Test
    void instantCorrectAnswerScoresFullPoints() {
        assertEquals(Scoring.MAX_POINTS, Scoring.points(true, 0, LIMIT));
    }

    @Test
    void answerAtTheDeadlineScoresHalf() {
        assertEquals(Scoring.MAX_POINTS / 2, Scoring.points(true, LIMIT, LIMIT));
    }

    @Test
    void answerHalfwayScoresThreeQuarters() {
        assertEquals(750, Scoring.points(true, LIMIT / 2, LIMIT));
    }

    @Test
    void answerAfterTheDeadlineIsClampedNotNegative() {
        assertEquals(Scoring.MAX_POINTS / 2, Scoring.points(true, LIMIT * 5, LIMIT));
    }

    @Test
    void negativeElapsedIsClampedToFullPoints() {
        // Guards against clock jitter producing an answer that appears to arrive
        // before the question was sent.
        assertEquals(Scoring.MAX_POINTS, Scoring.points(true, -1000, LIMIT));
    }

    @Test
    void fasterAnswersAlwaysScoreAtLeastAsMuch() {
        int previous = Integer.MAX_VALUE;
        for (long elapsed = 0; elapsed <= LIMIT; elapsed += LIMIT / 20) {
            int points = Scoring.points(true, elapsed, LIMIT);
            assertTrue(points <= previous, "points rose as time passed at " + elapsed);
            previous = points;
        }
    }

    @Test
    void aSlowCorrectAnswerStillBeatsAFastWrongOne() {
        assertTrue(Scoring.points(true, LIMIT, LIMIT) > Scoring.points(false, 0, LIMIT));
    }

    @Test
    void aZeroLimitDoesNotDivideByZero() {
        assertEquals(Scoring.MAX_POINTS, Scoring.points(true, 5000, 0));
    }
}
