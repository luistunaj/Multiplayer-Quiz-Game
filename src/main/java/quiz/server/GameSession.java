package quiz.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import quiz.protocol.Msg;

public final class GameSession implements Runnable {

    private static final int MAX_NAME_LENGTH = 16;

    private final BlockingQueue<GameEvent> events = new LinkedBlockingQueue<>();

    /** Insertion order is lobby order, so the list players see is stable. */
    private final Map<String, Player> players = new LinkedHashMap<>();
    private final Map<ClientConnection, Player> byConn = new HashMap<>();

    private Phase phase = Phase.LOBBY;
    private int nextPlayerId = 1;

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
            case GameEvent.Timeout unused -> {
                // No questions yet.
            }
            case GameEvent.Advance unused -> {
                // No questions yet.
            }
        }
    }

    private void handleMessage(GameEvent.Inbound inbound) {
        ClientConnection conn = inbound.conn();

        switch (inbound.msg()) {
            case Msg.Join join -> handleJoin(conn, join);
            case Msg.Start unused -> handleStart(conn);
            case Msg.Answer unused ->
                conn.send(new Msg.Error("WRONG_PHASE", "No question is in progress."));
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
            conn.send(new Msg.Error("BAD_NAME", "Names are at most " + MAX_NAME_LENGTH + " characters."));
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

        // Questions arrive in the next stage; for now this only proves the host
        // check and the phase check work.
        System.out.println("Start requested by " + player.name);
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
            // the map so their score survives.
            players.remove(player.id);
        }

        if (player.host) {
            player.host = false;
            promoteNewHost();
        }

        broadcastLobby();
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

    private void broadcast(Msg msg) {
        for (Player player : players.values()) {
            if (player.connected) {
                player.conn.send(msg);
            }
        }
    }
}
