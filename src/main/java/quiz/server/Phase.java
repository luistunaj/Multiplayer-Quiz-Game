package quiz.server;

/** Where a game currently is. Only {@link GameSession} changes this. */
public enum Phase {

    /** Waiting for players. Only the lobby accepts new joins. */
    LOBBY,

    /** A question is on screen and answers are being taken. */
    QUESTION,

    /** The answer has been shown and the next question is pending. */
    REVEAL,

    /** No questions left. */
    FINISHED
}
