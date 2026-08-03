package quiz.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import quiz.protocol.JsonWire;
import quiz.protocol.Msg;
import quiz.protocol.ProtocolException;

public final class ClientConnection {

    /** How many frames a client may fall behind before it is dropped. */
    private static final int OUTBOUND_CAPACITY = 256;

    private static final String CLOSE_SENTINEL = new String("__CLOSE__");

    private final Socket socket;
    private final String remote;
    private final Consumer<GameEvent> events;
    private final BufferedReader in;
    private final BufferedWriter out;
    private final BlockingQueue<String> outbound = new ArrayBlockingQueue<>(OUTBOUND_CAPACITY);
    private final AtomicBoolean closed = new AtomicBoolean();

    public ClientConnection(Socket socket, Consumer<GameEvent> events) throws IOException {
        this.socket = socket;
        // Captured now; getRemoteSocketAddress() returns null once the socket closes.
        this.remote = String.valueOf(socket.getRemoteSocketAddress());
        this.events = events;
        this.in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    public String remote() {
        return remote;
    }

    /** Starts the reader and writer threads. */
    public void start() {
        Thread.ofVirtual().name("reader-" + remote).start(this::readLoop);
        Thread.ofVirtual().name("writer-" + remote).start(this::writeLoop);
    }

    /**
     * Queues a message. Never blocks. A client that is too far behind to accept
     * another frame is dropped, since stale quiz frames are worth nothing.
     */
    public void send(Msg msg) {
        if (closed.get()) {
            return;
        }
        if (!outbound.offer(JsonWire.encode(msg))) {
            System.out.println("Dropping slow client " + remote);
            close();
        }
    }

    /** Closes the socket once, however many threads call this. */
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        // Make room for the sentinel first, so the writer wakes even when the
        // queue is full, which is exactly the case when a slow client is dropped.
        outbound.clear();
        outbound.offer(CLOSE_SENTINEL);

        try {
            socket.close();
        } catch (IOException ignored) {
            // Already going away; nothing useful left to do.
        }

        events.accept(new GameEvent.Closed(this));
    }

    private void readLoop() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                // Stamped before decoding, so parsing cost is not charged to the
                // player's answer time.
                long recvNanos = System.nanoTime();
                try {
                    Msg msg = JsonWire.decode(line);
                    events.accept(new GameEvent.Inbound(this, msg, recvNanos));
                } catch (ProtocolException e) {
                    // One unreadable line is not a reason to disconnect a player.
                    send(new Msg.Error("BAD_FRAME", e.getMessage()));
                }
            }
        } catch (IOException e) {
            // Abrupt disconnect, or this side closed the socket. Both end the loop.
        } finally {
            close();
        }
    }

    private void writeLoop() {
        try {
            while (true) {
                String line = outbound.take();
                if (line == CLOSE_SENTINEL) {
                    break;
                }
                out.write(line);
                out.write("\n");
                out.flush();
            }
        } catch (IOException e) {
            // Peer is gone; close() below tears the rest down.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            close();
        }
    }
}
