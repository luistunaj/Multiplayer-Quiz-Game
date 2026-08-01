package quiz.server;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
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

    public static void main(String[] args) throws SocketException {
        System.out.println("Quiz Server");
        printLanAddresses(5000);
    }
}
