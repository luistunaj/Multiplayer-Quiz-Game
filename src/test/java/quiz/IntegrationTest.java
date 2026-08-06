package quiz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import quiz.client.ClientCore;
import quiz.client.ClientListener;
import quiz.model.Question;
import quiz.model.QuestionBank;
import quiz.protocol.Msg;
import quiz.server.GameSession;
import quiz.server.QuizServer;

/** Runs a whole game over real sockets, server and clients in one process. */
class IntegrationTest {

    /** Short, because unanswered questions have to run their clock out. */
    private static final int LIMIT_MS = 1000;
    private static final long REVEAL_PAUSE_MS = 100;
    private static final long AWAIT_SECONDS = 10;

    private QuizServer server;
    private final List<ClientCore> clients = new ArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        QuestionBank bank = new QuestionBank(List.of(
                new Question("Q1", List.of("a", "b", "c", "d"), 1, LIMIT_MS),
                new Question("Q2", List.of("a", "b"), 0, LIMIT_MS)));

        // Port 0: the OS picks a free one, so the test never clashes with a
        // server already running on the developer's machine.
        server = new QuizServer(0, new GameSession(bank, REVEAL_PAUSE_MS));
        server.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        clients.forEach(ClientCore::close);
        server.close();
    }

    private Recorder connect() throws IOException {
        Recorder recorder = new Recorder();
        ClientCore core = new ClientCore("localhost", server.port(), recorder);
        core.connect();
        clients.add(core);
        recorder.core = core;
        return recorder;
    }

    @Test
    @Timeout(30)
    void threeClientsPlayAFullGame() throws Exception {
        Recorder alice = connect();
        Recorder bob = connect();
        Recorder carol = connect();

        alice.core.send(new Msg.Join("alice"));
        assertTrue(alice.await(Msg.Welcome.class).host(), "first to join should be host");

        bob.core.send(new Msg.Join("bob"));
        carol.core.send(new Msg.Join("carol"));
        assertEquals(false, carol.await(Msg.Welcome.class).host());

        alice.core.send(new Msg.Start());

        // Question 1: alice right and first, bob right and later, carol wrong.
        assertEquals("Q1", alice.await(Msg.Question.class).text());
        bob.await(Msg.Question.class);
        carol.await(Msg.Question.class);

        alice.core.send(new Msg.Answer(0, 1));
        alice.await(Msg.AnswerAck.class);
        bob.core.send(new Msg.Answer(0, 1));
        carol.core.send(new Msg.Answer(0, 3));

        Msg.Reveal aliceReveal = alice.await(Msg.Reveal.class);
        Msg.Reveal carolReveal = carol.await(Msg.Reveal.class);
        assertEquals(1, aliceReveal.correctIndex());
        assertTrue(aliceReveal.points() > 0);
        assertEquals(0, carolReveal.points());

        // Question 2: only alice answers, the rest run out of time.
        assertEquals("Q2", alice.await(Msg.Question.class).text());
        alice.core.send(new Msg.Answer(1, 0));

        alice.await(Msg.GameOver.class);
        bob.await(Msg.GameOver.class);
        carol.await(Msg.GameOver.class);

        List<Msg.Scores.Row> finalScores = alice.lastScores();
        assertEquals(3, finalScores.size());
        assertEquals("alice", finalScores.get(0).name());
        assertEquals(1, finalScores.get(0).rank());
        assertTrue(finalScores.get(0).score() > finalScores.get(1).score());
        assertEquals("carol", finalScores.get(2).name());
        assertEquals(0, finalScores.get(2).score());
    }

    @Test
    @Timeout(30)
    void aClientLeavingDoesNotStopTheGame() throws Exception {
        Recorder alice = connect();
        Recorder bob = connect();

        alice.core.send(new Msg.Join("alice"));
        alice.await(Msg.Welcome.class);
        bob.core.send(new Msg.Join("bob"));
        bob.await(Msg.Welcome.class);

        alice.core.send(new Msg.Start());
        alice.await(Msg.Question.class);
        bob.await(Msg.Question.class);

        alice.core.send(new Msg.Answer(0, 1));
        alice.await(Msg.AnswerAck.class);

        // Bob leaves without answering; the round should end for alice anyway.
        bob.core.close();

        assertTrue(alice.await(Msg.Reveal.class).points() > 0);
        alice.await(Msg.GameOver.class);
    }

    @Test
    @Timeout(30)
    void aDuplicateNameIsRejectedOverTheWire() throws Exception {
        Recorder alice = connect();
        Recorder bob = connect();

        alice.core.send(new Msg.Join("alice"));
        alice.await(Msg.Welcome.class);

        bob.core.send(new Msg.Join("alice"));

        assertEquals("BAD_NAME", bob.await(Msg.Error.class).code());
    }

    /** Turns listener callbacks back into messages so a test can wait for one. */
    private static final class Recorder implements ClientListener {

        private final BlockingQueue<Msg> received = new LinkedBlockingQueue<>();
        private final List<Msg.Scores> scores = new ArrayList<>();

        private ClientCore core;

        <T extends Msg> T await(Class<T> type) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS);
            List<Msg> skipped = new ArrayList<>();

            while (System.nanoTime() < deadline) {
                Msg msg = received.poll(200, TimeUnit.MILLISECONDS);
                if (msg == null) {
                    continue;
                }
                if (type.isInstance(msg)) {
                    return type.cast(msg);
                }
                skipped.add(msg);
            }
            throw new AssertionError(
                    "Timed out waiting for " + type.getSimpleName() + ". Saw: " + skipped);
        }

        List<Msg.Scores.Row> lastScores() {
            synchronized (scores) {
                return scores.get(scores.size() - 1).rows();
            }
        }

        @Override
        public void onWelcome(String playerId, boolean host) {
            received.add(new Msg.Welcome(playerId, host));
        }

        @Override
        public void onLobby(List<String> players) {
            received.add(new Msg.Lobby(players));
        }

        @Override
        public void onQuestion(Msg.Question question) {
            received.add(question);
        }

        @Override
        public void onAnswerAck(int questionIndex) {
            received.add(new Msg.AnswerAck(questionIndex));
        }

        @Override
        public void onReveal(Msg.Reveal reveal) {
            received.add(reveal);
        }

        @Override
        public void onScores(List<Msg.Scores.Row> rows) {
            Msg.Scores msg = new Msg.Scores(rows);
            synchronized (scores) {
                scores.add(msg);
            }
            received.add(msg);
        }

        @Override
        public void onGameOver() {
            received.add(new Msg.GameOver());
        }

        @Override
        public void onError(String code, String message) {
            received.add(new Msg.Error(code, message));
        }
    }
}
