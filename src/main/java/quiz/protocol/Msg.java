package quiz.protocol;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Msg.Join.class, name = "JOIN"),
    @JsonSubTypes.Type(value = Msg.Start.class, name = "START"),
    @JsonSubTypes.Type(value = Msg.Answer.class, name = "ANSWER"),
    @JsonSubTypes.Type(value = Msg.Welcome.class, name = "WELCOME"),
    @JsonSubTypes.Type(value = Msg.Lobby.class, name = "LOBBY"),
    @JsonSubTypes.Type(value = Msg.Question.class, name = "QUESTION"),
    @JsonSubTypes.Type(value = Msg.AnswerAck.class, name = "ANSWER_ACK"),
    @JsonSubTypes.Type(value = Msg.Reveal.class, name = "REVEAL"),
    @JsonSubTypes.Type(value = Msg.Scores.class, name = "SCORES"),
    @JsonSubTypes.Type(value = Msg.GameOver.class, name = "GAME_OVER"),
    @JsonSubTypes.Type(value = Msg.Error.class, name = "ERROR")
})
public sealed interface Msg {

    // Client to server.
    record Join(String name) implements Msg {

    }

    record Start() implements Msg {
    }

    record Answer(int questionIndex, int optionIndex) implements Msg {

    }

    // Server to client.
    record Welcome(String playerId, boolean host) implements Msg {

    }

    record Lobby(List<String> players) implements Msg {

    }

    record Question(int index, int total, String text, List<String> options, int limitMs)
            implements Msg {

    }

    record AnswerAck(int questionIndex) implements Msg {

    }

    record Reveal(int questionIndex, int correctIndex, int points, int total) implements Msg {

    }

    record Scores(List<Row> rows) implements Msg {

        public record Row(int rank, String name, int score) {

        }
    }

    record GameOver() implements Msg {
    }

    record Error(String code, String message) implements Msg {

    }
}
