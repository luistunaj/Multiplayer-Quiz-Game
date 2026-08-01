package quiz.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

public class ConsoleClient {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5001;

    private static void readFromServer(Socket socket) {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("< " + line);
            }
            System.out.println("Server closed the connection.");
        } catch (IOException e) {
            // Expected when the main thread quits and closes the socket underneath us.
            if (!socket.isClosed()) {
                System.out.println("Connection lost: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;

        try (Socket socket = new Socket(host, port); 
            BufferedWriter out = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)); 
            BufferedReader keyboard = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {

            System.out.println("Connected to " + host + ":" + port + ". Type 'quit' to exit.");
            Thread.ofVirtual().start(() -> readFromServer(socket));

            String line;
            while ((line = keyboard.readLine()) != null) {
                if (line.equals("quit")) {
                    break;
                }
                out.write(line);
                out.write("\n");
                out.flush();
            }
        } catch (ConnectException e) {
            System.out.println("No server listening at " + host + ":" + port);
        } catch (UnknownHostException e) {
            System.out.println("Unknown host: " + host);
        } catch (IOException e) {
            System.out.println("Connection failed: " + e.getMessage());
        }
    }
}
