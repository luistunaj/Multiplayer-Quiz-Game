package quiz.server;

final class Player {

    final String id;
    final String name;

    ClientConnection conn;
    int score;
    boolean host;
    boolean connected = true;

    /** Index of the question this player has already answered; -1 for none yet. */
    int answeredForQuestion = -1;

    /** Points from the current question, for the reveal message. */
    int lastQuestionPoints;

    Player(String id, String name, ClientConnection conn) {
        this.id = id;
        this.name = name;
        this.conn = conn;
    }
}
