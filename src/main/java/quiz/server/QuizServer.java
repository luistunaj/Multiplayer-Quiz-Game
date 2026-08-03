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

    private static void onEvent(GameEvent event) {
        switch (event) {
            case GameEvent.Inbound inbound -> {
                System.out.println("[in] " + inbound.conn().remote() + " " + inbound.msg());
                inbound.conn().send(inbound.msg());
            }
            case GameEvent.Closed closed ->
                System.out.println("Disconnected: " + closed.conn().remote());
            case GameEvent.Timeout unused -> {
                // No questions yet.
            }
            case GameEvent.Advance unused -> {
                // No questions yet.
            }
        }
    }

    public static void main(String[] args) throws IOException {
        System.out.println("Quiz Server");
        printLanAddresses(PORT);

        try (ServerSocket server = new ServerSocket(PORT)) {
            System.out.println("Listening on port: " + PORT);

            while (true) {
                Socket socket = server.accept();
                System.out.println("Connected: " + socket.getRemoteSocketAddress());

                try {
                    new ClientConnection(socket, QuizServer::onEvent).start();
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
