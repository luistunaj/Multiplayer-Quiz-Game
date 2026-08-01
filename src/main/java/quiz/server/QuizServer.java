package quiz.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public class QuizServer {

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
        int port = 5001;

        System.out.println("Quiz Server");
        printLanAddresses(port);

        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Listening on port: " + port);

            try (Socket socket = server.accept(); BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

                System.out.println("Connected: " + socket.getRemoteSocketAddress());

                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println("[in] " + line);
                }

                System.out.println("Disconnected: " + socket.getRemoteSocketAddress());
            }
        }
    }
}
