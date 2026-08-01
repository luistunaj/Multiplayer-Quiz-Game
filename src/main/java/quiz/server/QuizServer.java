package quiz.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QuizServer {

    private static final ExecutorService POOL = Executors.newVirtualThreadPerTaskExecutor();

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

    private static void handle(Socket socket) {
        // Captured before the socket closes; getRemoteSocketAddress() returns null afterwards.
        String remote = String.valueOf(socket.getRemoteSocketAddress());
        System.out.println("Connected: " + remote);

        try (socket;
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[in] " + remote + " " + line);
                out.write(line);
                out.write("\n");
                out.flush();
            }
        } catch (IOException e) {
            // Abrupt disconnect (peer killed, cable pulled). Normal in practice.
            System.out.println("Connection error " + remote + ": " + e.getMessage());
        }

        System.out.println("Disconnected: " + remote);
    }

    public static void main(String[] args) throws IOException {
        int port = 5001;

        System.out.println("Quiz Server");
        printLanAddresses(port);

        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Listening on port: " + port);

            while (true) {
                Socket socket = server.accept();
                POOL.submit(() -> handle(socket));
            }
        }
    }
}
