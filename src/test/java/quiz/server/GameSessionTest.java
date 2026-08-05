package quiz.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import quiz.model.Question;
import quiz.model.QuestionBank;
import quiz.protocol.Msg;

class GameSessionTest {

    private static final int LIMIT_MS = 20_000;
    private static final long LIMIT_NANOS = TimeUnit.MILLISECONDS.toNanos(LIMIT_MS);

    private GameSession session;
    private FakeConnection alice;
    private FakeConnection bob;

    @BeforeEach
    void setUp() {
        QuestionBank bank = new QuestionBank(List.of(
                new Question("Q1", List.of("a", "b", "c", "d"), 1, LIMIT_MS),
                new Question("Q2", List.of("a", "b"), 0, LIMIT_MS)));

        session = new GameSession(bank);
        alice = new FakeConnection("alice");
        bob = new FakeConnection("bob");
    }

    private void join(FakeConnection conn, String name) {
        session.handle(new GameEvent.Inbound(conn, new Msg.Join(name), System.nanoTime()));
    }

    private void send(FakeConnection conn, Msg msg) {
        session.handle(new GameEvent.Inbound(conn, msg, System.nanoTime()));
    }

    private void answer(FakeConnection conn, int questionIndex, int option, long recvNanos) {
        session.handle(
                new GameEvent.Inbound(conn, new Msg.Answer(questionIndex, option), recvNanos));
    }

    private void startGame() {
        join(alice, "alice");
        join(bob, "bob");
        send(alice, new Msg.Start());
        alice.clear();
        bob.clear();
    }

    @Test
    void firstPlayerBecomesHost() {
        join(alice, "alice");
        join(bob, "bob");

        assertTrue(alice.require(Msg.Welcome.class).host());
        assertFalse(bob.require(Msg.Welcome.class).host());
    }

    @Test
    void joiningAnnouncesEveryoneToEveryone() {
        join(alice, "alice");
        join(bob, "bob");

        assertEquals(List.of("alice", "bob"), alice.require(Msg.Lobby.class).players());
        assertEquals(List.of("alice", "bob"), bob.require(Msg.Lobby.class).players());
    }

    @Test
    void duplicateNameIsRejected() {
        join(alice, "alice");
        join(bob, "ALICE");

        assertEquals("BAD_NAME", bob.require(Msg.Error.class).code());
    }

    @Test
    void onlyTheHostCanStart() {
        join(alice, "alice");
        join(bob, "bob");
        bob.clear();

        send(bob, new Msg.Start());

        assertEquals("NOT_HOST", bob.require(Msg.Error.class).code());
    }

    @Test
    void startingSendsTheFirstQuestionToEveryone() {
        join(alice, "alice");
        join(bob, "bob");

        send(alice, new Msg.Start());

        assertEquals(0, alice.require(Msg.Question.class).index());
        assertEquals("Q1", bob.require(Msg.Question.class).text());
    }

    @Test
    void joiningAfterTheStartIsRejected() {
        startGame();

        FakeConnection late = new FakeConnection("late");
        join(late, "carol");

        assertEquals("WRONG_PHASE", late.require(Msg.Error.class).code());
    }

    @Test
    void correctAnswerIsAcknowledgedAndScored() {
        startGame();

        answer(alice, 0, 1, System.nanoTime());
        assertEquals(0, alice.require(Msg.AnswerAck.class).questionIndex());

        session.handle(new GameEvent.Timeout(0));

        assertTrue(alice.require(Msg.Reveal.class).total() > 0);
    }

    @Test
    void wrongAnswerScoresNothing() {
        startGame();

        answer(alice, 0, 3, System.nanoTime());
        session.handle(new GameEvent.Timeout(0));

        assertEquals(0, alice.require(Msg.Reveal.class).total());
    }

    @Test
    void answeringTwiceIsRejectedAndDoesNotScoreTwice() {
        startGame();

        answer(alice, 0, 1, System.nanoTime());
        answer(alice, 0, 1, System.nanoTime());
        assertEquals("ALREADY_ANSWERED", alice.require(Msg.Error.class).code());

        session.handle(new GameEvent.Timeout(0));

        // Scored once: the running total is exactly this question's points.
        Msg.Reveal reveal = alice.require(Msg.Reveal.class);
        assertEquals(reveal.points(), reveal.total());
    }

    @Test
    void answerForAnotherQuestionIsRejected() {
        startGame();

        answer(alice, 1, 1, System.nanoTime());

        assertEquals("WRONG_PHASE", alice.require(Msg.Error.class).code());
        assertEquals(0, alice.count(Msg.AnswerAck.class));
    }

    @Test
    void optionOutsideTheQuestionIsRejected() {
        startGame();

        answer(alice, 0, 99, System.nanoTime());

        assertEquals("BAD_FRAME", alice.require(Msg.Error.class).code());
    }

    @Test
    void fasterCorrectAnswerScoresMore() {
        startGame();

        long now = System.nanoTime();
        answer(alice, 0, 1, now);
        answer(bob, 0, 1, now + LIMIT_NANOS / 2);

        assertTrue(alice.require(Msg.Reveal.class).points()
                > bob.require(Msg.Reveal.class).points());
    }

    @Test
    void everyoneAnsweringEndsTheQuestionWithoutWaiting() {
        startGame();

        answer(alice, 0, 1, System.nanoTime());
        assertEquals(0, alice.count(Msg.Reveal.class));

        answer(bob, 0, 1, System.nanoTime());
        assertEquals(1, alice.count(Msg.Reveal.class));
    }

    @Test
    void revealCarriesTheCorrectOption() {
        startGame();

        answer(alice, 0, 1, System.nanoTime());
        session.handle(new GameEvent.Timeout(0));

        assertEquals(1, alice.require(Msg.Reveal.class).correctIndex());
    }

    @Test
    void timeoutEndsTheQuestionWhenNobodyAnswered() {
        startGame();

        session.handle(new GameEvent.Timeout(0));

        assertEquals(0, alice.require(Msg.Reveal.class).points());
        assertEquals(1, alice.count(Msg.Scores.class));
    }

    @Test
    void aLateTimeoutDoesNotSkipTheNextQuestion() {
        startGame();

        answer(alice, 0, 1, System.nanoTime());
        answer(bob, 0, 1, System.nanoTime());
        session.handle(new GameEvent.Advance(0));
        alice.clear();

        // Question 0's timer firing after everyone answered early.
        session.handle(new GameEvent.Timeout(0));

        assertEquals(0, alice.count(Msg.Reveal.class));
        assertEquals(0, alice.count(Msg.GameOver.class));
    }

    @Test
    void advanceMovesToTheNextQuestion() {
        startGame();

        session.handle(new GameEvent.Timeout(0));
        alice.clear();
        session.handle(new GameEvent.Advance(0));

        assertEquals(1, alice.require(Msg.Question.class).index());
    }

    @Test
    void runningOutOfQuestionsEndsTheGame() {
        startGame();

        session.handle(new GameEvent.Timeout(0));
        session.handle(new GameEvent.Advance(0));
        session.handle(new GameEvent.Timeout(1));
        session.handle(new GameEvent.Advance(1));

        assertEquals(1, alice.count(Msg.GameOver.class));
    }

    @Test
    void scoresAreRankedHighestFirst() {
        startGame();

        long now = System.nanoTime();
        answer(alice, 0, 1, now);
        answer(bob, 0, 3, now);

        List<Msg.Scores.Row> rows = alice.require(Msg.Scores.class).rows();

        assertEquals("alice", rows.get(0).name());
        assertEquals(1, rows.get(0).rank());
        assertTrue(rows.get(0).score() > rows.get(1).score());
    }

    @Test
    void leavingMidQuestionEndsTheRoundForWhoeverIsLeft() {
        startGame();

        answer(alice, 0, 1, System.nanoTime());
        assertEquals(0, alice.count(Msg.Reveal.class));

        session.handle(new GameEvent.Closed(bob));

        assertEquals(1, alice.count(Msg.Reveal.class));
    }

    @Test
    void theHostRoleMovesOnWhenTheHostLeaves() {
        join(alice, "alice");
        join(bob, "bob");
        bob.clear();

        session.handle(new GameEvent.Closed(alice));

        assertTrue(bob.require(Msg.Welcome.class).host());
    }

    @Test
    void leavingTheLobbyRemovesThePlayerFromIt() {
        join(alice, "alice");
        join(bob, "bob");
        bob.clear();

        session.handle(new GameEvent.Closed(alice));

        assertEquals(List.of("bob"), bob.require(Msg.Lobby.class).players());
    }

    @Test
    void aServerMessageFromAClientIsRejected() {
        join(alice, "alice");
        alice.clear();

        send(alice, new Msg.GameOver());

        assertEquals("BAD_FRAME", alice.require(Msg.Error.class).code());
    }
}
