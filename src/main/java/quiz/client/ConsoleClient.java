package quiz.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import quiz.protocol.Msg;

public final class ConsoleClient implements ClientListener {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5001;

    private ClientCore core;
    private int currentQuestion = -1;
    private int optionCount;

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;

        new ConsoleClient().run(host, port);
    }

    private void run(String host, int port) {
        core = new ClientCore(host, port, this);

        try {
            core.connect();
        } catch (ConnectException e) {
            System.out.println("No server listening at " + host + ":" + port);
            return;
        } catch (UnknownHostException e) {
            System.out.println("Unknown host: " + host);
            return;
        } catch (IOException e) {
            System.out.println("Could not connect: " + e.getMessage());
            return;
        }

        System.out.println("Connected to " + host + ":" + port);
        System.out.println("Commands: join <name> | start | <option number> | quit");

        readCommands();
        core.close();
    }

    private void readCommands() {
        BufferedReader keyboard =
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = keyboard.readLine()) != null) {
                if (!handleCommand(line.trim())) {
                    return;
                }
            }
        } catch (IOException e) {
            System.out.println("Input closed: " + e.getMessage());
        }
    }

    /** Returns false when the user wants to quit. */
    private boolean handleCommand(String command) {
        if (command.isEmpty()) {
            return true;
        }
        if (command.equals("quit")) {
            return false;
        }
        if (command.equals("start")) {
            core.send(new Msg.Start());
            return true;
        }
        if (command.startsWith("join ")) {
            core.send(new Msg.Join(command.substring(5).trim()));
            return true;
        }

        Integer choice = parseChoice(command);
        if (choice != null) {
            // Players see options numbered from 1; the protocol counts from 0.
            core.send(new Msg.Answer(currentQuestion, choice - 1));
            return true;
        }

        System.out.println("Unknown command. Try: join <name> | start | <option number> | quit");
        return true;
    }

    private Integer parseChoice(String command) {
        int choice;
        try {
            choice = Integer.parseInt(command);
        } catch (NumberFormatException e) {
            return null;
        }

        if (currentQuestion < 0) {
            System.out.println("No question to answer yet.");
            return null;
        }
        if (choice < 1 || choice > optionCount) {
            System.out.println("Pick a number between 1 and " + optionCount + ".");
            return null;
        }
        return choice;
    }

    @Override
    public void onWelcome(String playerId, boolean host) {
        System.out.println(host
                ? "Joined as host. Type 'start' when everyone is ready."
                : "Joined. Waiting for the host to start.");
    }

    @Override
    public void onLobby(List<String> players) {
        System.out.println("Players: " + String.join(", ", players));
    }

    @Override
    public void onQuestion(Msg.Question question) {
        currentQuestion = question.index();
        optionCount = question.options().size();

        System.out.println();
        System.out.println("Question " + (question.index() + 1) + "/" + question.total()
                + "  (" + question.limitMs() / 1000 + "s)");
        System.out.println(question.text());

        List<String> options = question.options();
        for (int i = 0; i < options.size(); i++) {
            System.out.println("  " + (i + 1) + ") " + options.get(i));
        }
    }

    @Override
    public void onAnswerAck(int questionIndex) {
        System.out.println("Answer locked in.");
    }

    @Override
    public void onReveal(Msg.Reveal reveal) {
        currentQuestion = -1;
        System.out.println("Correct answer: " + (reveal.correctIndex() + 1));
        System.out.println(reveal.points() > 0
                ? "+" + reveal.points() + " points (total " + reveal.total() + ")"
                : "No points (total " + reveal.total() + ")");
    }

    @Override
    public void onScores(List<Msg.Scores.Row> rows) {
        System.out.println("Scores:");
        for (Msg.Scores.Row row : rows) {
            System.out.println("  " + row.rank() + ". " + row.name() + "  " + row.score());
        }
    }

    @Override
    public void onGameOver() {
        System.out.println();
        System.out.println("Game over. Type 'quit' to exit.");
    }

    @Override
    public void onError(String code, String message) {
        System.out.println("! " + code + ": " + message);
    }

    @Override
    public void onDisconnected(String reason) {
        System.out.println("Disconnected: " + reason);
    }
}
