package quiz.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import quiz.protocol.JsonWire;
import quiz.protocol.Msg;
import quiz.protocol.ProtocolException;

/** The socket half of a client. Contains no user interface, so any UI can reuse it. */
public final class ClientCore {

    private final String host;
    private final int port;
    private final ClientListener listener;

    private Socket socket;
    private BufferedWriter out;

    public ClientCore(String host, int port, ClientListener listener) {
        this.host = host;
        this.port = port;
        this.listener = listener;
    }

    public void connect() throws IOException {
        socket = new Socket(host, port);
        out = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

        // Needed because the calling thread is blocked on user input and cannot
        // also wait on the socket.
        Thread.ofVirtual().name("client-reader").start(this::readLoop);
    }

    public synchronized void send(Msg msg) {
        if (out == null) {
            return;
        }
        try {
            out.write(JsonWire.encode(msg));
            out.write("\n");
            out.flush();
        } catch (IOException e) {
            listener.onDisconnected(e.getMessage());
        }
    }

    public void close() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // Going away anyway.
        }
    }

    private void readLoop() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = in.readLine()) != null) {
                try {
                    dispatch(JsonWire.decode(line));
                } catch (ProtocolException e) {
                    listener.onError("BAD_FRAME", e.getMessage());
                }
            }
            listener.onDisconnected("server closed the connection");
        } catch (IOException e) {
            if (socket != null && !socket.isClosed()) {
                listener.onDisconnected(e.getMessage());
            }
        }
    }

    private void dispatch(Msg msg) {
        switch (msg) {
            case Msg.Welcome welcome -> listener.onWelcome(welcome.playerId(), welcome.host());
            case Msg.Lobby lobby -> listener.onLobby(lobby.players());
            case Msg.Question question -> listener.onQuestion(question);
            case Msg.AnswerAck ack -> listener.onAnswerAck(ack.questionIndex());
            case Msg.Reveal reveal -> listener.onReveal(reveal);
            case Msg.Scores scores -> listener.onScores(scores.rows());
            case Msg.GameOver unused -> listener.onGameOver();
            case Msg.Error error -> listener.onError(error.code(), error.message());
            default -> listener.onError("BAD_FRAME", "Unexpected message: " + msg);
        }
    }
}
