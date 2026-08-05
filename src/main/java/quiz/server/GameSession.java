package quiz.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import quiz.model.Question;
import quiz.model.QuestionBank;
import quiz.protocol.Msg;

public final class GameSession implements Runnable {

    private static final int MAX_NAME_LENGTH = 16;

    /** How long players get to read the correct answer before the next question. */
    private static final long REVEAL_PAUSE_MS = 3000;

    private final BlockingQueue<GameEvent> events = new LinkedBlockingQueue<>();

    /** Insertion order is lobby order, so the list players see is stable. */
    private final Map<String, Player> players = new LinkedHashMap<>();
    private final Map<ClientConnection, Player> byConn = new HashMap<>();

    private final QuestionBank bank;

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(
            task -> Thread.ofPlatform().name("game-timer").daemon(true).unstarted(task));

    private Phase phase = Phase.LOBBY;
    private int nextPlayerId = 1;

    private int currentIndex = -1;
    private long questionStartNanos;
    private ScheduledFuture<?> pendingTimer;

    public GameSession(QuestionBank bank) {
        this.bank = bank;
    }

    /** Hands an event to the session thread. Safe to call from any thread. */
    public void post(GameEvent event) {
        events.offer(event);
    }

    @Override
    public void run() {
        while (true) {
            GameEvent event;
            try {
                event = events.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            try {
                handle(event);
            } catch (RuntimeException e) {
                // One bad event must not end the game for everyone.
                System.out.println("Failed to handle " + event + ": " + e);
            }
        }
    }

    private void handle(GameEvent event) {
        switch (event) {
            case GameEvent.Inbound inbound -> handleMessage(inbound);
            case GameEvent.Closed closed -> handleClosed(closed.conn());

            case GameEvent.Timeout timeout -> {
                // The index guard matters: everyone may have answered early, in
                // which case this timer belongs to a question that is already over
                // and firing it would skip the next one.
                if (phase == Phase.QUESTION && timeout.questionIndex() == currentIndex) {
                    endQuestion();
                }
            }

            case GameEvent.Advance advance -> {
                if (phase == Phase.REVEAL && advance.fromIndex() == currentIndex) {
                    startQuestion(currentIndex + 1);
                }
            }
        }
    }

    private void handleMessage(GameEvent.Inbound inbound) {
        ClientConnection conn = inbound.conn();

        switch (inbound.msg()) {
            case Msg.Join join -> handleJoin(conn, join);
            case Msg.Start unused -> handleStart(conn);
            case Msg.Answer answer -> handleAnswer(conn, answer, inbound.recvNanos());
            default ->
                // Everything else is a server-to-client message; a client sending
                // one is confused, but it is not a reason to disconnect it.
                conn.send(new Msg.Error("BAD_FRAME", "Clients cannot send this message."));
        }
    }

    private void handleJoin(ClientConnection conn, Msg.Join join) {
        if (phase != Phase.LOBBY) {
            conn.send(new Msg.Error("WRONG_PHASE", "The game has already started."));
            return;
        }
        if (byConn.containsKey(conn)) {
            conn.send(new Msg.Error("BAD_NAME", "You have already joined."));
            return;
        }

        String name = join.name() == null ? "" : join.name().trim();
        if (name.isEmpty()) {
            conn.send(new Msg.Error("BAD_NAME", "A name is required."));
            return;
        }
        if (name.length() > MAX_NAME_LENGTH) {
            conn.send(new Msg.Error("BAD_NAME",
                    "Names are at most " + MAX_NAME_LENGTH + " characters."));
            return;
        }
        if (isNameTaken(name)) {
            conn.send(new Msg.Error("BAD_NAME", "That name is taken."));
            return;
        }

        Player player = new Player(String.valueOf(nextPlayerId++), name, conn);
        // The first player to arrive runs the game.
        player.host = players.isEmpty();

        players.put(player.id, player);
        byConn.put(conn, player);

        System.out.println("Joined: " + name + (player.host ? " (host)" : ""));

        conn.send(new Msg.Welcome(player.id, player.host));
        broadcastLobby();
    }

    private void handleStart(ClientConnection conn) {
        Player player = byConn.get(conn);
        if (player == null) {
            conn.send(new Msg.Error("WRONG_PHASE", "Join before starting."));
            return;
        }
        if (!player.host) {
            conn.send(new Msg.Error("NOT_HOST", "Only the host can start the game."));
            return;
        }
        if (phase != Phase.LOBBY) {
            conn.send(new Msg.Error("WRONG_PHASE", "The game has already started."));
            return;
        }

        System.out.println("Started by " + player.name);
        startQuestion(0);
    }

    private void handleAnswer(ClientConnection conn, Msg.Answer answer, long recvNanos) {
        Player player = byConn.get(conn);
        if (player == null) {
            conn.send(new Msg.Error("WRONG_PHASE", "Join before answering."));
            return;
        }
        if (phase != Phase.QUESTION) {
            conn.send(new Msg.Error("WRONG_PHASE", "No question is in progress."));
            return;
        }
        if (answer.questionIndex() != currentIndex) {
            // An answer that arrived just after its question ended.
            conn.send(new Msg.Error("WRONG_PHASE", "That question is over."));
            return;
        }
        if (player.answeredForQuestion == currentIndex) {
            conn.send(new Msg.Error("ALREADY_ANSWERED", "Only your first answer counts."));
            return;
        }

        Question question = bank.get(currentIndex);
        int option = answer.optionIndex();
        if (option < 0 || option >= question.options().size()) {
            conn.send(new Msg.Error("BAD_FRAME", "No such option."));
            return;
        }

        player.answeredForQuestion = currentIndex;

        // recvNanos was stamped by the reader thread the moment the message
        // arrived, so a player is not charged for time their answer spent queued
        // behind everyone else's.
        long elapsedNanos = recvNanos - questionStartNanos;
        long limitNanos = TimeUnit.MILLISECONDS.toNanos(question.timeLimitMs());

        player.lastQuestionPoints =
                Scoring.points(question.isCorrect(option), elapsedNanos, limitNanos);
        player.score += player.lastQuestionPoints;

        conn.send(new Msg.AnswerAck(currentIndex));

        // No reason to keep everyone waiting once the last answer is in.
        if (allConnectedAnswered()) {
            endQuestion();
        }
    }

    private void handleClosed(ClientConnection conn) {
        Player player = byConn.remove(conn);
        if (player == null) {
            // Disconnected before joining, or already cleaned up.
            return;
        }

        player.connected = false;
        System.out.println("Left: " + player.name);

        if (phase == Phase.LOBBY) {
            // Nothing worth keeping yet. Once a game is running, players stay in
            // the map so their score still appears on the scoreboard.
            players.remove(player.id);
        }

        if (player.host) {
            player.host = false;
            promoteNewHost();
        }

        broadcastLobby();

        // Otherwise a player leaving mid-question would stall the round until the
        // clock ran out, even though everyone still here has answered.
        if (phase == Phase.QUESTION && allConnectedAnswered()) {
            endQuestion();
        }
    }

    private void startQuestion(int index) {
        if (index >= bank.size()) {
            finish();
            return;
        }

        currentIndex = index;
        phase = Phase.QUESTION;

        for (Player player : players.values()) {
            player.lastQuestionPoints = 0;
        }

        Question question = bank.get(index);
        questionStartNanos = System.nanoTime();

        broadcast(new Msg.Question(index, bank.size(), question.text(), question.options(),
                question.timeLimitMs()));

        // The task only posts an event. It never touches game state, so the timer
        // thread cannot race with the session thread.
        pendingTimer = timer.schedule(() -> post(new GameEvent.Timeout(index)),
                question.timeLimitMs(), TimeUnit.MILLISECONDS);
    }

    private void endQuestion() {
        if (pendingTimer != null) {
            pendingTimer.cancel(false);
            pendingTimer = null;
        }

        phase = Phase.REVEAL;
        Question question = bank.get(currentIndex);

        // Sent per player rather than broadcast: the points differ for each one.
        for (Player player : players.values()) {
            if (player.connected) {
                player.conn.send(new Msg.Reveal(currentIndex, question.correctIndex(),
                        player.lastQuestionPoints, player.score));
            }
        }

        broadcastScores();

        int finished = currentIndex;
        timer.schedule(() -> post(new GameEvent.Advance(finished)),
                REVEAL_PAUSE_MS, TimeUnit.MILLISECONDS);
    }

    private void finish() {
        phase = Phase.FINISHED;
        broadcastScores();
        broadcast(new Msg.GameOver());
        System.out.println("Game over");
    }

    /** True once every connected player has answered the current question. */
    private boolean allConnectedAnswered() {
        boolean anyConnected = false;
        for (Player player : players.values()) {
            if (!player.connected) {
                continue;
            }
            anyConnected = true;
            if (player.answeredForQuestion != currentIndex) {
                return false;
            }
        }
        return anyConnected;
    }

    /** Gives the host role to whoever has been connected longest. */
    private void promoteNewHost() {
        for (Player candidate : players.values()) {
            if (candidate.connected) {
                candidate.host = true;
                System.out.println("New host: " + candidate.name);
                candidate.conn.send(new Msg.Welcome(candidate.id, true));
                return;
            }
        }
    }

    private boolean isNameTaken(String name) {
        for (Player player : players.values()) {
            if (player.name.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private void broadcastLobby() {
        List<String> names = new ArrayList<>();
        for (Player player : players.values()) {
            if (player.connected) {
                names.add(player.name);
            }
        }
        broadcast(new Msg.Lobby(names));
    }

    private void broadcastScores() {
        List<Player> ranked = new ArrayList<>(players.values());
        ranked.sort(Comparator.comparingInt((Player player) -> player.score).reversed());

        List<Msg.Scores.Row> rows = new ArrayList<>();
        int rank = 1;
        for (Player player : ranked) {
            rows.add(new Msg.Scores.Row(rank++, player.name, player.score));
        }

        broadcast(new Msg.Scores(rows));
    }

    private void broadcast(Msg msg) {
        for (Player player : players.values()) {
            if (player.connected) {
                player.conn.send(msg);
            }
        }
    }
}
