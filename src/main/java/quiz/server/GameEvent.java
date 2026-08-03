package quiz.server;

import quiz.protocol.Msg;

public sealed interface GameEvent {

    /** A message arrived from a player. */
    record Inbound(ClientConnection conn, Msg msg, long recvNanos) implements GameEvent {
    }

    /** A player's connection ended, cleanly or otherwise. */
    record Closed(ClientConnection conn) implements GameEvent {
    }

    /** A question ran out of time. Carries the index so a late timer is ignored. */
    record Timeout(int questionIndex) implements GameEvent {
    }

    /** The pause after showing the answer is over. */
    record Advance(int fromIndex) implements GameEvent {
    }
}
