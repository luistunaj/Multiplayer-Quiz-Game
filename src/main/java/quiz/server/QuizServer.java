package quiz.server;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collections;

public class QuizServer {

    private static final int PORT = 5001;

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

    public static void main(String[] args) throws IOException {
        System.out.println("Quiz Server");
        printLanAddresses(PORT);

        GameSession session = new GameSession();
        // The single thread that owns all game state. Every other thread reaches
        // it by posting events, never by touching its fields.
        Thread.ofVirtual().name("game-session").start(session);

        try (ServerSocket server = new ServerSocket(PORT)) {
            System.out.println("Listening on port: " + PORT);

            while (true) {
                Socket socket = server.accept();
                System.out.println("Connected: " + socket.getRemoteSocketAddress());

                try {
                    new ClientConnection(socket, session::post).start();
                } catch (IOException e) {
                    // One connection failing to set up must not stop the server
                    // from accepting the next one.
                    System.out.println("Could not start connection: " + e.getMessage());
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                        // Nothing useful left to do.
                    }
                }
            }
        }
    }
}
