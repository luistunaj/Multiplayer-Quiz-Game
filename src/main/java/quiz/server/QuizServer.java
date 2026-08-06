package quiz.server;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collections;

import quiz.model.QuestionBank;

public final class QuizServer implements AutoCloseable {

    private static final int DEFAULT_PORT = 5001;

    private final ServerSocket server;
    private final GameSession session;

    /** Port 0 lets the operating system pick a free port, which is what tests use. */
    public QuizServer(int port, GameSession session) throws IOException {
        this.server = new ServerSocket(port);
        this.session = session;
    }

    public int port() {
        return server.getLocalPort();
    }

    /** Starts the game thread and the accept loop, then returns. */
    public void start() {
        Thread.ofVirtual().name("game-session").start(session);
        Thread.ofVirtual().name("accept-loop").start(this::acceptLoop);
    }

    @Override
    public void close() throws IOException {
        server.close();
    }

    private void acceptLoop() {
        while (!server.isClosed()) {
            try {
                Socket socket = server.accept();
                System.out.println("Connected: " + socket.getRemoteSocketAddress());
                new ClientConnection(socket, session::post).start();
            } catch (IOException e) {
                if (server.isClosed()) {
                    return;
                }
                // One failed connection must not stop the next one.
                System.out.println("Could not accept connection: " + e.getMessage());
            }
        }
    }

    private static void printLanAddresses(int port) throws SocketException {
        for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!nif.isUp() || nif.isLoopback()) {
                continue;
            }
            for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                if (!(addr instanceof Inet4Address)) {
                    continue;
                }
                System.out.println("Join with: " + addr.getHostAddress() + " port " + port);
            }
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;

        QuestionBank bank = QuestionBank.loadDefault();
        System.out.println("Quiz Server");
        System.out.println("Loaded " + bank.size() + " questions");
        printLanAddresses(port);

        QuizServer server = new QuizServer(port, new GameSession(bank));
        server.start();
        System.out.println("Listening on port: " + server.port());

        // Without this, Ctrl-C can leave the port bound and the next run fails.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.close();
            } catch (IOException ignored) {
                // Shutting down anyway.
            }
        }));

        // Keep the process alive; the accept loop runs on a virtual thread.
        Thread.currentThread().join();
    }
}
