package quiz.client;

import java.util.List;

import quiz.protocol.Msg;

/** Receives decoded messages from {@link ClientCore}. All methods are optional. */
public interface ClientListener {

    default void onWelcome(String playerId, boolean host) {
    }

    default void onLobby(List<String> players) {
    }

    default void onQuestion(Msg.Question question) {
    }

    default void onAnswerAck(int questionIndex) {
    }

    default void onReveal(Msg.Reveal reveal) {
    }

    default void onScores(List<Msg.Scores.Row> rows) {
    }

    default void onGameOver() {
    }

    default void onError(String code, String message) {
    }

    default void onDisconnected(String reason) {
    }
}
